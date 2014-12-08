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
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produces;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
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
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return new ImmutableSet.Builder<DependencyRequest>()
        .addAll(dependencies())
        .build();
  }

  enum Kind {
    /** Represents a binding configured by {@link Produces} that doesn't return a future. */
    IMMEDIATE,
    /** Represents a binding configured by {@link Produces} that returns a future. */
    FUTURE_PRODUCTION,
  }

  /**
   * The type of binding (whether the {@link Produces} method returns a future). For the particular
   * type of production, use {@link #productionType}.
   */
  abstract Kind bindingKind();

  /** Returns provision type that was used to bind the key. */
  abstract Produces.Type productionType();

  @Override
  BindingType bindingType() {
    switch (productionType()) {
      case SET:
      case SET_VALUES:
        return BindingType.SET;
      case MAP:
        return BindingType.MAP;
      case UNIQUE:
        return BindingType.UNIQUE;
      default:
        throw new IllegalStateException("Unknown production type: " + productionType());
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

    ProductionBinding forProducesMethod(ExecutableElement producesMethod) {
      checkNotNull(producesMethod);
      checkArgument(producesMethod.getKind().equals(METHOD));
      Produces producesAnnotation = producesMethod.getAnnotation(Produces.class);
      checkArgument(producesAnnotation != null);
      Key key = keyFactory.forProducesMethod(producesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredVariables(producesMethod.getParameters());
      Kind kind = MoreTypes.isTypeOf(ListenableFuture.class, producesMethod.getReturnType())
          ? Kind.FUTURE_PRODUCTION
          : Kind.IMMEDIATE;
      return new AutoValue_ProductionBinding(
          key,
          producesMethod,
          dependencies,
          findBindingPackage(key),
          kind,
          producesAnnotation.type());
    }
  }
}
