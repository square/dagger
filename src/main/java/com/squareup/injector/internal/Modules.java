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
import java.lang.reflect.Method;
import java.util.HashMap;
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
  public static Map<String, Binding<?>> getBindings(Object... modules) {
    Map<String, Binding<?>> result = new HashMap<String, Binding<?>>();
    int expectedSize = 0;
    for (Object module : modules) {
      Map<String, Binding<?>> moduleBindings = extractBindings(module);
      expectedSize += moduleBindings.size();
      result.putAll(moduleBindings);
    }
    if (result.size() != expectedSize) {
      throw new IllegalArgumentException("Duplicate bindings!");
    }
    return result;
  }

  /**
   * Creates bindings for the {@code @Provides} methods of {@code module}. The
   * returned bindings are not attached to a particular injector and cannot be
   * used to inject values.
   */
  private static Map<String, Binding<?>> extractBindings(Object module) {
    Map<String, Binding<?>> result = new HashMap<String, Binding<?>>();
    for (Class<?> c = module.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        if (method.getAnnotation(Provides.class) == null
            && method.getAnnotation(com.google.inject.Provides.class) == null) {
          continue;
        }
        Binding<Object> binding = methodToBinding(module, method);
        result.put(binding.key, binding);
      }
    }
    if (result.isEmpty()) {
      throw new IllegalArgumentException("No @Provides methods on " + module);
    }
    return result;
  }

  private static <T> Binding<T> methodToBinding(Object module, Method method) {
    String key = Keys.get(method.getGenericReturnType(), method.getAnnotations(), method);
    return new ProviderMethodBinding<T>(method, key, module);
  }
}
