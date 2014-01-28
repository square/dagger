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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@code Binding<T>} which contains contributors (other bindings marked with
 * {@code @Provides} {@code @OneOf}), to which it delegates provision
 * requests on an as-needed basis.
 */
public final class SetBinding<T> extends Binding<Set<T>> {

  public static <T> void add(BindingsGroup bindings, String setKey, Binding<?> binding) {
    prepareSetBinding(bindings, setKey, binding).contributors.add(Linker.scope(binding));
  }

  @SuppressWarnings("unchecked")
  private static <T> SetBinding<T> prepareSetBinding(
      BindingsGroup bindings, String setKey, Binding<?> binding) {
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
      bindings.contributeSetBinding(setKey, setBinding);
      return (SetBinding<T>) bindings.get(setKey); // BindingMap.put() copies SetBindings.
    }
  }

  /**
   * A {@link SetBinding} with whose contributing bindings this set-binding provides a union
   * view.
   */
  private final SetBinding<T> parent;

  /**
   * A {@link Set} of {@link Binding} instances which contribute values to the injected set.
   */
  private final List<Binding<?>> contributors;

  /**
   * Creates a new {@code SetBinding} with the given "provides" key, and the requiredBy object
   * for traceability.
   */
  public SetBinding(String key, Object requiredBy) {
    super(key, null, false, requiredBy);
    parent = null;
    contributors = new ArrayList<Binding<?>>();
  }

  /**
   * Creates a new {@code SetBinding} with all of the contributing bindings of the provided
   * original {@code SetBinding}.
   */
  public SetBinding(SetBinding<T> original) {
    super(original.provideKey, null, false, original.requiredBy);
    parent = original;
    this.setLibrary(original.library());
    this.setDependedOn(original.dependedOn());
    contributors = new ArrayList<Binding<?>>();
  }

  @Override public void attach(Linker linker) {
    for (Binding<?> contributor : contributors) {
      contributor.attach(linker);
    }
  }

  public int size() {
    int size = 0;
    for (SetBinding<T> binding = this; binding != null; binding = binding.parent) {
      size += binding.contributors.size();
    }
    return size;
  }

  @SuppressWarnings("unchecked") // Only Binding<T> and Set<T> are added to contributors.
  @Override public Set<T> get() {
    List<T> result = new ArrayList<T>();
    for (SetBinding<T> setBinding = this; setBinding != null; setBinding = setBinding.parent) {
      for (int i = 0, size = setBinding.contributors.size(); i < size; i++) {
        Binding<?> contributor = setBinding.contributors.get(i);
        Object contribution = contributor.get(); // Let runtime exceptions through.
        if (contributor.provideKey.equals(provideKey)) {
          result.addAll((Set<T>) contribution);
        } else {
          result.add((T) contribution);
        }
      }
    }
    return Collections.unmodifiableSet(new LinkedHashSet<T>(result));
  }

  @Override public void getDependencies(
      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {
    for (SetBinding<T> binding = this; binding != null; binding = binding.parent) {
      getBindings.addAll(binding.contributors);
    }
  }

  @Override public void injectMembers(Set<T> t) {
    throw new UnsupportedOperationException("Cannot inject members on a contributed Set<T>.");
  }

  @Override public String toString() {
    boolean first = true;
    StringBuilder builder = new StringBuilder("SetBinding[");
    for (SetBinding<T> setBinding = this; setBinding != null; setBinding = setBinding.parent) {
      for (int i = 0, size = setBinding.contributors.size(); i < size; i++) {
        if (!first) {
          builder.append(",");
        }
        builder.append(setBinding.contributors.get(i));
        first = false;
      }
    }
    builder.append("]");
    return builder.toString();
  }
}
