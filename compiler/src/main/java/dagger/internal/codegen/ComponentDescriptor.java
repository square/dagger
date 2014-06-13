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

import static dagger.internal.codegen.AnnotationMirrors.getAnnotationMirror;
import static dagger.internal.codegen.DependencyRequest.Kind.MEMBERS_INJECTOR;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import dagger.Component;
import dagger.MembersInjector;
import dagger.Module;
import dagger.Provides;

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The logical representation of a {@link Component} definition.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ComponentDescriptor {
  ComponentDescriptor() {}

  /**
   * The type (interface or abstract class) that defines the component. This is the element to which
   * the {@link Component} annotation was applied.
   */
  abstract TypeElement componentDefinitionType();

  /**
   * The list of {@link DependencyRequest} instances whose sources are methods on the component
   * definition type.  These are the user-requested dependencies.
   */
  abstract ImmutableList<DependencyRequest> interfaceRequests();

  /**
   * The total set of modules (those declared in {@link Component#modules} and their transitive
   * dependencies) required to construct the object graph declared by the component.
   */
  abstract ImmutableSet<TypeElement> moduleDependencies();

  /**
   * Returns the mapping from {@link Key} to {@link ProvisionBinding} that
   * (with {@link #resolvedMembersInjectionBindings}) represents the full adjacency matrix for the
   * object graph.
   */
  abstract ImmutableSetMultimap<Key, ProvisionBinding> resolvedProvisionBindings();

  /**
   * Returns the mapping from {@link Key} to {@link MembersInjectionBinding} that
   * (with {@link #resolvedProvisionBindings}) represents the full adjacency matrix for the object
   * graph.
   */
  abstract ImmutableMap<Key, MembersInjectionBinding> resolvedMembersInjectionBindings();

  /**
   * The ordering of {@link Key keys} that will allow all of the {@link Factory} and
   * {@link MembersInjector} implementations to initialize properly.
   */
  abstract ImmutableList<Key> initializationOrdering();

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final InjectBindingRegistry injectBindingRegistry;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, InjectBindingRegistry injectBindingRegistry,
        ProvisionBinding.Factory provisionBindingFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.injectBindingRegistry = injectBindingRegistry;
      this.provisionBindingFactory = provisionBindingFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    private ImmutableSet<TypeElement> getTransitiveModules(ImmutableSet<TypeElement> seedModules) {
      Queue<TypeElement> moduleQueue = Queues.newArrayDeque(seedModules);
      LinkedHashSet<TypeElement> moduleElements = Sets.newLinkedHashSet();
      for (TypeElement moduleElement = moduleQueue.poll();
          moduleElement != null;
          moduleElement = moduleQueue.poll()) {
        moduleElements.add(moduleElement);
        AnnotationMirror moduleMirror =
            getAnnotationMirror(moduleElement, Module.class).get();
        ImmutableSet<TypeElement> moduleDependencies = MoreTypes.asTypeElements(types,
            ConfigurationAnnotations.getModuleIncludes(elements, moduleMirror));
        for (TypeElement dependencyType : moduleDependencies) {
          if (!moduleElements.contains(dependencyType)) {
            moduleQueue.add(dependencyType);
          }
        }
      }
      return ImmutableSet.copyOf(moduleElements);
    }

    ComponentDescriptor create(TypeElement componentDefinitionType) {
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class).get();
      ImmutableSet<TypeElement> moduleTypes = MoreTypes.asTypeElements(types,
          ConfigurationAnnotations.getComponentModules(elements, componentMirror));
      ImmutableSet<TypeElement> transitiveModules = getTransitiveModules(moduleTypes);

      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDefinitionType);

      ImmutableSetMultimap.Builder<Key, ProvisionBinding> bindingIndexBuilder =
          new ImmutableSetMultimap.Builder<Key, ProvisionBinding>()
              .put(componentBinding.providedKey(), componentBinding);


      for (TypeElement module : transitiveModules) {
        // traverse the modules, collect the bindings
        List<ExecutableElement> moduleMethods =
            ElementFilter.methodsIn(elements.getAllMembers(module));
        for (ExecutableElement moduleMethod : moduleMethods) {
          if (moduleMethod.getAnnotation(Provides.class) != null) {
            ProvisionBinding providesMethodBinding =
                provisionBindingFactory.forProvidesMethod(moduleMethod);
            bindingIndexBuilder.put(providesMethodBinding.providedKey(), providesMethodBinding);
          }
        }
      }

      ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings = bindingIndexBuilder.build();

      ImmutableList.Builder<DependencyRequest> interfaceRequestsBuilder = ImmutableList.builder();

      Deque<DependencyRequest> requestsToResolve = Queues.newArrayDeque();

      for (ExecutableElement componentMethod
          : ElementFilter.methodsIn(elements.getAllMembers(componentDefinitionType))) {
        if (componentMethod.getModifiers().contains(ABSTRACT)) {
          List<? extends VariableElement> parameters = componentMethod.getParameters();
          switch (parameters.size()) {
            case 0:
              // provision method
              DependencyRequest provisionRequest =
                  dependencyRequestFactory.forComponentProvisionMethod(componentMethod);
              interfaceRequestsBuilder.add(provisionRequest);
              requestsToResolve.addLast(provisionRequest);
              break;
            case 1:
              // members injection method
              DependencyRequest membersInjectionRequest =
                  dependencyRequestFactory.forComponentMembersInjectionMethod(componentMethod);
              interfaceRequestsBuilder.add(membersInjectionRequest);
              requestsToResolve.addLast(membersInjectionRequest);
              break;
            default:
              throw new IllegalStateException();
          }
        }
      }

      SetMultimap<Key, ProvisionBinding> resolvedProvisionBindings = LinkedHashMultimap.create();
      Map<Key, MembersInjectionBinding> resolvedMembersInjectionBindings = Maps.newLinkedHashMap();
      // TODO(gak): we're really going to need to test this ordering
      ImmutableSet.Builder<Key> resolutionOrder = ImmutableSet.builder();

      for (DependencyRequest requestToResolve = requestsToResolve.pollLast();
          requestToResolve != null;
          requestToResolve = requestsToResolve.pollLast()) {
        Key key = requestToResolve.key();
        if (requestToResolve.kind().equals(MEMBERS_INJECTOR)) {
          if (!resolvedMembersInjectionBindings.containsKey(key)) {
            Optional<MembersInjectionBinding> binding =
                injectBindingRegistry.getMembersInjectionBindingForKey(key);
            if (binding.isPresent()) {
              requestsToResolve.addAll(binding.get().dependencySet());
              resolvedMembersInjectionBindings.put(key, binding.get());
            } else {
              // check and generate.
            }
          }
        } else { // all other requests are provision requests
          if (!resolvedProvisionBindings.containsKey(key)) {
            ImmutableSet<ProvisionBinding> explicitBindingsForKey = explicitBindings.get(key);
            if (explicitBindingsForKey.isEmpty()) {
              Optional<ProvisionBinding> injectBinding =
                  injectBindingRegistry.getProvisionBindingForKey(key);
              if (injectBinding.isPresent()) {
                requestsToResolve.addAll(injectBinding.get().dependencies());
                resolvedProvisionBindings.put(key, injectBinding.get());
                if (injectBinding.get().requiresMemberInjection()) {
                  DependencyRequest forMembersInjectedType =
                      dependencyRequestFactory.forMembersInjectedType(
                          injectBinding.get().providedKey().type());
                  requestsToResolve.add(forMembersInjectedType);
                }
              } else {
                // TODO(gak): support this
                throw new UnsupportedOperationException(
                    "@Injected classes that weren't run with the compoenent processor are "
                        + "(briefly) unsupported: " + key);
              }
            } else {
              resolvedProvisionBindings.putAll(key, explicitBindingsForKey);
            }
            for (ProvisionBinding binding : explicitBindingsForKey) {
              requestsToResolve.addAll(binding.dependencies());
            }
          }
        }
        resolutionOrder.add(key);
      }

      return new AutoValue_ComponentDescriptor(
          componentDefinitionType,
          interfaceRequestsBuilder.build(),
          moduleTypes,
          ImmutableSetMultimap.copyOf(resolvedProvisionBindings),
          ImmutableMap.copyOf(resolvedMembersInjectionBindings),
          resolutionOrder.build().asList().reverse());
    }
  }
}
