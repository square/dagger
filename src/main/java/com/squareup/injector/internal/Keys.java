/*
 * Copyright (C) 2012 Square, Inc.
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
package com.squareup.injector.internal;

import com.squareup.injector.MembersInjector;
import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.inject.Provider;
import javax.inject.Qualifier;

/**
 * Formats strings that identify the value to be injected.
 *
 * <h3>Implementation Note</h3>
 * This currently formats keys by concatenating the annotation name, a slash
 * "/", and the type name. Parameterized types are formatted with ", " between
 * type parameters. The exact key format may change in a future release.
 */
public final class Keys {
  private static final String PROVIDER_PREFIX = Provider.class.getName() + "<";
  private static final String MEMBERS_INJECTOR_PREFIX = MembersInjector.class.getName() + "<";
  private static final String GUICE_MEMBERS_INJECTOR_PREFIX
      = com.google.inject.MembersInjector.class.getName() + "<";
  private static final String GUICE_PROVIDER_PREFIX
      = com.google.inject.Provider.class.getName() + "<";

  private static final LruCache<Class<? extends Annotation>, Boolean> IS_QUALIFIER_ANNOTATION
      = new LruCache<Class<? extends Annotation>, Boolean>(Integer.MAX_VALUE) {
    @Override protected Boolean create(Class<? extends Annotation> annotationType) {
      return annotationType.isAnnotationPresent(Qualifier.class);
    }
  };

  Keys() {
  }

  /**
   * Returns a key for {@code type} with no annotation.
   */
  public static String get(Type type) {
    return get(type, null);
  }

  /**
   * Returns a key for {@code type} annotated by {@code annotation}.
   */
  public static String get(Type type, Annotation annotation) {
    type = boxIfPrimitive(type);
    if (annotation == null
        && type instanceof Class
        && !((Class<?>) type).isArray()) {
      return ((Class<?>) type).getName();
    }

    StringBuilder result = new StringBuilder();
    if (annotation != null) {
      result.append(annotation).append("/");
    }
    typeToString(type, result);
    return result.toString();
  }

  /**
   * Returns a key for {@code type} annotated with {@code annotations},
   * reporting failures against {@code subject}.
   *
   * @param annotations the annotations on a single method, field or parameter.
   *     This array may contain at most one qualifier annotation.
   */
  public static String get(Type type, Annotation[] annotations, Object subject) {
    Annotation qualifier = null;
    for (Annotation a : annotations) {
      if (!IS_QUALIFIER_ANNOTATION.get(a.annotationType())) {
        continue;
      }
      if (qualifier != null) {
        throw new IllegalArgumentException("Too many qualifier annotations on " + subject);
      }
      qualifier = a;
    }
    return get(type, qualifier);
  }

  private static void typeToString(Type type, StringBuilder result) {
    if (type instanceof Class) {
      Class<?> c = (Class<?>) type;
      if (c.isArray()) {
        result.append(c.getComponentType().getName());
        result.append("[]");
      } else if (c.isPrimitive()) {
        // TODO: support this?
        throw new UnsupportedOperationException("Uninjectable type " + type);
      } else {
        result.append(c.getName());
      }
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      typeToString(parameterizedType.getRawType(), result);
      Type[] arguments = parameterizedType.getActualTypeArguments();
      result.append("<");
      for (int i = 0; i < arguments.length; i++) {
        if (i != 0) {
          result.append(", ");
        }
        typeToString(arguments[i], result);
      }
      result.append(">");
    } else if (type instanceof GenericArrayType) {
      GenericArrayType genericArrayType = (GenericArrayType) type;
      result.append(((Class<?>) genericArrayType.getGenericComponentType()).getName());
      result.append("[]");
    } else {
      throw new UnsupportedOperationException("Uninjectable type " + type);
    }
  }

  /**
   * Returns a key for the type provided by, or injected by this key. For
   * example, if this is a key for a {@code Provider<Foo>}, this returns the
   * key for {@code Foo}. This retains annotations and supports both Provider
   * keys and MembersInjector keys.
   */
  public static String getDelegateKey(String key) {
    int start = 0;
    if (key.startsWith("@")) {
      start = key.lastIndexOf('/') + 1;
    }

    String wrapperPrefix;
    if (substringStartsWith(key, start, PROVIDER_PREFIX)) {
      wrapperPrefix = PROVIDER_PREFIX;
    } else if (substringStartsWith(key, start, GUICE_PROVIDER_PREFIX)) {
      wrapperPrefix = GUICE_PROVIDER_PREFIX;
    } else if (substringStartsWith(key, start, MEMBERS_INJECTOR_PREFIX)) {
      wrapperPrefix = MEMBERS_INJECTOR_PREFIX;
    } else if (substringStartsWith(key, start, GUICE_MEMBERS_INJECTOR_PREFIX)) {
      wrapperPrefix = GUICE_MEMBERS_INJECTOR_PREFIX;
    } else {
      return null;
    }
    return key.substring(0, start)
        + key.substring(start + wrapperPrefix.length(), key.length() - 1);
  }

  /**
   * Returns true if {@code string.substring(offset).startsWith(substring)}.
   */
  private static boolean substringStartsWith(String string, int offset, String substring) {
    return string.regionMatches(offset, substring, 0, substring.length());
  }

  /**
   * Returns true if {@code key} has a qualifier annotation.
   */
  public static boolean isAnnotated(String key) {
    return key.startsWith("@");
  }

  /**
   * Returns the class name for {@code key}, if {@code key} was created with a
   * class instance. Returns null if {@code key} represents a parameterized type
   * or an array type.
   */
  public static String getClassName(String key) {
    int start = 0;
    if (key.startsWith("@")) {
      start = key.lastIndexOf('/') + 1;
    }
    return (key.indexOf('<', start) == -1 && key.indexOf('[') == -1)
        ? key.substring(start)
        : null;
  }

  private static Type boxIfPrimitive(Type type) {
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == char.class) return Character.class;
    if (type == boolean.class) return Boolean.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    if (type == void.class) return Void.class;
    return type;
  }
}
