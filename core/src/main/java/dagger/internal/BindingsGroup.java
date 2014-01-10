/*
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A grouping of bindings that fails when existing values are clobbered, to be used in collecting
 * the initial set of bindings for a graph (from provides methods).
 */
public abstract class BindingsGroup {
  private final Map<String, Binding<?>> bindings = new LinkedHashMap<String, Binding<?>>();

  public abstract Binding<?> contributeSetBinding(String key, SetBinding<?> value);

  public Binding<?> contributeProvidesBinding(String key, ProvidesBinding<?> value) {
    return put(key, value);
  }

  protected Binding<?> put(String key, Binding<?> value) {
    Binding<?> clobbered = bindings.put(key, value);
    if (clobbered != null) {
      bindings.put(key, clobbered); // Put things back as they were.
      throw new IllegalArgumentException("Duplicate:\n    " + clobbered + "\n    " + value);
    }
    return null;
  }

  public Binding<?> get(String key) {
    return bindings.get(key);
  }

  public final Set<Entry<String, Binding<?>>> entrySet() {
    return bindings.entrySet();
  }

  @Override public String toString() {
    return getClass().getSimpleName() + bindings.toString();
  }
}
