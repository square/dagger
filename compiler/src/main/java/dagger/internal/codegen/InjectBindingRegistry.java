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
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Provides;
import dagger.internal.codegen.writer.ClassName;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.Key.Kind.PROVIDER;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor.
 * Note that this registry <b>does not</b> handle any explicit bindings (those from {@link Provides}
 * methods, {@link Component} dependencies, etc.).
 *
 * @author Gregory Kick
 */
final class InjectBindingRegistry {
  private final Elements elements;
  private final Types types;
  private final Messager messager;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final FactoryGenerator factoryGenerator;
  private final MembersInjectionBinding.Factory membersInjectionBindingFactory;
  private final MembersInjectorGenerator membersInjectorGenerator;

  private final Map<Key, Binding> bindingsByKey = Maps.newLinkedHashMap();
  private final Deque<Binding> bindingsRequiringGeneration = Queues.newArrayDeque();
  private final Set<Binding> materializedBindings = Sets.newLinkedHashSet();

  InjectBindingRegistry(Elements elements,
      Types types,
      Messager messager,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      MembersInjectionBinding.Factory membersInjectionBindingFactory,
      MembersInjectorGenerator membersInjectorGenerator) {
    this.elements = elements;
    this.types = types;
    this.messager = messager;
    this.provisionBindingFactory = provisionBindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.membersInjectionBindingFactory = membersInjectionBindingFactory;
    this.membersInjectorGenerator = membersInjectorGenerator;
  }

  /**
   * This method ensures that sources for all registered {@link Binding bindings} (either
   * {@linkplain #registerBinding explicitly} or implicitly via
   * {@link #getOrFindMembersInjectionBinding} or {@link #getOrFindProvisionBinding}) are generated.
   */
  void generateSourcesForRequiredBindings() throws SourceFileGenerationException {
    for (Binding binding = bindingsRequiringGeneration.poll();
        binding != null;
        binding = bindingsRequiringGeneration.poll()) {
      switch (binding.key().kind()) {
        case PROVIDER:
          factoryGenerator.generate((ProvisionBinding) binding);
          break;
        case MEMBERS_INJECTOR:
          membersInjectorGenerator.generate((MembersInjectionBinding) binding);
          break;
        default:
          throw new AssertionError();
      }
      materializedBindings.add(binding);
    }
  }

  <B extends Binding> B registerBinding(B binding) {
    return registerBinding(binding, true);
  }

  private <B extends Binding> B registerBinding(B binding, boolean explicit) {
    Key key = binding.key();
    Binding previousValue = bindingsByKey.put(key, binding);
    checkState(previousValue == null || binding.equals(previousValue),
        "couldn't register %s. %s was already registered for %s",
        binding, previousValue, key);
    if (!materializedBindings.contains(binding) && !bindingsRequiringGeneration.contains(binding)) {
      switch (key.kind()) {
        case PROVIDER:
          ProvisionBinding provisionBinding =  (ProvisionBinding) binding;
          ClassName factoryName = SourceFiles.factoryNameForProvisionBinding(provisionBinding);
          if (elements.getTypeElement(factoryName.canonicalName()) == null) {
            bindingsRequiringGeneration.offer(provisionBinding);
            if (!explicit) {
              messager.printMessage(Kind.NOTE, String.format("Generating a Factory for %s. "
                  + "Prefer to run the dagger processor over that class instead.", key.type()));
            }
          }
          break;
        case MEMBERS_INJECTOR:
          MembersInjectionBinding membersInjectionBinding = (MembersInjectionBinding) binding;
          if (membersInjectionBinding.injectionSites().isEmpty()) {
            // empty members injection bindings are special and don't need source files.
            // so, we just pretend
            materializedBindings.add(binding);
          } else  {
            ClassName membersInjectorName =
                SourceFiles.membersInjectorNameForMembersInjectionBinding(membersInjectionBinding);
            if (elements.getTypeElement(membersInjectorName.canonicalName()) == null) {
              bindingsRequiringGeneration.offer(membersInjectionBinding);
              if (!explicit) {
                messager.printMessage(Kind.NOTE, String.format(
                    "Generating a MembersInjector for %s. "
                          + "Prefer to run the dagger processor over that class instead.",
                    key.type()));
              }
            }
          }
          break;
        default:
          throw new AssertionError();
      }
    }
    return binding;
  }

  Optional<ProvisionBinding> getOrFindProvisionBinding(Key key) {
    checkNotNull(key);
    checkArgument(key.kind().equals(PROVIDER));
    if (key.qualifier().isPresent()) {
      return Optional.absent();
    }
    Binding binding = bindingsByKey.get(key);
    if (binding != null) {
      verify(binding instanceof ProvisionBinding);
      return Optional.of((ProvisionBinding) binding);
    }
    // ok, let's see if we can find an @Inject constructor
    TypeElement element = MoreElements.asType(types.asElement(key.type()));
    List<ExecutableElement> constructors =
        ElementFilter.constructorsIn(element.getEnclosedElements());
    ImmutableSet<ExecutableElement> injectConstructors = FluentIterable.from(constructors)
        .filter(new Predicate<ExecutableElement>() {
          @Override public boolean apply(ExecutableElement input) {
            return isAnnotationPresent(input, Inject.class);
          }
        }).toSet();
    switch (injectConstructors.size()) {
      case 0:
        // No constructor found.
        return Optional.absent();
      case 1:
        ProvisionBinding constructorBinding = provisionBindingFactory.forInjectConstructor(
            Iterables.getOnlyElement(injectConstructors));
        return Optional.of(registerBinding(constructorBinding, false));
      default:
        throw new IllegalStateException("Found multiple @Inject constructors: "
            + injectConstructors);
    }
  }

  MembersInjectionBinding getOrFindMembersInjectionBinding(Key key) {
    checkNotNull(key);
    // TODO(gak): is checking the kind enough?
    checkArgument(key.isValidMembersInjectionKey());
    Binding binding = bindingsByKey.get(key);
    if (binding == null) {
      TypeElement element = MoreElements.asType(types.asElement(key.type()));
      binding = registerBinding(membersInjectionBindingFactory.forInjectedType(element), false);
    }
    verify(binding instanceof MembersInjectionBinding);
    return (MembersInjectionBinding) binding;
  }
}
