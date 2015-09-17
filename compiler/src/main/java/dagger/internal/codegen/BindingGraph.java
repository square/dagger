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
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;
import dagger.Component;
import dagger.Subcomponent;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.ProductionComponent;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.BindingKey.Kind.CONTRIBUTION;
import static dagger.internal.codegen.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.ComponentDescriptor.Kind.PRODUCTION_COMPONENT;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.INJECT_MEMBERS;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * The canonical representation of a full-resolved graph.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class BindingGraph {
  abstract ComponentDescriptor componentDescriptor();
  abstract ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings();
  abstract ImmutableMap<ExecutableElement, BindingGraph> subgraphs();

  /**
   * Returns the set of modules that are owned by this graph regardless of whether or not any of
   * their bindings are used in this graph. For graphs representing top-level {@link Component
   * components}, this set will be the same as
   * {@linkplain ComponentDescriptor#transitiveModules the component's transitive modules}. For
   * {@linkplain Subcomponent subcomponents}, this set will be the transitive modules that are not
   * owned by any of their ancestors.
   */
  abstract ImmutableSet<ModuleDescriptor> ownedModules();

  ImmutableSet<TypeElement> ownedModuleTypes() {
    return FluentIterable.from(ownedModules())
        .transform(ModuleDescriptor.getModuleElement())
        .toSet();
  }

  private static final TreeTraverser<BindingGraph> SUBGRAPH_TRAVERSER =
      new TreeTraverser<BindingGraph>() {
        @Override
        public Iterable<BindingGraph> children(BindingGraph node) {
          return node.subgraphs().values();
        }
      };

  /**
   * Returns the set of types necessary to implement the component, but are not part of the injected
   * graph.  This includes modules, component dependencies and an {@link Executor} in the case of
   * {@link ProductionComponent}.
   */
  ImmutableSet<TypeElement> componentRequirements() {
    return SUBGRAPH_TRAVERSER.preOrderTraversal(this)
        .transformAndConcat(new Function<BindingGraph, Iterable<ResolvedBindings>>() {
          @Override
          public Iterable<ResolvedBindings> apply(BindingGraph input) {
            return input.resolvedBindings().values();
          }
        })
        .transformAndConcat(new Function<ResolvedBindings, Set<? extends ContributionBinding>>() {
          @Override
          public Set<? extends ContributionBinding> apply(ResolvedBindings input) {
            return (input.bindingKey().kind().equals(CONTRIBUTION))
                ? input.contributionBindings()
                : ImmutableSet.<ContributionBinding>of();
          }
        })
        .transformAndConcat(new Function<ContributionBinding, Set<TypeElement>>() {
          @Override
          public Set<TypeElement> apply(ContributionBinding input) {
            return input.bindingElement().getModifiers().contains(STATIC)
                ? ImmutableSet.<TypeElement>of()
                : input.contributedBy().asSet();
          }
        })
        .filter(in(ownedModuleTypes()))
        .append(componentDescriptor().dependencies())
        .append(componentDescriptor().executorDependency().asSet())
        .toSet();
  }

  ImmutableSet<TypeElement> availableDependencies() {
    return new ImmutableSet.Builder<TypeElement>()
        .addAll(componentDescriptor().transitiveModuleTypes())
        .addAll(componentDescriptor().dependencies())
        .addAll(componentDescriptor().executorDependency().asSet())
        .build();
  }

  static final class Factory {
    private final Elements elements;
    private final InjectBindingRegistry injectBindingRegistry;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final ProductionBinding.Factory productionBindingFactory;

    Factory(Elements elements,
        InjectBindingRegistry injectBindingRegistry,
        Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory,
        ProvisionBinding.Factory provisionBindingFactory,
        ProductionBinding.Factory productionBindingFactory) {
      this.elements = elements;
      this.injectBindingRegistry = injectBindingRegistry;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.provisionBindingFactory = provisionBindingFactory;
      this.productionBindingFactory = productionBindingFactory;
    }

    BindingGraph create(ComponentDescriptor componentDescriptor) {
      return create(Optional.<Resolver>absent(), componentDescriptor);
    }

    private BindingGraph create(
        Optional<Resolver> parentResolver, ComponentDescriptor componentDescriptor) {
      ImmutableSet.Builder<ProvisionBinding> explicitProvisionBindingsBuilder =
          ImmutableSet.builder();
      ImmutableSet.Builder<ProductionBinding> explicitProductionBindingsBuilder =
          ImmutableSet.builder();

      // binding for the component itself
      TypeElement componentDefinitionType = componentDescriptor.componentDefinitionType();
      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDefinitionType);
      explicitProvisionBindingsBuilder.add(componentBinding);

      // Collect Component dependencies.
      Optional<AnnotationMirror> componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class)
              .or(getAnnotationMirror(componentDefinitionType, ProductionComponent.class));
      ImmutableSet<TypeElement> componentDependencyTypes = componentMirror.isPresent()
          ? MoreTypes.asTypeElements(getComponentDependencies(componentMirror.get()))
          : ImmutableSet.<TypeElement>of();
      for (TypeElement componentDependency : componentDependencyTypes) {
        explicitProvisionBindingsBuilder.add(
            provisionBindingFactory.forComponent(componentDependency));
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement method : dependencyMethods) {
          // MembersInjection methods aren't "provided" explicitly, so ignore them.
          if (isComponentContributionMethod(elements, method)) {
            if (componentDescriptor.kind().equals(PRODUCTION_COMPONENT)
                && isComponentProductionMethod(elements, method)) {
              explicitProductionBindingsBuilder.add(
                  productionBindingFactory.forComponentMethod(method));
            } else {
              explicitProvisionBindingsBuilder.add(
                  provisionBindingFactory.forComponentMethod(method));
            }
          }
        }
      }

      // Collect transitive module bindings.
      for (ModuleDescriptor moduleDescriptor : componentDescriptor.transitiveModules()) {
        for (ContributionBinding binding : moduleDescriptor.bindings()) {
          if (binding instanceof ProvisionBinding) {
            explicitProvisionBindingsBuilder.add((ProvisionBinding) binding);
          }
          if (binding instanceof ProductionBinding) {
            explicitProductionBindingsBuilder.add((ProductionBinding) binding);
          }
        }
      }

      Resolver requestResolver =
          new Resolver(
              parentResolver,
              componentDescriptor,
              explicitBindingsByKey(explicitProvisionBindingsBuilder.build()),
              explicitBindingsByKey(explicitProductionBindingsBuilder.build()));
      for (ComponentMethodDescriptor componentMethod : componentDescriptor.componentMethods()) {
        Optional<DependencyRequest> componentMethodRequest = componentMethod.dependencyRequest();
        if (componentMethodRequest.isPresent()) {
          requestResolver.resolve(componentMethodRequest.get());
        }
      }

      ImmutableMap.Builder<ExecutableElement, BindingGraph> subgraphsBuilder =
          ImmutableMap.builder();
      for (Entry<ComponentMethodDescriptor, ComponentDescriptor> subcomponentEntry :
          componentDescriptor.subcomponents().entrySet()) {
        subgraphsBuilder.put(
            subcomponentEntry.getKey().methodElement(),
            create(Optional.of(requestResolver), subcomponentEntry.getValue()));
      }

      for (ResolvedBindings resolvedBindings : requestResolver.getResolvedBindings().values()) {
        verify(
            resolvedBindings.owningComponent().equals(componentDescriptor),
            "%s is not owned by %s",
            resolvedBindings,
            componentDescriptor);
      }

      return new AutoValue_BindingGraph(
          componentDescriptor,
          requestResolver.getResolvedBindings(),
          subgraphsBuilder.build(),
          requestResolver.getOwnedModules());
    }

    private <B extends ContributionBinding> ImmutableSetMultimap<Key, B> explicitBindingsByKey(
        Iterable<? extends B> bindings) {
      // Multimaps.index() doesn't do ImmutableSetMultimaps.
      ImmutableSetMultimap.Builder<Key, B> builder = ImmutableSetMultimap.builder();
      for (B binding : bindings) {
        builder.put(binding.key(), binding);
      }
      return builder.build();
    }

    private final class Resolver {
      final Optional<Resolver> parentResolver;
      final ComponentDescriptor componentDescriptor;
      final ImmutableSetMultimap<Key, ProvisionBinding> explicitProvisionBindings;
      final ImmutableSet<ProvisionBinding> explicitProvisionBindingsSet;
      final ImmutableSetMultimap<Key, ProductionBinding> explicitProductionBindings;
      final Map<BindingKey, ResolvedBindings> resolvedBindings;
      final Deque<BindingKey> cycleStack = new ArrayDeque<>();
      final Cache<BindingKey, Boolean> dependsOnLocalMultibindingsCache =
          CacheBuilder.newBuilder().<BindingKey, Boolean>build();

      Resolver(
          Optional<Resolver> parentResolver,
          ComponentDescriptor componentDescriptor,
          ImmutableSetMultimap<Key, ProvisionBinding> explicitProvisionBindings,
          ImmutableSetMultimap<Key, ProductionBinding> explicitProductionBindings) {
        assert parentResolver != null;
        this.parentResolver = parentResolver;
        assert componentDescriptor != null;
        this.componentDescriptor = componentDescriptor;
        assert explicitProvisionBindings != null;
        this.explicitProvisionBindings = explicitProvisionBindings;
        this.explicitProvisionBindingsSet = ImmutableSet.copyOf(explicitProvisionBindings.values());
        assert explicitProductionBindings != null;
        this.explicitProductionBindings = explicitProductionBindings;
        this.resolvedBindings = Maps.newLinkedHashMap();
      }

      /**
       *  Looks up the bindings associated with a given dependency request and returns them.  In the
       *  event that the binding is owned by a parent component it will trigger resolution in that
       *  component's resolver but will return an {@link Optional#absent} value.
       */
      ResolvedBindings lookUpBindings(DependencyRequest request) {
        BindingKey bindingKey = request.bindingKey();
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            // First, check for explicit keys (those from modules and components)
            ImmutableSet<ProvisionBinding> explicitProvisionBindingsForKey =
                getExplicitProvisionBindings(bindingKey.key());
            ImmutableSet<ProductionBinding> explicitProductionBindingsForKey =
                getExplicitProductionBindings(bindingKey.key());

            // If the key is Map<K, V>, get its implicit binding keys, which are either
            // Map<K, Provider<V>> or Map<K, Producer<V>>, and grab their explicit bindings.
            Optional<Key> mapProviderKey = keyFactory.implicitMapProviderKeyFrom(bindingKey.key());
            ImmutableSet<ProvisionBinding> explicitMapProvisionBindings = ImmutableSet.of();
            if (mapProviderKey.isPresent()) {
              explicitMapProvisionBindings = getExplicitProvisionBindings(mapProviderKey.get());
            }

            Optional<Key> mapProducerKey = keyFactory.implicitMapProducerKeyFrom(bindingKey.key());
            ImmutableSet<ProductionBinding> explicitMapProductionBindings = ImmutableSet.of();
            if (mapProducerKey.isPresent()) {
              explicitMapProductionBindings = getExplicitProductionBindings(mapProducerKey.get());
            }

            if (!explicitProvisionBindingsForKey.isEmpty()
                || !explicitProductionBindingsForKey.isEmpty()) {
              // we have some explicit binding for this key, so we collect all explicit implicit map
              // bindings that might conflict with this and let the validator sort it out
              ImmutableSet.Builder<ContributionBinding> ownedBindings = ImmutableSet.builder();
              ImmutableSetMultimap.Builder<ComponentDescriptor, ContributionBinding>
                  inheritedBindings = ImmutableSetMultimap.builder();
              for (ProvisionBinding provisionBinding :
                  Sets.union(explicitProvisionBindingsForKey, explicitMapProvisionBindings)) {
                if (isResolvedInParent(request, provisionBinding)
                    && !shouldOwnParentBinding(request, provisionBinding)) {
                  inheritedBindings.put(
                      getOwningResolver(provisionBinding).get().componentDescriptor,
                      provisionBinding);
                } else {
                  ownedBindings.add(provisionBinding);
                }
              }
              return ResolvedBindings.create(bindingKey,
                  componentDescriptor,
                  ownedBindings
                      .addAll(explicitProductionBindingsForKey)
                      .addAll(explicitMapProductionBindings)
                      .build(),
                  inheritedBindings.build());
            } else {
              if (!explicitMapProductionBindings.isEmpty()) {
                // if we have any explicit Map<K, Producer<V>> bindings, then this Map<K, V> binding
                // must be considered an implicit ProductionBinding
                DependencyRequest implicitRequest =
                    dependencyRequestFactory.forImplicitMapBinding(request, mapProducerKey.get());
                return ResolvedBindings.create(
                    bindingKey,
                    componentDescriptor,
                    productionBindingFactory.forImplicitMapBinding(request, implicitRequest));
              } else if (!explicitMapProvisionBindings.isEmpty()) {
                // if there are Map<K, Provider<V>> bindings, then it'll be an implicit
                // ProvisionBinding
                DependencyRequest implicitRequest =
                    dependencyRequestFactory.forImplicitMapBinding(request, mapProviderKey.get());
                return ResolvedBindings.create(
                    bindingKey,
                    componentDescriptor,
                    provisionBindingFactory.forImplicitMapBinding(request, implicitRequest));
              } else {
                // no explicit binding, look it up.
                Optional<ProvisionBinding> provisionBinding =
                    injectBindingRegistry.getOrFindProvisionBinding(bindingKey.key());
                if (provisionBinding.isPresent()) {
                  if (isResolvedInParent(request, provisionBinding.get())
                      && !shouldOwnParentBinding(request, provisionBinding.get())) {
                    return ResolvedBindings.create(
                        bindingKey,
                        componentDescriptor,
                        ImmutableSet.<Binding>of(),
                        ImmutableSetMultimap.of(
                            getOwningResolver(provisionBinding.get()).get().componentDescriptor,
                            provisionBinding.get()));
                  }
                }
                return ResolvedBindings.create(
                    bindingKey,
                    componentDescriptor,
                    provisionBinding.asSet(),
                    ImmutableSetMultimap.<ComponentDescriptor, Binding>of());
              }
            }
          case MEMBERS_INJECTION:
            // no explicit deps for members injection, so just look it up
            return ResolvedBindings.create(
                bindingKey,
                componentDescriptor,
                rollUpMembersInjectionBindings(bindingKey.key()));
          default:
            throw new AssertionError();
        }
      }

      /**
       * Returns {@code true} if {@code provisionBinding} is owned by a parent resolver. If so,
       * calls {@link #resolve(DependencyRequest) resolve(request)} on that resolver.
       */
      private boolean isResolvedInParent(
          DependencyRequest request, ProvisionBinding provisionBinding) {
        Optional<Resolver> owningResolver = getOwningResolver(provisionBinding);
        if (owningResolver.isPresent() && !owningResolver.get().equals(this)) {
          owningResolver.get().resolve(request);
          return true;
        } else {
          return false;
        }
      }

      /**
       * Returns {@code true} if {@code provisionBinding}, which was previously resolved by a parent
       * resolver, should be moved into this resolver's bindings for {@code request} because it is
       * unscoped and {@linkplain #dependsOnLocalMultibindings(ResolvedBindings) depends on local
       * multibindings}, or {@code false} if it can satisfy {@code request} as an inherited binding.
       */
      private boolean shouldOwnParentBinding(
          DependencyRequest request, ProvisionBinding provisionBinding) {
        return !isScoped(provisionBinding)
            && dependsOnLocalMultibindings(
                getPreviouslyResolvedBindings(request.bindingKey()).get());
      }

      private MembersInjectionBinding rollUpMembersInjectionBindings(Key key) {
        MembersInjectionBinding membersInjectionBinding =
            injectBindingRegistry.getOrFindMembersInjectionBinding(key);

        if (membersInjectionBinding.parentInjectorRequest().isPresent()
            && membersInjectionBinding.injectionStrategy().equals(INJECT_MEMBERS)) {
          MembersInjectionBinding parentBinding =
              rollUpMembersInjectionBindings(
                  membersInjectionBinding.parentInjectorRequest().get().key());
          if (parentBinding.injectionStrategy().equals(NO_OP)) {
            return membersInjectionBinding.withoutParentInjectorRequest();
          }
        }

        return membersInjectionBinding;
      }

      private Optional<Resolver> getOwningResolver(ProvisionBinding provisionBinding) {
        for (Resolver requestResolver : getResolverLineage().reverse()) {
          if (requestResolver.explicitProvisionBindingsSet.contains(provisionBinding)) {
            return Optional.of(requestResolver);
          }
        }

        // look for scope separately.  we do this for the case where @Singleton can appear twice
        // in the † compatibility mode
        Scope bindingScope = provisionBinding.scope();
        if (bindingScope.isPresent()) {
          for (Resolver requestResolver : getResolverLineage().reverse()) {
            if (bindingScope.equals(requestResolver.componentDescriptor.scope())) {
              return Optional.of(requestResolver);
            }
          }
        }
        return Optional.absent();
      }

      /** Returns the resolver lineage from parent to child. */
      private ImmutableList<Resolver> getResolverLineage() {
        List<Resolver> resolverList = Lists.newArrayList();
        for (Optional<Resolver> currentResolver = Optional.of(this);
            currentResolver.isPresent();
            currentResolver = currentResolver.get().parentResolver) {
          resolverList.add(currentResolver.get());
        }
        return ImmutableList.copyOf(Lists.reverse(resolverList));
      }

      private ImmutableSet<ProvisionBinding> getExplicitProvisionBindings(Key requestKey) {
        ImmutableSet.Builder<ProvisionBinding> explicitBindingsForKey = ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          explicitBindingsForKey.addAll(resolver.explicitProvisionBindings.get(requestKey));
        }
        return explicitBindingsForKey.build();
      }

      private ImmutableSet<ProductionBinding> getExplicitProductionBindings(Key requestKey) {
        ImmutableSet.Builder<ProductionBinding> explicitBindingsForKey = ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          explicitBindingsForKey.addAll(resolver.explicitProductionBindings.get(requestKey));
        }
        return explicitBindingsForKey.build();
      }

      private Optional<ResolvedBindings> getPreviouslyResolvedBindings(
          final BindingKey bindingKey) {
        Optional<ResolvedBindings> result = Optional.fromNullable(resolvedBindings.get(bindingKey));
        if (result.isPresent()) {
          return result;
        } else if (parentResolver.isPresent()) {
          return parentResolver.get().getPreviouslyResolvedBindings(bindingKey);
        } else {
          return Optional.absent();
        }
      }

      void resolve(DependencyRequest request) {
        BindingKey bindingKey = request.bindingKey();

        // If we find a cycle, stop resolving. The original request will add it with all of the
        // other resolved deps.
        if (cycleStack.contains(bindingKey)) {
          return;
        }

        // If the binding was previously resolved in this (sub)component, don't resolve it again.
        if (resolvedBindings.containsKey(bindingKey)) {
          return;
        }

        // If the binding was previously resolved in a supercomponent, then test to see if it
        // depends on multibindings with contributions from this subcomponent. If it does, then we
        // have to resolve it in this subcomponent so that it sees the local contributions. If it
        // does not, then we can stop resolving it in this subcomponent and rely on the
        // supercomponent resolution.
        Optional<ResolvedBindings> bindingsPreviouslyResolvedInParent =
            getPreviouslyResolvedBindings(bindingKey);
        if (bindingsPreviouslyResolvedInParent.isPresent()
            && !dependsOnLocalMultibindings(bindingsPreviouslyResolvedInParent.get())) {
          return;
        }

        cycleStack.push(bindingKey);
        try {
          ResolvedBindings bindings = lookUpBindings(request);
          for (Binding binding : bindings.ownedBindings()) {
            for (DependencyRequest dependency : binding.implicitDependencies()) {
              resolve(dependency);
            }
          }
          resolvedBindings.put(bindingKey, bindings);
        } finally {
          cycleStack.pop();
        }
      }

      /**
       * Returns {@code true} if {@code previouslyResolvedBindings} is multibindings with
       * contributions declared within this (sub)component's modules, or if any of its unscoped
       * provision-dependencies depend on such local multibindings.
       *
       * <p>We don't care about scoped dependencies or production bindings because they will never
       * depend on multibindings with contributions from subcomponents.
       */
      private boolean dependsOnLocalMultibindings(ResolvedBindings previouslyResolvedBindings) {
        return dependsOnLocalMultibindings(previouslyResolvedBindings, new HashSet<BindingKey>());
      }

      private boolean dependsOnLocalMultibindings(
          final ResolvedBindings previouslyResolvedBindings, final Set<BindingKey> cycleChecker) {
        // Don't recur infinitely if there are valid cycles in the dependency graph.
        if (!cycleChecker.add(previouslyResolvedBindings.bindingKey())) {
          return false;
        }
        try {
          return dependsOnLocalMultibindingsCache.get(
              previouslyResolvedBindings.bindingKey(),
              new Callable<Boolean>() {
                @Override
                public Boolean call() {
                  if (previouslyResolvedBindings.isMultibindings()
                      && hasLocalContributions(previouslyResolvedBindings)) {
                    return true;
                  }

                  for (Binding binding : previouslyResolvedBindings.bindings()) {
                    if (!isScoped(binding) && !(binding instanceof ProductionBinding)) {
                      for (DependencyRequest dependency : binding.implicitDependencies()) {
                        if (dependsOnLocalMultibindings(
                            getPreviouslyResolvedBindings(dependency.bindingKey()).get(),
                            cycleChecker)) {
                          return true;
                        }
                      }
                    }
                  }
                  return false;
                }
              });
        } catch (ExecutionException e) {
          throw new AssertionError(e);
        }
      }

      private boolean hasLocalContributions(ResolvedBindings resolvedBindings) {
        return !explicitProvisionBindings.get(resolvedBindings.bindingKey().key()).isEmpty()
            || !explicitProductionBindings.get(resolvedBindings.bindingKey().key()).isEmpty();
      }

      private boolean isScoped(Binding binding) {
        if (binding instanceof ProvisionBinding) {
          ProvisionBinding provisionBinding = (ProvisionBinding) binding;
          return provisionBinding.scope().isPresent();
        }
        return false;
      }

      ImmutableMap<BindingKey, ResolvedBindings> getResolvedBindings() {
        ImmutableMap.Builder<BindingKey, ResolvedBindings> resolvedBindingsBuilder =
            ImmutableMap.builder();
        resolvedBindingsBuilder.putAll(resolvedBindings);
        if (parentResolver.isPresent()) {
          Collection<ResolvedBindings> bindingsResolvedInParent =
              Maps.difference(parentResolver.get().getResolvedBindings(), resolvedBindings)
                  .entriesOnlyOnLeft()
                  .values();
          for (ResolvedBindings resolvedInParent : bindingsResolvedInParent) {
            resolvedBindingsBuilder.put(
                resolvedInParent.bindingKey(),
                resolvedInParent.asInheritedIn(componentDescriptor));
          }
        }
        return resolvedBindingsBuilder.build();
      }

      ImmutableSet<ModuleDescriptor> getInheritedModules() {
        return parentResolver.isPresent()
            ? Sets.union(
                    parentResolver.get().getInheritedModules(),
                    parentResolver.get().componentDescriptor.transitiveModules())
                .immutableCopy()
            : ImmutableSet.<ModuleDescriptor>of();
      }

      ImmutableSet<ModuleDescriptor> getOwnedModules() {
        return Sets.difference(componentDescriptor.transitiveModules(), getInheritedModules())
            .immutableCopy();
      }
    }
  }
}
