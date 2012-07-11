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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A binding that uses the constructor of a concrete class.
 */
final class ConstructorBinding<T> extends Binding<T> {
  private final Constructor<T> constructor;
  private final Field[] fields;
  private Binding<?>[] parameters;
  private Binding<?>[] fieldBindings;

  /**
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  private ConstructorBinding(String key, boolean singleton, Class<?> type,
      Constructor<T> constructor, Field[] fields) {
    super(key, singleton, constructor == null, type);
    this.constructor = constructor;
    this.fields = fields;
  }

  @Override public void attach(Linker linker) {
    // Field bindings.
    fieldBindings = new Binding<?>[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];
      String fieldKey = Keys.get(field.getGenericType(), field.getAnnotations(), field);
      fieldBindings[i] = linker.requestBinding(fieldKey, field, false);
    }

    // Constructor bindings.
    if (constructor != null) {
      Type[] types = constructor.getGenericParameterTypes();
      Annotation[][] annotations = constructor.getParameterAnnotations();
      parameters = new Binding[types.length];
      for (int i = 0; i < parameters.length; i++) {
        String key = Keys.get(types[i], annotations[i], constructor + " parameter " + i);
        parameters[i] = linker.requestBinding(key, constructor, false);
      }
    }
  }

  @Override public T get() {
    if (constructor == null) {
      throw new UnsupportedOperationException();
    }
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      args[i] = parameters[i].get();
    }
    T result;
    try {
      result = constructor.newInstance(args);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    injectMembers(result);
    return result;
  }

  @Override public void injectMembers(T t) {
    try {
      for (int i = 0; i < fields.length; i++) {
        fields[i].set(t, fieldBindings[i].get());
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  @Override public Binding<?>[] getDependencies() {
    if (parameters == null) {
      return fieldBindings;
    }
    Binding<?>[] result = new Binding<?>[parameters.length + fieldBindings.length];
    System.arraycopy(parameters, 0, result, 0, parameters.length);
    System.arraycopy(fieldBindings, 0, result, parameters.length, fieldBindings.length);
    return result;
  }

  @Override public String toString() {
    return key;
  }

  public static <T> Binding<T> create(Class<T> type) {
    /*
     * Lookup the injectable fields and their corresponding keys.
     */
    final List<Field> injectedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (field.getAnnotation(Inject.class) == null || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        injectedFields.add(field);
      }
    }

    /*
     * Lookup @Inject-annotated constructors. If there's no @Inject-annotated
     * constructor, use a default public constructor if the class has other
     * injections. Otherwise treat the class as non-injectable.
     */
    Constructor<T> injectedConstructor = null;
    for (Constructor<T> constructor : (Constructor<T>[]) type.getDeclaredConstructors()) {
      if (constructor.getAnnotation(Inject.class) == null) {
        continue;
      }
      if (injectedConstructor != null) {
        throw new IllegalArgumentException("Too many injectable constructors on " + type.getName());
      }
      injectedConstructor = constructor;
    }
    if (injectedConstructor == null) {
      if (injectedFields.isEmpty()) {
        throw new IllegalArgumentException("No injectable constructor on " + type.getName());
      }
      try {
        injectedConstructor = type.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        injectedConstructor = null;
      }
    }

    if (injectedConstructor != null) {
      injectedConstructor.setAccessible(true);
    }

    boolean singleton = type.isAnnotationPresent(Singleton.class);
    if (singleton && injectedConstructor == null) {
      throw new IllegalArgumentException(
          "No injectable constructor on @Singleton " + type.getName());
    }

    return new ConstructorBinding<T>(Keys.get(type, null), singleton, type, injectedConstructor,
        injectedFields.toArray(new Field[injectedFields.size()]));
  }
}
