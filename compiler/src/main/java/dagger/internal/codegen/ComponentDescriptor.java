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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
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
import java.util.Queue;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

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
   *  The set of component dependencies listed in {@link Component#dependencies}.
   */
  abstract ImmutableSet<TypeElement> dependencies();

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
  abstract ImmutableList<FrameworkKey> initializationOrdering();

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
      ImmutableSet<TypeElement> componentDependencyTypes = MoreTypes.asTypeElements(types,
          ConfigurationAnnotations.getComponentDependencies(elements, componentMirror));
      ImmutableSet<TypeElement> transitiveModules = getTransitiveModules(moduleTypes);

      ProvisionBinding componentBinding =
          provisionBindingFactory.forComponent(componentDefinitionType);

      ImmutableSetMultimap.Builder<Key, ProvisionBinding> bindingIndexBuilder =
          new ImmutableSetMultimap.Builder<Key, ProvisionBinding>()
              .put(componentBinding.providedKey(), componentBinding);


      for (TypeElement componentDependency : componentDependencyTypes) {
        ProvisionBinding componentDependencyBinding =
            provisionBindingFactory.forComponent(componentDependency);
        bindingIndexBuilder.put(
            componentDependencyBinding.providedKey(), componentDependencyBinding);
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement dependencyMethod : dependencyMethods) {
          if (isComponentProvisionMethod(dependencyMethod)) {
            ProvisionBinding componentMethodBinding =
                provisionBindingFactory.forComponentMethod(dependencyMethod);
            bindingIndexBuilder.put(componentMethodBinding.providedKey(), componentMethodBinding);
          }
        }
      }

      for (TypeElement module : transitiveModules) {
        // traverse the modules, collect the bindings
        List<ExecutableElement> moduleMethods =
            ElementFilter.methodsIn(elements.getAllMembers(module));
        for (ExecutableElement moduleMethod : moduleMethods) {
          if (isAnnotationPresent(moduleMethod, Provides.class)) {
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

      ImmutableSetMultimap.Builder<Key, ProvisionBinding> resolvedProvisionBindings =
          ImmutableSetMultimap.builder();
      ImmutableMap.Builder<Key, MembersInjectionBinding> resolvedMembersInjectionBindings =
          ImmutableMap.builder();
      SetMultimap<FrameworkKey, Binding> resolvedBindings =
          MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();

      ImmutableList<DependencyRequest> interfaceRequests = interfaceRequestsBuilder.build();

      for (DependencyRequest interfaceRequest : interfaceRequests) {
        resolveRequest(interfaceRequest, explicitBindings, resolvedBindings,
            resolvedProvisionBindings, resolvedMembersInjectionBindings);
      }

      return new AutoValue_ComponentDescriptor(
          componentDefinitionType,
          componentDependencyTypes,
          interfaceRequests,
          transitiveModules,
          resolvedProvisionBindings.build(),
          resolvedMembersInjectionBindings.build(),
          ImmutableList.copyOf(resolvedBindings.keySet()));
    }

    private void resolveRequest(DependencyRequest request,
        ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings,
        SetMultimap<FrameworkKey, Binding> resolvedBindings,
        ImmutableSetMultimap.Builder<Key, ProvisionBinding> resolvedProvisionsBindingBuilder,
        ImmutableMap.Builder<Key, MembersInjectionBinding> resolvedMembersIjectionBindingsBuilder) {
      FrameworkKey frameworkKey = FrameworkKey.forDependencyRequest(request);
      Key requestKey = request.key();
      if (resolvedBindings.containsKey(frameworkKey)) {
        return;
      }
      switch (request.kind()) {
        case INSTANCE:
        case LAZY:
        case PROVIDER:
          // First, check for explicit keys (those from modules and components)
          ImmutableSet<ProvisionBinding> explicitBindingsForKey =
              explicitBindings.get(requestKey);
          if (explicitBindingsForKey.isEmpty()) {
            // no explicit binding, look it up
            Optional<ProvisionBinding> provisionBinding =
                injectBindingRegistry.getProvisionBindingForKey(requestKey);
            if (provisionBinding.isPresent()) {
              // found a binding, resolve its deps and then mark it resolved
              for (DependencyRequest dependency : Iterables.concat(
                  provisionBinding.get().dependencies(),
                  provisionBinding.get().memberInjectionRequest().asSet())) {
                resolveRequest(dependency, explicitBindings, resolvedBindings,
                    resolvedProvisionsBindingBuilder, resolvedMembersIjectionBindingsBuilder);
              }
              resolvedBindings.put(frameworkKey, provisionBinding.get());
              resolvedProvisionsBindingBuilder.put(requestKey, provisionBinding.get());
            } else {
              throw new UnsupportedOperationException(
                  "@Injected classes that weren't run with the compoenent processor are "
                      + "(briefly) unsupported: " + requestKey);

            }
          } else {
            // we found explicit bindings. resolve the deps and them mark them resolved
            for (ProvisionBinding explicitBinding : explicitBindingsForKey) {
              for (DependencyRequest dependency : explicitBinding.dependencies()) {
                resolveRequest(dependency, explicitBindings, resolvedBindings,
                    resolvedProvisionsBindingBuilder, resolvedMembersIjectionBindingsBuilder);
              }
            }
            resolvedBindings.putAll(frameworkKey, explicitBindingsForKey);
            resolvedProvisionsBindingBuilder.putAll(requestKey, explicitBindingsForKey);
          }
          break;
        case MEMBERS_INJECTOR:
          // no explicit deps for members injection, so just look it up
          Optional<MembersInjectionBinding> membersInjectionBinding =
              injectBindingRegistry.getMembersInjectionBindingForKey(requestKey);
          if (membersInjectionBinding.isPresent()) {
            // found a binding, resolve its deps and then mark it resolved
            for (DependencyRequest dependency : membersInjectionBinding.get().dependencies()) {
              resolveRequest(dependency, explicitBindings, resolvedBindings,
                  resolvedProvisionsBindingBuilder, resolvedMembersIjectionBindingsBuilder);
            }
            resolvedBindings.put(frameworkKey, membersInjectionBinding.get());
            resolvedMembersIjectionBindingsBuilder.put(requestKey, membersInjectionBinding.get());
          } else {
            // TOOD(gak): make an implicit injector for cases where we need one, but it has no
            // members
          }
          break;
        default:
          throw new AssertionError();
      }
    }

    private static boolean isComponentProvisionMethod(ExecutableElement method) {
      return method.getParameters().isEmpty()
          && !method.getReturnType().getKind().equals(VOID);
    }
  }
}
