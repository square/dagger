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

import com.squareup.injector.Provides;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper methods for dealing with collections of bindings. Any object whose
 * declaring class defines one or more {@code @Provides} is considered to be a
 * collection of bindings.
 *
 * @author Jesse Wilson
 */
final class Modules {
  private Modules() {
  }

  /**
   * Returns a module containing all bindings in {@code modules}.
   *
   * @throws IllegalArgumentException if any bindings are duplicated.
   */
  public static Map<String, Binding<?>> getBindings(Iterable<Object> modules) {
    UniqueMap<String, Binding<?>> result = new UniqueMap<String, Binding<?>>();
    for (Object module : modules) {
      extractBindings(module, result);
    }
    return result;
  }

  /**
   * Creates bindings for the {@code @Provides} methods of {@code module}. The
   * returned bindings are not attached to a particular injector and cannot be
   * used to inject values.
   */
  private static void extractBindings(Object module, UniqueMap<String, Binding<?>> bindings) {
    // First look for a generated ModuleAdapter.
    ModuleAdapter<Object> moduleAdapter = null;
    try {
      String adapter = module.getClass().getName() + "$ModuleAdapter";
      Class<?> c = Class.forName(adapter);
      Constructor<?> constructor = c.getConstructor();
      constructor.setAccessible(true);
      moduleAdapter = (ModuleAdapter) constructor.newInstance();
    } catch (Exception ignored) {
      // TODO: verbose log that code gen isn't enabled for this module
    }

    if (moduleAdapter != null) {
      moduleAdapter.getBindings(module, bindings);
      return;
    }

    // Fall back to runtime reflection.
    int count = 0;
    for (Class<?> c = module.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        if (!method.isAnnotationPresent(Provides.class)
            && !method.isAnnotationPresent(com.google.inject.Provides.class)) {
          continue;
        }
        count++;
        Binding<?> binding = methodToBinding(module, method);
        bindings.put(binding.provideKey, binding);
      }
    }
    if (count == 0) {
      throw new IllegalArgumentException("No @Provides methods on " + module);
    }
  }

  private static <T> Binding<T> methodToBinding(Object module, Method method) {
    String key = Keys.get(method.getGenericReturnType(), method.getAnnotations(), method);
    return new ProviderMethodBinding<T>(method, key, module);
  }

  /**
   * A map that fails when existing values are clobbered.
   */
  private static class UniqueMap<K, V> extends LinkedHashMap<K, V> {
    @Override public V put(K key, V value) {
      V clobbered = super.put(key, value);
      if (clobbered != null) {
        super.put(key, clobbered); // Put things back as they were.
        throw new IllegalArgumentException("Duplicate:\n    " + clobbered + "\n    " + value);
      }
      return null;
    }
    @Override public void putAll(Map<? extends K, ? extends V> map) {
      for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }
  }
}
