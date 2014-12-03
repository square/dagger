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
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import dagger.Provides;
import dagger.internal.codegen.BindingGraph.ResolvedBindings.State;
import dagger.internal.codegen.ProvisionBinding.BindingType;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProvisionMethod;
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
  abstract ImmutableMap<FrameworkKey, ResolvedBindings> resolvedBindings();

  @AutoValue
  abstract static class ResolvedBindings {
    enum State {
      COMPLETE,
      INCOMPLETE,
      MULTIPLE_BINDING_TYPES,
      DUPLICATE_BINDINGS,
      CYCLE,
      MALFORMED,
      MISSING,
    }

    abstract FrameworkKey.Kind kind();
    abstract State state();
    abstract ImmutableSet<ProvisionBinding> internalProvisionBindings();
    abstract ImmutableSet<MembersInjectionBinding> internalMembersInjectionBindings();

    static ResolvedBindings createForProvisionBindings(
        State state, ImmutableSet<ProvisionBinding> provisionBindings) {
      return new AutoValue_BindingGraph_ResolvedBindings(
          FrameworkKey.Kind.PROVIDER, state, provisionBindings,
          ImmutableSet.<MembersInjectionBinding>of());
    }

    static ResolvedBindings createForMembersInjectionBindings(
        State state, ImmutableSet<MembersInjectionBinding> membersInjectionBindings) {
      return new AutoValue_BindingGraph_ResolvedBindings(
          FrameworkKey.Kind.MEMBERS_INJECTOR, state, ImmutableSet.<ProvisionBinding>of(),
          membersInjectionBindings);
    }

    ImmutableSet<? extends Binding> bindings() {
      switch (kind()) {
        case PROVIDER:
          return internalProvisionBindings();
        case MEMBERS_INJECTOR:
          return internalMembersInjectionBindings();
        default:
          throw new AssertionError();
      }
    }

    ImmutableSet<ProvisionBinding> provisionBindings() {
      checkState(kind() == FrameworkKey.Kind.PROVIDER);
      return internalProvisionBindings();
    }

    ImmutableSet<MembersInjectionBinding> membersInjectionBindings() {
      checkState(kind() == FrameworkKey.Kind.MEMBERS_INJECTOR);
      return internalMembersInjectionBindings();
    }
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final InjectBindingRegistry injectBindingRegistry;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;
    private final ProvisionBinding.Factory provisionBindingFactory;

    Factory(Elements elements,
        Types types,
        InjectBindingRegistry injectBindingRegistry,
        dagger.internal.codegen.Key.Factory keyFactory,
        dagger.internal.codegen.DependencyRequest.Factory dependencyRequestFactory,
        dagger.internal.codegen.ProvisionBinding.Factory provisionBindingFactory) {
      this.elements = elements;
      this.types = types;
      this.injectBindingRegistry = injectBindingRegistry;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.provisionBindingFactory = provisionBindingFactory;
    }

    BindingGraph create(ComponentDescriptor componentDescriptor) {
      ImmutableSet.Builder<ProvisionBinding> explicitBindingsBuilder = ImmutableSet.builder();
      AnnotationMirror componentAnnotation = componentDescriptor.componentAnnotation();

      // binding for the component itself
      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDescriptor.componentDefinitionType());
      explicitBindingsBuilder.add(componentBinding);

      // Collect Component dependencies.
      ImmutableSet<TypeElement> componentDependencyTypes =
          MoreTypes.asTypeElements(types, getComponentDependencies(componentAnnotation));
      for (TypeElement componentDependency : componentDependencyTypes) {
        explicitBindingsBuilder.add(provisionBindingFactory.forComponent(componentDependency));
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement method : dependencyMethods) {
          if (isComponentProvisionMethod(elements, method)) {
            // MembersInjection methods aren't "provided" explicitly, so ignore them.
            explicitBindingsBuilder.add(provisionBindingFactory.forComponentMethod(method));
          }
        }
      }

      // Collect transitive modules provisions.
      ImmutableSet<TypeElement> moduleTypes =
          MoreTypes.asTypeElements(types, getComponentModules(componentAnnotation));

      ImmutableMap<TypeElement, ImmutableSet<TypeElement>> transitiveModules =
          getTransitiveModules(types, moduleTypes);
      for (TypeElement module : transitiveModules.keySet()) {
        // traverse the modules, collect the bindings
        List<ExecutableElement> moduleMethods = methodsIn(elements.getAllMembers(module));
        for (ExecutableElement moduleMethod : moduleMethods) {
          if (isAnnotationPresent(moduleMethod, Provides.class)) {
            try {
              explicitBindingsBuilder.add(provisionBindingFactory.forProvidesMethod(moduleMethod));
            } catch (IllegalArgumentException e) {
              // just ignore it
            }
          }
        }
      }

      RequestResolver requestResolver =
          new RequestResolver(explicitBindingsByKey(explicitBindingsBuilder.build()));
      ImmutableSet<DependencyRequest> componentMethodRequests =
          componentMethodRequests(componentDescriptor.componentDefinitionType());
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

    private ImmutableSetMultimap<Key, ProvisionBinding> explicitBindingsByKey(
        Iterable<ProvisionBinding> bindings) {
      // Multimaps.index() doesn't do ImmutableSetMultimaps.
      ImmutableSetMultimap.Builder<Key, ProvisionBinding> builder = ImmutableSetMultimap.builder();
      for (ProvisionBinding binding : bindings) {
        builder.put(binding.key(), binding);
      }
      return builder.build();
    }

    private ImmutableSet<DependencyRequest> componentMethodRequests(TypeElement componentType) {
      ImmutableSet.Builder<DependencyRequest> interfaceRequestsBuilder = ImmutableSet.builder();
      for (ExecutableElement componentMethod : methodsIn(elements.getAllMembers(componentType))) {
        if (componentMethod.getModifiers().contains(Modifier.ABSTRACT)) { // Elide Object.*;
          if (ComponentDescriptor.isComponentProvisionMethod(elements, componentMethod)) {
            interfaceRequestsBuilder.add(
                dependencyRequestFactory.forComponentProvisionMethod(componentMethod));
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
      final ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings;
      final Map<FrameworkKey, ResolvedBindings> resolvedBindings;
      final Deque<FrameworkKey> cycleStack = Queues.newArrayDeque();

      RequestResolver(ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings) {
        assert explicitBindings != null;
        this.explicitBindings = explicitBindings;
        this.resolvedBindings = Maps.newLinkedHashMap();
      }

      State resolve(DependencyRequest request) {
        Key requestKey = request.key();
        FrameworkKey frameworkKey = FrameworkKey.forDependencyRequest(request);

        ResolvedBindings previouslyResolvedBinding = resolvedBindings.get(frameworkKey);
        if (previouslyResolvedBinding != null) {
          return previouslyResolvedBinding.state();
        }

        if (cycleStack.contains(frameworkKey)) {
          // return malformed, but don't add a resolved binding.
          // the original request will add it with all of the other resolved deps
          return State.CYCLE;
        }

        cycleStack.push(frameworkKey);
        try {
          switch (request.kind()) {
            case INSTANCE:
            case LAZY:
            case PROVIDER:
              // First, check for explicit keys (those from modules and components)
              ImmutableSet<ProvisionBinding> explicitBindingsForKey =
                  explicitBindings.get(requestKey);
              if (explicitBindingsForKey.isEmpty()) {
                // If the key is Map<K, V>, get its implicit binding key which is
                // Map<K, Provider<V>>
                Optional<Key> mapProviderKey = keyFactory.implicitMapProviderKeyFrom(request.key());
                if (mapProviderKey.isPresent()) {
                  DependencyRequest implicitRequest =
                      dependencyRequestFactory.forImplicitMapBinding(request, mapProviderKey.get());
                  ProvisionBinding implicitBinding =
                      provisionBindingFactory.forImplicitMapBinding(request, implicitRequest);
                  State implicitState = resolve(implicitRequest);
                  resolvedBindings.put(frameworkKey,
                      ResolvedBindings.createForProvisionBindings(
                          implicitState.equals(State.COMPLETE) ? State.COMPLETE : State.INCOMPLETE,
                          ImmutableSet.of(implicitBinding)));
                  return State.COMPLETE;
                } else {
                  // no explicit binding, look it up.
                  Optional<ProvisionBinding> provisionBinding =
                      injectBindingRegistry.getOrFindProvisionBinding(requestKey);
                  if (provisionBinding.isPresent()) {
                    // found a binding, resolve its deps and then mark it resolved
                    State bindingState =
                        resolveDependencies(provisionBinding.get().implicitDependencies());
                    resolvedBindings.put(frameworkKey,
                        ResolvedBindings.createForProvisionBindings(
                            bindingState,
                            ImmutableSet.copyOf(provisionBinding.asSet())));
                    return bindingState;
                  } else {
                    // no explicit binding, no inject binding.  it's missing
                    resolvedBindings.put(frameworkKey,
                        ResolvedBindings.createForProvisionBindings(
                            State.MISSING, ImmutableSet.<ProvisionBinding>of()));
                    return State.MISSING;
                  }
                }
              } else {
                // If this is an explicit Map<K, V> request then add in any map binding provision
                // methods which are implied by and must collide with explicit Map<K, V> bindings.
                Optional<Key> underlyingMapKey =
                    keyFactory.implicitMapProviderKeyFrom(request.key());
                if (underlyingMapKey.isPresent()) {
                  explicitBindingsForKey = ImmutableSet.<ProvisionBinding>builder()
                      .addAll(explicitBindingsForKey)
                      .addAll(explicitBindings.get(underlyingMapKey.get()))
                      .build();
                }
                ImmutableSet<DependencyRequest> allDeps =
                    FluentIterable.from(explicitBindingsForKey)
                        .transformAndConcat(
                            new Function<ProvisionBinding, Set<DependencyRequest>>() {
                              @Override
                              public Set<DependencyRequest> apply(ProvisionBinding input) {
                                return input.implicitDependencies();
                              }
                            })
                        .toSet();
                State bindingState = resolveDependencies(allDeps);
                if (explicitBindingsForKey.size() > 1) {
                  // Multiple Explicit bindings. Validate that they are multi-bindings.
                  ImmutableListMultimap<BindingType, ProvisionBinding> bindingsByType =
                      ProvisionBinding.bindingTypesFor(explicitBindingsForKey);
                  if (bindingsByType.keySet().size() > 1) {
                    resolvedBindings.put(frameworkKey,
                        ResolvedBindings.createForProvisionBindings(
                            State.MULTIPLE_BINDING_TYPES,
                            explicitBindingsForKey));
                    return State.MULTIPLE_BINDING_TYPES;
                  } else if (getOnlyElement(bindingsByType.keySet()).equals(BindingType.UNIQUE)) {
                    resolvedBindings.put(frameworkKey,
                        ResolvedBindings.createForProvisionBindings(
                            State.DUPLICATE_BINDINGS,
                            explicitBindingsForKey));
                    return State.DUPLICATE_BINDINGS;
                  }
                }
                resolvedBindings.put(frameworkKey,
                    ResolvedBindings.createForProvisionBindings(
                        bindingState, explicitBindingsForKey));
                return bindingState;
              }
            case MEMBERS_INJECTOR:
              // no explicit deps for members injection, so just look it up
              Optional<MembersInjectionBinding> membersInjectionBinding = Optional.fromNullable(
                  injectBindingRegistry.getOrFindMembersInjectionBinding(requestKey));
              if (membersInjectionBinding.isPresent()) {
                // found a binding, resolve its deps and then mark it resolved
                State bindingState =
                    resolveDependencies(membersInjectionBinding.get().implicitDependencies());
                resolvedBindings.put(frameworkKey,
                    ResolvedBindings.createForMembersInjectionBindings(
                        bindingState,
                        ImmutableSet.copyOf(membersInjectionBinding.asSet())));
                return bindingState;
              } else {
                return State.MISSING;
              }
            default:
              throw new AssertionError();
          }
        } finally {
          cycleStack.pop();
        }
      }

      private State resolveDependencies(Iterable<DependencyRequest> dependencies) {
        State bindingState = State.COMPLETE;
        for (DependencyRequest dependency : dependencies) {
          State dependencyState = resolve(dependency);
          if (dependencyState.equals(State.CYCLE)) {
            bindingState = State.CYCLE;
          } else if (!bindingState.equals(State.CYCLE) && !dependencyState.equals(State.COMPLETE)) {
            bindingState = State.INCOMPLETE;
          }
        }
        return bindingState;
      }
    }
  }
}
