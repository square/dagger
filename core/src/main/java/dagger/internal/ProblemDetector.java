/*
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

import dagger.internal.plugins.AbstractProviderMethodBinding;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Detects problems like cyclic dependencies.
 */
public final class ProblemDetector {
  public void detectCircularDependencies(Collection<Binding<?>> bindings) {
    detectCircularDependencies(bindings, new ArrayList<Binding<?>>());
  }

  public void detectUnusedBinding(Collection<Binding<?>> bindings) {
    ArrayList<Binding> unusedBindings = new ArrayList<Binding>();
    for (Binding<?> binding : bindings) {
      if (!binding.library() && !binding.dependedOn()) {
        unusedBindings.add(binding);
      }
    }
    if (unusedBindings.size() > 0) {
      StringBuilder builder = new StringBuilder();
      builder.append("You have these unused @Provider methods! ");
      builder.append("Set library=true in your module to disable this check.\n");
      for (Binding<?> binding : unusedBindings) {
        AbstractProviderMethodBinding<?> providerBinding =
            (AbstractProviderMethodBinding<?>) binding;
        builder.append(providerBinding.getModuleName())
            .append(".")
            .append(providerBinding.getMethodName())
            .append("()\n");
      }
      throw new IllegalStateException(builder.toString());
    }
  }

  private static void detectCircularDependencies(Collection<Binding<?>> bindings,
      List<Binding<?>> path) {
    for (Binding<?> binding : bindings) {
      if (binding.isCycleFree()) {
        continue;
      }

      if (binding.isVisiting()) {
        int index = path.indexOf(binding);
        StringBuilder message = new StringBuilder()
            .append("Dependency cycle:");
        for (int i = index; i < path.size(); i++) {
          message.append("\n    ").append(i - index).append(". ")
              .append(path.get(i).provideKey).append(" bound by ").append(path.get(i));
        }
        message.append("\n    ").append(0).append(". ").append(binding.provideKey);
        throw new IllegalStateException(message.toString());
      }

      binding.setVisiting(true);
      path.add(binding);
      try {
        ArraySet<Binding<?>> dependencies = new ArraySet<Binding<?>>();
        binding.getDependencies(dependencies, dependencies);
        detectCircularDependencies(dependencies, path);
        binding.setCycleFree(true);
      } finally {
        path.remove(path.size() - 1);
        binding.setVisiting(false);
      }
    }
  }

  public void detectProblems(Collection<Binding<?>> values) {
    detectCircularDependencies(values);
    detectUnusedBinding(values);
  }

  static class ArraySet<T> extends AbstractSet<T> {
    private final ArrayList<T> list = new ArrayList<T>();

    @Override public boolean add(T t) {
      list.add(t);
      return true;
    }

    @Override public Iterator<T> iterator() {
      return list.iterator();
    }

    @Override public int size() {
      throw new UnsupportedOperationException();
    }
  }
}
