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
import java.util.Map;
import java.util.Set;

/**
 * A {@code Binding<T>} which contains contributors (other bindings marked with
 * {@code @Provides} {@code @OneOf}), to which it delegates provision
 * requests on an as-needed basis.
 */
public final class SetBinding<T> extends Binding<Set<T>> {

  public static <T> void add(Map<String, Binding<?>> bindings, String setKey, Binding<?> binding) {
    prepareSetBinding(bindings, setKey, binding).contributors.add(Linker.scope(binding));
  }

  @SuppressWarnings("unchecked")
  private static <T> SetBinding<T> prepareSetBinding(
      Map<String, Binding<?>> bindings, String setKey, Binding<?> binding) {
    Binding<?> previous = bindings.get(setKey);
    SetBinding<T> setBinding;
    if (previous instanceof SetBinding) {
      setBinding = (SetBinding<T>) previous;
      setBinding.setLibrary(setBinding.library() && binding.library());
      return setBinding;
    } else if (previous != null) {
      throw new IllegalArgumentException("Duplicate:\n    " + previous + "\n    " + binding);
    } else {
      setBinding = new SetBinding<T>(setKey, binding.requiredBy);
      setBinding.setLibrary(binding.library());
      bindings.put(setKey, setBinding);
      return (SetBinding<T>) bindings.get(setKey); // BindingMap.put() copies SetBindings.
    }
  }

  private final Set<Binding<?>> contributors;

  /**
   * Creates a new {@code SetBinding} with the given "provides" key, and the requiredBy object
   * for traceability.
   */
  public SetBinding(String key, Object requiredBy) {
    super(key, null, false, requiredBy);
    contributors = new LinkedHashSet<Binding<?>>();
  }

  /**
   * Creates a new {@code SetBinding} with all of the contributing bindings of the provided
   * original {@code SetBinding}.
   */
  public SetBinding(SetBinding<T> original) {
    super(original.provideKey, null, false, original.requiredBy);
    this.setLibrary(original.library());
    this.setDependedOn(original.dependedOn());
    contributors = new LinkedHashSet<Binding<?>>(original.contributors);
  }

  @Override public void attach(Linker linker) {
    for (Binding<?> contributor : contributors) {
      contributor.attach(linker);
    }
  }

  @SuppressWarnings("unchecked") // Only Binding<T> and Set<T> are added to contributors.
  @Override public Set<T> get() {
    Set<T> result = new LinkedHashSet<T>(contributors.size());
    for (Binding<?> contributor : contributors) {
      Object contribution = contributor.get(); // Let runtime exceptions through.
      if (contributor.provideKey.equals(provideKey)) {
        result.addAll((Set<T>) contribution);
      } else {
        result.add((T) contribution);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  @Override public void getDependencies(
      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {
    getBindings.addAll(contributors);
  }

  @Override public void injectMembers(Set<T> t) {
    throw new UnsupportedOperationException("Cannot inject into a Set binding");
  }

  @Override public String toString() {
    return "SetBinding" + contributors;
  }
}
