/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.ContributionBinding.contributionTypeFor;

/**
 * The collection of bindings that have been resolved for a binding key.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class ResolvedBindings {
  /**
   * The binding key for which the {@link #bindings()} have been resolved.
   */
  abstract BindingKey bindingKey();

  /**
   * The component in which the bindings in {@link #ownedBindings()},
   * {@link #ownedContributionBindings()}, and {@link #ownedMembersInjectionBinding()} were
   * resolved.
   */
  abstract ComponentDescriptor owningComponent();

  /**
   * The contribution bindings for {@link #bindingKey()} that were resolved in
   * {@link #owningComponent()} or its ancestor components, keyed by the component in which the
   * binding was resolved. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#CONTRIBUTION}, this is empty.
   */
  abstract ImmutableSetMultimap<ComponentDescriptor, ContributionBinding> allContributionBindings();

  /**
   * The members-injection bindings for {@link #bindingKey()} that were resolved in
   * {@link #owningComponent()} or its ancestor components, keyed by the component in which the
   * binding was resolved. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#MEMBERS_INJECTION}, this is empty.
   */
  abstract ImmutableMap<ComponentDescriptor, MembersInjectionBinding> allMembersInjectionBindings();

  /**
   * All bindings for {@link #bindingKey()}, regardless of in which component they were resolved.
   */
  ImmutableSet<? extends Binding> bindings() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return contributionBindings();

      case MEMBERS_INJECTION:
        return ImmutableSet.copyOf(membersInjectionBinding().asSet());

      default:
        throw new AssertionError(bindingKey());
    }
  }

  /**
   * All bindings for {@link #bindingKey()} that were resolved in {@link #owningComponent()}.
   */
  ImmutableSet<? extends Binding> ownedBindings() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return ownedContributionBindings();

      case MEMBERS_INJECTION:
        return ImmutableSet.copyOf(ownedMembersInjectionBinding().asSet());

      default:
        throw new AssertionError(bindingKey());
    }
  }

  /**
   * All contribution bindings, regardless of owning component.
   *
   * @throws IllegalStateException if {@link #bindingKey()} is not a
   * {@link BindingKey.Kind#CONTRIBUTION}.
   */
  ImmutableSet<ContributionBinding> contributionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  /**
   * The contribution bindings that were resolved in {@link #owningComponent()}.
   *
   * @throws IllegalStateException if {@link #bindingKey()} is not a
   * {@link BindingKey.Kind#CONTRIBUTION}.
   */
  ImmutableSet<ContributionBinding> ownedContributionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
    return allContributionBindings().get(owningComponent());
  }

  /**
   * The members-injection binding, regardless of owning component.
   *
   * @throws IllegalStateException if {@link #bindingKey()} is not a
   * {@link BindingKey.Kind#MEMBERS_INJECTION}.
   */
  Optional<MembersInjectionBinding> membersInjectionBinding() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
        FluentIterable.from(allMembersInjectionBindings().values()).toSet();
    return membersInjectionBindings.isEmpty()
        ? Optional.<MembersInjectionBinding>absent()
        : Optional.of(Iterables.getOnlyElement(membersInjectionBindings));
  }

  /**
   * The members-injection binding that was resolved in {@link #owningComponent()}.
   *
   * @throws IllegalStateException if {@link #bindingKey()} is not a
   * {@link BindingKey.Kind#MEMBERS_INJECTION}.
   */
  Optional<MembersInjectionBinding> ownedMembersInjectionBinding() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    return Optional.fromNullable(allMembersInjectionBindings().get(owningComponent()));
  }

  /**
   * Creates a {@link ResolvedBindings} for contribution bindings.
   */
  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      Multimap<ComponentDescriptor, ? extends ContributionBinding> contributionBindings) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>copyOf(contributionBindings),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of());
  }

  /**
   * Creates a {@link ResolvedBindings} for contribution bindings.
   */
  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      ContributionBinding... ownedContributionBindings) {
    return forContributionBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>builder()
            .putAll(owningComponent, ownedContributionBindings)
            .build());
  }

  /**
   * Creates a {@link ResolvedBindings} for members injection bindings.
   */
  static ResolvedBindings forMembersInjectionBinding(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      MembersInjectionBinding ownedMembersInjectionBinding) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.of(owningComponent, ownedMembersInjectionBinding));
  }

  /**
   * Creates a {@link ResolvedBindings} appropriate for when there are no bindings for the key.
   */
  static ResolvedBindings noBindings(BindingKey bindingKey, ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of());
  }

  /**
   * Returns a {@code ResolvedBindings} with the same {@link #bindingKey()} and {@link #bindings()}
   * as this one, but no {@link #ownedBindings()}.
   */
  ResolvedBindings asInheritedIn(ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey(), owningComponent, allContributionBindings(), allMembersInjectionBindings());
  }

  /**
   * {@code true} if this is a multibindings contribution.
   */
  boolean isMultibindings() {
    return bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)
        && !contributionBindings().isEmpty()
        && contributionTypeFor(contributionBindings()).isMultibinding();
  }

  /**
   * {@code true} if this is a unique contribution binding.
   */
  boolean isUniqueContribution() {
    return bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)
        && !contributionBindings().isEmpty()
        && !contributionTypeFor(contributionBindings()).isMultibinding();
  }
}
