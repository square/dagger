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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Provider;

/**
 * A {@link Factory} implementation used to implement {@link Map} bindings. This factory returns a
 * {@code Map<K, Provider<V>>} when calling {@link #get} (as specified by {@link Factory}).
 *
 * @author Chenying Hou
 * @since 2.0
 *
 */
public class MapProviderFactory<K, V> implements Factory<Map<K, Provider<V>>> {
  private final Map<K, Provider<V>> contributingMap;

  /**
   * Returns a new {@link Builder}
   */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<K, V>(size);
  }

  private MapProviderFactory(LinkedHashMap<K, Provider<V>> contributingMap) {
    this.contributingMap = Collections.unmodifiableMap(contributingMap);
  }

  /**
   * Returns a {@code Map<K, Provider<V>>} whose iteration order is that of the elements
   * given by each of the providers, which are invoked in the order given at creation.
   *
   */
  @Override
  public Map<K, Provider<V>> get() {
    return this.contributingMap;
  }

  /**
   * A builder to help build the {@link MapProviderFactory}
   */
  public static class Builder<K, V> {
    private final LinkedHashMap<K, Provider<V>> mapBuilder;

    private Builder(int size) {
      // TODO(user): consider which way to initialize mapBuilder is better
      this.mapBuilder = newLinkedHashMapWithExpectedSize(size);
    }

    /**
     * Returns a new {@link MapProviderFactory}
     */
    public MapProviderFactory<K, V> build() {
      return new MapProviderFactory<K, V>(this.mapBuilder);
    }

    /**
     * Associate k with providerOfValue in {@code Builder}
     */
    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      if (key == null) {
        throw new NullPointerException("The key is null");
      }
      if (providerOfValue == null) {
        throw new NullPointerException("The provider of the value is null");
      }

      this.mapBuilder.put(key, providerOfValue);
      return this;
    }

    private static <K, V> LinkedHashMap<K, Provider<V>> newLinkedHashMapWithExpectedSize(
        int expectedSize) {
      if (expectedSize < 0) {
        throw new IllegalArgumentException("The expected size of map cannot be negative.");
      }
      int initialCapacity = (expectedSize < 3) ? expectedSize + 1
          : (expectedSize < (1 << (Integer.SIZE - 2))) ? expectedSize + expectedSize / 3
              : Integer.MAX_VALUE;
      return new LinkedHashMap<K, Provider<V>>(initialCapacity);
    }
  }
}
