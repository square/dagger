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
 * A binding that injects the constructor and fields of a class.
 */
final class AtInjectBinding<T> extends Binding<T> {
  private final Constructor<T> constructor;
  private final Field[] fields;
  private Binding<?>[] parameters;
  private Binding<?>[] fieldBindings;
  // TODO: delegate to supertype members injector (which may be generated)

  /**
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  private AtInjectBinding(String key, String membersKey, boolean singleton, Class<?> type,
      Constructor<T> constructor, Field[] fields) {
    super(key, membersKey, singleton, type);
    this.constructor = constructor;
    this.fields = fields;
  }

  @Override public void attach(Linker linker) {
    // Field bindings.
    fieldBindings = new Binding<?>[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];
      String fieldKey = Keys.get(field.getGenericType(), field.getAnnotations(), field);
      fieldBindings[i] = linker.requestBinding(fieldKey, field);
    }

    // Constructor bindings.
    if (constructor != null) {
      Type[] types = constructor.getGenericParameterTypes();
      Annotation[][] annotations = constructor.getParameterAnnotations();
      parameters = new Binding[types.length];
      for (int i = 0; i < parameters.length; i++) {
        String key = Keys.get(types[i], annotations[i], constructor + " parameter " + i);
        parameters[i] = linker.requestBinding(key, constructor);
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
    return provideKey != null ? provideKey : membersKey;
  }

  /**
   * @param forMembersInjection true if the binding is being created to inject
   *     members only. Such injections do not require {@code @Inject}
   *     annotations.
   */
  public static <T> Binding<T> create(Class<T> type, boolean forMembersInjection) {
    /*
     * Lookup the injectable fields and their corresponding keys.
     */
    final List<Field> injectedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (!field.isAnnotationPresent(Inject.class) || Modifier.isStatic(field.getModifiers())) {
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
      if (!constructor.isAnnotationPresent(Inject.class)) {
        continue;
      }
      if (injectedConstructor != null) {
        throw new IllegalArgumentException("Too many injectable constructors on " + type.getName());
      }
      injectedConstructor = constructor;
    }

    if (injectedConstructor == null) {
      if (injectedFields.isEmpty() && !forMembersInjection) {
        throw new IllegalArgumentException("No injectable members on " + type.getName()
            + ". Do you want to add an injectable constructor?");
      }
      try {
        injectedConstructor = type.getDeclaredConstructor();
      } catch (NoSuchMethodException ignored) {
      }
    }

    String key;
    boolean singleton = type.isAnnotationPresent(Singleton.class);
    if (injectedConstructor != null) {
      key = Keys.get(type);
      injectedConstructor.setAccessible(true);
    } else {
      key = null;
      if (singleton) {
        throw new IllegalArgumentException(
            "No injectable constructor on @Singleton " + type.getName());
      }
    }

    String membersKey = Keys.getMembersKey(type);
    return new AtInjectBinding<T>(key, membersKey, singleton, type, injectedConstructor,
        injectedFields.toArray(new Field[injectedFields.size()]));
  }
}
