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

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that is responsible for dealing with the {@link ProductionComponent}
 * annotation as part of the {@link ComponentProcessor}.
 *
 * @author Jesse Beder
 */
final class ProductionComponentProcessingStep extends AbstractComponentProcessingStep {
  private final Messager messager;
  private final ProductionComponentValidator componentValidator;
  private final BuilderValidator componentBuilderValidator;

  ProductionComponentProcessingStep(
      Messager messager,
      ProductionComponentValidator componentValidator,
      BuilderValidator componentBuilderValidator,
      ComponentHierarchyValidator componentHierarchyValidator,
      BindingGraphValidator bindingGraphValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraph.Factory bindingGraphFactory,
      ComponentGenerator componentGenerator) {
    super(
        ProductionComponent.class,
        messager,
        componentHierarchyValidator,
        bindingGraphValidator,
        componentDescriptorFactory,
        bindingGraphFactory,
        componentGenerator);
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.componentBuilderValidator = componentBuilderValidator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(
        ProductionComponent.class, ProductionComponent.Builder.class);
  }

  // TODO(beder): Move common logic into the AbstractComponentProcessingStep when implementing
  // production subcomponents.
  @Override
  protected ComponentElementValidator componentElementValidator(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    final Map<Element, ValidationReport<TypeElement>> builderReportsByComponent =
        processComponentBuilders(elementsByAnnotation.get(ProductionComponent.Builder.class));
    return new ComponentElementValidator() {
      @Override
      boolean validateComponent(TypeElement componentTypeElement, Messager messager) {
        ValidationReport<TypeElement> validationReport =
            componentValidator.validate(componentTypeElement);
        validationReport.printMessagesTo(messager);
        if (!validationReport.isClean()) {
          return false;
        }
        ValidationReport<?> builderReport = builderReportsByComponent.get(componentTypeElement);
        return builderReport == null || builderReport.isClean();
      }
    };
  }

  private Map<Element, ValidationReport<TypeElement>> processComponentBuilders(
      Set<? extends Element> componentBuilderElements) {
    Map<Element, ValidationReport<TypeElement>> builderReportsByComponent = Maps.newHashMap();
    for (Element element : componentBuilderElements) {
      ValidationReport<TypeElement> report =
          componentBuilderValidator.validate(MoreElements.asType(element));
      report.printMessagesTo(messager);
      builderReportsByComponent.put(element.getEnclosingElement(), report);
    }
    return builderReportsByComponent;
  }
}
