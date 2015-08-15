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
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.internal.codegen.writer.TypeVariableName;
import dagger.internal.codegen.writer.VoidName;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.SourceFiles.parameterizedMembersInjectorNameForMembersInjectionBinding;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final DependencyRequestMapper dependencyRequestMapper;

  MembersInjectorGenerator(
      Filer filer,
      DependencyRequestMapper dependencyRequestMapper) {
    super(filer);
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  ClassName nameGeneratedType(MembersInjectionBinding binding) {
    return membersInjectorNameForType(binding.bindingElement());
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
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, MembersInjectionBinding binding) {
    Set<String> delegateMethods = new HashSet<>();

    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkState(!binding.hasNonDefaultTypeParameters()); 

    TypeName injectedTypeName = TypeNames.forTypeMirror(binding.key().type());
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter injectorWriter = writer.addClass(generatedTypeName.simpleName());
    List<TypeVariableName> typeParameters = Lists.newArrayList();
    for (TypeParameterElement typeParameter : binding.bindingTypeElement().getTypeParameters()) {
      typeParameters.add(TypeVariableName.fromTypeParameterElement(typeParameter));
    }
    injectorWriter.addTypeParameters(typeParameters);
    injectorWriter.annotate(Generated.class)
        .setValue(ComponentProcessor.class.getCanonicalName());
    injectorWriter.addModifiers(PUBLIC, FINAL);
    TypeName implementedType =
        ParameterizedTypeName.create(MembersInjector.class, injectedTypeName);
    injectorWriter.addImplementedType(implementedType);

    ConstructorWriter constructorWriter = injectorWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);
    MethodWriter injectMembersWriter = injectorWriter.addMethod(VoidName.VOID, "injectMembers");
    injectMembersWriter.addModifiers(PUBLIC);
    injectMembersWriter.annotate(Override.class);
    injectMembersWriter.addParameter(injectedTypeName, "instance");
    injectMembersWriter.body().addSnippet(Joiner.on('\n').join(
        "if (instance == null) {",
        "  throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "}"));

    ImmutableMap<BindingKey, FrameworkField> fields =
        SourceFiles.generateBindingFieldsForDependencies(
            dependencyRequestMapper, ImmutableSet.copyOf(binding.dependencies()));

    ImmutableMap.Builder<BindingKey, FieldWriter> dependencyFieldsBuilder =
        ImmutableMap.builder();

    for (Entry<BindingKey, FrameworkField> fieldEntry : fields.entrySet()) {
      FrameworkField bindingField = fieldEntry.getValue();
      TypeName fieldType = bindingField.frameworkType();
      FieldWriter field = injectorWriter.addField(fieldType, bindingField.name());
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body().addSnippet("assert %s != null;", field.name());
      constructorWriter.body().addSnippet("this.%1$s = %1$s;", field.name());
      dependencyFieldsBuilder.put(fieldEntry.getKey(), field);
    }
    
    // We use a static create method so that generated components can avoid having
    // to refer to the generic types of the factory.
    // (Otherwise they may have visibility problems referring to the types.)
    MethodWriter createMethodWriter = injectorWriter.addMethod(implementedType, "create");
    createMethodWriter.addTypeParameters(typeParameters);
    createMethodWriter.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
    Map<String, TypeName> params = constructorWriter.parameters();
    for (Map.Entry<String, TypeName> param : params.entrySet()) {
      createMethodWriter.addParameter(param.getValue(), param.getKey());      
    }
    createMethodWriter.body().addSnippet("  return new %s(%s);",
        parameterizedMembersInjectorNameForMembersInjectionBinding(binding),
        Joiner.on(", ").join(params.keySet()));
    
    ImmutableMap<BindingKey, FieldWriter> dependencyFields = dependencyFieldsBuilder.build();
    for (InjectionSite injectionSite : binding.injectionSites()) {
      Element injectionSiteElement = injectionSite.element();
      ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
      if (!visibleTo(binding, injectionSite)) {
        parameters.add(Snippet.format("instance"));
      }
      switch (injectionSite.kind()) {
        case FIELD:
          DependencyRequest fieldDependency =
              Iterables.getOnlyElement(injectionSite.dependencies());
          FieldWriter singleField = dependencyFields.get(fieldDependency.bindingKey());
          parameters.add(
              frameworkTypeUsageStatement(
                  Snippet.format("%s", singleField.name()), fieldDependency.kind()));
          break;
        case METHOD:
          for (DependencyRequest methodDependency : injectionSite.dependencies()) {
            FieldWriter field = dependencyFields.get(methodDependency.bindingKey());
            parameters.add(
                frameworkTypeUsageStatement(
                    Snippet.format("%s", field.name()), methodDependency.kind()));
          }
          break;
        default:
          throw new AssertionError();
      }
      injectMembersWriter
          .body()
          .addSnippet(
              injectMemberSnippet(
                  injectionSite, binding, Snippet.makeParametersSnippet(parameters.build())));
      if (!injectionSiteElement.getModifiers().contains(PUBLIC)
          && injectionSiteElement.getEnclosingElement().equals(binding.bindingElement())
          && delegateMethods.add(injectionSiteDelegateMethodName(injectionSiteElement))) {
        writeInjectorMethodForSubclasses(
            injectorWriter,
            typeParameters,
            injectedTypeName,
            injectionSiteElement,
            injectionSite.dependencies());
      }
    }
    return ImmutableSet.of(writer);
  }
  
  private boolean visibleTo(Binding binding, InjectionSite injectionSite) {
    return MoreElements.getPackage(injectionSite.element())
            .equals(MoreElements.getPackage(binding.bindingElement()))
        || injectionSite.element().getModifiers().contains(PUBLIC);
  }

  private Snippet injectMemberSnippet(
      InjectionSite injectionSite, MembersInjectionBinding binding, Snippet parametersSnippet) {

    if (visibleTo(binding, injectionSite)) {
      return Snippet.format(
          (injectionSite.element().getKind().isField()) ? "%s.%s = %s;" : "%s.%s(%s);",
          getInstanceSnippetWithPotentialCast(
              injectionSite.element().getEnclosingElement(), binding.bindingElement()),
          injectionSite.element().getSimpleName(),
          parametersSnippet);
    } else {
      return Snippet.format(
          "%s.%s(%s);",
          membersInjectorNameForType(
              MoreElements.asType(injectionSite.element().getEnclosingElement())),
          injectionSiteDelegateMethodName(injectionSite.element()),
          parametersSnippet);
    }
  }

  private Snippet getInstanceSnippetWithPotentialCast(
      Element injectionSiteElement, Element bindingElement) {
    return (injectionSiteElement.equals(bindingElement))
        ? Snippet.format("instance")
        : Snippet.format("((%s)instance)", injectionSiteElement);
  }

  private String injectionSiteDelegateMethodName(Element injectionSiteElement) {
    return "inject"
        + CaseFormat.LOWER_CAMEL.to(
            CaseFormat.UPPER_CAMEL, injectionSiteElement.getSimpleName().toString());
  }

  private void writeInjectorMethodForSubclasses(
      ClassWriter injectorWriter,
      List<TypeVariableName> typeParameters,
      TypeName injectedTypeName,
      Element injectionElement,
      ImmutableSet<DependencyRequest> dependencies) {
    MethodWriter methodWriter =
        injectorWriter.addMethod(VoidName.VOID, injectionSiteDelegateMethodName(injectionElement));
    methodWriter.addModifiers(PUBLIC, STATIC);
    methodWriter.addParameter(injectedTypeName, "instance");
    methodWriter.addTypeParameters(typeParameters);
    ImmutableList.Builder<Snippet> providedParameters = ImmutableList.builder();
    for (DependencyRequest methodDependency : dependencies) {
      Element requestElement = methodDependency.requestElement();
      methodWriter.addParameter(
          TypeNames.forTypeMirror(requestElement.asType()), requestElement.toString());
      providedParameters.add(Snippet.format("%s", requestElement));
    }
    if (injectionElement.getKind().isField()) {
      methodWriter
          .body()
          .addSnippet(
              "instance.%s = %s;",
              injectionElement.getSimpleName(),
              Iterables.getOnlyElement(providedParameters.build()));
    } else {
      methodWriter
          .body()
          .addSnippet(
              "instance.%s(%s);",
              injectionElement.getSimpleName(),
              Snippet.makeParametersSnippet(providedParameters.build()));
    }
  }
}
