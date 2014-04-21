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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import dagger.Provides;

import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends Binding {
  /** The {@link Key} that is provided by this binding. */
  abstract Key providedKey();

  /** The scope in which the binding declares the {@link #providedKey()}. */
  abstract Optional<AnnotationMirror> scope();

  /** Returns {@code true} if this provision binding requires members to be injected implicitly. */
  abstract boolean requiresMemberInjection();

  static final class Factory {
    private final DependencyRequest.Factory keyRequestFactory;

    Factory(DependencyRequest.Factory keyRequestFactory) {
      this.keyRequestFactory = keyRequestFactory;
    }

    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(constructorElement.getAnnotation(Inject.class) != null);
      Key key = Key.forInjectConstructor(constructorElement);
      checkArgument(!key.qualifier().isPresent());
      return new AutoValue_ProvisionBinding(constructorElement,
          keyRequestFactory.forVariables(constructorElement.getParameters()),
          key,
          getScopeAnnotation(constructorElement.getEnclosingElement()),
          requiresMemeberInjection(
              ElementUtil.asTypeElement(constructorElement.getEnclosingElement())));
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private static boolean requiresMemeberInjection(TypeElement type) {
      for (Element enclosedElement : type.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (enclosedElement.getAnnotation(Inject.class) != null)) {
          return true;
        }
      }
      return false;
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(CONSTRUCTOR));
      checkArgument(providesMethod.getAnnotation(Provides.class) != null);
      return new AutoValue_ProvisionBinding(providesMethod,
          keyRequestFactory.forVariables(providesMethod.getParameters()),
          Key.forProvidesMethod(providesMethod),
          getScopeAnnotation(providesMethod),
          false);
    }
  }
}
