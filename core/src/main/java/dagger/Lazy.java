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
package dagger;

/**
 * A value that is lazily returned. A {@code Lazy<T>} creates or obtains its underlying
 * value once, and caches that value thereafter.
 * <p>
 * Despite the similarity of these interfaces, {@code Lazy<T>} is semantically quite
 * distinct from {@code Provider<T>} which provides a new value on each call.
 */
public interface Lazy<T> {
  /**
   * Return the underlying value, creating the value (once) if needed. Any two calls will
   * return the same instance.
   */
  T get();
}
