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
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static dagger.internal.codegen.Util.unwrapOptionalEquivalence;
import static dagger.internal.codegen.Util.wrapOptionalInEquivalence;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * The logical representation of a {@link Component} definition.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ComponentDescriptor {
  ComponentDescriptor() {}

  abstract AnnotationMirror componentAnnotation();

  /**
   * The type (interface or abstract class) that defines the component. This is the element to which
   * the {@link Component} annotation was applied.
   */
  abstract TypeElement componentDefinitionType();

  /**
   * The set of component dependencies listed in {@link Component#dependencies}.
   */
  abstract ImmutableSet<TypeElement> dependencies();

  /**
   * An index of the type to which this component holds a reference (the type listed in
   * {@link Component#dependencies} as opposed to the enclosing type) for each method from a
   * component dependency that can be used for binding.
   */
  abstract ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex();

  /**
   * An optional annotation constraining the scope of this component.
   */
  Optional<AnnotationMirror> scope() {
    return unwrapOptionalEquivalence(wrappedScope());
  }

  /**
   * An optional annotation constraining the scope of this component wrapped in an
   * {@link com.google.common.base.Equivalence.Wrapper} to preserve comparison semantics of
   * {@link AnnotationMirror}.
   */
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedScope();

  static final class Factory {
    private final Elements elements;
    private final Types types;

    Factory(Elements elements, Types types) {
      this.elements = elements;
      this.types = types;
    }

    ComponentDescriptor create(TypeElement componentDefinitionType) {
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class).get();
      ImmutableSet<TypeElement> componentDependencyTypes =
          MoreTypes.asTypeElements(types, getComponentDependencies(componentMirror));

      ImmutableMap.Builder<ExecutableElement, TypeElement> dependencyMethodIndex =
          ImmutableMap.builder();

      for (TypeElement componentDependency : componentDependencyTypes) {
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement dependencyMethod : dependencyMethods) {
          if (isComponentProvisionMethod(elements, dependencyMethod)) {
            dependencyMethodIndex.put(dependencyMethod, componentDependency);
          }
        }
      }

      Optional<AnnotationMirror> scope = getScopeAnnotation(componentDefinitionType);
      return new AutoValue_ComponentDescriptor(
          componentMirror,
          componentDefinitionType,
          componentDependencyTypes,
          dependencyMethodIndex.build(),
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope));
    }
  }

  static boolean isComponentProvisionMethod(Elements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement());
  }
}
