/*
 * Copyright (C) 2016 Google, Inc.
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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.TypeElement;

/**
 * Convenience methods for use with JavaPoet's {@link TypeSpec}.
 */
final class TypeSpecs {

  /**
   * If {@code supertype} is a class, adds it as a superclass for {@code typeBuilder}; if it is an
   * interface, adds it as a superinterface.
   */
  static void addSupertype(TypeSpec.Builder typeBuilder, TypeElement supertype) {
    switch (supertype.getKind()) {
      case CLASS:
        typeBuilder.superclass(ClassName.get(supertype));
        break;
      case INTERFACE:
        typeBuilder.addSuperinterface(ClassName.get(supertype));
        break;
      default:
        throw new AssertionError(supertype + " is neither a class nor an interface.");
    }
  }

  private TypeSpecs() {}
}
