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
import dagger.Module;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.AnnotationMirrors.getAttributeAsListOfTypes;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component}
 * and {@link Module}).
 *
 * @author Gregory Kick
 */
final class ConfigurationAnnotations {
  private static final String MODULES_ATTRIBUTE = "modules";

  static ImmutableList<TypeMirror> getComponentModules(Elements elements,
      AnnotationMirror componentAnnotation) {
    checkNotNull(elements);
    checkNotNull(componentAnnotation);
    return getAttributeAsListOfTypes(elements, componentAnnotation, MODULES_ATTRIBUTE);
  }

  private static final String DEPENDENCIES_ATTRIBUTE = "dependencies";

  static ImmutableList<TypeMirror> getComponentDependencies(Elements elements,
      AnnotationMirror componentAnnotation) {
    checkNotNull(elements);
    checkNotNull(componentAnnotation);
    return getAttributeAsListOfTypes(elements, componentAnnotation, DEPENDENCIES_ATTRIBUTE);
  }

  private static final String INCLUDES_ATTRIBUTE = "includes";

  static ImmutableList<TypeMirror> getModuleIncludes(Elements elements,
      AnnotationMirror moduleAnnotation) {
    checkNotNull(elements);
    checkNotNull(moduleAnnotation);
    return getAttributeAsListOfTypes(elements, moduleAnnotation, INCLUDES_ATTRIBUTE);
  }

  private static final String INJECTS_ATTRIBUTE = "injects";

  static ImmutableList<TypeMirror> getModuleInjects(Elements elements,
      AnnotationMirror moduleAnnotation) {
    checkNotNull(elements);
    checkNotNull(moduleAnnotation);
    return getAttributeAsListOfTypes(elements, moduleAnnotation, INJECTS_ATTRIBUTE);
  }

  private ConfigurationAnnotations() {}
}
