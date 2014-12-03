/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package dagger.internal.codegen;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import dagger.internal.DoubleCheckLazy;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.Snippet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;

/**
 * Utilities for generating files.
 *
 * @author Gregory Kick
 * @since 2.0
 */
class SourceFiles {
  /**
   * Sorts {@link DependencyRequest} instances in an order likely to reflect their logical
   * importance.
   */
  static final Ordering<DependencyRequest> DEPENDENCY_ORDERING = new Ordering<DependencyRequest>() {
    @Override
    public int compare(DependencyRequest left, DependencyRequest right) {
      return ComparisonChain.start()
      // put fields before parameters
          .compare(left.requestElement().getKind(), right.requestElement().getKind())
          // order by dependency kind
          .compare(left.kind(), right.kind())
          // then sort by name
          .compare(left.requestElement().getSimpleName().toString(),
              right.requestElement().getSimpleName().toString()).result();
    }
  };

  static ImmutableSetMultimap<FrameworkKey, DependencyRequest> indexDependenciesByKey(
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<FrameworkKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<FrameworkKey, DependencyRequest>().orderValuesBy(
            DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      dependenciesByKeyBuilder.put(FrameworkKey.forDependencyRequest(dependency), dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  /**
   * This method generates names for the {@link Provider} references necessary for all of the
   * bindings. It is responsible for the following:
   * <ul>
   * <li>Choosing a name that associates the provider with all of the dependency requests for this
   * type.
   * <li>Choosing a name that is <i>probably</i> associated with the type being provided.
   * <li>Ensuring that no two providers end up with the same name.
   * </ul>
   *
   * @return Returns the mapping from {@link Key} to provider name sorted by the name of the
   *         provider.
   */
  static ImmutableMap<FrameworkKey, String> generateFrameworkReferenceNamesForDependencies(
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap<FrameworkKey, DependencyRequest> dependenciesByKey =
        indexDependenciesByKey(dependencies);
    Map<FrameworkKey, Collection<DependencyRequest>> dependenciesByKeyMap =
        dependenciesByKey.asMap();
    ImmutableMap.Builder<FrameworkKey, String> providerNames = ImmutableMap.builder();
    for (Entry<FrameworkKey, Collection<DependencyRequest>> entry
        : dependenciesByKeyMap.entrySet()) {
      // collect together all of the names that we would want to call the provider
      ImmutableSet<String> dependencyNames =
          FluentIterable.from(entry.getValue()).transform(new DependencyVariableNamer()).toSet();

      if (dependencyNames.size() == 1) {
        // if there's only one name, great! use it!
        String name = Iterables.getOnlyElement(dependencyNames);
        providerNames.put(entry.getKey(), name.endsWith("Provider") ? name : name + "Provider");
      } else {
        // in the event that a provider is being used for a bunch of deps with different names,
        // add all the names together with "And"s in the middle. E.g.: stringAndS
        Iterator<String> namesIterator = dependencyNames.iterator();
        String first = namesIterator.next();
        StringBuilder compositeNameBuilder = new StringBuilder(first);
        while (namesIterator.hasNext()) {
          compositeNameBuilder.append("And").append(
              CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
        }
        providerNames.put(entry.getKey(), compositeNameBuilder.append("Provider").toString());
      }
    }
    return providerNames.build();
  }

  static Snippet frameworkTypeUsageStatement(Snippet frameworkTypeMemberSelect,
      DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return Snippet.format("%s.create(%s)", ClassName.fromClass(DoubleCheckLazy.class),
            frameworkTypeMemberSelect);
      case INSTANCE:
        return Snippet.format("%s.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case MEMBERS_INJECTOR:
        return Snippet.format("%s", frameworkTypeMemberSelect);
      default:
        throw new AssertionError();
    }
  }

  static ClassName factoryNameForProvisionBinding(ProvisionBinding binding) {
    TypeElement enclosingTypeElement = binding.bindingTypeElement();
    ClassName enclosingClassName = ClassName.fromTypeElement(enclosingTypeElement);
    switch (binding.bindingKind()) {
      case INJECTION:
      case PROVISION:
        return enclosingClassName.topLevelClassName().peerNamed(
            enclosingClassName.classFileName() + "$$" + factoryPrefix(binding) + "Factory");
      default:
        throw new AssertionError();
    }
  }

  static ClassName membersInjectorNameForMembersInjectionBinding(MembersInjectionBinding binding) {
    ClassName injectedClassName = ClassName.fromTypeElement(binding.bindingElement());
    return injectedClassName.topLevelClassName().peerNamed(
        injectedClassName.classFileName() + "$$MembersInjector");
  }

  private static String factoryPrefix(ProvisionBinding binding) {
    switch (binding.bindingKind()) {
      case INJECTION:
        return "";
      case PROVISION:
        return CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL,
            ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());
      default:
        throw new IllegalArgumentException();
    }
  }

  private SourceFiles() {}
}
