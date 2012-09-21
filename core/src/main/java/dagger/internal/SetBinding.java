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
package dagger.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@code Binding<T>} which contains contributors (other bindings marked with
 * {@code @Provides} {@code @Element}), to which it delegates provision
 * requests on an as-needed basis.
 */
final class SetBinding<T> extends Binding<Set<T>> {
  private final Set<Binding<?>> contributors = new LinkedHashSet<Binding<?>>();

  public SetBinding(String key) {
    super(key, null, false, null);
  }

  @Override public void attach(Linker linker) {
    for (Binding<?> contributor : contributors) {
      contributor.attach(linker);
    }
  }

  @Override public Set<T> get() {
    Set<T> result = new LinkedHashSet<T>(contributors.size());
    for (Binding<?> contributor : contributors) {
      result.add((T) contributor.get()); // Let runtime exceptions through.
    }
    return Collections.unmodifiableSet(result);
  }

  @Override public String toString() {
    return "SetBinding" + contributors;
  }

  public void add(Binding<?> binding) {
    contributors.add(binding);
  }
}
