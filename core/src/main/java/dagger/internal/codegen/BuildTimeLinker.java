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
import dagger.internal.Keys;
import dagger.internal.Linker;
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
  private final String moduleName;

  BuildTimeLinker(ProcessingEnvironment processingEnv, String moduleName) {
    this.processingEnv = processingEnv;
    this.moduleName = moduleName;
  }

  @Override protected Binding<?> createAtInjectBinding(String key, String className) {
    String sourceClassName = className.replace('$', '.');
    TypeElement type = processingEnv.getElementUtils().getTypeElement(sourceClassName);
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
    return AtInjectBinding.create(type, Keys.isMembersInjection(key));
  }

  @Override protected void reportErrors(List<String> errors) {
    for (String error : errors) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error + " for " + moduleName);
    }
  }
}
