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
import com.google.auto.common.SuperficialValidation;
import dagger.Component;
import dagger.internal.codegen.ComponentDescriptor.Factory;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that is responsible for dealing with the {@link Component} annotation
 * as part of the {@link ComponentProcessor}.
 *
 * @author Gregory Kick
 */
final class ComponentProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final ComponentValidator componentValidator;
  private final BindingGraphValidator bindingGraphValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraph.Factory bindingGraphFactory;
  private final ComponentGenerator componentGenerator;

  ComponentProcessingStep(
      Messager messager,
      ComponentValidator componentValidator,
      BindingGraphValidator bindingGraphValidator,
      Factory componentDescriptorFactory,
      BindingGraph.Factory bindingGraphFactory,
      ComponentGenerator componentGenerator) {
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.bindingGraphValidator = bindingGraphValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> componentElements = roundEnv.getElementsAnnotatedWith(Component.class);

    for (Element element : componentElements) {
      if (SuperficialValidation.validateElement(element)) {
        TypeElement componentTypeElement = MoreElements.asType(element);
        ValidationReport<TypeElement> componentReport =
            componentValidator.validate(componentTypeElement);
        componentReport.printMessagesTo(messager);
        if (componentReport.isClean()) {
          ComponentDescriptor componentDescriptor =
              componentDescriptorFactory.create(componentTypeElement);
          BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
          ValidationReport<BindingGraph> graphReport =
              bindingGraphValidator.validate(bindingGraph);
          graphReport.printMessagesTo(messager);
          if (graphReport.isClean()) {
            try {
              componentGenerator.generate(bindingGraph);
            } catch (SourceFileGenerationException e) {
              e.printMessageTo(messager);
            }
          }
        }
      }
    }

    return false;
  }
}
