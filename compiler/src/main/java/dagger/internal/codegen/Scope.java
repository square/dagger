/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static com.google.auto.common.MoreTypes.isTypeOf;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;

/**
 * A representation of the scope (or lack of it) associated with a component, providing method
 * or injection location.
 */
final class Scope {

  /**
   * An internal representation for an unscoped binding.
   */
  private static final Scope UNSCOPED = new Scope();

  /**
   * The underlying {@link AnnotationMirror} that represents the scope annotation.
   */
  @Nullable
  private final AnnotationMirror annotationMirror;

  private Scope(@Nullable AnnotationMirror annotationMirror) {
    this.annotationMirror = annotationMirror;
  }

  private Scope() {
    this(null);
  }

  /**
   * Returns representation for an unscoped binding.
   */
  static Scope unscoped() {
    return UNSCOPED;
  }

  /**
   * If the source code element has an associated scoped annotation then returns a representation
   * of that scope, otherwise returns a representation for an unscoped binding.
   */
  static Scope scopeOf(Element element) {
    Optional<AnnotationMirror> scopeAnnotation = getScopeAnnotation(element);
    return scopeAnnotation.isPresent() ? new Scope(scopeAnnotation.get()) : UNSCOPED;
  }

  /**
   * Returns true if the scope is present, i.e. it's not unscoped binding.
   */
  public boolean isPresent() {
    return annotationMirror != null;
  }

  /**
   * Returns true if the scope represents the {@link Singleton @Singleton} annotation.
   */
  public boolean isSingleton() {
    return annotationMirror != null
        && isTypeOf(Singleton.class, annotationMirror.getAnnotationType());
  }

  /**
   * Returns the readable source representation (name with @ prefix) of the annotation type.
   *
   * <p>It's readable source because it has had common package prefixes removed, e.g.
   * {@code @javax.inject.Singleton} is returned as {@code @Singleton}.
   *
   * <p>Make sure that the scope is actually {@link #isPresent() present} before calling as it will
   * throw an {@link IllegalStateException} otherwise. This does not return any annotation values
   * as according to {@link javax.inject.Scope} scope annotations are not supposed to use them.
   */
  public String getReadableSource() {
    return stripCommonTypePrefixes("@" + getQualifiedName());
  }

  /**
   * Returns the fully qualified name of the annotation type.
   *
   * <p>Make sure that the scope is actually {@link #isPresent() present} before calling as it will
   * throw an {@link IllegalStateException} otherwise. This does not return any annotation values
   * as according to {@link javax.inject.Scope} scope annotations are not supposed to use them.
   */
  public String getQualifiedName() {
    Preconditions.checkState(annotationMirror != null,
        "Cannot create a stripped source representation of no annotation");
    TypeElement typeElement = MoreTypes.asTypeElement(annotationMirror.getAnnotationType());
    return typeElement.getQualifiedName().toString();
  }

  /**
   * Scopes are equal if the underlying {@link AnnotationMirror} are equivalent according to
   * {@link AnnotationMirrors#equivalence()}.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof Scope) {
      Scope that = (Scope) obj;
      return AnnotationMirrors.equivalence()
        .equivalent(this.annotationMirror, that.annotationMirror);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return AnnotationMirrors.equivalence().hash(annotationMirror);
  }

  /**
   * Returns a debug representation of the scope.
   */
  @Override
  public String toString() {
    return annotationMirror == null ? "UNSCOPED" : annotationMirror.toString();
  }
}
