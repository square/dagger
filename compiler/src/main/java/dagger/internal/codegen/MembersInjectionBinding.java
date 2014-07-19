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
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents the full members injection of a particular type. This does not pay attention to
 * injected members on supertypes.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class MembersInjectionBinding extends Binding {
  @Override abstract TypeElement bindingElement();

  /**
   * Creates a {@link MembersInjectionBinding} for the given bindings.
   *
   * @throws IllegalArgumentException if the bindings are not all associated with the same type.
   */
  static MembersInjectionBinding create(Iterable<InjectionSite> injectionSites) {
    ImmutableSortedSet<InjectionSite> injectionSiteSet =
        ImmutableSortedSet.copyOf(INJECTION_ORDERING, injectionSites);
    TypeElement injectedTypeElement = Iterables.getOnlyElement(FluentIterable.from(injectionSiteSet)
        .transform(new Function<InjectionSite, TypeElement>() {
          @Override public TypeElement apply(InjectionSite injectionSite) {
            return MoreElements.asType(injectionSite.element().getEnclosingElement());
          }
        })
        .toSet());
    ImmutableSet<DependencyRequest> dependencies = FluentIterable.from(injectionSiteSet)
        .transformAndConcat(new Function<InjectionSite, Set<DependencyRequest>>() {
          @Override public Set<DependencyRequest> apply(InjectionSite input) {
            return input.dependencies();
          }
        })
        .toSet();
    return new AutoValue_MembersInjectionBinding(
        dependencies, injectedTypeElement, injectionSiteSet);
  }

  /** The set of individual sites where {@link Inject} is applied. */
  abstract ImmutableSortedSet<InjectionSite> injectionSites();

  /** The total set of dependencies required by all injection sites. */
  final ImmutableSet<DependencyRequest> dependencySet() {
    return ImmutableSet.copyOf(dependencies());
  }

  private static final Ordering<InjectionSite> INJECTION_ORDERING =
      new Ordering<InjectionSite>() {
        @Override
        public int compare(InjectionSite left, InjectionSite right) {
          checkArgument(left.element().getEnclosingElement()
              .equals(right.element().getEnclosingElement()));
          return ComparisonChain.start()
              // fields before methods
              .compare(left.element().getKind(), right.element().getKind())
              // then sort by whichever element comes first in the parent
              // this isn't necessary, but makes the processor nice and predictable
              .compare(targetIndexInEnclosing(left), targetIndexInEnclosing(right))
              .result();
        }

        private int targetIndexInEnclosing(InjectionSite injectionSite)  {
          return injectionSite.element().getEnclosingElement().getEnclosedElements()
              .indexOf(injectionSite.element());
        }
      };

  @AutoValue
  abstract static class InjectionSite {
    enum Kind {
      FIELD,
      METHOD,
    }

    abstract Kind kind();

    abstract Element element();

    abstract ImmutableSet<DependencyRequest> dependencies();

    static final class Factory {
      private final DependencyRequest.Factory dependencyRequestFactory;

      Factory(DependencyRequest.Factory dependencyRequestFactory) {
        this.dependencyRequestFactory = checkNotNull(dependencyRequestFactory);
      }

      InjectionSite forInjectMethod(ExecutableElement methodElement) {
        checkNotNull(methodElement);
        checkArgument(methodElement.getKind().equals(ElementKind.METHOD));
        checkArgument(isAnnotationPresent(methodElement, Inject.class));
        return new AutoValue_MembersInjectionBinding_InjectionSite(Kind.METHOD, methodElement,
            dependencyRequestFactory.forRequiredVariables(methodElement.getParameters()));
      }

      InjectionSite forInjectField(VariableElement fieldElement) {
        checkNotNull(fieldElement);
        checkArgument(fieldElement.getKind().equals(ElementKind.FIELD));
        checkArgument(isAnnotationPresent(fieldElement, Inject.class));
        return new AutoValue_MembersInjectionBinding_InjectionSite(Kind.FIELD, fieldElement,
            ImmutableSet.of(dependencyRequestFactory.forRequiredVariable(fieldElement)));
      }
    }
  }
}
