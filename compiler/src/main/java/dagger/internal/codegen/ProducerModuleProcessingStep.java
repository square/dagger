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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static javax.lang.model.element.ElementKind.METHOD;

/**
 * An annotation processor for generating Dagger implementation code based on the
 * {@link ProducerModule} (and {@link Produces}) annotation.
 *
 * @author Jesse Beder
 * @since 2.0
 */
final class ProducerModuleProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final ProducesMethodValidator producesMethodValidator;
  private final Set<Element> processedModuleElements = Sets.newLinkedHashSet();

  ProducerModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      ProducesMethodValidator producesMethodValidator) {
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.producesMethodValidator = producesMethodValidator;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // first, check and collect all produces methods
    ImmutableSet.Builder<ExecutableElement> validProducesMethodsBuilder = ImmutableSet.builder();
    for (Element producesElement : roundEnv.getElementsAnnotatedWith(Produces.class)) {
      if (producesElement.getKind().equals(METHOD)) {
        ExecutableElement producesMethodElement = (ExecutableElement) producesElement;
        ValidationReport<ExecutableElement> methodReport =
            producesMethodValidator.validate(producesMethodElement);
        methodReport.printMessagesTo(messager);
        if (methodReport.isClean()) {
          validProducesMethodsBuilder.add(producesMethodElement);
        }
      }
    }
    @SuppressWarnings("unused")
    ImmutableSet<ExecutableElement> validProducesMethods = validProducesMethodsBuilder.build();

    // process each module
    for (Element moduleElement :
        Sets.difference(roundEnv.getElementsAnnotatedWith(ProducerModule.class),
            processedModuleElements)) {
      if (SuperficialValidation.validateElement(moduleElement)) {
        ValidationReport<TypeElement> report =
            moduleValidator.validate(MoreElements.asType(moduleElement));
        report.printMessagesTo(messager);
        // TODO(user): Validate @Produces methods and generate factories.
        processedModuleElements.add(moduleElement);
      }
    }
    return false;
  }
}
