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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import dagger.Provides;
import dagger.producers.Produces;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.ComponentDescriptor.Kind.PRODUCTION_COMPONENT;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.getTransitiveModules;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

/**
 * The canonical representation of a full-resolved graph.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class BindingGraph {
  abstract ComponentDescriptor componentDescriptor();
  abstract ImmutableSet<DependencyRequest> entryPoints();
  abstract ImmutableMap<TypeElement, ImmutableSet<TypeElement>> transitiveModules();
  abstract ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings();

  @AutoValue
  abstract static class ResolvedBindings {
    abstract BindingKey bindingKey();
    abstract ImmutableSet<? extends Binding> bindings();

    static ResolvedBindings create(
        BindingKey bindingKey, ImmutableSet<? extends Binding> bindings) {
      return new AutoValue_BindingGraph_ResolvedBindings(bindingKey, bindings);
    }

    @SuppressWarnings("unchecked")  // checked by validator
    ImmutableSet<? extends ContributionBinding> contributionBindings() {
      checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
      return (ImmutableSet<? extends ContributionBinding>) bindings();
    }

    @SuppressWarnings("unchecked")  // checked by validator
    ImmutableSet<? extends MembersInjectionBinding> membersInjectionBindings() {
      checkState(bindingKey().kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
      return (ImmutableSet<? extends MembersInjectionBinding>) bindings();
    }
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final InjectBindingRegistry injectBindingRegistry;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final ProductionBinding.Factory productionBindingFactory;

    Factory(Elements elements,
        Types types,
        InjectBindingRegistry injectBindingRegistry,
        Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory,
        ProvisionBinding.Factory provisionBindingFactory,
        ProductionBinding.Factory productionBindingFactory) {
      this.elements = elements;
      this.types = types;
      this.injectBindingRegistry = injectBindingRegistry;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.provisionBindingFactory = provisionBindingFactory;
      this.productionBindingFactory = productionBindingFactory;
    }

    BindingGraph create(ComponentDescriptor componentDescriptor) {
      ImmutableSet.Builder<ProvisionBinding> explicitProvisionBindingsBuilder =
          ImmutableSet.builder();
      ImmutableSet.Builder<ProductionBinding> explicitProductionBindingsBuilder =
          ImmutableSet.builder();
      AnnotationMirror componentAnnotation = componentDescriptor.componentAnnotation();

      // binding for the component itself
      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDescriptor.componentDefinitionType());
      explicitProvisionBindingsBuilder.add(componentBinding);

      // Collect Component dependencies.
      ImmutableSet<TypeElement> componentDependencyTypes =
          MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation));
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

      // Collect transitive modules provisions.
      ImmutableSet<TypeElement> moduleTypes =
          MoreTypes.asTypeElements(getComponentModules(componentAnnotation));

      ImmutableMap<TypeElement, ImmutableSet<TypeElement>> transitiveModules =
          getTransitiveModules(types, elements, moduleTypes);
      for (TypeElement module : transitiveModules.keySet()) {
        // traverse the modules, collect the bindings
        List<ExecutableElement> moduleMethods = methodsIn(elements.getAllMembers(module));
        for (ExecutableElement moduleMethod : moduleMethods) {
          if (isAnnotationPresent(moduleMethod, Provides.class)) {
            explicitProvisionBindingsBuilder.add(
                provisionBindingFactory.forProvidesMethod(moduleMethod, module.asType()));
          }
          if (isAnnotationPresent(moduleMethod, Produces.class)) {
            explicitProductionBindingsBuilder.add(
                productionBindingFactory.forProducesMethod(moduleMethod, module.asType()));
           }
        }
      }

      RequestResolver requestResolver = new RequestResolver(
          explicitBindingsByKey(explicitProvisionBindingsBuilder.build()),
          explicitBindingsByKey(explicitProductionBindingsBuilder.build()));
      ImmutableSet<DependencyRequest> componentMethodRequests = componentMethodRequests(
          componentDescriptor.componentDefinitionType(), componentDescriptor.kind());
      for (DependencyRequest componentMethodRequest :
          componentMethodRequests) {
        requestResolver.resolve(componentMethodRequest);
      }

      return new AutoValue_BindingGraph(
          componentDescriptor,
          componentMethodRequests,
          transitiveModules,
          ImmutableMap.copyOf(requestResolver.resolvedBindings));
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

    private ImmutableSet<DependencyRequest> componentMethodRequests(TypeElement componentType,
        ComponentDescriptor.Kind componentKind) {
      ImmutableSet.Builder<DependencyRequest> interfaceRequestsBuilder = ImmutableSet.builder();
      for (ExecutableElement componentMethod : methodsIn(elements.getAllMembers(componentType))) {
        if (componentMethod.getModifiers().contains(Modifier.ABSTRACT)) { // Elide Object.*;
          if (isComponentContributionMethod(elements, componentMethod)) {
            if (componentKind.equals(PRODUCTION_COMPONENT)
                && isComponentProductionMethod(elements, componentMethod)) {
              interfaceRequestsBuilder.add(
                  dependencyRequestFactory.forComponentProductionMethod(componentMethod));
            } else {
              interfaceRequestsBuilder.add(
                  dependencyRequestFactory.forComponentProvisionMethod(componentMethod));
            }
          } else if (isComponentMembersInjectionMethod(componentMethod)) {
            interfaceRequestsBuilder.add(
                dependencyRequestFactory.forComponentMembersInjectionMethod(componentMethod));
          }
        }
      }
      return interfaceRequestsBuilder.build();
    }

    private boolean isComponentMembersInjectionMethod(ExecutableElement method) {
      List<? extends VariableElement> parameters = method.getParameters();
      TypeMirror returnType = method.getReturnType();
      return parameters.size() == 1
          && (returnType.getKind().equals(VOID)
              || MoreTypes.equivalence().equivalent(returnType, parameters.get(0).asType()))
          && !elements.getTypeElement(Object.class.getCanonicalName())
              .equals(method.getEnclosingElement());
    }

    private final class RequestResolver {
      final ImmutableSetMultimap<Key, ProvisionBinding> explicitProvisionBindings;
      final ImmutableSetMultimap<Key, ProductionBinding> explicitProductionBindings;
      final Map<BindingKey, ResolvedBindings> resolvedBindings;
      final Deque<BindingKey> cycleStack = Queues.newArrayDeque();

      RequestResolver(ImmutableSetMultimap<Key, ProvisionBinding> explicitProvisionBindings,
          ImmutableSetMultimap<Key, ProductionBinding> explicitProductionBindings) {
        assert explicitProvisionBindings != null;
        this.explicitProvisionBindings = explicitProvisionBindings;
        assert explicitProductionBindings != null;
        this.explicitProductionBindings = explicitProductionBindings;
        this.resolvedBindings = Maps.newLinkedHashMap();
      }

      ImmutableSet<? extends Binding> lookUpBindings(DependencyRequest request) {
        BindingKey bindingKey = BindingKey.forDependencyRequest(request);
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            // First, check for explicit keys (those from modules and components)
            ImmutableSet<ProvisionBinding> explicitProvisionBindingsForKey =
                explicitProvisionBindings.get(bindingKey.key());
            ImmutableSet<ProductionBinding> explicitProductionBindingsForKey =
                explicitProductionBindings.get(bindingKey.key());

            // If the key is Map<K, V>, get its implicit binding keys, which are either
            // Map<K, Provider<V>> or Map<K, Producer<V>>, and grab their explicit bindings.
            Optional<Key> mapProviderKey = keyFactory.implicitMapProviderKeyFrom(bindingKey.key());
            ImmutableSet<ProvisionBinding> explicitMapProvisionBindings = ImmutableSet.of();
            if (mapProviderKey.isPresent()) {
              explicitMapProvisionBindings = explicitProvisionBindings.get(mapProviderKey.get());
            }

            Optional<Key> mapProducerKey = keyFactory.implicitMapProducerKeyFrom(bindingKey.key());
            ImmutableSet<ProductionBinding> explicitMapProductionBindings = ImmutableSet.of();
            if (mapProducerKey.isPresent()) {
              explicitMapProductionBindings = explicitProductionBindings.get(mapProducerKey.get());
            }

            if (!explicitProvisionBindingsForKey.isEmpty()
                || !explicitProductionBindingsForKey.isEmpty()) {
              // we have some explicit binding for this key, so we collect all explicit implicit map
              // bindings that might conflict with this and let the validator sort it out
              return ImmutableSet.<ContributionBinding>builder()
                  .addAll(explicitProvisionBindingsForKey)
                  .addAll(explicitMapProvisionBindings)
                  .addAll(explicitProductionBindingsForKey)
                  .addAll(explicitMapProductionBindings)
                  .build();
            } else {
              if (!explicitMapProductionBindings.isEmpty()) {
                // if we have any explicit Map<K, Producer<V>> bindings, then this Map<K, V> binding
                // must be considered an implicit ProductionBinding
                DependencyRequest implicitRequest =
                    dependencyRequestFactory.forImplicitMapBinding(request, mapProducerKey.get());
                return ImmutableSet.of(productionBindingFactory.forImplicitMapBinding(
                    request, implicitRequest));
              } else if (!explicitMapProvisionBindings.isEmpty()) {
                // if there are Map<K, Provider<V>> bindings, then it'll be an implicit
                // ProvisionBinding
                DependencyRequest implicitRequest =
                    dependencyRequestFactory.forImplicitMapBinding(request, mapProviderKey.get());
                return ImmutableSet.of(provisionBindingFactory.forImplicitMapBinding(
                    request, implicitRequest));
              } else {
                // no explicit binding, look it up.
                Optional<ProvisionBinding> provisionBinding =
                    injectBindingRegistry.getOrFindProvisionBinding(bindingKey.key());
                return ImmutableSet.copyOf(provisionBinding.asSet());
              }
            }
          case MEMBERS_INJECTION:
            // no explicit deps for members injection, so just look it up
            MembersInjectionBinding membersInjectionBinding =
                injectBindingRegistry.getOrFindMembersInjectionBinding(bindingKey.key());
            return ImmutableSet.of(membersInjectionBinding);
          default:
            throw new AssertionError();
        }
      }

      void resolve(DependencyRequest request) {
        BindingKey bindingKey = BindingKey.forDependencyRequest(request);

        ResolvedBindings previouslyResolvedBinding = resolvedBindings.get(bindingKey);
        if (previouslyResolvedBinding != null) {
          return;
        }

        if (cycleStack.contains(bindingKey)) {
          // We found a cycle. Don't add a resolved binding, since the original request will add it
          // with all of the other resolved deps
          return;
        }

        cycleStack.push(bindingKey);
        try {
          ImmutableSet<? extends Binding> bindings = lookUpBindings(request);
          for (Binding binding : bindings) {
            resolveDependencies(binding.implicitDependencies());
          }
          resolvedBindings.put(bindingKey, ResolvedBindings.create(bindingKey, bindings));
        } finally {
          cycleStack.pop();
        }
      }

      private void resolveDependencies(Iterable<DependencyRequest> dependencies) {
        for (DependencyRequest dependency : dependencies) {
          resolve(dependency);
        }
      }
    }
  }
}
