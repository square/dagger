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

import com.squareup.objectgraph.Module;
import com.squareup.objectgraph.Provides;
import com.squareup.objectgraph.internal.Binding;
import com.squareup.objectgraph.internal.Linker;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Performs full graph analysis on a module.
 */
@SupportedAnnotationTypes("com.squareup.objectgraph.Module")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class FullGraphProcessor extends AbstractProcessor {
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    Linker linker = new BuildTimeLinker(processingEnv);

    for (Element moduleType : env.getElementsAnnotatedWith(Module.class)) {
      Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, moduleType);

      // Gather the entry points from the annotation.
      for (Object entryPoint : (Object[]) annotation.get("entryPoints")) {
        linker.requestBinding(GeneratorKeys.rawMembersKey((TypeMirror) entryPoint),
            ((TypeElement) moduleType).getQualifiedName().toString());
      }

      // Gather the static injections.
      // TODO.

      // Gather the enclosed @Provides methods.
      for (Element enclosed : moduleType.getEnclosedElements()) {
        if (enclosed.getAnnotation(Provides.class) == null) {
          continue;
        }
        ExecutableElement providerMethod = (ExecutableElement) enclosed;
        String key = GeneratorKeys.get(providerMethod);
        linker.installBinding(key, new ProviderMethodBinding(key, providerMethod));
      }
    }

    // Link the bindings. This will traverse the dependency graph, and report
    // errors if any dependencies are missing.
    linker.linkAll();

    return true;
  }

  static class ProviderMethodBinding extends Binding<Object> {
    private final ExecutableElement method;
    protected ProviderMethodBinding(String provideKey, ExecutableElement method) {
      super(provideKey, null, method.getAnnotation(Singleton.class) != null, method.toString());
      this.method = method;
    }
    @Override public void attach(Linker linker) {
      for (VariableElement parameter : method.getParameters()) {
        String parameterKey = GeneratorKeys.get(parameter);
        linker.requestBinding(parameterKey, method.toString());
      }
    }
  }
}
