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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Detects problems like cyclic dependencies.
 */
public final class ProblemDetector {
  Set<Binding<?>> done = new HashSet<Binding<?>>();
  Queue<Binding<?>> roots = new LinkedList<Binding<?>>();
  List<Binding<?>> path = new LinkedList<Binding<?>>();

  public void detectProblems(Collection<Binding<?>> bindings) {
    roots.addAll(bindings);

    StringBuilder message = null;
    Binding<?> root;
    while ((root = roots.poll()) != null) {
      if (done.add(root)) {
        try {
          detectCircularDependencies(root);
        } catch (IllegalStateException e) {
          if (message == null) {
            message = new StringBuilder().append("Graph problems:");
          }
          message.append("\n  ").append(e.getMessage());
        }
      }
    }

    if (message != null) {
      throw new RuntimeException(message.toString());
    }
  }

  private void detectCircularDependencies(Binding<?> binding) {
    int index = path.indexOf(binding);
    if (index != -1) {
      StringBuilder message = new StringBuilder()
          .append("Dependency cycle:");
      for (int i = index; i < path.size(); i++) {
        message.append("\n    ").append(i - index).append(". ")
            .append(path.get(i).provideKey).append(" bound by ").append(path.get(i));
      }
      message.append("\n    ").append(0).append(". ").append(binding.provideKey);
      throw new IllegalStateException(message.toString());
    }

    path.add(binding);
    try {
      // TODO: perform 2-phase injection to avoid some circular dependency problems
      Set<Binding<?>> dependencies = new LinkedHashSet<Binding<?>>();
      binding.getDependencies(dependencies, dependencies);
      for (Binding<?> dependency : dependencies) {
        if (dependency instanceof BuiltInBinding) {
          roots.add(((BuiltInBinding<?>) dependency).getDelegate());
        } else {
          detectCircularDependencies(dependency);
        }
      }
    } finally {
      path.remove(path.size() - 1);
    }
  }
}
