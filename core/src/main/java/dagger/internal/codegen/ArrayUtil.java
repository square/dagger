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
package dagger.internal.codegen;


/**
 * A utility to provide Array utilities above and beyond what are provided in the
 * java.util.Arrays class.
 */
class ArrayUtil {
  /**
   * A class that returns the concatenation of two {@code Class<T>[]}s.
   *
   * TODO(cgruber): Remove this method when module children are removed if no other callers.
   *
   * @deprecated this method exists only to support a legacy deprecation case
   */
  @Deprecated
  static Object[] concatenate(Object[] first, Object[] second) {
    final Object[] result = new Object[second.length + first.length];
    System.arraycopy(second, 0, result, 0, second.length);
    System.arraycopy(first, 0, result, second.length, first.length);
    return result;
  }
}
