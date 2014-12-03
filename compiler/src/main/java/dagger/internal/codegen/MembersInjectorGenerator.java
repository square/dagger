/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.MembersInjector;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.VoidName;
import java.util.Map.Entry;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final Elements elements;
  private final Types types;

  MembersInjectorGenerator(Filer filer, Elements elements, Types types) {
    super(filer);
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  @Override
  ClassName nameGeneratedType(MembersInjectionBinding binding) {
    return SourceFiles.membersInjectorNameForMembersInjectionBinding(binding);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(
      MembersInjectionBinding binding) {
    return FluentIterable.from(binding.injectionSites())
        .transform(new Function<InjectionSite, Element>() {
          @Override public Element apply(InjectionSite injectionSite) {
            return injectionSite.element();
          }
        })
        .toSet();
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(MembersInjectionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName injectorClassName, MembersInjectionBinding binding) {
    ClassName injectedClassName = ClassName.fromTypeElement(binding.bindingElement());

    JavaWriter writer = JavaWriter.inPackage(injectedClassName.packageName());

    ClassWriter injectorWriter = writer.addClass(injectorClassName.simpleName());
    injectorWriter.annotate(Generated.class)
        .setValue(ComponentProcessor.class.getCanonicalName());
    injectorWriter.addModifiers(PUBLIC, FINAL);
    injectorWriter.addImplementedType(
        ParameterizedTypeName.create(MembersInjector.class, injectedClassName));

    ConstructorWriter constructorWriter = injectorWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);
    MethodWriter injectMembersWriter = injectorWriter.addMethod(VoidName.VOID, "injectMembers");
    injectMembersWriter.addModifiers(PUBLIC);
    injectMembersWriter.annotate(Override.class);
    injectMembersWriter.addParameter(injectedClassName, "instance");
    injectMembersWriter.body().addSnippet(Joiner.on('\n').join(
        "if (instance == null) {",
        "  throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "}"));

    Optional<TypeElement> supertype = supertype(binding.bindingElement());
    if (supertype.isPresent()) {
      ParameterizedTypeName supertypeMemebersInjectorType = ParameterizedTypeName.create(
          MembersInjector.class, ClassName.fromTypeElement(supertype.get()));
      injectorWriter
          .addField(supertypeMemebersInjectorType, "supertypeInjector")
          .addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(supertypeMemebersInjectorType, "supertypeInjector");
      constructorWriter.body()
          .addSnippet("assert supertypeInjector != null;")
          .addSnippet("this.supertypeInjector = supertypeInjector;");
      injectMembersWriter.body().addSnippet("supertypeInjector.injectMembers(instance);");
    }

    ImmutableMap<FrameworkKey, String> names =
        SourceFiles.generateFrameworkReferenceNamesForDependencies(
            ImmutableSet.copyOf(binding.dependencies()));

    ImmutableMap.Builder<FrameworkKey, FieldWriter> dependencyFieldsBuilder =
        ImmutableMap.builder();

    for (Entry<FrameworkKey, String> nameEntry : names.entrySet()) {
      ParameterizedTypeName fieldType = nameEntry.getKey().frameworkType();
      FieldWriter field = injectorWriter.addField(fieldType, nameEntry.getValue());
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body().addSnippet("assert %s != null;", field.name());
      constructorWriter.body().addSnippet("this.%1$s = %1$s;", field.name());
      dependencyFieldsBuilder.put(nameEntry.getKey(), field);
    }
    ImmutableMap<FrameworkKey, FieldWriter> depedencyFields = dependencyFieldsBuilder.build();
    for (InjectionSite injectionSite : binding.injectionSites()) {
      switch (injectionSite.kind()) {
        case FIELD:
          DependencyRequest fieldDependency =
              Iterables.getOnlyElement(injectionSite.dependencies());
          FieldWriter singleField = depedencyFields.get(
              FrameworkKey.forDependencyRequest(fieldDependency));
          injectMembersWriter.body().addSnippet("instance.%s = %s;",
              injectionSite.element().getSimpleName(),
              frameworkTypeUsageStatement(Snippet.format(singleField.name()),
                  fieldDependency.kind()));
          break;
        case METHOD:
          ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
          for (DependencyRequest methodDependency : injectionSite.dependencies()) {
            FieldWriter field = depedencyFields.get(
                FrameworkKey.forDependencyRequest(methodDependency));
            parameters.add(frameworkTypeUsageStatement(Snippet.format(field.name()),
                methodDependency.kind()));
          }
          injectMembersWriter.body().addSnippet("instance.%s(%s);",
              injectionSite.element().getSimpleName(),
              Snippet.makeParametersSnippet(parameters.build()));
          break;
        default:
          throw new AssertionError();
      }
    }
    return ImmutableSet.of(writer);
  }

  private Optional<TypeElement> supertype(TypeElement type) {
    TypeMirror superclass = type.getSuperclass();
    boolean nonObjectSuperclass = !types.isSameType(
        elements.getTypeElement(Object.class.getCanonicalName()).asType(), superclass);
    return nonObjectSuperclass
        ? Optional.of(MoreElements.asType(types.asElement(superclass)))
        : Optional.<TypeElement>absent();
  }
}
