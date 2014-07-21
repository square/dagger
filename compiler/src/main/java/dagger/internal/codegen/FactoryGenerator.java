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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import dagger.Factory;
import dagger.MembersInjector;
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
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;

import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

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
  JavaWriter write(ClassName generatedTypeName, ProvisionBinding binding) {
    TypeName providedTypeName = TypeNames.forTypeMirror(binding.providedKey().type());
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter factoryWriter = writer.addClass(generatedTypeName.simpleName());
    factoryWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getName());
    factoryWriter.addModifiers(PUBLIC, FINAL);
    factoryWriter.addImplementedType(ParameterizedTypeName.create(
        ClassName.fromClass(Factory.class),
        providedTypeName));


    MethodWriter getMethodWriter = factoryWriter.addMethod(binding.providedKey().type(), "get");
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

    ImmutableMap<FrameworkKey, String> names =
        SourceFiles.generateFrameworkReferenceNamesForDependencies(binding.dependencies());

    for (Entry<FrameworkKey, String> nameEntry : names.entrySet()) {
      final FieldWriter field;
      if (nameEntry.getKey().frameworkClass().equals(Provider.class)) {
        ParameterizedTypeName providerType = ParameterizedTypeName.create(
            ClassName.fromClass(Provider.class),
            TypeNames.forTypeMirror(nameEntry.getKey().key().type()));
        field = factoryWriter.addField(providerType, nameEntry.getValue());
      } else if (nameEntry.getKey().frameworkClass().equals(MembersInjector.class)) {
        ParameterizedTypeName membersInjectorType = ParameterizedTypeName.create(
            ClassName.fromClass(MembersInjector.class),
            TypeNames.forTypeMirror(nameEntry.getKey().key().type()));
        field = factoryWriter.addField(membersInjectorType, nameEntry.getValue());
      } else {
        throw new IllegalStateException();
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
          names.get(FrameworkKey.forDependencyRequest(dependency)), dependency.kind()));
    }
    Snippet parametersSnippet = makeParametersSnippet(parameters);

    if (binding.bindingKind().equals(PROVISION)) {
      switch (binding.provisionType()) {
        case UNIQUE:
        case SET_VALUES:
          getMethodWriter.body().addSnippet("return module.%s(%s);",
              binding.bindingElement().getSimpleName(), parametersSnippet);
          break;
        case SET:
          getMethodWriter.body().addSnippet("return %s.singleton(module.%s(%s));",
              ClassName.fromClass(Collections.class),
              binding.bindingElement().getSimpleName(), parametersSnippet);
          break;
        default:
          throw new AssertionError();
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
    return writer;
  }
}
