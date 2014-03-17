/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An abstract supertype that provides
 * <a href="http://en.wikipedia.org/wiki/Memoize">memoization</a> for idempotent operations that
 * <i>may</i> be computed more than once, but for which performance is prohibitive. Subclasses
 * implement the {@link #create} method with the operation to be memoized, while callers invoke the
 * {@link #get} method to utilize the memoization.
 *
 * <p>Synchronization on this class is implemented using a {@link ReadWriteLock}. Multiple threads
 * may accessed previously memoized results without contention.
 *
 * <p>This class is implemented such that concurrent requests for the same key may result in
 * simultaneous computation in multiple threads - the instance of the result that is persisted for
 * subsequent invocations in not guaranteed.
 *
 * <p><b>Warning:</b> there is no eviction. Large input sets will result in growth without bound.
 */
abstract class Memoizer<K, V> {
  private final Map<K, V> map;
  private final Lock readLock;
  private final Lock writeLock;

  Memoizer() {
    // Don't use LinkedHashMap. This is a performance-oriented class and we don't want overhead
    this.map = new HashMap<K, V>();
    ReadWriteLock lock = new ReentrantReadWriteLock();
    this.readLock = lock.readLock();
    this.writeLock = lock.writeLock();
  }

  final V get(K key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    // check to see if we already have a value
    readLock.lock();
    try {
      V value = map.get(key);
      if (value != null) {
        return value;
      }
    } finally {
      readLock.unlock();
    }

    // create a new value.  this may race and we might create more than one instance, but that's ok
    V newValue = create(key);
    if (newValue == null) {
      throw new NullPointerException("create returned null");
    }

    // write the new value and return it
    writeLock.lock();
    try {
      map.put(key, newValue);
      return newValue;
    } finally {
      writeLock.unlock();
    }
  }

  abstract V create(K key);

  @Override public final String toString() {
    readLock.lock();
    try {
      return map.toString();
    } finally {
      readLock.unlock();
    }
  }
}
