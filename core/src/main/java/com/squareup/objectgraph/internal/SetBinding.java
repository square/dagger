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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@code Binding<T>} which contains contributors (other bindings), to which it dispatches
 * provision requests on an as-needed basis.
 */
final class SetBinding<T> extends Binding<T> {
  private final Set<Binding<?>> contributors;

  public SetBinding(String key) {
    super(key, null, false, null);
    contributors = new HashSet<Binding<?>>();
  }

  @Override public void attach(Linker linker) {
    for (Binding<?> contributor : contributors) {
      contributor.attach(linker);
    }
  }

  @Override public T get() {
    Set<Object> volatileResult = new HashSet<Object>(contributors.size());
    for (Binding<?> contributor : contributors) {
      volatileResult.add(contributor.get()); // let runtime exceptions through.
    }
    return (T)Collections.unmodifiableSet(volatileResult);
  }

  @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    throw new UnsupportedOperationException("Doesn't support getDependencies()");
    //for (Binding binding : parameters) {
    //  get.add(binding);
    //}
  }

  @Override public String toString() {
    return new StringBuilder("SetBinding").append(contributors).toString();
  }

  public void add(Binding<?> unitaryBinding) {
    contributors.add(unitaryBinding);
  }
}
