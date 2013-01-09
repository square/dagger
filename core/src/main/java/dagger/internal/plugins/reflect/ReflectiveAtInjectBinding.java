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
package dagger.internal.plugins.reflect;

import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Injects the {@code @Inject}-annotated fields and constructors of a class
 * using reflection.
 */
final class ReflectiveAtInjectBinding<T> extends Binding<T> {
  private final Field[] fields;
  private final Constructor<T> constructor;
  private final Class<?> supertype;
  private final String[] keys;
  private final Binding<?>[] fieldBindings;
  private final Binding<?>[] parameterBindings;
  private Binding<? super T> supertypeBinding;
  private Field[] assistedFields;
  private Integer[] assistedParamIndexes;
  private String[] assistedKeys;

  private static final Object[] EMPTY = new Object[0];

  /**
   * @param keys keys for the fields, constructor parameters and supertype in
   *     that order. These are precomputed to minimize reflection when {@code
   *     attach} is called multiple times.
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   * @param supertype the injectable supertype, or null if the supertype is a
   */
  private ReflectiveAtInjectBinding(String provideKey, String membersKey, boolean singleton,
      Class<?> type, Field[] fields, Constructor<T> constructor, int parameterCount,
      Class<?> supertype, String[] keys, Field[] assistedFields, Integer[] assistedParamIndexes,
      String[] assistedKeys) {
    super(provideKey, membersKey, singleton, type);
    this.constructor = constructor;
    this.fields = fields;
    this.supertype = supertype;
    this.keys = keys;
    this.parameterBindings = new Binding<?>[parameterCount];
    this.fieldBindings = new Binding<?>[fields.length];
    this.assistedFields = assistedFields;
    this.assistedParamIndexes = assistedParamIndexes;

    this.assistedKeys = assistedKeys;
  }

  @SuppressWarnings("unchecked") // We're careful to make keys and bindings match up.
  @Override public void attach(Linker linker) {
    int k = 0;
    for (int i = 0; i < fields.length; i++) {
      if (fieldBindings[i] == null) {
        fieldBindings[i] = linker.requestBinding(keys[k], fields[i]);
      }
      k++;
    }
    if (constructor != null) {
      for (int i = 0; i < parameterBindings.length; i++) {
        if (parameterBindings[i] == null) {
          parameterBindings[i] = linker.requestBinding(keys[k], constructor);
        }
        k++;
      }
    }
    if (supertype != null && supertypeBinding == null) {
      supertypeBinding = (Binding<? super T>) linker.requestBinding(keys[k], membersKey, false);
    }
  }

  @Override public T get() {
    return get(EMPTY);
  }

  @Override public T get(Object[] assistedArgs) {
    if (constructor == null) {
      throw new UnsupportedOperationException();
    }
    Object[] args = new Object[parameterBindings.length + assistedParamIndexes.length];
    int bindingIndex = 0;
    int assistedIndex = assistedFields.length;
    for (int i = 0; i < args.length; i++) {
      if (assistedIndex < assistedParamIndexes.length
          && assistedParamIndexes[assistedIndex] == i) {
        args[i] = assistedArgs[assistedIndex++];
      } else {
        args[i] = parameterBindings[bindingIndex++].get();
      }
    }
    T result;
    try {
      result = constructor.newInstance(args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      throw cause instanceof RuntimeException
          ? (RuntimeException) cause
          : new RuntimeException(cause);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    injectMembers(result);
    injectAssistedMembers(result, assistedArgs);
    return result;
  }

  @Override
  public void injectAssistedMembers(T t, Object[] args) {
    try {
      for (int i = 0; i < assistedFields.length; i++) {
        assistedFields[i].set(t, args[i]);
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void getAssistedDependencies(Set<String> assistedKeys) {
    Collections.addAll(assistedKeys, this.assistedKeys);
  }

  @Override
  public int assistedParamsSize() {
    return assistedKeys.length;
  }

  @Override public void injectMembers(T t) {
    try {
      for (int i = 0; i < fields.length; i++) {
        fields[i].set(t, fieldBindings[i].get());
      }
      if (supertypeBinding != null) {
        supertypeBinding.injectMembers(t);
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    if (parameterBindings != null) {
      for (Binding<?> binding : parameterBindings) {
        get.add(binding);
      }
    }
    for (Binding<?> binding : fieldBindings) {
      injectMembers.add(binding);
    }
    if (supertypeBinding != null) {
      injectMembers.add(supertypeBinding);
    }
  }

  @Override public String toString() {
    return provideKey != null ? provideKey : membersKey;
  }

  /**
   * @param mustBeInjectable true if the binding must have {@code @Inject}
   *     annotations.
   */
  public static <T> Binding<T> create(Class<T> type, boolean mustBeInjectable) {
    boolean singleton = type.isAnnotationPresent(Singleton.class);
    List<String> keys = new ArrayList<String>();
    List<String> assistedKeys = new ArrayList<String>();

    // Lookup the injectable fields and their corresponding keys.
    List<Field> injectedFields = new ArrayList<Field>();
    List<Field> assistedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (!field.isAnnotationPresent(Inject.class) || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        String key = Keys.get(field.getGenericType(), field.getAnnotations(), field);
        if (Keys.isAssisted(key)) {
          assistedFields.add(field);
          assistedKeys.add(key);
        } else {
          injectedFields.add(field);
          keys.add(key);
        }
      }
    }

    // Look up @Inject-annotated constructors. If there's no @Inject-annotated
    // constructor, use a default public constructor if the class has other
    // injections. Otherwise treat the class as non-injectable.
    Constructor<T> injectedConstructor = null;
    for (Constructor<T> constructor : getConstructorsForType(type)) {
      if (!constructor.isAnnotationPresent(Inject.class)) {
        continue;
      }
      if (injectedConstructor != null) {
        throw new IllegalArgumentException("Too many injectable constructors on " + type.getName());
      }
      injectedConstructor = constructor;
    }
    if (injectedConstructor == null) {
      if (injectedFields.isEmpty() && assistedFields.isEmpty() && mustBeInjectable) {
        throw new IllegalArgumentException("No injectable members on " + type.getName()
            + ". Do you want to add an injectable constructor?");
      }
      try {
        injectedConstructor = type.getDeclaredConstructor();
      } catch (NoSuchMethodException ignored) {
      }
    }

    int parameterCount;
    String provideKey;
    List<Integer> assistedParamIndexes = new ArrayList<Integer>();
    if (injectedConstructor != null) {
      provideKey = Keys.get(type);
      injectedConstructor.setAccessible(true);
      Type[] types = injectedConstructor.getGenericParameterTypes();
      parameterCount = 0;
      if (types.length != 0) {
        Annotation[][] annotations = injectedConstructor.getParameterAnnotations();
        for (int p = 0; p < types.length; p++) {
          String key = Keys.get(types[p], annotations[p], injectedConstructor);
          if (Keys.isAssisted(key)) {
            assistedKeys.add(key);
            assistedParamIndexes.add(p);
          } else {
            parameterCount++;
            keys.add(key);
          }
        }
      }
    } else {
      provideKey = null;
      parameterCount = 0;
      if (singleton) {
        throw new IllegalArgumentException(
            "No injectable constructor on @Singleton " + type.getName());
      }
    }

    Class<? super T> supertype = type.getSuperclass();
    if (supertype != null) {
      if (Keys.isPlatformType(supertype.getName())) {
        supertype = null;
      } else {
        keys.add(Keys.getMembersKey(supertype));
      }
    }

    String membersKey = Keys.getMembersKey(type);
    return new ReflectiveAtInjectBinding<T>(provideKey, membersKey, singleton, type,
        injectedFields.toArray(new Field[injectedFields.size()]), injectedConstructor,
        parameterCount, supertype, keys.toArray(new String[keys.size()]),
        assistedFields.toArray(new Field[assistedFields.size()]),
        assistedParamIndexes.toArray(new Integer[assistedParamIndexes.size()]),
        assistedKeys.toArray(new String[assistedKeys.size()]));
  }

  @SuppressWarnings("unchecked") // Class.getDeclaredConstructors is an unsafe API.
  private static <T> Constructor<T>[] getConstructorsForType(Class<T> type) {
    return (Constructor<T>[]) type.getDeclaredConstructors();
  }
}
