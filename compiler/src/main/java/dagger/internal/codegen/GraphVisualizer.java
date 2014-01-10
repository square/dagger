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
package dagger.internal.codegen;

import dagger.internal.Binding;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emits an object graph in dot format.
 */
public final class GraphVisualizer {
  private static final Pattern KEY_PATTERN = Pattern.compile(""
      + "(?:@"            // Full annotation start.
      + "(?:[\\w$]+\\.)*" // Annotation package
      + "([\\w$]+)"       // Annotation simple name. Group 1.
      + "(?:\\(.*\\))?"   // Annotation arguments
      + "/)?"             // Full annotation end.
      + "(?:members/)?"   // Members prefix.
      + "(?:[\\w$]+\\.)*" // Type package.
      + "([\\w$]+)"       // Type simple name. Group 2.
      + "(\\<[^/]+\\>)?"  // Type parameters. Group 3.
      + "((\\[\\])*)"     // Arrays. Group 4.
      + "");

  public void write(Map<String, Binding<?>> bindings, GraphVizWriter writer) throws IOException {
    Map<Binding<?>, String> namesIndex = buildNamesIndex(bindings);

    writer.beginGraph("concentrate", "true");
    for (Map.Entry<Binding<?>, String> entry : namesIndex.entrySet()) {
      Binding<?> sourceBinding = entry.getKey();
      String sourceName = entry.getValue();
      Set<Binding<?>> dependencies = new TreeSet<Binding<?>>(new BindingComparator());
      sourceBinding.getDependencies(dependencies, dependencies);
      for (Binding<?> targetBinding : dependencies) {
        String targetName = namesIndex.get(targetBinding);
        if (targetName == null) {
          targetName = "Unbound:" + targetBinding.provideKey;
        }
        writer.edge(sourceName, targetName);
      }
    }
    writer.endGraph();
  }

  private Map<Binding<?>, String> buildNamesIndex(Map<String, Binding<?>> bindings) {
    // Optimistically shorten each binding to the class short name; remembering collisions.
    Map<String, Binding<?>> shortNameToBinding = new TreeMap<String, Binding<?>>();
    Set<Binding<?>> collisions = new HashSet<Binding<?>>();
    for (Map.Entry<String, Binding<?>> entry : bindings.entrySet()) {
      String key = entry.getKey();
      Binding<?> binding = entry.getValue();
      String shortName = shortName(key);
      Binding<?> collision = shortNameToBinding.put(shortName, binding);
      if (collision != null && collision != binding) {
        collisions.add(binding);
        collisions.add(collision);
      }
    }

    // Replace collisions with full names.
    for (Map.Entry<String, Binding<?>> entry : bindings.entrySet()) {
      Binding<?> binding = entry.getValue();
      if (collisions.contains(binding)) {
        String key = entry.getKey();
        String shortName = shortName(key);
        shortNameToBinding.remove(shortName);
        shortNameToBinding.put(key, binding);
      }
    }

    // Reverse the map.
    Map<Binding<?>, String> bindingToName = new LinkedHashMap<Binding<?>, String>();
    for (Map.Entry<String, Binding<?>> entry : shortNameToBinding.entrySet()) {
      bindingToName.put(entry.getValue(), entry.getKey());
    }

    return bindingToName;
  }

  String shortName(String key) {
    Matcher matcher = KEY_PATTERN.matcher(key);
    if (!matcher.matches()) throw new IllegalArgumentException("Unexpected key: " + key);
    StringBuilder result = new StringBuilder();

    String annotationSimpleName = matcher.group(1);
    if (annotationSimpleName != null) {
      result.append('@').append(annotationSimpleName).append(' ');
    }

    String simpleName = matcher.group(2);
    result.append(simpleName);

    String typeParameters = matcher.group(3);
    if (typeParameters != null) {
      result.append(typeParameters);
    }

    String arrays = matcher.group(4);
    if (arrays != null) {
      result.append(arrays);
    }

    return result.toString();
  }

  /** A Comparator for Bindings so we can insure a consistent ordering of output. */
  private static class BindingComparator implements Comparator<Binding<?>> {
    @Override
    public int compare(Binding<?> left, Binding<?> right) {
      return getStringForBinding(left).compareTo(getStringForBinding(right));
    }

    private String getStringForBinding(Binding<?> binding) {
      return binding == null ? "" : binding.toString();
    }
  }
}
