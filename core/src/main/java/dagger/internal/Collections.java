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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

final class Collections {
  private Collections() {
  }

  /**
   * Creates a {@link LinkedHashSet} instance, with a high enough "initial capacity" that it
   * <em>should</em> hold {@code expectedSize} elements without growth. The load factor is set at
   * {@code 1f} under the assumption this will be filled and wrapped in an unmodifiable wrapper.
   */
  static <E> LinkedHashSet<E> newLinkedHashSetWithExpectedSize(int expectedSize) {
    return new LinkedHashSet<E>(calculateInitialCapacity(expectedSize), 1f);
  }

  /**
   * Creates a {@link LinkedHashMap} instance, with a high enough "initial capacity" that it
   * <em>should</em> hold {@code expectedSize} elements without growth. The load factor is set at
   * {@code 1f} under the assumption this will be filled and wrapped in an unmodifiable wrapper.
   */
  static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return new LinkedHashMap<K, V>(calculateInitialCapacity(expectedSize), 1f);
  }

  private static int calculateInitialCapacity(int expectedSize) {
    return (expectedSize < 3)
        ? expectedSize + 1
        : (expectedSize < (1 << (Integer.SIZE - 2)))
            ? expectedSize + expectedSize / 3
            : Integer.MAX_VALUE;
  }
}
