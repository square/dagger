/*
 * Copyright (C) 2014 Google, Inc.
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

import dagger.Factory;
import javax.inject.Provider;

/**
 * A {@link Provider} implementation that memoizes the result of a {@link Factory} instance.
 *
 * @author Gregory Kick
 * @since 2.0
 */
public final class ScopedProvider<T> implements Provider<T> {
  private final Factory<T> factory;
  private volatile T instance = null;

  private ScopedProvider(Factory<T> factory) {
    assert factory != null;
    this.factory = factory;
  }

  @Override
  public T get() {
    // double-check idiom from EJ2: Item 71
    T result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          instance = result = factory.get();
          if (result == null) {
            throw new NullPointerException(factory + " returned null");
          }
        }
      }
    }
    return result;
  }

  /** Returns a new scoped provider for the given factory. */
  public static <T> Provider<T> create(Factory<T> factory) {
    if (factory == null) {
      throw new NullPointerException();
    }
    return new ScopedProvider<T>(factory);
  }
}
