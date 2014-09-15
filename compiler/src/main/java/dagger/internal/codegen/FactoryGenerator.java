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
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import dagger.Factory;
import dagger.MembersInjector;
import dagger.Provides.Type;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.StringLiteral;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import static dagger.Provides.Type.SET;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for
 * {@link Inject} constructors.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class FactoryGenerator extends SourceFileGenerator<ProvisionBinding> {
  FactoryGenerator(Filer filer) {
    super(filer);
  }

  @Override
  ClassName nameGeneratedType(ProvisionBinding binding) {
    return factoryNameForProvisionBinding(binding);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(ProvisionBinding binding) {
    return ImmutableSet.of(binding.bindingElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProvisionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, ProvisionBinding binding) {
    TypeMirror keyType = binding.provisionType().equals(Type.MAP)
        ? Util.getProvidedValueTypeOfMap(Util.asDeclaredType(binding.key().type()))
        : binding.key().type();
    TypeName providedTypeName = TypeNames.forTypeMirror(keyType);
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter factoryWriter = writer.addClass(generatedTypeName.simpleName());
    factoryWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getName());
    factoryWriter.addModifiers(PUBLIC, FINAL);
    factoryWriter.addImplementedType(ParameterizedTypeName.create(
        ClassName.fromClass(Factory.class),
        providedTypeName));

    // TODO(gak): stop doing this weird thing with the optional when javawriter lets me put fields
    // in arbitrary places
    Optional<FieldWriter> loggerField = Optional.absent();
    if (binding.bindingKind().equals(PROVISION)) {
      loggerField = Optional.of(factoryWriter.addField(Logger.class, "logger"));
      loggerField.get().addModifiers(PRIVATE, STATIC, FINAL);
      loggerField.get().setInitializer("%s.getLogger(%s.class.getCanonicalName())",
          ClassName.fromClass(Logger.class), factoryWriter.name());
    }

    MethodWriter getMethodWriter = factoryWriter.addMethod(keyType, "get");
    getMethodWriter.annotate(Override.class);
    getMethodWriter.addModifiers(PUBLIC);

    ConstructorWriter constructorWriter = factoryWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);
    if (binding.bindingKind().equals(PROVISION)) {
      factoryWriter.addField(binding.bindingTypeElement(), "module").addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(binding.bindingTypeElement(), "module");
      constructorWriter.body()
          .addSnippet("assert module != null;")
          .addSnippet("this.module = module;");
    }

    if (binding.memberInjectionRequest().isPresent()) {
      ParameterizedTypeName membersInjectorType = ParameterizedTypeName.create(
          MembersInjector.class, providedTypeName);
      factoryWriter.addField(membersInjectorType, "membersInjector").addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(membersInjectorType, "membersInjector");
      constructorWriter.body()
          .addSnippet("assert membersInjector != null;")
          .addSnippet("this.membersInjector = membersInjector;");
    }

    ImmutableMap<Key, String> names =
        SourceFiles.generateFrameworkReferenceNamesForDependencies(binding.dependencies());

    for (Entry<Key, String> nameEntry : names.entrySet()) {
      final FieldWriter field;
      switch (nameEntry.getKey().kind()) {
        case PROVIDER:
          ParameterizedTypeName providerType = ParameterizedTypeName.create(
              ClassName.fromClass(Provider.class),
              TypeNames.forTypeMirror(nameEntry.getKey().type()));
          field = factoryWriter.addField(providerType, nameEntry.getValue());
          break;
        case MEMBERS_INJECTOR:
          ParameterizedTypeName membersInjectorType = ParameterizedTypeName.create(
              ClassName.fromClass(MembersInjector.class),
              TypeNames.forTypeMirror(nameEntry.getKey().type()));
          field = factoryWriter.addField(membersInjectorType, nameEntry.getValue());
          break;
        default:
          throw new AssertionError();
      }
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body()
          .addSnippet("assert %s != null;", field.name())
          .addSnippet("this.%1$s = %1$s;", field.name());
    }

    List<Snippet> parameters = Lists.newArrayList();
    for (DependencyRequest dependency : binding.dependencies()) {
      parameters.add(frameworkTypeUsageStatement(
          Snippet.format(names.get(dependency.key())),
          dependency.kind()));
    }
    Snippet parametersSnippet = makeParametersSnippet(parameters);

    if (binding.bindingKind().equals(PROVISION)) {
      TypeMirror providesMethodReturnType =
          MoreElements.asExecutable(binding.bindingElement()).getReturnType();
      getMethodWriter.body().addSnippet("%s result = module.%s(%s);",
          TypeNames.forTypeMirror(providesMethodReturnType),
          binding.bindingElement().getSimpleName(), parametersSnippet);
      if (!providesMethodReturnType.getKind().isPrimitive()) {
        getMethodWriter.body().addSnippet(Joiner.on('\n').join(
            "if (result == null) {",
            "  %s.warning(%s);",
            "}"),
            loggerField.get().name(),
            StringLiteral.forValue(String.format(
                "%s.%s provided null. "
                    + "This is not allowed and will soon throw a NullPointerException.",
                    binding.bindingTypeElement().getQualifiedName(),
                    binding.bindingElement())));
      }
      if (binding.provisionType().equals(SET)) {
        getMethodWriter.body().addSnippet("return %s.singleton(result);",
            ClassName.fromClass(Collections.class));
      } else {
        getMethodWriter.body().addSnippet("return result;");
      }
    } else if (binding.memberInjectionRequest().isPresent()) {
      getMethodWriter.body().addSnippet("%1$s instance = new %1$s(%2$s);",
          providedTypeName, parametersSnippet);
      getMethodWriter.body().addSnippet("membersInjector.injectMembers(instance);");
      getMethodWriter.body().addSnippet("return instance;");
    } else {
      getMethodWriter.body()
          .addSnippet("return new %s(%s);", providedTypeName, parametersSnippet);
    }

    // TODO(gak): write a sensible toString
    return ImmutableSet.of(writer);
  }
}
