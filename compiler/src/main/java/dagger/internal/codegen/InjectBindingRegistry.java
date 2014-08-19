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
import com.google.common.collect.Sets;
import dagger.internal.codegen.writer.ClassName;
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

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor.
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
  private final Key.Factory keyFactory;
  private final Map<Key, ProvisionBinding> provisionBindingsByKey;
  private final Map<Key, MembersInjectionBinding> membersInjectionBindingsByKey;
  private final Set<ClassName> generatedTypeNames;

  InjectBindingRegistry(Elements elements,
      Types types,
      Messager messager,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      MembersInjectionBinding.Factory membersInjectionBindingFactory,
      MembersInjectorGenerator membersInjectorGenerator,
      Key.Factory keyFactory) {
    this.elements = elements;
    this.types = types;
    this.messager = messager;
    this.provisionBindingFactory = provisionBindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.membersInjectionBindingFactory = membersInjectionBindingFactory;
    this.membersInjectorGenerator = membersInjectorGenerator;
    this.provisionBindingsByKey = Maps.newLinkedHashMap();
    this.membersInjectionBindingsByKey = Maps.newLinkedHashMap();
    this.generatedTypeNames = Sets.newLinkedHashSet();
    this.keyFactory = keyFactory;
  }

  // TODO(gak): rework how we handle knowing what we've generated and not.
  void registerGeneratedFile(ClassName generatedTypeName) {
    checkState(generatedTypeNames.add(generatedTypeName),
        "couldn't register %s as it was already registered.", generatedTypeName);
  }

  void registerProvisionBinding(ProvisionBinding binding) {
    ProvisionBinding previousValue = provisionBindingsByKey.put(binding.providedKey(), binding);
    checkState(previousValue == null, "couldn't register %s. %s was already registered", binding,
        previousValue);
  }

  void registerMembersInjectionBinding(MembersInjectionBinding binding) {
    MembersInjectionBinding previousValue = membersInjectionBindingsByKey.put(
        keyFactory.forType(binding.bindingElement().asType()), binding);
    checkState(previousValue == null, "couldn't register %s. %s was already registered", binding,
        previousValue);
  }

  Optional<ProvisionBinding> getOrFindOrCreateProvisionBinding(Key key)
      throws SourceFileGenerationException {
    Optional<ProvisionBinding> binding = getOrFindProvisionBinding(key);
    if (binding.isPresent()) {
      ClassName factoryName = SourceFiles.factoryNameForProvisionBinding(binding.get());
      if (!generatedTypeNames.contains(factoryName)
          && elements.getTypeElement(factoryName.canonicalName()) == null) {
        // does not exist.  generate
        factoryGenerator.generate(binding.get());
        generatedTypeNames.add(factoryName);
        messager.printMessage(Kind.NOTE, String.format("Generating a Factory for %s. "
            + "Prefer to run the dagger processor over that class instead.", key.type()));
      }
    }
    return binding;
  }

  Optional<ProvisionBinding> getOrFindProvisionBinding(Key key) {
    checkNotNull(key);
    if (key.qualifier().isPresent()) {
      return Optional.absent();
    }
    Optional<ProvisionBinding> binding = Optional.fromNullable(provisionBindingsByKey.get(key));
    if (binding.isPresent()) {
      return binding;
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
        registerProvisionBinding(constructorBinding);
        return Optional.of(constructorBinding);
      default:
        throw new IllegalStateException("Found multiple @Inject constructors: "
            + injectConstructors);
    }
  }

  MembersInjectionBinding getOrFindOrCreateMembersInjectionBinding(Key key)
      throws SourceFileGenerationException {
    MembersInjectionBinding binding = getOrFindMembersInjectionBinding(key);
    if (!binding.injectionSites().isEmpty()) {
      ClassName membersInjectorName =
          SourceFiles.membersInjectorNameForMembersInjectionBinding(binding);
      if (!generatedTypeNames.contains(membersInjectorName)
          && elements.getTypeElement(membersInjectorName.canonicalName()) == null) {
        // does not exist.  generate
        membersInjectorGenerator.generate(binding);
        messager.printMessage(Kind.NOTE, String.format("Generating a MembersInjector for %s. "
            + "Prefer to run the dagger processor over that class instead.", key.type()));
        registerMembersInjectionBinding(binding);
        generatedTypeNames.add(membersInjectorName);
      }
    }
    return binding;
  }

  MembersInjectionBinding getOrFindMembersInjectionBinding(Key key) {
    checkNotNull(key);
    checkArgument(key.isValidMembersInjectionKey());
    MembersInjectionBinding binding = membersInjectionBindingsByKey.get(key);
    if (binding == null) {
      TypeElement element = MoreElements.asType(types.asElement(key.type()));
      binding = membersInjectionBindingFactory.forInjectedType(element);
    }
    return binding;
  }
}
