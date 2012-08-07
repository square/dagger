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
package com.squareup.objectgraph.internal.codegen;

import com.squareup.objectgraph.internal.Binding;
import com.squareup.objectgraph.internal.Linker;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Linker suitable for tool use at build time. The bindings created by this
 * linker have the correct dependency graph, but do not implement {@link
 * Binding#get} or {@link Binding#injectMembers} methods. They are only suitable
 * for graph analysis and error detection.
 */
final class BuildTimeLinker extends Linker {
  private final ProcessingEnvironment processingEnv;

  /** Classes the compiler was unable to introspect. */
  private final List<String> unavailableClasses = new ArrayList<String>();

  BuildTimeLinker(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override protected Binding<?> createAtInjectBinding(String key, String className) {
    TypeElement type = processingEnv.getElementUtils().getTypeElement(className);
    if (type == null) {
      // We've encountered a type that the compiler can't introspect. Remember
      // the class name so we can warn about it later.
      unavailableClasses.add(className);
      return Binding.UNRESOLVED;
    }
    if (type.getKind() == ElementKind.INTERFACE) {
      return null;
    }
    return AtInjectBinding.create(type);
  }

  @Override protected void reportErrors(List<String> errors) {
    if (!unavailableClasses.isEmpty()) {
      String warning = String.format("%s and %d other classes were not available. Runtime failures "
          + "are possible!", unavailableClasses.get(0), unavailableClasses.size() - 1);
      processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, warning);
    }
    for (String error : errors) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error);
    }
  }
}
