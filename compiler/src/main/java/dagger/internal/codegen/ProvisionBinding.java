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
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Provides;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.Scope.scopeOf;
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
  Binding.Type bindingType() {
    return Binding.Type.PROVISION;
  }
  
  @Override
  abstract Scope scope();

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

    /** Returns an unresolved version of this binding. */
    ProvisionBinding unresolve(ProvisionBinding binding) {
      checkState(binding.hasNonDefaultTypeParameters());
      return forInjectConstructor((ExecutableElement) binding.bindingElement(),
          Optional.<TypeMirror>absent());
    }

    /**
     * Returns a ProvisionBinding for the given element. If {@code resolvedType} is present, this
     * will return a resolved binding, with the key & type resolved to the given type (using
     * {@link Types#asMemberOf(DeclaredType, Element)}).
     */
    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement,
        Optional<TypeMirror> resolvedType) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(isAnnotationPresent(constructorElement, Inject.class));
      checkArgument(!getQualifier(constructorElement).isPresent());

      ExecutableType cxtorType = MoreTypes.asExecutable(constructorElement.asType());
      DeclaredType enclosingCxtorType =
          MoreTypes.asDeclared(constructorElement.getEnclosingElement().asType());
      // If the class this is constructing has some type arguments, resolve everything.
      if (!enclosingCxtorType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
        DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
        // Validate that we're resolving from the correct type.
        checkState(types.isSameType(types.erasure(resolved), types.erasure(enclosingCxtorType)),
            "erased expected type: %s, erased actual type: %s",
            types.erasure(resolved), types.erasure(enclosingCxtorType));
        cxtorType = MoreTypes.asExecutable(types.asMemberOf(resolved, constructorElement));
        enclosingCxtorType = resolved;
      }

      Key key = keyFactory.forInjectConstructorWithResolvedType(enclosingCxtorType);
      checkArgument(!key.qualifier().isPresent());
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(enclosingCxtorType,
              constructorElement.getParameters(),
              cxtorType.getParameterTypes());
      Optional<DependencyRequest> membersInjectionRequest =
          membersInjectionRequest(enclosingCxtorType);
      Scope scope = Scope.scopeOf(constructorElement.getEnclosingElement());

      TypeElement bindingTypeElement =
          MoreElements.asType(constructorElement.getEnclosingElement());

      return new AutoValue_ProvisionBinding(
          key,
          constructorElement,
          dependencies,
          findBindingPackage(key),
          hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types),
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          membersInjectionRequest,
          Kind.INJECTION,
          Provides.Type.UNIQUE,
          scope);
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private Optional<DependencyRequest> membersInjectionRequest(DeclaredType type) {
      TypeElement typeElement = MoreElements.asType(type.asElement());
      if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
          typeElement.getSuperclass())) {
        return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
      }
      for (Element enclosedElement : typeElement.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (isAnnotationPresent(enclosedElement, Inject.class))) {
          return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
        }
      }
      return Optional.absent();
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod, TypeMirror contributedBy) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(METHOD));
      checkArgument(contributedBy.getKind().equals(TypeKind.DECLARED));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      DeclaredType declaredContainer = MoreTypes.asDeclared(contributedBy);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(types.asMemberOf(declaredContainer, providesMethod));
      Key key = keyFactory.forProvidesMethod(resolvedMethod, providesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              declaredContainer,
              providesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      Scope scope = Scope.scopeOf(providesMethod);
      return new AutoValue_ProvisionBinding(
          key,
          providesMethod,
          dependencies,
          findBindingPackage(key),
          false /* no non-default parameter types */,
          ConfigurationAnnotations.getNullableType(providesMethod),
          Optional.of(MoreTypes.asTypeElement(declaredContainer)),
          Optional.<DependencyRequest>absent(),
          Kind.PROVISION,
          providesAnnotation.type(),
          scope);
    }

    ProvisionBinding implicitMapOfProviderBinding(DependencyRequest mapOfValueRequest) {
      checkNotNull(mapOfValueRequest);
      Optional<Key> implicitMapOfProviderKey =
          keyFactory.implicitMapProviderKeyFrom(mapOfValueRequest.key());
      checkArgument(
          implicitMapOfProviderKey.isPresent(),
          "%s is not a request for Map<K, V>",
          mapOfValueRequest);
      DependencyRequest implicitMapOfProviderRequest =
          dependencyRequestFactory.forImplicitMapBinding(
              mapOfValueRequest, implicitMapOfProviderKey.get());
      return new AutoValue_ProvisionBinding(
          mapOfValueRequest.key(),
          implicitMapOfProviderRequest.requestElement(),
          ImmutableSet.of(implicitMapOfProviderRequest),
          findBindingPackage(mapOfValueRequest.key()),
          false /* no non-default parameter types */,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.SYNTHETIC,
          Provides.Type.MAP,
          scopeOf(implicitMapOfProviderRequest.requestElement()));
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      return new AutoValue_ProvisionBinding(
          keyFactory.forComponent(componentDefinitionType.asType()),
          componentDefinitionType,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false /* no non-default parameter types */,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.COMPONENT,
          Provides.Type.UNIQUE,
          Scope.unscoped());
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      Scope scope = Scope.scopeOf(componentMethod);
      return new AutoValue_ProvisionBinding(
          keyFactory.forComponentMethod(componentMethod),
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false /* no non-default parameter types */,
          ConfigurationAnnotations.getNullableType(componentMethod),
          Optional.<TypeElement>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.COMPONENT_PROVISION,
          Provides.Type.UNIQUE,
          scope);
    }

    ProvisionBinding forSubcomponentBuilderMethod(
        ExecutableElement subcomponentBuilderMethod, TypeElement contributedBy) {
      checkNotNull(subcomponentBuilderMethod);
      checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
      checkArgument(subcomponentBuilderMethod.getParameters().isEmpty());
      DeclaredType declaredContainer = asDeclared(contributedBy.asType());
      return new AutoValue_ProvisionBinding(
          keyFactory.forSubcomponentBuilderMethod(subcomponentBuilderMethod, declaredContainer),
          subcomponentBuilderMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false /* no non-default parameter types */,
          Optional.<DeclaredType>absent(),
          Optional.of(contributedBy),
          Optional.<DependencyRequest>absent(),
          Kind.SUBCOMPONENT_BUILDER,
          Provides.Type.UNIQUE,
          Scope.unscoped());
    }
  }
}
