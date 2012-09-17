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
package dagger.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A runtime binding that injects the constructor and fields of a class.
 */
final class AtInjectBinding<T> extends Binding<T> {
  private final Field[] fields;
  private final Constructor<T> constructor;
  private final Class<?> supertype;
  private final String[] keys;
  private Binding<?>[] fieldBindings;
  private Binding<?>[] parameterBindings;
  private Binding<? super T> supertypeBinding;

  /**
   * @param keys keys for the fields, constructor parameters and supertype in
   *     that order. These are precomputed to minimize reflection when {@code
   *     attach} is called multiple times.
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   * @param supertype the injectable supertype, or null if the supertype is a
   */
  private AtInjectBinding(String provideKey, String membersKey, boolean singleton, Class<?> type,
      Field[] fields, Constructor<T> constructor, int parameterCount, Class<?> supertype,
      String[] keys) {
    super(provideKey, membersKey, singleton, type);
    this.constructor = constructor;
    this.fields = fields;
    this.supertype = supertype;
    this.keys = keys;
    this.parameterBindings = new Binding<?>[parameterCount];
    this.fieldBindings = new Binding<?>[fields.length];
  }

  @SuppressWarnings("unchecked") // The linker promises it's safe to cast to Binding<? super T>.
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
      supertypeBinding = (Binding<? super T>) linker.requestBinding(keys[k], membersKey);
    }
  }

  @Override public T get() {
    if (constructor == null) {
      throw new UnsupportedOperationException();
    }
    Object[] args = new Object[parameterBindings.length];
    for (int i = 0; i < parameterBindings.length; i++) {
      args[i] = parameterBindings[i].get();
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
   * @param forMembersInjection true if the binding is being created to inject
   *     members only. Such injections do not require {@code @Inject}
   *     annotations.
   */
  public static <T> Binding<T> create(Class<T> type, boolean forMembersInjection) {
    boolean singleton = type.isAnnotationPresent(Singleton.class);
    List<String> keys = new ArrayList<String>();

    // Lookup the injectable fields and their corresponding keys.
    List<Field> injectedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (!field.isAnnotationPresent(Inject.class) || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        injectedFields.add(field);
        keys.add(Keys.get(field.getGenericType(), field.getAnnotations(), field));
      }
    }

    // Look up @Inject-annotated constructors. If there's no @Inject-annotated
    // constructor, use a default public constructor if the class has other
    // injections. Otherwise treat the class as non-injectable.
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

    int parameterCount;
    String provideKey;
    if (injectedConstructor != null) {
      provideKey = Keys.get(type);
      injectedConstructor.setAccessible(true);
      Type[] types = injectedConstructor.getGenericParameterTypes();
      parameterCount = types.length;
      if (parameterCount != 0) {
        Annotation[][] annotations = injectedConstructor.getParameterAnnotations();
        for (int p = 0; p < types.length; p++) {
          keys.add(Keys.get(types[p], annotations[p], injectedConstructor));
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
    return new AtInjectBinding<T>(provideKey, membersKey, singleton, type,
        injectedFields.toArray(new Field[injectedFields.size()]), injectedConstructor,
        parameterCount, supertype, keys.toArray(new String[keys.size()]));
  }
}
