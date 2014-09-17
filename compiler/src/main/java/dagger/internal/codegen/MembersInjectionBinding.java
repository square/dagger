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
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.type.TypeKind.NONE;

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

  /** The set of individual sites where {@link Inject} is applied. */
  abstract ImmutableSortedSet<InjectionSite> injectionSites();

  abstract Optional<DependencyRequest> parentInjectorRequest();

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
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = checkNotNull(elements);
      this.types = checkNotNull(types);
      this.dependencyRequestFactory = checkNotNull(dependencyRequestFactory);
    }

    private InjectionSite injectionSiteForInjectMethod(ExecutableElement methodElement) {
      checkNotNull(methodElement);
      checkArgument(methodElement.getKind().equals(ElementKind.METHOD));
      checkArgument(isAnnotationPresent(methodElement, Inject.class));
      return new AutoValue_MembersInjectionBinding_InjectionSite(InjectionSite.Kind.METHOD,
          methodElement,
          dependencyRequestFactory.forRequiredVariables(methodElement.getParameters()));
    }

    private InjectionSite injectionSiteForInjectField(VariableElement fieldElement) {
      checkNotNull(fieldElement);
      checkArgument(fieldElement.getKind().equals(ElementKind.FIELD));
      checkArgument(isAnnotationPresent(fieldElement, Inject.class));
      return new AutoValue_MembersInjectionBinding_InjectionSite(InjectionSite.Kind.FIELD,
          fieldElement,
          ImmutableSet.of(dependencyRequestFactory.forRequiredVariable(fieldElement)));
    }

    MembersInjectionBinding forInjectedType(TypeElement typeElement) {
      ImmutableSortedSet.Builder<InjectionSite> injectionSitesBuilder =
          ImmutableSortedSet.orderedBy(INJECTION_ORDERING);
      for (Element enclosedElement : typeElement.getEnclosedElements()) {
        injectionSitesBuilder.addAll(enclosedElement.accept(
            new ElementKindVisitor6<Optional<InjectionSite>, Void>(
                Optional.<InjectionSite>absent()) {
                  @Override
                  public Optional<InjectionSite> visitExecutableAsMethod(ExecutableElement e,
                      Void p) {
                    return isAnnotationPresent(e, Inject.class)
                        ? Optional.of(injectionSiteForInjectMethod(e))
                        : Optional.<InjectionSite>absent();
                  }

                  @Override
                  public Optional<InjectionSite> visitVariableAsField(VariableElement e, Void p) {
                    return isAnnotationPresent(e, Inject.class)
                        ? Optional.of(injectionSiteForInjectField(e))
                        : Optional.<InjectionSite>absent();
                  }
                }, null).asSet());
      }
      ImmutableSortedSet<InjectionSite> injectionSites = injectionSitesBuilder.build();

      ImmutableSet<DependencyRequest> dependencies = FluentIterable.from(injectionSites)
          .transformAndConcat(new Function<InjectionSite, Set<DependencyRequest>>() {
            @Override public Set<DependencyRequest> apply(InjectionSite input) {
              return input.dependencies();
            }
          })
          .toSet();

      Optional<DependencyRequest> parentInjectorRequest = nonObjectSupertype(typeElement)
          .transform(new Function<TypeElement, DependencyRequest>() {
            @Override public DependencyRequest apply(TypeElement input) {
              return dependencyRequestFactory.forMembersInjectedType(input);
            }
          });

      return new AutoValue_MembersInjectionBinding(
          dependencies,
          new ImmutableSet.Builder<DependencyRequest>()
              .addAll(dependencies)
              .addAll(parentInjectorRequest.asSet())
              .build(),
          Optional.of(MoreElements.getPackage(typeElement).getQualifiedName().toString()),
          typeElement,
          injectionSites,
          parentInjectorRequest);
    }

    private Optional<TypeElement> nonObjectSupertype(TypeElement type) {
      TypeMirror superclass = type.getSuperclass();
      boolean nonObjectSuperclass = !superclass.getKind().equals(NONE)
          && !types.isSameType(
              elements.getTypeElement(Object.class.getCanonicalName()).asType(), superclass);
      return nonObjectSuperclass
          ? Optional.of(MoreElements.asType(types.asElement(superclass)))
          : Optional.<TypeElement>absent();
    }
  }
}
