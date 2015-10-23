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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Provides;
import dagger.producers.Produces;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.ElementKind.METHOD;

/**
 * A value object representing the mechanism by which a {@link Key} can be produced. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Jesse Beder
 * @since 2.0
 */
@AutoValue
abstract class ProductionBinding extends ContributionBinding {

  @Override
  Binding.Type bindingType() {
    return Binding.Type.PRODUCTION;
  }

  @Override
  Provides.Type provisionType() {
    return Provides.Type.valueOf(productionType().name());
  }

  @Override
  Set<DependencyRequest> implicitDependencies() {
    // Similar optimizations to ContributionBinding.implicitDependencies().
    if (!monitorRequest().isPresent()) {
      return super.implicitDependencies();
    } else {
      return Sets.union(monitorRequest().asSet(), super.implicitDependencies());
    }
  }

  /** Returns provision type that was used to bind the key. */
  abstract Produces.Type productionType();

  /** Returns the list of types in the throws clause of the method. */
  abstract ImmutableList<? extends TypeMirror> thrownTypes();

  /** If this production requires a monitor, this will be the corresponding request. */
  abstract Optional<DependencyRequest> monitorRequest();

  @Override
  ContributionType contributionType() {
    switch (productionType()) {
      case SET:
      case SET_VALUES:
        return ContributionType.SET;
      case MAP:
        return ContributionType.MAP;
      case UNIQUE:
        return ContributionType.UNIQUE;
      default:
        throw new AssertionError("Unknown production type: " + productionType());
    }
  }

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(
        Types types, Key.Factory keyFactory, DependencyRequest.Factory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProductionBinding forProducesMethod(
        ExecutableElement producesMethod, TypeMirror contributedBy) {
      checkNotNull(producesMethod);
      checkArgument(producesMethod.getKind().equals(METHOD));
      checkArgument(contributedBy.getKind().equals(TypeKind.DECLARED));
      Produces producesAnnotation = producesMethod.getAnnotation(Produces.class);
      checkArgument(producesAnnotation != null);
      DeclaredType declaredContainer = MoreTypes.asDeclared(contributedBy);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(types.asMemberOf(declaredContainer, producesMethod));
      Key key = keyFactory.forProducesMethod(resolvedMethod, producesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              declaredContainer,
              producesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      DependencyRequest monitorRequest =
          dependencyRequestFactory.forProductionComponentMonitorProvider();
      Kind kind = MoreTypes.isTypeOf(ListenableFuture.class, producesMethod.getReturnType())
          ? Kind.FUTURE_PRODUCTION
          : Kind.IMMEDIATE;
      return new AutoValue_ProductionBinding(
          key,
          producesMethod,
          dependencies,
          findBindingPackage(key),
          false,
          ConfigurationAnnotations.getNullableType(producesMethod),
          Optional.of(MoreTypes.asTypeElement(declaredContainer)),
          Optional.<DependencyRequest>absent(),
          kind,
          producesAnnotation.type(),
          ImmutableList.copyOf(producesMethod.getThrownTypes()),
          Optional.of(monitorRequest));
    }

    ProductionBinding implicitMapOfProducerBinding(DependencyRequest mapOfValueRequest) {
      checkNotNull(mapOfValueRequest);
      Optional<Key> implicitMapOfProducerKey =
          keyFactory.implicitMapProducerKeyFrom(mapOfValueRequest.key());
      checkArgument(
          implicitMapOfProducerKey.isPresent(), "%s is not for a Map<K, V>", mapOfValueRequest);
      DependencyRequest implicitMapOfProducerRequest =
          dependencyRequestFactory.forImplicitMapBinding(
              mapOfValueRequest, implicitMapOfProducerKey.get());
      return new AutoValue_ProductionBinding(
          mapOfValueRequest.key(),
          implicitMapOfProducerRequest.requestElement(),
          ImmutableSet.of(implicitMapOfProducerRequest),
          findBindingPackage(mapOfValueRequest.key()),
          false,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.SYNTHETIC,
          Produces.Type.MAP,
          ImmutableList.<TypeMirror>of(),
          Optional.<DependencyRequest>absent());
    }

    ProductionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      checkArgument(MoreTypes.isTypeOf(ListenableFuture.class, componentMethod.getReturnType()));
      return new AutoValue_ProductionBinding(
          keyFactory.forProductionComponentMethod(componentMethod),
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.COMPONENT_PRODUCTION,
          Produces.Type.UNIQUE,
          ImmutableList.copyOf(componentMethod.getThrownTypes()),
          Optional.<DependencyRequest>absent());
    }
  }
}
