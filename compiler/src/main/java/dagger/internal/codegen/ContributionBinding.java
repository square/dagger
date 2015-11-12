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
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Component;
import dagger.MapKey;
import dagger.Provides;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MapKeys.unwrapValue;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * An abstract class for a value object representing the mechanism by which a {@link Key} can be
 * contributed to a dependency graph.
 *
 * @author Jesse Beder
 * @since 2.0
 */
abstract class ContributionBinding extends Binding {
  
  @Override
  Set<DependencyRequest> implicitDependencies() {
    // Optimization: If we don't need the memberInjectionRequest, don't create more objects.
    if (!membersInjectionRequest().isPresent()) {
      return dependencies();
    } else {
      // Optimization: Avoid creating an ImmutableSet+Builder just to union two things together.
      return Sets.union(membersInjectionRequest().asSet(), dependencies());
    }
  }

  static enum ContributionType {
    /** Represents map bindings. */
    MAP,
    /** Represents set bindings. */
    SET,
    /** Represents a valid non-collection binding. */
    UNIQUE;

    boolean isMultibinding() {
      return !this.equals(UNIQUE);
    }
  }

  ContributionType contributionType() {
    switch (provisionType()) {
      case SET:
      case SET_VALUES:
        return ContributionType.SET;
      case MAP:
        return ContributionType.MAP;
      case UNIQUE:
        return ContributionType.UNIQUE;
      default:
        throw new AssertionError("Unknown provision type: " + provisionType());
    }
  }
  
  /** Returns the type that specifies this' nullability, absent if not nullable. */
  abstract Optional<DeclaredType> nullableType();

  /**
   * If this is a provision request from an {@code @Provides} or {@code @Produces} method, this will
   * be the element that contributed it. In the case of subclassed modules, this may differ than the
   * binding's enclosed element, as this will return the subclass whereas the enclosed element will
   * be the superclass.
   */
  abstract Optional<TypeElement> contributedBy();

  /**
   * Returns whether this binding is synthetic, i.e., not explicitly tied to code, but generated
   * implicitly by the framework.
   */
  boolean isSyntheticBinding() {
    return bindingKind().equals(Kind.SYNTHETIC);
  }

  /** If this provision requires members injection, this will be the corresponding request. */
  abstract Optional<DependencyRequest> membersInjectionRequest();

  /**
   * The kind of contribution this binding represents. Defines which elements can specify this kind
   * of contribution.
   */
  enum Kind {
    /**
     * A binding that is not explicitly tied to an element, but generated implicitly by the
     * framework.
     */
    SYNTHETIC,

    // Provision kinds

    /** An {@link Inject}-annotated constructor. */
    INJECTION,

    /** A {@link Provides}-annotated method. */
    PROVISION,

    /** An implicit binding to a {@link Component @Component}-annotated type. */
    COMPONENT,

    /** A provision method on a component's {@linkplain Component#dependencies() dependency}. */
    COMPONENT_PROVISION,

    /**
     * A subcomponent builder method on a component or subcomponent.
     */
    SUBCOMPONENT_BUILDER,

    // Production kinds

    /** A {@link Produces}-annotated method that doesn't return a {@link ListenableFuture}. */
    IMMEDIATE,

    /** A {@link Produces}-annotated method that returns a {@link ListenableFuture}. */
    FUTURE_PRODUCTION,

    /**
     * A production method on a production component's
     * {@linkplain ProductionComponent#dependencies() dependency} that returns a
     * {@link ListenableFuture}. Methods on production component dependencies that don't return a
     * {@link ListenableFuture} are considered {@linkplain #PROVISION provision bindings}.
     */
    COMPONENT_PRODUCTION,
  }

  /**
   * The kind of this contribution binding.
   */
  protected abstract Kind bindingKind();
  
  /**
   * A predicate that passes for bindings of a given kind.
   */
  static Predicate<ContributionBinding> isOfKind(final Kind kind) {
    return new Predicate<ContributionBinding>() {
      @Override
      public boolean apply(ContributionBinding binding) {
        return binding.bindingKind().equals(kind);
      }};
  }

  /** The provision type that was used to bind the key. */
  abstract Provides.Type provisionType();

  /**
   * The strategy for getting an instance of a factory for a {@link ContributionBinding}.
   */
  enum FactoryCreationStrategy {
    /** The factory class is an enum with one value named {@code INSTANCE}. */
    ENUM_INSTANCE,
    /** The factory must be created by calling the constructor. */
    CLASS_CONSTRUCTOR,
  }

  /**
   * Returns {@link FactoryCreationStrategy#ENUM_INSTANCE} if the binding has no dependencies and
   * is a static provision binding or an {@link Inject @Inject} constructor binding. Otherwise
   * returns {@link FactoryCreationStrategy#CLASS_CONSTRUCTOR}.
   */
  FactoryCreationStrategy factoryCreationStrategy() {
    switch (bindingKind()) {
      case PROVISION:
        return implicitDependencies().isEmpty() && bindingElement().getModifiers().contains(STATIC)
            ? FactoryCreationStrategy.ENUM_INSTANCE
            : FactoryCreationStrategy.CLASS_CONSTRUCTOR;

      case INJECTION:
        return implicitDependencies().isEmpty()
            ? FactoryCreationStrategy.ENUM_INSTANCE
            : FactoryCreationStrategy.CLASS_CONSTRUCTOR;

      default:
        return FactoryCreationStrategy.CLASS_CONSTRUCTOR;
    }
  }

  /**
   * Returns the {@link ContributionType}s represented by a given {@link ContributionBinding}
   * collection.
   */
  static <B extends ContributionBinding>
      ImmutableListMultimap<ContributionType, B> contributionTypesFor(
          Iterable<? extends B> bindings) {
    ImmutableListMultimap.Builder<ContributionType, B> builder = ImmutableListMultimap.builder();
    builder.orderKeysBy(Ordering.<ContributionType>natural());
    for (B binding : bindings) {
      builder.put(binding.contributionType(), binding);
    }
    return builder.build();
  }

  /**
   * Returns a single {@link ContributionType} represented by a given collection of
   * {@link ContributionBinding}s.
   *
   * @throws IllegalArgumentException if the given bindings are not all of one type
   */
  static ContributionType contributionTypeFor(Iterable<ContributionBinding> bindings) {
    checkNotNull(bindings);
    checkArgument(!Iterables.isEmpty(bindings), "no bindings");
    Set<ContributionType> types = EnumSet.noneOf(ContributionType.class);
    for (ContributionBinding binding : bindings) {
      types.add(binding.contributionType());
    }
    if (types.size() > 1) {
      throw new IllegalArgumentException(
          String.format(ErrorMessages.MULTIPLE_CONTRIBUTION_TYPES_FORMAT, types));
    }
    return Iterables.getOnlyElement(types);
  }

  /**
   * Indexes map-multibindings by map key (the result of calling
   * {@link AnnotationValue#getValue()} on a single member or the whole {@link AnnotationMirror}
   * itself, depending on {@link MapKey#unwrapValue()}).
   */
  static ImmutableSetMultimap<Object, ContributionBinding> indexMapBindingsByMapKey(
      Set<ContributionBinding> mapBindings) {
    return ImmutableSetMultimap.copyOf(
        Multimaps.index(
            mapBindings,
            new Function<ContributionBinding, Object>() {
              @Override
              public Object apply(ContributionBinding mapBinding) {
                AnnotationMirror mapKey = getMapKey(mapBinding.bindingElement()).get();
                Optional<? extends AnnotationValue> unwrappedValue = unwrapValue(mapKey);
                return unwrappedValue.isPresent() ? unwrappedValue.get().getValue() : mapKey;
              }
            }));
  }

  /**
   * Indexes map-multibindings by map key annotation type.
   */
  static ImmutableSetMultimap<Wrapper<DeclaredType>, ContributionBinding>
      indexMapBindingsByAnnotationType(Set<ContributionBinding> mapBindings) {
    return ImmutableSetMultimap.copyOf(
        Multimaps.index(
            mapBindings,
            new Function<ContributionBinding, Equivalence.Wrapper<DeclaredType>>() {
              @Override
              public Equivalence.Wrapper<DeclaredType> apply(ContributionBinding mapBinding) {
                return MoreTypes.equivalence()
                    .wrap(getMapKey(mapBinding.bindingElement()).get().getAnnotationType());
              }
            }));
  }
}
