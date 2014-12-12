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
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Provides.Type;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.producers.Producer;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import static dagger.internal.codegen.SourceFiles.factoryNameForProductionBinding;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link Producer} implementations from {@link ProductionBinding} instances.
 *
 * @author Jesse Beder
 * @since 2.0
 */
final class ProducerFactoryGenerator extends SourceFileGenerator<ProductionBinding> {
  private final DependencyRequestMapper dependencyRequestMapper;

  ProducerFactoryGenerator(Filer filer, DependencyRequestMapper dependencyRequestMapper) {
    super(filer);
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  ClassName nameGeneratedType(ProductionBinding binding) {
    return factoryNameForProductionBinding(binding);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(ProductionBinding binding) {
    return ImmutableSet.of(binding.bindingElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProductionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, ProductionBinding binding) {
    TypeMirror keyType = binding.productionType().equals(Type.MAP)
        ? Util.getProvidedValueTypeOfMap(MoreTypes.asDeclared(binding.key().type()))
        : binding.key().type();
    TypeName providedTypeName = TypeNames.forTypeMirror(keyType);
    TypeName futureTypeName = ParameterizedTypeName.create(
        ClassName.fromClass(ListenableFuture.class), providedTypeName);
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter factoryWriter = writer.addClass(generatedTypeName.simpleName());
    ConstructorWriter constructorWriter = factoryWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);

    factoryWriter.addField(binding.bindingTypeElement(), "module")
        .addModifiers(PRIVATE, FINAL);
    constructorWriter.addParameter(binding.bindingTypeElement(), "module");
    constructorWriter.body()
        .addSnippet("assert module != null;")
        .addSnippet("this.module = module;");

    factoryWriter.addField(Executor.class, "executor")
        .addModifiers(PRIVATE, FINAL);
    constructorWriter.addParameter(Executor.class, "executor");
    constructorWriter.body()
        .addSnippet("assert executor != null;")
        .addSnippet("this.executor = executor;");

    factoryWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getName());
    factoryWriter.addModifiers(PUBLIC);
    factoryWriter.addModifiers(FINAL);
    factoryWriter.addImplementedType(
        ParameterizedTypeName.create(Producer.class, providedTypeName));

    MethodWriter getMethodWriter = factoryWriter.addMethod(futureTypeName, "get");
    getMethodWriter.annotate(Override.class);
    getMethodWriter.addModifiers(PUBLIC);

    ImmutableMap<FrameworkKey, String> names =
        SourceFiles.generateFrameworkReferenceNamesForDependencies(
            dependencyRequestMapper, binding.dependencies());

    for (Entry<FrameworkKey, String> nameEntry : names.entrySet()) {
      ParameterizedTypeName fieldType = nameEntry.getKey().frameworkType();
      FieldWriter field = factoryWriter.addField(fieldType, nameEntry.getValue());
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body()
          .addSnippet("assert %s != null;", field.name())
          .addSnippet("this.%1$s = %1$s;", field.name());
    }

    // TODO(user): Implement this method.
    getMethodWriter.body().addSnippet("return null;");

    // TODO(gak): write a sensible toString
    return ImmutableSet.of(writer);
  }
}
