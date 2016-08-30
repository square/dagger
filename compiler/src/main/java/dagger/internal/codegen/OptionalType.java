/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor7;

/**
 * Information about an {@code Optional} {@link TypeMirror}.
 *
 * <p>Only {@link com.google.common.base.Optional} is supported.
 */
// TODO(dpb): Support java.util.Optional.
@AutoValue
abstract class OptionalType {

  private static final String OPTIONAL_TYPE = "com.google.common.base.Optional";

  private static final SimpleTypeVisitor7<Boolean, Void> IS_OPTIONAL =
      new SimpleTypeVisitor7<Boolean, Void>(false) {
        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return MoreElements.asType(t.asElement()).getQualifiedName().contentEquals(OPTIONAL_TYPE);
        }
      };

  /**
   * The optional type itself, wrapped using {@link MoreTypes#equivalence()}.
   *
   * @deprecated Use {@link #declaredOptionalType()} instead.
   */
  @Deprecated
  protected abstract Equivalence.Wrapper<DeclaredType> wrappedDeclaredOptionalType();

  /** The optional type itself. */
  @SuppressWarnings("deprecation")
  DeclaredType declaredOptionalType() {
    return wrappedDeclaredOptionalType().get();
  }

  /** The value type. */
  TypeMirror valueType() {
    return declaredOptionalType().getTypeArguments().get(0);
  }

  /** Returns {@code true} if {@code type} is an {@code Optional} type. */
  static boolean isOptional(TypeMirror type) {
    return type.accept(IS_OPTIONAL, null);
  }

  /** Returns {@code true} if {@code key.type()} is an {@code Optional} type. */
  static boolean isOptional(Key key) {
    return isOptional(key.type());
  }

  /**
   * Returns a {@link OptionalType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not an {@code Optional} type
   */
  static OptionalType from(TypeMirror type) {
    checkArgument(isOptional(type), "%s must be an Optional", type);
    return new AutoValue_OptionalType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }

  /**
   * Returns a {@link OptionalType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not an {@code Optional} type
   */
  static OptionalType from(Key key) {
    return from(key.type());
  }
}
