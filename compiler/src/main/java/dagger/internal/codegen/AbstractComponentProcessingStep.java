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

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that is responsible for dealing with a component or production component
 * as part of the {@link ComponentProcessor}.
 */
abstract class AbstractComponentProcessingStep implements ProcessingStep {

  private final Class<? extends Annotation> componentAnnotation;
  private final Messager messager;
  private final ComponentHierarchyValidator componentHierarchyValidator;
  private final BindingGraphValidator bindingGraphValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraph.Factory bindingGraphFactory;
  private final ComponentGenerator componentGenerator;

  AbstractComponentProcessingStep(
      Class<? extends Annotation> componentAnnotation,
      Messager messager,
      ComponentHierarchyValidator componentHierarchyValidator,
      BindingGraphValidator bindingGraphValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraph.Factory bindingGraphFactory,
      ComponentGenerator componentGenerator) {
    this.componentAnnotation = componentAnnotation;
    this.messager = messager;
    this.componentHierarchyValidator = componentHierarchyValidator;
    this.bindingGraphValidator = bindingGraphValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
  }

  @Override
  public final ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();
    ComponentElementValidator componentElementValidator =
        componentElementValidator(elementsByAnnotation);
    for (Element element : elementsByAnnotation.get(componentAnnotation)) {
      TypeElement componentTypeElement = MoreElements.asType(element);
      try {
        if (componentElementValidator.validateComponent(componentTypeElement, messager)) {
          ComponentDescriptor componentDescriptor =
              componentDescriptorFactory.forComponent(componentTypeElement);
          ValidationReport<TypeElement> hierarchyReport =
              componentHierarchyValidator.validate(componentDescriptor);
          hierarchyReport.printMessagesTo(messager);
          if (hierarchyReport.isClean()) {
            BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
            ValidationReport<TypeElement> graphReport =
                bindingGraphValidator.validate(bindingGraph);
            graphReport.printMessagesTo(messager);
            if (graphReport.isClean()) {
              generateComponent(bindingGraph);
            }
          }
        }
      } catch (TypeNotPresentException e) {
        rejectedElements.add(componentTypeElement);
      }
    }
    return rejectedElements.build();
  }

  private void generateComponent(BindingGraph bindingGraph) {
    try {
      componentGenerator.generate(bindingGraph);
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(messager);
    }
  }

  /**
   * Returns an object that can validate a type element annotated with the component type.
   */
  protected abstract ComponentElementValidator componentElementValidator(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation);

  /**
   * Validates a component type element.
   */
  protected static abstract class ComponentElementValidator {
    /**
     * Validates a component type element. Prints any messages about the element to
     * {@code messager}.
     *
     * @throws TypeNotPresentException if any type required to validate the component cannot be
     *     found
     */
    abstract boolean validateComponent(TypeElement componentTypeElement, Messager messager);
  }
}
