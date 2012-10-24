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

import dagger.Module;
import dagger.OneOf;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import dagger.internal.ModuleAdapter;
import dagger.internal.SetBinding;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

public class ReflectiveModuleAdapter extends ModuleAdapter<Object> {
  final Class<?> moduleClass;

  public ReflectiveModuleAdapter(Class<?> moduleClass, Module annotation) {
    super(
        toKeys(annotation.entryPoints()),
        annotation.staticInjections(),
        annotation.overrides(),
        annotation.includes(),
        annotation.complete());
    this.moduleClass = moduleClass;
  }

  private static String[] toKeys(Class<?>[] entryPoints) {
    String[] result = new String[entryPoints.length];
    for (int i = 0; i < entryPoints.length; i++) {
      result[i] = Keys.get(entryPoints[i]);
    }
    return result;
  }

  @Override public void getBindings(Map<String, Binding<?>> bindings) {
    // Fall back to runtime reflection.
    for (Class<?> c = moduleClass; c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Provides.class)) {
          String key = Keys.get(method.getGenericReturnType(), method.getAnnotations(), method);
          if (method.isAnnotationPresent(OneOf.class)) {
            handleSetBindings(bindings, method, key);
          } else {
            handleBindings(bindings, method, key);
          }
        }
      }
    }
  }

  private <T> void handleBindings(Map<String, Binding<?>> bindings, Method method, String key) {
    bindings.put(key, new ProviderMethodBinding<T>(method, key, module));
  }

  private <T> void handleSetBindings(Map<String, Binding<?>> bindings, Method method, String key) {
    String elementKey =
        Keys.getElementKey(method.getGenericReturnType(), method.getAnnotations(), method);
    SetBinding.<T>add(bindings, elementKey, new ProviderMethodBinding<T>(method, key, module));
  }

  @Override protected Object newModule() {
    if (moduleClass.isInterface()) {
      throw new IllegalStateException(moduleClass.getSimpleName() + " is an interface.");
    }
    try {
      try {
        Constructor<?> includeConstructor = moduleClass.getDeclaredConstructor();
        includeConstructor.setAccessible(true);
        return includeConstructor.newInstance();
      } catch (NoSuchMethodException e) {
        return moduleClass.newInstance();
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to instantiate " + moduleClass.getName(), e);
    }
  }

  /**
   * Invokes a method to provide a value. The method's parameters are injected.
   */
  private final class ProviderMethodBinding<T> extends Binding<T> {
    private Binding<?>[] parameters;
    private final Method method;
    private final Object instance;

    public ProviderMethodBinding(Method method, String key, Object instance) {
      super(key, null, method.isAnnotationPresent(Singleton.class), method);
      this.method = method;
      this.instance = instance;
      method.setAccessible(true);
    }

    @Override public void attach(Linker linker) {
      Type[] types = method.getGenericParameterTypes();
      Annotation[][] annotations = method.getParameterAnnotations();
      parameters = new Binding[types.length];
      for (int i = 0; i < parameters.length; i++) {
        String key = Keys.get(types[i], annotations[i], method + " parameter " + i);
        parameters[i] = linker.requestBinding(key, method);
      }
    }

    @Override public T get() {
      Object[] args = new Object[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        args[i] = parameters[i].get();
      }
      try {
        return (T) method.invoke(instance, args);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e.getCause());
      }
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      for (Binding<?> binding : parameters) {
        get.add(binding);
      }
    }

    @Override public String toString() {
      return method.toString();
    }
  }
}