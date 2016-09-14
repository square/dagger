/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import dagger.internal.codegen.BindingType.HasBindingType;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.Key.HasKey;
import java.util.Map;

/**
 * The collection of bindings that have been resolved for a binding key. For valid graphs, contains
 * exactly one binding.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class ResolvedBindings implements HasBindingType, HasContributionType, HasKey {
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

  @Override
  public Key key() {
    return bindingKey().key();
  }
  
  /**
   * The multibinding declarations for {@link #bindingKey()}. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#CONTRIBUTION}, this is empty.
   */
  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  /**
   * The subcomponent declarations for {@link #bindingKey()}. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#CONTRIBUTION}, this is empty.
   */
  abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /**
   * The optional binding declarations for {@link #bindingKey()}. If {@link #bindingKey()}'s kind is
   * not {@link BindingKey.Kind#CONTRIBUTION}, this is empty.
   */
  abstract ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations();

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
   * Returns the single binding.
   *
   * @throws IllegalStateException if there is not exactly one element in {@link #bindings()},
   *     which will never happen for contributions in valid graphs
   */
  Binding binding() {
    return getOnlyElement(bindings());
  }

  /**
   * All bindings for {@link #bindingKey()}, together with the component in which they were
   * resolved.
   */
  ImmutableList<Map.Entry<ComponentDescriptor, ? extends Binding>> bindingsByComponent() {
    return new ImmutableList.Builder<Map.Entry<ComponentDescriptor, ? extends Binding>>()
        .addAll(allContributionBindings().entries())
        .addAll(allMembersInjectionBindings().entrySet())
        .build();
  }

  /**
   * {@code true} if there are no {@link #bindings()}, {@link #multibindingDeclarations()}, or
   * {@link #subcomponentDeclarations()}.
   */
  boolean isEmpty() {
    return bindings().isEmpty()
        && multibindingDeclarations().isEmpty()
        && subcomponentDeclarations().isEmpty();
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
   * All contribution bindings, regardless of owning component. Empty if this is a members-injection
   * binding.
   */
  ImmutableSet<ContributionBinding> contributionBindings() {
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  /**
   * The contribution bindings that were resolved in {@link #owningComponent()}. Empty if this is a
   * members-injection binding.
   */
  ImmutableSet<ContributionBinding> ownedContributionBindings() {
    return allContributionBindings().get(owningComponent());
  }

  /** The component that owns {@code binding}. */
  ComponentDescriptor owningComponent(ContributionBinding binding) {
    checkArgument(
        contributionBindings().contains(binding),
        "binding is not resolved for %s: %s",
        bindingKey(),
        binding);
    return getOnlyElement(allContributionBindings().inverse().get(binding));
  }

  /**
   * The members-injection binding, regardless of owning component. Empty if these are contribution
   * bindings.
   */
  Optional<MembersInjectionBinding> membersInjectionBinding() {
    ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
        FluentIterable.from(allMembersInjectionBindings().values()).toSet();
    return membersInjectionBindings.isEmpty()
        ? Optional.<MembersInjectionBinding>absent()
        : Optional.of(Iterables.getOnlyElement(membersInjectionBindings));
  }

  /**
   * The members-injection binding that was resolved in {@link #owningComponent()}. Empty if these
   * are contribution bindings.
   */
  Optional<MembersInjectionBinding> ownedMembersInjectionBinding() {
    return Optional.fromNullable(allMembersInjectionBindings().get(owningComponent()));
  }

  /** Creates a {@link ResolvedBindings} for contribution bindings. */
  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      Multimap<ComponentDescriptor, ? extends ContributionBinding> contributionBindings,
      Iterable<MultibindingDeclaration> multibindings,
      Iterable<SubcomponentDeclaration> subcomponentDeclarations,
      Iterable<OptionalBindingDeclaration> optionalBindingDeclarations) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>copyOf(contributionBindings),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of(),
        ImmutableSet.copyOf(multibindings),
        ImmutableSet.copyOf(subcomponentDeclarations),
        ImmutableSet.copyOf(optionalBindingDeclarations));
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
        ImmutableMap.of(owningComponent, ownedMembersInjectionBinding),
        ImmutableSet.<MultibindingDeclaration>of(),
        ImmutableSet.<SubcomponentDeclaration>of(),
        ImmutableSet.<OptionalBindingDeclaration>of());
  }

  /**
   * Creates a {@link ResolvedBindings} appropriate for when there are no bindings for the key.
   */
  static ResolvedBindings noBindings(BindingKey bindingKey, ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of(),
        ImmutableSet.<MultibindingDeclaration>of(),
        ImmutableSet.<SubcomponentDeclaration>of(),
        ImmutableSet.<OptionalBindingDeclaration>of());
  }

  /**
   * Returns a {@code ResolvedBindings} with the same {@link #bindingKey()} and {@link #bindings()}
   * as this one, but no {@link #ownedBindings()}.
   */
  ResolvedBindings asInheritedIn(ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey(),
        owningComponent,
        allContributionBindings(),
        allMembersInjectionBindings(),
        multibindingDeclarations(),
        subcomponentDeclarations(),
        optionalBindingDeclarations());
  }

  /**
   * {@code true} if this is a multibinding contribution.
   */
  boolean isMultibindingContribution() {
    return contributionBindings().size() == 1
        && contributionBinding().contributionType().isMultibinding();
  }

  /**
   * Returns the single contribution binding.
   *
   * @throws IllegalStateException if there is not exactly one element in
   *     {@link #contributionBindings()}, which will never happen for contributions in valid graphs
   */
  ContributionBinding contributionBinding() {
    return getOnlyElement(contributionBindings());
  }

  /**
   * The binding type for these bindings. If there are {@link #multibindingDeclarations()} or {@link
   * #subcomponentDeclarations()} but no {@link #bindings()}, returns {@link BindingType#PROVISION}.
   *
   * @throws IllegalStateException if {@link #isEmpty()} or the binding types conflict
   */
  @Override
  public BindingType bindingType() {
    checkState(!isEmpty(), "empty bindings for %s", bindingKey());
    if (bindings().isEmpty()
        && (!multibindingDeclarations().isEmpty() || !subcomponentDeclarations().isEmpty())) {
      // Only multibinding declarations, so assume provision.
      return BindingType.PROVISION;
    }
    ImmutableSet<BindingType> bindingTypes = bindingTypes();
    checkState(bindingTypes.size() == 1, "conflicting binding types: %s", bindings());
    return getOnlyElement(bindingTypes);
  }

  /** The binding types for {@link #bindings()}. */
  ImmutableSet<BindingType> bindingTypes() {
    return FluentIterable.from(bindings()).transform(HasBindingType::bindingType).toSet();
  }

  /**
   * The contribution type for these bindings.
   *
   * @throws IllegalStateException if there is not exactly one element in {@link
   *     #contributionBindings()}, which will never happen for contributions in valid graphs
   */
  @Override
  public ContributionType contributionType() {
    return contributionBinding().contributionType();
  }

  /**
   * The name of the package in which these bindings must be managed, for
   * example if a binding references non-public types.
   * 
   * @throws IllegalArgumentException if the bindings must be managed in more than one package
   */
  Optional<String> bindingPackage() {
    ImmutableSet.Builder<String> bindingPackagesBuilder = ImmutableSet.builder();
    for (Binding binding : bindings()) {
      bindingPackagesBuilder.addAll(binding.bindingPackage().asSet());
    }
    ImmutableSet<String> bindingPackages = bindingPackagesBuilder.build();
    switch (bindingPackages.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.of(bindingPackages.iterator().next());
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * The framework class associated with these bindings.
   */
  Class<?> frameworkClass() {
    return bindingType().frameworkClass();
  }

  /**
   * The scope associated with the single binding.
   *
   * @throws IllegalStateException if {@link #bindings()} does not have exactly one element
   */
  Optional<Scope> scope() {
    return getOnlyElement(bindings()).scope();
  }
}
