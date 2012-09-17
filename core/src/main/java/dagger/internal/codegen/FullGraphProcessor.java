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

import dagger.Module;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Linker;
import java.util.LinkedHashMap;
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
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Performs full graph analysis on a module.
 */
@SupportedAnnotationTypes("dagger.Module")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class FullGraphProcessor extends AbstractProcessor {
  /**
   * Perform full-graph analysis on complete modules. This checks that all of
   * the module's dependencies are satisfied.
   */
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    for (Element moduleType : env.getElementsAnnotatedWith(Module.class)) {
      Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, moduleType);
      if (annotation.get("complete").equals(Boolean.TRUE)) {
        validateComplete((TypeElement) moduleType);
      }
    }
    return true;
  }

  private void validateComplete(TypeElement rootModule) {
    Map<String, TypeElement> allModules = new LinkedHashMap<String, TypeElement>();
    collectIncludesRecursively(rootModule, allModules);

    Linker linker = new BuildTimeLinker(processingEnv, rootModule.getQualifiedName().toString());
    Map<String, ProviderMethodBinding> baseBindings
        = new LinkedHashMap<String, ProviderMethodBinding>();
    Map<String, ProviderMethodBinding> overrideBindings
        = new LinkedHashMap<String, ProviderMethodBinding>();
    for (TypeElement module : allModules.values()) {
      Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, module);
      boolean overrides = (Boolean) annotation.get("overrides");

      // Gather the entry points from the annotation.
      for (Object entryPoint : (Object[]) annotation.get("entryPoints")) {
        linker.requestBinding(GeneratorKeys.rawMembersKey((TypeMirror) entryPoint),
            module.getQualifiedName().toString());
      }

      // Gather the static injections.
      // TODO.

      // Gather the enclosed @Provides methods.
      for (Element enclosed : module.getEnclosedElements()) {
        if (enclosed.getAnnotation(Provides.class) == null) {
          continue;
        }
        ExecutableElement providerMethod = (ExecutableElement) enclosed;
        String key = GeneratorKeys.get(providerMethod);
        ProviderMethodBinding binding = new ProviderMethodBinding(key, providerMethod);
        Map<String, ProviderMethodBinding> addTo = overrides ? overrideBindings : baseBindings;
        ProviderMethodBinding clobbered = addTo.put(key, binding);
        if (clobbered != null) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
              "Duplicate bindings for " + key
                  + ": " + shortMethodName(clobbered.method)
                  + ", " + shortMethodName(binding.method));
        }
      }
    }

    linker.installBindings(baseBindings);
    linker.installBindings(overrideBindings);

    // Link the bindings. This will traverse the dependency graph, and report
    // errors if any dependencies are missing.
    linker.linkAll();
  }

  private String shortMethodName(ExecutableElement method) {
    return method.getEnclosingElement().getSimpleName().toString()
        + "." + method.getSimpleName() + "()";
  }

  private void collectIncludesRecursively(TypeElement module, Map<String, TypeElement> result) {
    // Add the module.
    result.put(module.getQualifiedName().toString(), module);

    // Recurse for each included module.
    Types typeUtils = processingEnv.getTypeUtils();
    Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, module);
    @SuppressWarnings("deprecation") // Use known deprecated method. TODO(cgruber): remove.
    Object[] includes = ArrayUtil.concatenate(
        (Object[]) annotation.get("includes"),
        (Object[]) annotation.get("children"));
    for (Object include : includes) {
      if (!(include instanceof TypeMirror)) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value for include: " + include + " in " + module);
        continue;
      }
      TypeElement includedModule = (TypeElement) typeUtils.asElement((TypeMirror) include);
      collectIncludesRecursively(includedModule, result);
    }
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
