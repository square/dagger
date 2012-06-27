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
public final class Modules {
  /**
   * Returns a map containing the bindings in {@code object}.
   *
   * @param  module either a {@code map} of bindings, or an instance of a class
   *     that declares one or more {@code @Provides} methods.
   */
  static Map<Key<?>, Binding<?>> moduleToMap(Object module) {
    if (module instanceof Map) {
      return (Map<Key<?>, Binding<?>>) module;
    }
    return extractBindings(module);
  }

  /**
   * Creates bindings for the {@code @Provides} methods of {@code module}. The
   * returned bindings are not attached to a particular injector and cannot be
   * used to inject values.
   */
  private static Map<Key<?>, Binding<?>> extractBindings(Object module) {
    Map<Key<?>, Binding<?>> result = new HashMap<Key<?>, Binding<?>>();
    for (Class<?> c = module.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        if (method.getAnnotation(Provides.class) == null) {
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
    Key<T> key = Key.get(method.getGenericReturnType(), method.getAnnotations(), method);
    return new ProviderMethodBinding<T>(method, key, module);
  }

  /**
   * Returns a module containing the union of the bindings of {@code base} and
   * the bindings of {@code overrides}. If any key is represented in both
   * modules, the binding from {@code overrides} is retained.
   */
  public static Object override(Object base, Object overrides) {
    Map<Key<?>, Binding<?>> result = new HashMap<Key<?>, Binding<?>>();
    result.putAll(moduleToMap(base));
    result.putAll(moduleToMap(overrides));
    return result;
  }

  /**
   * Returns a module containing all bindings in {@code modules}.
   */
  public static Object combine(Object... modules) {
    Map<Key<?>, Binding<?>> result = new HashMap<Key<?>, Binding<?>>();
    int expectedSize = 0;
    for (Object module : modules) {
      Map<Key<?>, Binding<?>> moduleBindings = moduleToMap(module);
      expectedSize += moduleBindings.size();
      result.putAll(moduleBindings);
    }
    if (result.size() != expectedSize) {
      throw new IllegalArgumentException("Duplicate bindings!");
    }
    return result;
  }
}
