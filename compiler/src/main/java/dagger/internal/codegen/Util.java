/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Utilities for handling types in annotation processors
 */
final class Util {
  /**
   * Returns the {@code V} type for a {@link Map} type like Map<K, Provider<V>>} if the map
   * includes such a construction
   */
  public static TypeMirror getProvidedValueTypeOfMap(DeclaredType mapType) {
    checkState(isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    return asDeclaredType(mapType.getTypeArguments().get(1)).getTypeArguments().get(0);
  }

  // TODO(user): Consider an object that holds and exposes the various parts of a Map type.
  /**
   * returns the value type for a {@link Map} type like Map<K, V>}.
   */
  public static TypeMirror getValueTypeOfMap(DeclaredType mapType) {
    checkState(isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    List<? extends TypeMirror> mapArgs = mapType.getTypeArguments();
    return mapArgs.get(1);
  }

  /**
   * Returns the key type for a {@link Map} type like Map<K, Provider<V>>}
   */
  public static DeclaredType getKeyTypeOfMap(DeclaredType mapType) {
    checkState(isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    List<? extends TypeMirror> mapArgs = mapType.getTypeArguments();
    return asDeclaredType(mapArgs.get(0));
  }

  /**
   * Returns the unwrapped key's {@link TypeElement} for a {@link Map} given the
   * {@link AnnotationMirror} of the key.
   */
  public static TypeElement getKeyTypeElement(AnnotationMirror mapKey, final Elements elements) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> map = mapKey.getElementValues();
    // TODO(user) Support literals other than String and Enum
    AnnotationValueVisitor<TypeElement, Void> mapKeyVisitor =
        new SimpleAnnotationValueVisitor6<TypeElement, Void>() {
          @Override
          public TypeElement visitEnumConstant(VariableElement c, Void p) {
            return MoreElements.asType(c.getEnclosingElement()) ;
          }

          @Override
          public TypeElement visitString(String s, Void p) {
            return elements.getTypeElement(String.class.getCanonicalName());
          }

          @Override
          protected TypeElement defaultAction(Object o, Void v) {
            throw new IllegalStateException(
                "Non-supported key type for map binding " + o.getClass().getCanonicalName());
          }
        };
    TypeElement keyTypeElement =
        Iterables.getOnlyElement(map.entrySet()).getValue().accept(mapKeyVisitor, null);
    return keyTypeElement;
  }

  // TODO(user): move to MoreTypes
  /**
   * Returns a {@link DeclaredType} if the {@link TypeMirror} represents a declared type such
   * as a class, interface, or enum.
   */
  static DeclaredType asDeclaredType(TypeMirror type) {
    return type.accept(new SimpleTypeVisitor6<DeclaredType, Void>() {
      @Override protected DeclaredType defaultAction(TypeMirror type, Void ignored) {
        throw new IllegalStateException(type + " is not a DeclaredType.");
      }

      @Override public DeclaredType visitDeclared(DeclaredType type, Void ignored) {
        return type;
      }
    }, null);
  }

  // TODO(user): move to MoreTypes
  /**
   * Returns true if the raw type underlying the given {@link TypeMirror} represents the
   * same raw type as the given {@link Class} and throws an IllegalArgumentException if the
   * {@link TypeMirror} does not represent a type that can be referenced by a {@link Class}
   */
  static boolean isTypeOf(final Class<?> clazz, TypeMirror type) {
    checkNotNull(clazz);
    checkNotNull(type);
    return type.accept(new SimpleTypeVisitor6<Boolean, Void>() {
      @Override protected Boolean defaultAction(TypeMirror type, Void ignored) {
        throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
      }

      @Override public Boolean visitNoType(NoType noType, Void p) {
        if (noType.getKind().equals(TypeKind.VOID)) {
          return clazz.equals(Void.TYPE);
        }
        throw new IllegalArgumentException(noType + " cannot be represented as a Class<?>.");
      }

      @Override public Boolean visitPrimitive(PrimitiveType type, Void p) {
        switch (type.getKind()) {
          case BOOLEAN:
            return clazz.equals(Boolean.TYPE);
          case BYTE:
            return clazz.equals(Byte.TYPE);
          case CHAR:
            return clazz.equals(Character.TYPE);
          case DOUBLE:
            return clazz.equals(Double.TYPE);
          case FLOAT:
            return clazz.equals(Float.TYPE);
          case INT:
            return clazz.equals(Integer.TYPE);
          case LONG:
            return clazz.equals(Long.TYPE);
          case SHORT:
            return clazz.equals(Short.TYPE);
          default:
            throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
        }
      }

      @Override public Boolean visitArray(ArrayType array, Void p) {
        return clazz.isArray()
            && isTypeOf(clazz.getComponentType(), array.getComponentType());
      }

      @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
        TypeElement typeElement = MoreElements.asType(type.asElement());
        return typeElement.getQualifiedName().contentEquals(clazz.getCanonicalName());
      }
    }, null);
  }

  private Util() {}
}
