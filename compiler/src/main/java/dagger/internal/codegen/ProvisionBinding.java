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
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Provides;
import java.util.Iterator;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.Provides.Type.MAP;
import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;
import static dagger.internal.codegen.ErrorMessages.NON_SETBINDING;
import static dagger.internal.codegen.ErrorMessages.NON_MAPBINDING;
import static dagger.internal.codegen.ErrorMessages.INVALID_COLLECTIONBINDING;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends Binding {
  enum Kind {
    /** Represents an {@link Inject} binding. */
    INJECTION,
    /** Represents a binding configured by {@link Provides}. */
    PROVISION,
    /** Represents the implicit binding to the component. */
    COMPONENT,
    /** Represents a binding from a provision method on a component dependency. */
    COMPONENT_PROVISION,
  }

  /**
   * The type of binding ({@link Inject} or {@link Provides}). For the particular type of provision,
   * use {@link #provisionType}.
   */
  abstract Kind bindingKind();

  /** Returns provision type that was used to bind the key. */
  abstract Provides.Type provisionType();

  /** The {@link Key} that is provided by this binding. */
  abstract Key providedKey();

  /** The scope in which the binding declares the {@link #providedKey()}. */
  abstract Optional<AnnotationMirror> scope();

  /** If this provision requires members injeciton, this will be the corresonding request. */
  abstract Optional<DependencyRequest> memberInjectionRequest();

  private static ImmutableSet<Provides.Type> SET_BINDING_TYPES = immutableEnumSet(SET, SET_VALUES);
  private static ImmutableSet<Provides.Type> MAP_BINDING_TYPES = immutableEnumSet(MAP);
  
  
  static enum BindingsType {
    /** Represents set bindings. */
    SETBINDING, 
    /** Represents map bindings. */
    MAPBINDING, 
    /** Represents a valid non-collection binding. */
    SINGULARBINDING,
  }
  
  /**
   * Returns {@code BindingsType} for bindings, which can be {@code SETBINDING} if the given
   * bindings are all contributors to a set binding. Returns {@code MAPBINDING} if the given
   * bindings are all contributors to a map binding. Returns {@code NONCOLLECTIONBINDING} if the
   * given bindings is not a collection.
   *
   * @throws IllegalArgumentException if some of the bindings are map bindings or set bindings and
   *         some are not.
   * @throws IllegalArgumentException if the bindings in the collection are not supported in Dagger
   *         (Not set bindings or map Bindings).
   */
  static BindingsType getBindingsType(Iterable<ProvisionBinding> bindings) {
    checkNotNull(bindings);
    switch (Iterables.size(bindings)) {
      case 0:
        throw new IllegalArgumentException("no bindings");
      case 1:
        if (SET_BINDING_TYPES.contains(Iterables.getOnlyElement(bindings).provisionType())) {
          return BindingsType.SETBINDING;
        } else if (MAP_BINDING_TYPES.contains(Iterables.getOnlyElement(bindings).provisionType())) {
          return BindingsType.MAPBINDING;
        }
        return BindingsType.SINGULARBINDING;
      default:
        Iterator<ProvisionBinding> iterator = bindings.iterator();
        boolean setBinding = SET_BINDING_TYPES.contains(iterator.next().provisionType());
        if (setBinding) {
          while (iterator.hasNext()) {
            checkArgument(setBinding, NON_SETBINDING);
            checkArgument(SET_BINDING_TYPES.contains(iterator.next().provisionType()),
                NON_SETBINDING);
          }
          return BindingsType.SETBINDING;
        }

        iterator = bindings.iterator();
        boolean mapBinding = MAP_BINDING_TYPES.contains(iterator.next().provisionType());
        if (mapBinding) {
          while (iterator.hasNext()) {
            checkArgument(mapBinding, NON_MAPBINDING);
            checkArgument(MAP_BINDING_TYPES.contains(iterator.next().provisionType()),
                NON_MAPBINDING);
          }
          return BindingsType.MAPBINDING;
        }
        throw new IllegalStateException(INVALID_COLLECTIONBINDING);
    }
  }
  
  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(isAnnotationPresent(constructorElement, Inject.class));
      Key key = keyFactory.forInjectConstructor(constructorElement);
      checkArgument(!key.qualifier().isPresent());
      return new AutoValue_ProvisionBinding(
          constructorElement,
          dependencyRequestFactory.forRequiredVariables(constructorElement.getParameters()),
          Kind.INJECTION,
          Provides.Type.UNIQUE,
          key,
          getScopeAnnotation(constructorElement.getEnclosingElement()),
          membersInjectionRequest(
              MoreElements.asType(constructorElement.getEnclosingElement())));
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private Optional<DependencyRequest> membersInjectionRequest(TypeElement type) {
      if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
          type.getSuperclass())) {
        return Optional.of(dependencyRequestFactory.forMembersInjectedType(type.asType()));
      }
      for (Element enclosedElement : type.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (isAnnotationPresent(enclosedElement, Inject.class))) {
          return Optional.of(dependencyRequestFactory.forMembersInjectedType(type.asType()));
        }
      }
      return Optional.absent();
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(METHOD));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      return new AutoValue_ProvisionBinding(
          providesMethod,
          dependencyRequestFactory.forRequiredVariables(providesMethod.getParameters()),
          Kind.PROVISION,
          providesAnnotation.type(),
          keyFactory.forProvidesMethod(providesMethod),
          getScopeAnnotation(providesMethod),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      Component componentAnnotation = componentDefinitionType.getAnnotation(Component.class);
      checkArgument(componentAnnotation != null);
      return new AutoValue_ProvisionBinding(
          componentDefinitionType,
          ImmutableSet.<DependencyRequest>of(),
          Kind.COMPONENT,
          Provides.Type.UNIQUE,
          keyFactory.forType(componentDefinitionType.asType()),
          Optional.<AnnotationMirror>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      return new AutoValue_ProvisionBinding(
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Kind.COMPONENT_PROVISION,
          Provides.Type.UNIQUE,
          keyFactory.forComponentMethod(componentMethod),
          getScopeAnnotation(componentMethod),
          Optional.<DependencyRequest>absent());
    }
  }
}
