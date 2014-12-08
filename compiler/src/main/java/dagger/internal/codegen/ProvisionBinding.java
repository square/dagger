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
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Provides;
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
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static dagger.internal.codegen.ProvisionBinding.Kind.INJECTION;
import static dagger.internal.codegen.Util.unwrapOptionalEquivalence;
import static dagger.internal.codegen.Util.wrapOptionalInEquivalence;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends ContributionBinding {
  @Override
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return new ImmutableSet.Builder<DependencyRequest>()
        .addAll(dependencies())
        .addAll(memberInjectionRequest().asSet())
        .build();
  }

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

  /** The scope in which the binding declares the {@link #key()}. */
  Optional<AnnotationMirror> scope() {
    return unwrapOptionalEquivalence(wrappedScope());
  }

  /**
   * An optional annotation constraining the scope of this component wrapped in an
   * {@link com.google.common.base.Equivalence.Wrapper} to preserve comparison semantics of
   * {@link AnnotationMirror}.
   */
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedScope();

  /** If this provision requires members injeciton, this will be the corresonding request. */
  abstract Optional<DependencyRequest> memberInjectionRequest();

  @Override
  BindingType bindingType() {
    switch (provisionType()) {
      case SET:
      case SET_VALUES:
        return BindingType.SET;
      case MAP:
        return BindingType.MAP;
      case UNIQUE:
        return BindingType.UNIQUE;
      default:
        throw new IllegalStateException("Unknown provision type: " + provisionType());
    }
  }

  enum FactoryCreationStrategy {
    ENUM_INSTANCE,
    CLASS_CONSTRUCTOR,
  }

  FactoryCreationStrategy factoryCreationStrategy() {
    return (bindingKind().equals(INJECTION) && implicitDependencies().isEmpty())
        ? FactoryCreationStrategy.ENUM_INSTANCE
        : FactoryCreationStrategy.CLASS_CONSTRUCTOR;
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
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredVariables(constructorElement.getParameters());
      Optional<DependencyRequest> membersInjectionRequest = membersInjectionRequest(
          MoreElements.asType(constructorElement.getEnclosingElement()));
      Optional<AnnotationMirror> scope =
          getScopeAnnotation(constructorElement.getEnclosingElement());
      return new AutoValue_ProvisionBinding(
          key,
          constructorElement,
          dependencies,
          findBindingPackage(key),
          Kind.INJECTION,
          Provides.Type.UNIQUE,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          membersInjectionRequest);
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private Optional<DependencyRequest> membersInjectionRequest(TypeElement type) {
      if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
          type.getSuperclass())) {
        return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
      }
      for (Element enclosedElement : type.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (isAnnotationPresent(enclosedElement, Inject.class))) {
          return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
        }
      }
      return Optional.absent();
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(METHOD));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      Key key = keyFactory.forProvidesMethod(providesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredVariables(providesMethod.getParameters());
      Optional<AnnotationMirror> scope = getScopeAnnotation(providesMethod);
      return new AutoValue_ProvisionBinding(
          key,
          providesMethod,
          dependencies,
          findBindingPackage(key),
          Kind.PROVISION,
          providesAnnotation.type(),
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forImplicitMapBinding(DependencyRequest explicitRequest,
        DependencyRequest implicitRequest) {
      checkNotNull(explicitRequest);
      checkNotNull(implicitRequest);
      ImmutableSet<DependencyRequest> dependencies = ImmutableSet.of(implicitRequest);
      Optional<AnnotationMirror> scope = getScopeAnnotation(implicitRequest.requestElement());
      return new AutoValue_ProvisionBinding(
          explicitRequest.key(),
          implicitRequest.requestElement(),
          dependencies,
          findBindingPackage(explicitRequest.key()),
          Kind.PROVISION,
          Provides.Type.MAP,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      Component componentAnnotation = componentDefinitionType.getAnnotation(Component.class);
      checkArgument(componentAnnotation != null);
      return new AutoValue_ProvisionBinding(
          keyFactory.forComponent(componentDefinitionType.asType()),
          componentDefinitionType,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          Kind.COMPONENT,
          Provides.Type.UNIQUE,
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      Optional<AnnotationMirror> scope = getScopeAnnotation(componentMethod);
      return new AutoValue_ProvisionBinding(
          keyFactory.forComponentMethod(componentMethod),
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          Kind.COMPONENT_PROVISION,
          Provides.Type.UNIQUE,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          Optional.<DependencyRequest>absent());
    }
  }
}
