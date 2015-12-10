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

import com.google.common.annotations.VisibleForTesting;
import dagger.internal.Binding;
import dagger.internal.Loader;
import dagger.internal.ModuleAdapter;
import dagger.internal.StaticInjection;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A {@code Binding.Resolver} suitable for tool use at build time. The bindings created by
 * this {@code Binding.Resolver} have the correct dependency graph, but do not implement
 * {@link Binding#get} or {@link Binding#injectMembers} methods. They are only suitable
 * for graph analysis and error detection.
 */
public final class GraphAnalysisLoader extends Loader {

  private final ProcessingEnvironment processingEnv;

  public GraphAnalysisLoader(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override public Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader classLoader, boolean mustHaveInjections) {
    TypeElement type = resolveType(processingEnv.getElementUtils(), className);
    if (type == null) {
      // We've encountered a type that the compiler can't introspect. If this
      // causes problems in practice (due to incremental compiles, etc.) we
      // should return a new unresolved binding and warn about the possibility
      // of runtime failures.
      return null;
    }
    if (type.getKind() == ElementKind.INTERFACE) {
      return null;
    }
    return GraphAnalysisInjectBinding.create(type, mustHaveInjections);
  }

  /**
   * Resolves the given class name into a {@link TypeElement}. The class name is a binary name, but
   * {@link Elements#getTypeElement(CharSequence)} wants a canonical name. So this method searches
   * the space of possible canonical names, starting with the most likely (since '$' is rarely used
   * in canonical class names).
   */
  @VisibleForTesting static TypeElement resolveType(Elements elements, String className) {
    int index = nextDollar(className, className, 0);
    if (index == -1) {
      return getTypeElement(elements, className);
    }
    // have to test various possibilities of replacing '$' with '.' since '.' in a canonical name
    // of a nested type is replaced with '$' in the binary name.
    StringBuilder sb = new StringBuilder(className);
    return resolveType(elements, className, sb, index);
  }

  /**
   * Recursively explores the space of possible canonical names for a given binary class name.
   *
   * @param elements used to resolve a name into a {@link TypeElement}
   * @param className binary class name
   * @param sb the current permutation of canonical name to attempt to resolve
   * @param index the index of a {@code '$'} which may be changed to {@code '.'} in a canonical name
   */
  private static TypeElement resolveType(Elements elements, String className, StringBuilder sb,
      final int index) {

    // We assume '$' should be converted to '.'. So we search for classes with dots first.
    sb.setCharAt(index, '.');
    int nextIndex = nextDollar(className, sb, index + 1);
    TypeElement type = nextIndex == -1
        ? getTypeElement(elements, sb)
        : resolveType(elements, className, sb, nextIndex);
    if (type != null) {
      return type;
    }

    // if not found, change back to dollar and search.
    sb.setCharAt(index, '$');
    nextIndex = nextDollar(className, sb, index + 1);
    return nextIndex == -1
        ? getTypeElement(elements, sb)
        : resolveType(elements, className, sb, nextIndex);
  }

  /**
   * Finds the next {@code '$'} in a class name which can be changed to a {@code '.'} when computing
   * a canonical class name.
   */
  private static int nextDollar(String className, CharSequence current, int searchStart) {
    while (true) {
      int index = className.indexOf('$', searchStart);
      if (index == -1) {
        return -1;
      }
      // We'll never have two dots nor will a type name end or begin with dot. So no need to
      // consider dollars at the beginning, end, or adjacent to dots.
      if (index == 0 || index == className.length() - 1
          || current.charAt(index - 1) == '.' || current.charAt(index + 1) == '.') {
        searchStart = index + 1;
        continue;
      }
      return index;
    }
  }

  private static TypeElement getTypeElement(Elements elements, CharSequence className) {
    try {
      return elements.getTypeElement(className);
    } catch (ClassCastException e) {
      // work-around issue in javac in Java 7 where querying for non-existent type can
      // throw a ClassCastException
      // TODO(jh): refer to Oracle Bug ID if/when one is assigned to bug report
      // (Review ID: JI-9027367)
      return null;
    }
  }

  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<T> moduleClass) {
    throw new UnsupportedOperationException();
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    throw new UnsupportedOperationException();
  }
}
