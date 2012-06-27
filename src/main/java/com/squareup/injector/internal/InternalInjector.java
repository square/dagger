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
package com.squareup.injector.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the injector, with internal APIs for use by the reflective
 * and code gen implementation.
 *
 * @author Jesse Wilson
 */
public final class InternalInjector {
  private static final Object UNINITIALIZED = new Object();

  /** All errors encountered during injection. */
  private final List<String> errors = new ArrayList<String>();

  /** All of the injector's bindings. */
  private final Map<String, Binding<?>> bindings = new HashMap<String, Binding<?>>();

  public Object inject(String key) {
    Linker linker = new Linker(this);
    linker.requestBinding(key, "root injection"); // Seed this requirement early.
    linker.link(bindings.values());

    if (!errors.isEmpty()) {
      StringBuilder message = new StringBuilder();
      message.append("Errors creating injector:");
      for (String error : errors) {
        message.append("\n  ").append(error);
      }
      throw new IllegalArgumentException(message.toString());
    }

    Binding<?> root = linker.requestBinding(key, "root injection");
    return root.get(); // Linker.link() guarantees that this will be non-null.
  }

  Binding<?> getBinding(String key) {
    return bindings.get(key);
  }

  public <T> void putBinding(final Binding<T> binding) {
    Binding<T> toInsert = binding;
    if (binding.isSingleton()) {
      toInsert = new Binding<T>(binding.requiredBy, binding.key) {
        private Object onlyInstance = UNINITIALIZED;
        @Override public void attach(Linker linker) {
          binding.attach(linker);
        }
        @Override public void injectMembers(T t) {
          binding.injectMembers(t);
        }
        @SuppressWarnings("unchecked") // 'onlyInstance is either UNINITIALIZED' or a 'T'.
        @Override public T get() {
          if (onlyInstance == UNINITIALIZED) {
            onlyInstance = binding.get();
          }
          return (T) onlyInstance;
        }
        @Override public boolean isSingleton() {
          return binding.isSingleton();
        }
      };
    }

    if (bindings.put(toInsert.key, toInsert) != null) {
      throw new IllegalArgumentException("Duplicate binding: " + toInsert.key);
    }
  }

  void addError(String message) {
    errors.add(message);
  }
}
