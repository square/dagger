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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Queues;
import dagger.Component;
import dagger.Provides;
import dagger.internal.codegen.ProvisionBinding.BindingType;
import dagger.internal.codegen.ValidationReport.Builder;
import java.util.Deque;
import java.util.Formatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.getTransitiveModules;
import static dagger.internal.codegen.ErrorMessages.INDENT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_FORMAT;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

/**
 * Performs validation of object graphs rooted in the provision and injection methods of
 * a {link @Component} interface.
 *
 * @author Christian Gruber
 */
public class GraphValidator implements Validator<TypeElement> {
  private final Elements elements;
  private final Types types;
  private final InjectBindingRegistry bindingRegistry;
  private final DependencyRequest.Factory dependencyRequestFactory;
  private final Key.Factory keyFactory;
  private final ProvisionBinding.Factory provisionBindingFactory;

  GraphValidator(
      Elements elements,
      Types types,
      InjectBindingRegistry bindingRegistry,
      DependencyRequest.Factory dependencyRequestFactory,
      Key.Factory keyFactory,
      ProvisionBinding.Factory provisionBindingFactory) {
    this.elements = elements;
    this.types = types;
    this.bindingRegistry = bindingRegistry;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.keyFactory = keyFactory;
    this.provisionBindingFactory = provisionBindingFactory;
  }

  @Override
  public ValidationReport<TypeElement> validate(TypeElement subject) {
    ValidationReport.Builder<TypeElement> reportBuilder = ValidationReport.Builder.about(subject);
    validateGraph(subject, reportBuilder);
    return reportBuilder.build();
  }

  void validateGraph(TypeElement component,
      ValidationReport.Builder<TypeElement> reportBuilder) {
    AnnotationMirror componentMirror =
        getAnnotationMirror(component, Component.class).get();
    ImmutableSet.Builder<ProvisionBinding> explicitBindingsBuilder = ImmutableSet.builder();
    ProvisionBinding componentBinding = provisionBindingFactory.forComponent(component);
    explicitBindingsBuilder.add(componentBinding);

    // Collect Component dependencies.
    ImmutableSet<TypeElement> componentDependencyTypes = MoreTypes.asTypeElements(types,
        ConfigurationAnnotations.getComponentDependencies(elements, componentMirror));
    for (TypeElement componentDependency : componentDependencyTypes) {
      explicitBindingsBuilder.add(provisionBindingFactory.forComponent(componentDependency));
      List<ExecutableElement> dependencyMethods =
          ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
      for (ExecutableElement method : dependencyMethods) {
        if (isComponentProvisionMethod(method)) {
          // MembersInjection methods aren't "provided" explicitly, so ignore them.
          try {
            explicitBindingsBuilder.add(provisionBindingFactory.forComponentMethod(method));
          } catch (IllegalArgumentException e) {
            // Should not ever get here due to previous component validation.
            reportBuilder.addItem("Component provision methods cannot have parameters.", method);
          }
        }
      }
    }

    // Collect transitive modules provisions.
    ImmutableSet<TypeElement> moduleTypes =
        MoreTypes.asTypeElements(types, getComponentModules(elements, componentMirror));

    for (TypeElement module : getTransitiveModules(elements, types, moduleTypes)) {
      // traverse the modules, collect the bindings
      List<ExecutableElement> moduleMethods = methodsIn(elements.getAllMembers(module));
      for (ExecutableElement moduleMethod : moduleMethods) {
        if (isAnnotationPresent(moduleMethod, Provides.class)) {
          try {
            explicitBindingsBuilder.add(provisionBindingFactory.forProvidesMethod(moduleMethod));
          } catch (IllegalArgumentException e) {
            // Should not ever get here due to previous module validation.
            reportBuilder.addItem(
                String.format(ErrorMessages.MALFORMED_MODULE_METHOD_FORMAT,
                    moduleMethod.getSimpleName(),
                    MoreElements.asType(moduleMethod.getEnclosingElement()).getQualifiedName()),
                component);
          }
        }
      }
    }

    for (DependencyRequest componentMethodRequest : componentMethodRequests(component)) {
      Deque<Key> cycleStack = Queues.newArrayDeque();
      Deque<DependencyRequest> dependencyPath = Queues.newArrayDeque();
      resolveRequest(
          componentMethodRequest,
          componentMethodRequest,
          reportBuilder,
          explicitBindingsByKey(explicitBindingsBuilder.build()),
          new LinkedHashSet<Key>(),
          cycleStack,
          dependencyPath);
    }
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

  private ImmutableList<DependencyRequest> componentMethodRequests(TypeElement componentType) {
    ImmutableList.Builder<DependencyRequest> interfaceRequestsBuilder = ImmutableList.builder();
    for (ExecutableElement componentMethod : methodsIn(elements.getAllMembers(componentType))) {
      if (componentMethod.getModifiers().contains(Modifier.ABSTRACT)) { // Elide Object.*;
        if (isComponentProvisionMethod(componentMethod)) {
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

  private void resolveRequest(DependencyRequest request,
      DependencyRequest rootRequest,
      ValidationReport.Builder<TypeElement> reportBuilder,
      ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings,
      Set<Key> resolvedBindings,
      Deque<Key> cycleStack,
      Deque<DependencyRequest> dependencyPath) {

    Key requestKey = request.key();
    if (cycleStack.contains(requestKey) && !isComponent(requestKey.type())) {
      resolvedBindings.add(requestKey); // it's present, but bad, and we report that.
      dependencyPath = Queues.newArrayDeque(dependencyPath); // copy
      dependencyPath.push(request); // add current request.
      dependencyPath.pollLast(); // strip off original request from the component method.
      ImmutableList<String> printableDependencyPath = FluentIterable.from(dependencyPath)
          .transform(DependencyRequestFormatter.instance()).toList().reverse();
      TypeElement componentType =
          MoreElements.asType(rootRequest.requestElement().getEnclosingElement());
      // TODO(user): Restructure to provide a hint for the start and end of the cycle.
      reportBuilder.addItem(
          String.format(ErrorMessages.CONTAINS_DEPENDENCY_CYCLE_FORMAT,
              componentType.getQualifiedName(),
              rootRequest.requestElement().getSimpleName(),
              Joiner.on("\n").join(printableDependencyPath)),
          rootRequest.requestElement());

      return;
    }
    if (resolvedBindings.contains(requestKey)) {
      return;
    }

    dependencyPath.push(request);
    cycleStack.push(requestKey);
    try {
      switch (request.kind()) {
        case INSTANCE:
        case LAZY:
        case PROVIDER:
          // First, check for explicit keys (those from modules and components)
          ImmutableSet<ProvisionBinding> explicitBindingsForKey = explicitBindings.get(requestKey);
          if (explicitBindingsForKey.isEmpty()) {
            // If the key is Map<K, V>, get its implicit binding key which is Map<K, Provider<V>>
            Optional<Key> key = keyFactory.implicitMapProviderKeyFrom(request.key());
            if (key.isPresent()) {
              DependencyRequest implicitRequest =
                  dependencyRequestFactory.forImplicitMapBinding(request, key.get());
              Binding implicitBinding =
                  provisionBindingFactory.forImplicitMapBinding(request, implicitRequest);
              resolveRequest(Iterables.getOnlyElement(implicitBinding.dependencies()),
                  rootRequest, reportBuilder, explicitBindings, resolvedBindings, cycleStack,
                  dependencyPath);
              resolvedBindings.add(requestKey);
            } else {
              // no explicit binding, look it up or fail.
              Optional<ProvisionBinding> provisionBinding =
                  findProvidableType(requestKey, reportBuilder, rootRequest, dependencyPath);
              if (provisionBinding.isPresent()) {
                // found a binding, resolve its deps and then mark it resolved
                for (DependencyRequest dependency : provisionBinding.get().implicitDependencies()) {
                  resolveRequest(dependency, rootRequest, reportBuilder, explicitBindings,
                      resolvedBindings, cycleStack, dependencyPath);
                }
                resolvedBindings.add(requestKey);
              }
            }
          } else {
            // If this is an explicit Map<K, V> request then add in any map binding provision
            // methods which are implied by and must collide with explicit Map<K, V> bindings.
            Optional<Key> underlyingMapKey = keyFactory.implicitMapProviderKeyFrom(request.key());
            if (underlyingMapKey.isPresent()) {
              explicitBindingsForKey = ImmutableSet.<ProvisionBinding>builder()
                  .addAll(explicitBindingsForKey)
                  .addAll(explicitBindings.get(underlyingMapKey.get()))
                  .build();
            }
            if (explicitBindingsForKey.size() > 1) {
              // Multiple Explicit bindings. Validate that they are multi-bindings.
              ImmutableListMultimap<BindingType, ProvisionBinding> bindingsByType =
                  ProvisionBinding.bindingTypesFor(explicitBindingsForKey);
              if (bindingsByType.keySet().size() > 1) {
                reportMultipleBindingTypes(rootRequest, requestKey, bindingsByType, reportBuilder);
              } else if (getOnlyElement(bindingsByType.keySet()).equals(BindingType.UNIQUE)) {
                reportDuplicateBindings(rootRequest, requestKey, bindingsByType, reportBuilder);
              }
            }
            for (Binding explicitBinding : explicitBindingsForKey) {
              for (DependencyRequest dependency : explicitBinding.dependencies()) {
                resolveRequest(dependency, rootRequest, reportBuilder, explicitBindings,
                    resolvedBindings, cycleStack, dependencyPath);
              }
            }
            resolvedBindings.add(requestKey);
          }
          break;
        case MEMBERS_INJECTOR:
          // no explicit deps for members injection, so just look it up
          Optional<MembersInjectionBinding> membersInjectionBinding =
              Optional.fromNullable(bindingRegistry.getOrFindMembersInjectionBinding(requestKey));
          if (membersInjectionBinding.isPresent()) {
            // found a binding, resolve its deps and then mark it resolved
            for (DependencyRequest dependency : membersInjectionBinding.get().dependencies()) {
              resolveRequest(dependency, rootRequest, reportBuilder, explicitBindings,
                  resolvedBindings, cycleStack, dependencyPath);
            }
            resolvedBindings.add(requestKey);
          }
          break;
        default:
          throw new AssertionError();
      }
    } finally {
      dependencyPath.pop();
      cycleStack.pop();
    }
  }

  @SuppressWarnings("resource") // Appendable is a StringBuilder.
  private void reportDuplicateBindings(DependencyRequest rootRequest, Key requestKey,
      ListMultimap<BindingType, ProvisionBinding> bindingsByType,
      ValidationReport.Builder<TypeElement> reportBuilder) {
    StringBuilder builder = new StringBuilder();
    new Formatter(builder).format(ErrorMessages.DUPLICATE_BINDINGS_FOR_KEY_FORMAT,
        KeyFormatter.instance().format(requestKey));
    for (ProvisionBinding binding : bindingsByType.values()) {
      builder.append('\n').append(INDENT);
      builder.append(ProvisionBindingFormatter.instance().format(binding));
    }
    reportBuilder.addItem(builder.toString(), rootRequest.requestElement());
  }

  @SuppressWarnings("resource") // Appendable is a StringBuilder.
  private void reportMultipleBindingTypes(DependencyRequest rootRequest, Key requestKey,
      ListMultimap<BindingType, ProvisionBinding> bindingsByType,
      ValidationReport.Builder<TypeElement> reportBuilder) {
    StringBuilder builder = new StringBuilder();
    new Formatter(builder).format(ErrorMessages.MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT,
        KeyFormatter.instance().format(requestKey));
    for (BindingType type :
        Ordering.natural().immutableSortedCopy(bindingsByType.keySet())) {
      builder.append(INDENT);
      builder.append(formatBindingType(type));
      builder.append(" bindings:\n");
      for (ProvisionBinding binding : bindingsByType.get(type)) {
        builder.append(INDENT).append(INDENT);
        builder.append(ProvisionBindingFormatter.instance().format(binding));
        builder.append('\n');
      }
    }
    reportBuilder.addItem(builder.toString(), rootRequest.requestElement());
  }

  private String formatBindingType(BindingType type) {
    switch(type) {
      case MAP:
        return "Map";
      case SET:
        return "Set";
      case UNIQUE:
        return "Unique";
      default:
        throw new IllegalStateException("Unknown binding type: " + type);
    }
  }

  // TODO(user) determine what bits of InjectBindingRegistry's findOrCreate logic to factor out.
  private Optional<ProvisionBinding> findProvidableType(Key key, Builder<TypeElement> reportBuilder,
      DependencyRequest rootRequest, Deque<DependencyRequest> dependencyPath) {
    Optional<ProvisionBinding> binding = bindingRegistry.getOrFindProvisionBinding(key);
    if (!binding.isPresent()) {
      TypeMirror type = key.type();
      Name typeName = MoreElements.asType(types.asElement(type)).getQualifiedName();
      boolean requiresProvidesMethod = type.accept(new SimpleTypeVisitor6<Boolean, Void>() {
        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          return true;
        }

        @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
          // Note - this logic is also in InjectConstructorValidator but is woven into errors.
          TypeElement typeElement = MoreElements.asType(type.asElement());
          if (typeElement.getTypeParameters().isEmpty()
              && typeElement.getKind().equals(ElementKind.CLASS)
              && !typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
          }
          return true;
        }
      }, null);
      StringBuilder errorMessage = new StringBuilder();
      if(requiresProvidesMethod) {
        errorMessage.append(String.format(REQUIRES_PROVIDER_FORMAT, typeName));
      } else {
        errorMessage.append(
            String.format(REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT, typeName));
      }
      if (key.isValidMembersInjectionKey()
          && !bindingRegistry.getOrFindMembersInjectionBinding(key).injectionSites().isEmpty()) {
        errorMessage.append(" ").append(ErrorMessages.MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION);
      }
      dependencyPath = Queues.newArrayDeque(dependencyPath); // copy
      dependencyPath.pollLast();
      ImmutableList<String> printableDependencyPath = FluentIterable.from(dependencyPath)
          .transform(DependencyRequestFormatter.instance()).toList().reverse();
      for(String dependency : printableDependencyPath) {
        errorMessage.append("\n").append(dependency);
      }
      reportBuilder.addItem(errorMessage.toString(), rootRequest.requestElement());
    }
    return binding;
  }

  private boolean isComponent(TypeMirror type) {
    // No need to fully validate. Components themselves will be validated by the ComponentValidator
    return MoreElements.isAnnotationPresent(types.asElement(type), Component.class);
  }

  private boolean isComponentProvisionMethod(ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement());
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
}
