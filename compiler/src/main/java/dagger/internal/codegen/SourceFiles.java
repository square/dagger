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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import dagger.internal.DoubleCheckLazy;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;

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

  /**
   * A variant of {@link #indexDependenciesByKey} that maps from unresolved keys
   * to requests.  This is used when generating component's initialize()
   * methods (and in members injectors) in order to instantiate dependent
   * providers.  Consider a generic type of {@code Foo<T>} with a constructor
   * of {@code Foo(T t, T t1, A a, A a1)}.  That will be collapsed to a factory
   * taking a {@code Provider<T> tProvider, Provider<A> aProvider}. However,
   * if it was referenced as {@code Foo<A>}, we need to make sure we still
   * pass two providers.  Naively (if we just referenced by resolved BindingKey),
   * we would have passed a single {@code aProvider}.
   */
  // TODO(user): Refactor these indexing methods so that the binding itself knows what sort of
  // binding keys and framework classes that it needs.
  static ImmutableSetMultimap<BindingKey, DependencyRequest> indexDependenciesByUnresolvedKey(
      Types types, Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<BindingKey, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      BindingKey resolved = dependency.bindingKey();
      // To get the proper unresolved type, we have to extract the proper type from the
      // request type again (because we're looking at the actual element's type).
      TypeMirror unresolvedType =
          DependencyRequest.Factory.extractKindAndType(dependency.requestElement().asType()).type();
      BindingKey unresolved =
          BindingKey.create(resolved.kind(), resolved.key().withType(types, unresolvedType));
      dependenciesByKeyBuilder.put(unresolved, dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  /**
   * Allows dependency requests to be grouped by the key they're requesting.
   * This is used by factory generation in order to minimize the number of parameters
   * required in the case where a given key is requested more than once.  This expects
   * unresolved dependency requests, otherwise we may generate factories based on
   * a particular usage of a class as opposed to the generic types of the class.
   */
  static ImmutableSetMultimap<BindingKey, DependencyRequest> indexDependenciesByKey(
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<BindingKey, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      dependenciesByKeyBuilder.put(dependency.bindingKey(), dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  /**
   * This method generates names and keys for the framework classes necessary for all of the
   * bindings. It is responsible for the following:
   * <ul>
   * <li>Choosing a name that associates the binding with all of the dependency requests for this
   * type.
   * <li>Choosing a name that is <i>probably</i> associated with the type being bound.
   * <li>Ensuring that no two bindings end up with the same name.
   * </ul>
   *
   * @return Returns the mapping from {@link BindingKey} to field, sorted by the name of the field.
   */
  static ImmutableMap<BindingKey, FrameworkField> generateBindingFieldsForDependencies(
      DependencyRequestMapper dependencyRequestMapper,
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap<BindingKey, DependencyRequest> dependenciesByKey =
        indexDependenciesByKey(dependencies);
    Map<BindingKey, Collection<DependencyRequest>> dependenciesByKeyMap =
        dependenciesByKey.asMap();
    ImmutableMap.Builder<BindingKey, FrameworkField> bindingFields = ImmutableMap.builder();
    for (Entry<BindingKey, Collection<DependencyRequest>> entry
        : dependenciesByKeyMap.entrySet()) {
      BindingKey bindingKey = entry.getKey();
      Collection<DependencyRequest> requests = entry.getValue();
      Class<?> frameworkClass =
          dependencyRequestMapper.getFrameworkClass(requests.iterator().next());
      // collect together all of the names that we would want to call the provider
      ImmutableSet<String> dependencyNames =
          FluentIterable.from(requests).transform(new DependencyVariableNamer()).toSet();

      if (dependencyNames.size() == 1) {
        // if there's only one name, great! use it!
        String name = Iterables.getOnlyElement(dependencyNames);
        bindingFields.put(bindingKey,
            FrameworkField.createWithTypeFromKey(frameworkClass, bindingKey, name));
      } else {
        // in the event that a field is being used for a bunch of deps with different names,
        // add all the names together with "And"s in the middle. E.g.: stringAndS
        Iterator<String> namesIterator = dependencyNames.iterator();
        String first = namesIterator.next();
        StringBuilder compositeNameBuilder = new StringBuilder(first);
        while (namesIterator.hasNext()) {
          compositeNameBuilder.append("And").append(
              CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
        }
        bindingFields.put(bindingKey, FrameworkField.createWithTypeFromKey(
            frameworkClass, bindingKey, compositeNameBuilder.toString()));
      }
    }
    return bindingFields.build();
  }

  static Snippet frameworkTypeUsageStatement(Snippet frameworkTypeMemberSelect,
      DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return Snippet.format("%s.create(%s)", ClassName.fromClass(DoubleCheckLazy.class),
            frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return Snippet.format("%s.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
      case MEMBERS_INJECTOR:
        return Snippet.format("%s", frameworkTypeMemberSelect);
      default:
        throw new AssertionError();
    }
  }
  
  /**
   * Returns the generated factory or members injector name for a binding.
   */
  static ClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contribution = (ContributionBinding) binding;
        checkArgument(!contribution.isSyntheticBinding());
        ClassName enclosingClassName = ClassName.fromTypeElement(contribution.bindingTypeElement());
        switch (contribution.bindingKind()) {
          case INJECTION:
          case PROVISION:
          case IMMEDIATE:
          case FUTURE_PRODUCTION:
            return enclosingClassName
                .topLevelClassName()
                .peerNamed(
                    enclosingClassName.classFileName()
                        + "_"
                        + factoryPrefix(contribution)
                        + "Factory");

          default:
            throw new AssertionError();
        }

      case MEMBERS_INJECTION:
        return membersInjectorNameForType(binding.bindingTypeElement());

      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the generated factory or members injector name parameterized with the proper type
   * parameters if necessary.
   */
  static TypeName parameterizedGeneratedTypeNameForBinding(Binding binding) {
    return generatedClassNameForBinding(binding).withTypeParameters(bindingTypeParameters(binding));
  }
  
  private static ImmutableList<TypeName> bindingTypeParameters(Binding binding)
      throws AssertionError {
    TypeMirror bindingType;
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contributionBinding = (ContributionBinding) binding;
        if (contributionBinding.contributionType().isMultibinding()) {
          return ImmutableList.of();
        }
        switch (contributionBinding.bindingKind()) {
          case INJECTION:
            bindingType = contributionBinding.key().type();
            break;
            
          case PROVISION:
            // For provision bindings, we parameterize creation on the types of
            // the module, not the types of the binding.
            // Consider: Module<A, B, C> { @Provides List<B> provideB(B b) { .. }}
            // The binding is just parameterized on <B>, but we need all of <A, B, C>.
            bindingType = contributionBinding.bindingTypeElement().asType();
            break;
            
          case IMMEDIATE:
          case FUTURE_PRODUCTION:
            // TODO(beder): Can these be treated just like PROVISION?
            throw new UnsupportedOperationException();
            
          default:
            return ImmutableList.of();
        }
        break;

      case MEMBERS_INJECTION:
        bindingType = binding.key().type();
        break;

      default:
        throw new AssertionError();
    }
    TypeName bindingTypeName = TypeNames.forTypeMirror(bindingType);
    return bindingTypeName instanceof ParameterizedTypeName
        ? ((ParameterizedTypeName) bindingTypeName).parameters()
        : ImmutableList.<TypeName>of();
  }
  
  static ClassName membersInjectorNameForType(TypeElement typeElement) {
    ClassName injectedClassName = ClassName.fromTypeElement(typeElement);
    return injectedClassName
        .topLevelClassName()
        .peerNamed(injectedClassName.classFileName() + "_MembersInjector");
  }

  static ClassName generatedMonitoringModuleName(TypeElement componentElement) {
    ClassName componentName = ClassName.fromTypeElement(componentElement);
    return componentName
        .topLevelClassName()
        .peerNamed(componentName.classFileName() + "_MonitoringModule");
  }

  private static String factoryPrefix(ContributionBinding binding) {
    switch (binding.bindingKind()) {
      case INJECTION:
        return "";

      case PROVISION:
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return CaseFormat.LOWER_CAMEL.to(
            UPPER_CAMEL, ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());

      default:
        throw new IllegalArgumentException();
    }
  }

  private SourceFiles() {}
}
