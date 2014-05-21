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
import static dagger.Provides.Type.UNIQUE;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.MoreElements;
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
  enum Type {
    INJECT,
    PROVIDES
  }

  /** The type of binding ({@link Inject} or {@link Provides}). */
  abstract Type type();

  /** The {@link Key} that is provided by this binding. */
  abstract Key providedKey();

  /** The scope in which the binding declares the {@link #providedKey()}. */
  abstract Optional<AnnotationMirror> scope();

  /** Returns {@code true} if this provision binding requires members to be injected implicitly. */
  abstract boolean requiresMemberInjection();

  /**
   * Returns {@code true} if this binding contributes to a single logical binding. I.e. multiple
   * bindings are allowed for the same {@link Key}.
   */
  abstract boolean contributingBinding();

  static final class Factory {
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory keyRequestFactory;

    Factory(Key.Factory keyFactory, DependencyRequest.Factory keyRequestFactory) {
      this.keyFactory = keyFactory;
      this.keyRequestFactory = keyRequestFactory;
    }

    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(constructorElement.getAnnotation(Inject.class) != null);
      Key key = keyFactory.forInjectConstructor(constructorElement);
      checkArgument(!key.qualifier().isPresent());
      return new AutoValue_ProvisionBinding(
          constructorElement,
          keyRequestFactory.forRequiredVariables(constructorElement.getParameters()),
          Type.INJECT,
          key,
          getScopeAnnotation(constructorElement.getEnclosingElement()),
          requiresMemeberInjection(
              MoreElements.asType(constructorElement.getEnclosingElement())),
          false);
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
      checkArgument(providesMethod.getKind().equals(METHOD));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      return new AutoValue_ProvisionBinding(
          providesMethod,
          keyRequestFactory.forRequiredVariables(providesMethod.getParameters()),
          Type.PROVIDES,
          keyFactory.forProvidesMethod(providesMethod),
          getScopeAnnotation(providesMethod),
          false,
          !providesAnnotation.type().equals(UNIQUE));
    }
  }
}
