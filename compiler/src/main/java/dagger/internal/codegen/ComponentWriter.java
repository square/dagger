/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import javax.annotation.Generated;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Creates the implementation class for a component.
 */
class ComponentWriter extends AbstractComponentWriter {

  ComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      Kind nullableValidationType,
      ClassName name,
      BindingGraph graph) {
    super(types, elements, keyFactory, nullableValidationType, name, graph);
  }

  @Override
  protected ClassWriter createComponentClass() {
    JavaWriter javaWriter = JavaWriter.inPackage(name.packageName());
    javaWriters.add(javaWriter);

    ClassWriter componentWriter = javaWriter.addClass(name.simpleName());
    componentWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getCanonicalName());
    componentWriter.addModifiers(PUBLIC, FINAL);
    componentWriter.setSupertype(componentDefinitionType());
    return componentWriter;
  }

  @Override
  protected ClassWriter createBuilder() {
    ClassWriter builderWriter = componentWriter.addNestedClass("Builder");
    builderWriter.addModifiers(STATIC);

    // Only top-level components have the factory builder() method.
    // Mirror the user's builder API type if they had one.
    MethodWriter builderFactoryMethod =
        graph.componentDescriptor().builderSpec().isPresent()
            ? componentWriter.addMethod(
                graph
                    .componentDescriptor()
                    .builderSpec()
                    .get()
                    .builderDefinitionType()
                    .asType(),
                "builder")
            : componentWriter.addMethod(builderWriter, "builder");
    builderFactoryMethod.addModifiers(PUBLIC, STATIC);
    builderFactoryMethod.body().addSnippet("return new %s();", builderWriter.name());
    return builderWriter;
  }

  @Override
  protected void addFactoryMethods() {
    if (canInstantiateAllRequirements()) {
      MethodWriter factoryMethod =
          componentWriter.addMethod(componentDefinitionTypeName(), "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod
          .body()
          .addSnippet(
              "return builder().%s();",
              graph.componentDescriptor().builderSpec().isPresent()
                  ? graph
                      .componentDescriptor()
                      .builderSpec()
                      .get()
                      .buildMethod()
                      .getSimpleName()
                  : "build");
    }
  }

  /** {@code true} if all of the graph's required dependencies can be automatically constructed. */
  private boolean canInstantiateAllRequirements() {
    return Iterables.all(
        graph.componentRequirements(),
        new Predicate<TypeElement>() {
          @Override
          public boolean apply(TypeElement dependency) {
            return componentCanMakeNewInstances(dependency);
          }
        });
  }
}
