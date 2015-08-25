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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.ContributionBinding.bindingTypeFor;

/**
 * The collection of bindings that have been resolved for a binding key.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class ResolvedBindings {
  abstract BindingKey bindingKey();
  abstract ComponentDescriptor owningComponent();
  abstract ImmutableSet<? extends Binding> ownedBindings();
  abstract ImmutableSetMultimap<ComponentDescriptor, ? extends Binding> inheritedBindings();

  static ResolvedBindings create(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      Set<? extends Binding> ownedBindings,
      Multimap<ComponentDescriptor, ? extends Binding> inheritedBindings) {
    return new AutoValue_ResolvedBindings(
            bindingKey,
            owningComponent,
            ImmutableSet.copyOf(ownedBindings),
            ImmutableSetMultimap.copyOf(inheritedBindings));
  }

  static ResolvedBindings create(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      Binding... ownedBindings) {
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSet.copyOf(ownedBindings),
        ImmutableSetMultimap.<ComponentDescriptor, Binding>of());
  }

  ImmutableSet<? extends Binding> bindings() {
     return new ImmutableSet.Builder<Binding>()
         .addAll(ownedBindings())
         .addAll(inheritedBindings().values())
         .build();
  }

  @SuppressWarnings("unchecked")  // checked by validator
  ImmutableSet<? extends ContributionBinding> ownedContributionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
    return (ImmutableSet<? extends ContributionBinding>) ownedBindings();
  }

  @SuppressWarnings("unchecked")  // checked by validator
  ImmutableSet<? extends ContributionBinding> contributionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
    return new ImmutableSet.Builder<ContributionBinding>()
        .addAll((Iterable<? extends ContributionBinding>) ownedBindings())
        .addAll((Iterable<? extends ContributionBinding>) inheritedBindings().values())
        .build();
  }

  @SuppressWarnings("unchecked")  // checked by validator
  ImmutableSet<? extends MembersInjectionBinding> membersInjectionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    return new ImmutableSet.Builder<MembersInjectionBinding>()
        .addAll((Iterable<? extends MembersInjectionBinding>) ownedBindings())
        .addAll((Iterable<? extends MembersInjectionBinding>) inheritedBindings().values())
        .build();
  }

  /**
   * Returns a {@code ResolvedBindings} with the same {@link #bindingKey()} and {@link #bindings()}
   * as this one, but no {@link #ownedBindings()}.
   */
  ResolvedBindings asInheritedIn(ComponentDescriptor owningComponent) {
    return ResolvedBindings.create(
            bindingKey(),
            owningComponent,
            ImmutableSet.<Binding>of(),
            new ImmutableSetMultimap.Builder<ComponentDescriptor, Binding>()
                .putAll(inheritedBindings())
                .putAll(owningComponent, ownedBindings())
                .build());
  }

  /**
   * {@code true} if this is a multibindings contribution.
   */
  boolean isMultibindings() {
    return bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)
        && !contributionBindings().isEmpty()
        && bindingTypeFor(contributionBindings()).isMultibinding();
  }

  /**
   * {@code true} if this is a unique contribution binding.
   */
  boolean isUniqueContribution() {
    return bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)
        && !contributionBindings().isEmpty()
        && !bindingTypeFor(contributionBindings()).isMultibinding();
  }
}
