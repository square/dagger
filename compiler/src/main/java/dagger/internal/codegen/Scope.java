/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopes;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Reusable;
import dagger.producers.ProductionScope;
import java.lang.annotation.Annotation;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** A javax.inject.Scope. */
@AutoValue
abstract class Scope {

  /** The underlying {@link AnnotationMirror} that represents the scope annotation. */
  abstract Equivalence.Wrapper<AnnotationMirror> scopeAnnotation();

  /**
   * Creates a {@link Scope} object from the {@link javax.inject.Scope}-annotated annotation type.
   */
  static Scope scope(AnnotationMirror scopeAnnotation) {
    checkArgument(
        isAnnotationPresent(
            scopeAnnotation.getAnnotationType().asElement(), javax.inject.Scope.class));
    return new AutoValue_Scope(AnnotationMirrors.equivalence().wrap(scopeAnnotation));
  }

  /**
   * Creates a {@link Scope} object from the {@link javax.inject.Scope}-annotated annotation type.
   */
  static Scope scope(TypeElement scopeType) {
    return scope(SimpleAnnotationMirror.of(scopeType));
  }

  private static Scope scope(Elements elements, Class<? extends Annotation> scopeAnnotationClass) {
    return scope(elements.getTypeElement(scopeAnnotationClass.getCanonicalName()));
  }

  /** Returns all of the associated scopes for a source code element. */
  static ImmutableSet<Scope> scopesOf(Element element) {
    return FluentIterable.from(getScopes(element)).transform(Scope::scope).toSet();
  }

  /**
   * Returns at most one associated scoped annotation from the source code element, throwing an
   * exception if there are more than one.
   */
  static Optional<Scope> uniqueScopeOf(Element element) {
    ImmutableSet<? extends AnnotationMirror> scopeAnnotations = getScopes(element);
    if (scopeAnnotations.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(scope(Iterables.getOnlyElement(scopeAnnotations)));
  }

  /**
   * Returns a representation for {@link ProductionScope @ProductionScope} scope.
   */
  static Scope productionScope(Elements elements) {
    return scope(elements, ProductionScope.class);
  }

  /**
   * Returns a representation for {@link Singleton @Singleton} scope.
   */
  static Scope singletonScope(Elements elements) {
    return scope(elements, Singleton.class);
  }

  /**
   * Returns a representation for {@link Reusable @Reusable} scope.
   */
  static Scope reusableScope(Elements elements) {
    return scope(elements, Reusable.class);
  }

  /**
   * Returns the readable source representation (name with @ prefix) of the annotation type.
   *
   * <p>It's readable source because it has had common package prefixes removed, e.g.
   * {@code @javax.inject.Singleton} is returned as {@code @Singleton}.
   *
   * <p>Does not return any annotation values, since {@link javax.inject.Scope @Scope}
   * annotations are not supposed to have any.
   */
  public String getReadableSource() {
    return stripCommonTypePrefixes("@" + getQualifiedName());
  }

  /**
   * Returns the fully qualified name of the annotation type.
   */
  public String getQualifiedName() {
    return scopeAnnotationElement().getQualifiedName().toString();
  }

  /**
   * The scope annotation element.
   */
  public TypeElement scopeAnnotationElement() {
    return MoreTypes.asTypeElement(scopeAnnotation().get().getAnnotationType());
  }

  /**
   * Returns a debug representation of the scope.
   */
  @Override
  public String toString() {
    return scopeAnnotation().get().toString();
  }
}
