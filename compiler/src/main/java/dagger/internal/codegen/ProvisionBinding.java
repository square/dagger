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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
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
  public BindingType bindingType() {
    return BindingType.PROVISION;
  }

  @Override
  abstract Optional<ProvisionBinding> unresolved();

  @Override
  abstract Optional<Scope> scope();

  private static Builder builder() {
    return new AutoValue_ProvisionBinding.Builder()
        .dependencies(ImmutableSet.<DependencyRequest>of());
  }

  @AutoValue.Builder
  @CanIgnoreReturnValue
  abstract static class Builder extends ContributionBinding.Builder<Builder> {

    abstract Builder unresolved(ProvisionBinding unresolved);

    abstract Builder scope(Optional<Scope> scope);

    @CheckReturnValue
    abstract ProvisionBinding build();
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
          dependencyRequestFactory.forRequiredResolvedVariables(
              constructorElement.getParameters(), cxtorType.getParameterTypes());
      Optional<DependencyRequest> membersInjectionRequest =
          membersInjectionRequest(enclosingCxtorType);

      ProvisionBinding.Builder builder =
          ProvisionBinding.builder()
              .contributionType(ContributionType.UNIQUE)
              .bindingElement(constructorElement)
              .key(key)
              .dependencies(dependencies)
              .membersInjectionRequest(membersInjectionRequest)
              .bindingKind(Kind.INJECTION)
              .scope(Scope.uniqueScopeOf(constructorElement.getEnclosingElement()));

      TypeElement bindingTypeElement =
          MoreElements.asType(constructorElement.getEnclosingElement());
      if (hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types)) {
        builder.unresolved(forInjectConstructor(constructorElement, Optional.<TypeMirror>absent()));
      }
      return builder.build();
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

    ProvisionBinding forProvidesMethod(
        ExecutableElement providesMethod, TypeElement contributedBy) {
      checkArgument(providesMethod.getKind().equals(METHOD));
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), providesMethod));
      Key key = keyFactory.forProvidesMethod(providesMethod, contributedBy);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              providesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.fromBindingMethod(providesMethod))
          .bindingElement(providesMethod)
          .contributingModule(contributedBy)
          .key(key)
          .dependencies(dependencies)
          .nullableType(ConfigurationAnnotations.getNullableType(providesMethod))
          .wrappedMapKey(wrapOptionalInEquivalence(getMapKey(providesMethod)))
          .bindingKind(Kind.PROVISION)
          .scope(Scope.uniqueScopeOf(providesMethod))
          .build();
    }

    /**
     * A synthetic binding of {@code Map<K, V>} that depends on {@code Map<K, Provider<V>>}.
     */
    ProvisionBinding syntheticMapOfValuesBinding(DependencyRequest requestForMapOfValues) {
      checkNotNull(requestForMapOfValues);
      Optional<Key> mapOfProvidersKey =
          keyFactory.implicitMapProviderKeyFrom(requestForMapOfValues.key());
      checkArgument(
          mapOfProvidersKey.isPresent(),
          "%s is not a request for Map<K, V>",
          requestForMapOfValues);
      DependencyRequest requestForMapOfProviders =
          dependencyRequestFactory.forImplicitMapBinding(
              requestForMapOfValues, mapOfProvidersKey.get());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(requestForMapOfProviders.requestElement())
          .key(requestForMapOfValues.key())
          .dependencies(requestForMapOfProviders)
          .wrappedMapKey(
              wrapOptionalInEquivalence(getMapKey(requestForMapOfProviders.requestElement())))
          .bindingKind(Kind.SYNTHETIC_MAP)
          .scope(Scope.uniqueScopeOf(requestForMapOfProviders.requestElement()))
          .build();
    }

    /**
     * A synthetic binding that depends explicitly on a set of individual provision multibinding
     * contribution methods.
     *
     * <p>Note that these could be set multibindings or map multibindings.
     */
    ProvisionBinding syntheticMultibinding(
        DependencyRequest request, Iterable<ContributionBinding> multibindingContributions) {
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(request.requestElement())
          .key(request.key())
          .dependencies(
              dependencyRequestFactory.forMultibindingContributions(
                  request, multibindingContributions))
          .bindingKind(Kind.forMultibindingRequest(request))
          .scope(Scope.uniqueScopeOf(request.requestElement()))
          .build();
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(componentDefinitionType)
          .key(keyFactory.forComponent(componentDefinitionType.asType()))
          .bindingKind(Kind.COMPONENT)
          .build();
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(componentMethod)
          .key(keyFactory.forComponentMethod(componentMethod))
          .nullableType(ConfigurationAnnotations.getNullableType(componentMethod))
          .bindingKind(Kind.COMPONENT_PROVISION)
          .scope(Scope.uniqueScopeOf(componentMethod))
          .build();
    }

    ProvisionBinding forSubcomponentBuilderMethod(
        ExecutableElement subcomponentBuilderMethod, TypeElement contributedBy) {
      checkNotNull(subcomponentBuilderMethod);
      checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
      checkArgument(subcomponentBuilderMethod.getParameters().isEmpty());
      DeclaredType declaredContainer = asDeclared(contributedBy.asType());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(subcomponentBuilderMethod)
          .key(
              keyFactory.forSubcomponentBuilderMethod(subcomponentBuilderMethod, declaredContainer))
          .bindingKind(Kind.SUBCOMPONENT_BUILDER)
          .build();
    }

    ProvisionBinding delegate(
        DelegateDeclaration delegateDeclaration, ProvisionBinding delegate) {
      return ProvisionBinding.builder()
          .contributionType(delegateDeclaration.contributionType())
          .bindingElement(delegateDeclaration.bindingElement())
          .contributingModule(delegateDeclaration.contributingModule().get())
          .key(keyFactory.forDelegateBinding(delegateDeclaration, Provider.class))
          .dependencies(delegateDeclaration.delegateRequest())
          .nullableType(delegate.nullableType())
          .wrappedMapKey(delegateDeclaration.wrappedMapKey())
          .bindingKind(Kind.SYNTHETIC_DELEGATE_BINDING)
          .scope(Scope.uniqueScopeOf(delegateDeclaration.bindingElement()))
          .build();
    }
  }
}
