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
import dagger.OneOf;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Linker;
import dagger.internal.SetBinding;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

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
    try {
      for (Element element : env.getElementsAnnotatedWith(Module.class)) {
        Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, element);
        if (!annotation.get("complete").equals(Boolean.TRUE)) {
          continue;
        }
        TypeElement moduleType = (TypeElement) element;
        Map<String, Binding<?>> bindings = processCompleteModule(moduleType);
        writeDotFile(moduleType, bindings);
      }
    } catch (IOException e) {
      error("Graph processing failed: " + e);
    }
    return true;
  }

  private void error(String message) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
  }

  private Map<String, Binding<?>> processCompleteModule(TypeElement rootModule) {
    Map<String, TypeElement> allModules = new LinkedHashMap<String, TypeElement>();
    collectIncludesRecursively(rootModule, allModules);

    Linker linker = new Linker(null, new CompileTimePlugin(processingEnv),
        new ReportingErrorHandler(processingEnv, rootModule.getQualifiedName().toString()));
    Map<String, Binding<?>> baseBindings = new LinkedHashMap<String, Binding<?>>();
    Map<String, Binding<?>> overrideBindings = new LinkedHashMap<String, Binding<?>>();
    for (TypeElement module : allModules.values()) {
      Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, module);
      boolean overrides = (Boolean) annotation.get("overrides");
      Map<String, Binding<?>> addTo = overrides ? overrideBindings : baseBindings;

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
        if (providerMethod.getAnnotation(OneOf.class) != null) {
          String elementKey = GeneratorKeys.getElementKey(providerMethod);
          SetBinding.add(addTo, elementKey, binding);
        } else {
          ProviderMethodBinding clobbered = (ProviderMethodBinding) addTo.put(key, binding);
          if (clobbered != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Duplicate bindings for " + key
                    + ": " + shortMethodName(clobbered.method)
                    + ", " + shortMethodName(binding.method));
          }
        }
      }
    }

    linker.installBindings(baseBindings);
    linker.installBindings(overrideBindings);

    // Link the bindings. This will traverse the dependency graph, and report
    // errors if any dependencies are missing.
    return linker.linkAll();
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
    List<Object> seedModules = new ArrayList<Object>();
    seedModules.addAll(Arrays.asList((Object[]) annotation.get("includes")));
    if (!annotation.get("augments").equals(Void.class)) seedModules.add(annotation.get("augments"));
    for (Object include : seedModules) {
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
    private final Binding<?>[] parameters;

    protected ProviderMethodBinding(String provideKey, ExecutableElement method) {
      super(provideKey, null, method.getAnnotation(Singleton.class) != null, method.toString());
      this.method = method;
      this.parameters = new Binding[method.getParameters().size()];
    }

    @Override public void attach(Linker linker) {
      for (int i = 0; i < method.getParameters().size(); i++) {
        VariableElement parameter = method.getParameters().get(i);
        String parameterKey = GeneratorKeys.get(parameter);
        parameters[i] = linker.requestBinding(parameterKey, method.toString());
      }
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      for (Binding<?> binding : parameters) {
        get.add(binding);
      }
    }
  }

  void writeDotFile(TypeElement module, Map<String, Binding<?>> bindings) throws IOException {
    JavaFileManager.Location location = StandardLocation.SOURCE_OUTPUT;
    String path = CodeGen.getPackage(module).getQualifiedName().toString();
    String file = module.getQualifiedName().toString().substring(path.length() + 1) + ".dot";
    FileObject resource = processingEnv.getFiler().createResource(location, path, file, module);

    Writer writer = resource.openWriter();
    DotWriter dotWriter = new DotWriter(writer);
    new GraphVisualizer().write(bindings, dotWriter);
    dotWriter.close();
  }
}
