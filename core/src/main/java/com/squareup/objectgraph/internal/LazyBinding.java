/*
 * Copyright (C) 2012 Google, Inc.
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
package com.squareup.objectgraph.internal;

import com.squareup.objectgraph.Lazy;

/**
 * Injects a Lazy wrapper for a type T
 */
final class LazyBinding<T> extends Binding<Lazy<T>> {

  private final static Object NOT_PRESENT = new Object();

  private final String lazyKey;
  private Binding<T> delegate;

  public LazyBinding(String key, Object requiredBy, String lazyKey) {
    super(key, null, false, requiredBy);
    this.lazyKey = lazyKey;
  }

  @SuppressWarnings("unchecked") // At runtime we know it's a Binding<Lazy<T>>.
  @Override
  public void attach(Linker linker) {
    delegate = (Binding<T>) linker.requestBinding(lazyKey, requiredBy);
  }

  @Override public void injectMembers(Lazy<T> t) {
    throw new UnsupportedOperationException(); // not a member injection binding.
  }

  @Override
  public Lazy<T> get() {
    return new Lazy<T>() {
      private Object cacheValue = NOT_PRESENT;

      @SuppressWarnings("unchecked") // Delegate is of type T
      @Override
      public T get() {
        return (T) ((cacheValue != NOT_PRESENT) ? cacheValue : (cacheValue = delegate.get()));
      }
    };
  }

}
