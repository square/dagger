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

import com.google.common.collect.ImmutableList;
import dagger.Component;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * Performs superficial validation of the contract of the {@link Component} annotation.
 *
 * @author Gregory Kick
 */
final class ComponentValidator implements Validator<TypeElement> {
  private final ModuleValidator moduleValidator;
  
  ComponentValidator(ModuleValidator moduleValidator) {
    this.moduleValidator = moduleValidator;
  }
  
  @Override public ValidationReport<TypeElement> validate(final TypeElement subject) {
    final ValidationReport.Builder<TypeElement> builder = ValidationReport.Builder.about(subject);

    if (!subject.getKind().equals(INTERFACE)
        && !(subject.getKind().equals(CLASS) && subject.getModifiers().contains(ABSTRACT))) {
      builder.addItem("@Component may only be applied to an interface or abstract class", subject);
    }

    AnnotationMirror componentMirror = getAnnotationMirror(subject, Component.class).get();
    ImmutableList<TypeMirror> moduleTypes = getComponentModules(componentMirror);
    moduleValidator.validateReferencedModules(subject, builder, moduleTypes);
    return builder.build();
  }
}
