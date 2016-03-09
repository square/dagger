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

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import static com.google.common.base.Functions.constant;

/**
 * Validates the relationships between parent components and subcomponents.
 */
final class ComponentHierarchyValidator {
  ValidationReport<TypeElement> validate(ComponentDescriptor componentDescriptor) {
    return validateSubcomponentMethods(
        componentDescriptor,
        Maps.toMap(
            componentDescriptor.transitiveModuleTypes(),
            constant(componentDescriptor.componentDefinitionType())));
  }

  private ValidationReport<TypeElement> validateSubcomponentMethods(
      ComponentDescriptor componentDescriptor,
      ImmutableMap<TypeElement, TypeElement> existingModuleToOwners) {
    ValidationReport.Builder<TypeElement> reportBuilder =
        ValidationReport.about(componentDescriptor.componentDefinitionType());
    for (Map.Entry<ComponentMethodDescriptor, ComponentDescriptor> subcomponentEntry :
        componentDescriptor.subcomponents().entrySet()) {
      ComponentMethodDescriptor subcomponentMethodDescriptor = subcomponentEntry.getKey();
      ComponentDescriptor subcomponentDescriptor = subcomponentEntry.getValue();
      // validate the way that we create subcomponents
      switch (subcomponentMethodDescriptor.kind()) {
        case SUBCOMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          for (VariableElement factoryMethodParameter :
              subcomponentMethodDescriptor.methodElement().getParameters()) {
            TypeElement moduleType = MoreTypes.asTypeElement(factoryMethodParameter.asType());
            TypeElement originatingComponent = existingModuleToOwners.get(moduleType);
            if (originatingComponent != null) {
              /* Factory method tries to pass a module that is already present in the parent.
               * This is an error. */
              reportBuilder.addError(
                  String.format(
                      "%s is present in %s. A subcomponent cannot use an instance of a "
                          + "module that differs from its parent.",
                      moduleType.getSimpleName(),
                      originatingComponent.getQualifiedName()),
                  factoryMethodParameter);
            }
          }
          break;
          
        case SUBCOMPONENT_BUILDER:
        case PRODUCTION_SUBCOMPONENT_BUILDER:
          /* A subcomponent builder allows you to pass a module that is already present in the
           * parent.  This can't be an error because it might be valid in _other_ components. Don't
           * bother warning, because there's nothing to do except suppress the warning. */
          break;
          
        default:
          throw new AssertionError();
      }
      reportBuilder.addSubreport(
          validateSubcomponentMethods(
              subcomponentDescriptor,
              new ImmutableMap.Builder<TypeElement, TypeElement>()
                  .putAll(existingModuleToOwners)
                  .putAll(
                      Maps.toMap(
                          Sets.difference(
                              subcomponentDescriptor.transitiveModuleTypes(),
                              existingModuleToOwners.keySet()),
                          constant(subcomponentDescriptor.componentDefinitionType())))
                  .build()));
    }
    return reportBuilder.build();
  }
}
