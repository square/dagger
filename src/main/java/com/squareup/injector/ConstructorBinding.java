/**
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
package com.squareup.injector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A binding that uses the constructor of a concrete class.
 *
 * @author Jesse Wilson
 */
final class ConstructorBinding<T> extends Binding<T> {
  private final Constructor<T> constructor;
  private final Field[] fields;
  private Binding<?>[] parameters;
  private Binding<?>[] fieldBindings;

  private ConstructorBinding(Class<?> type, Key<T> key, Constructor<T> constructor, Field[] fields) {
    super(type, key);
    this.constructor = constructor;
    this.fields = fields;
  }

  @Override void attach(Linker linker) {
    // Field bindings.
    fieldBindings = new Binding<?>[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];
      Key<Object> fieldKey = Key.get(field.getGenericType(), field.getAnnotations(), field);
      fieldBindings[i] = linker.requestBinding(fieldKey, field);
    }

    // Constructor bindings.
    Type[] types = constructor.getGenericParameterTypes();
    Annotation[][] annotations = constructor.getParameterAnnotations();
    parameters = new Binding[types.length];
    for (int i = 0; i < parameters.length; i++) {
      String name = constructor + " parameter " + i;
      parameters[i] = linker.requestBinding(Key.get(types[i], annotations[i], name), constructor);
    }
  }

  @Override public T get() {
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

  @Override public boolean isSingleton() {
    return constructor.getDeclaringClass().isAnnotationPresent(Singleton.class);
  }

  public static <T> Binding<T> create(Key<T> key) {
    if (!(key.type instanceof Class) || key.annotation != null) {
      throw new IllegalArgumentException("No binding for " + key);
    }

    @SuppressWarnings("unchecked") // The key type implies the class type.
    Class<T> type = (Class<T>) key.type;

    /*
     * Lookup the injectable fields and their corresponding keys.
     */
    final List<Field> injectedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (field.getAnnotation(Inject.class) == null) {
          continue;
        }
        field.setAccessible(true);
        injectedFields.add(field);
      }
    }

    /*
     * Lookup @Inject-annotated constructors. If there's no @Inject-annotated
     * constructor, use a default constructor if the class has other injections.
     */
    Constructor<T> injectedConstructor = null;
    for (Constructor<T> constructor : (Constructor<T>[]) type.getDeclaredConstructors()) {
      if (constructor.getAnnotation(Inject.class) == null) {
        continue;
      }
      if (injectedConstructor != null) {
        throw new IllegalArgumentException("Too many injectable constructors on " + type);
      }
      constructor.setAccessible(true);
      injectedConstructor = constructor;
    }
    if (injectedConstructor == null) {
      if (injectedFields.isEmpty()) {
        throw new IllegalArgumentException("No injectable constructor on " + type);
      }
      try {
        injectedConstructor = type.getConstructor();
      } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException("No injectable constructor on " + type);
      }
    }

    return new ConstructorBinding<T>(type, key, injectedConstructor,
        injectedFields.toArray(new Field[injectedFields.size()]));
  }
}
