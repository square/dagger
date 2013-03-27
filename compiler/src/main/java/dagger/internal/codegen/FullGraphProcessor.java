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
import dagger.internal.ProblemDetector;
import dagger.internal.SetBinding;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
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
public final class FullGraphProcessor extends AbstractProcessor {
  private final Set<String> delayedModuleNames = new LinkedHashSet<String>();

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Perform full-graph analysis on complete modules. This checks that all of
   * the module's dependencies are satisfied.
   */
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    if (!env.processingOver()) {
      // Storing module names for later retrieval as the element instance is invalidated across
      // passes.
      for (Element e : env.getElementsAnnotatedWith(Module.class)) {
        if (!(e instanceof TypeElement)) {
          error("@Module applies to a type, " + e.getSimpleName() + " is a " + e.getKind(), e);
          continue;
        }
        delayedModuleNames.add(((TypeElement) e).getQualifiedName().toString());
      }
      return true;
    }

    Set<Element> modules = new LinkedHashSet<Element>();
    for (String moduleName : delayedModuleNames) {
      modules.add(processingEnv.getElementUtils().getTypeElement(moduleName));
    }

    for (Element element : modules) {
      Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, element);
      if (!annotation.get("complete").equals(Boolean.TRUE)) {
        continue;
      }
      TypeElement moduleType = (TypeElement) element;
      Map<String, Binding<?>> bindings = null;
      try {
        bindings = processCompleteModule(moduleType);
        new ProblemDetector().detectProblems(bindings.values());
      } catch (ModuleValidationException e) {
        error("Graph validation failed: " + e.getMessage(), e.source);
        continue;
      } catch (IllegalStateException e) {
        error("Graph validation failed: " + e.getMessage(), moduleType);
        continue;
      }
      try {
        writeDotFile(moduleType, bindings);
      } catch (IOException e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        processingEnv.getMessager()
            .printMessage(Diagnostic.Kind.WARNING,
                "Graph visualization failed. Please report this as a bug.\n\n" + sw, moduleType);
      }
    }
    return true;
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  private Map<String, Binding<?>> processCompleteModule(TypeElement rootModule) {
    Map<String, TypeElement> allModules = new LinkedHashMap<String, TypeElement>();
    collectIncludesRecursively(rootModule, allModules, new LinkedList<String>());

    Linker linker = new Linker(null, new CompileTimePlugin(processingEnv),
        new ReportingErrorHandler(processingEnv, rootModule.getQualifiedName().toString()));
    // Linker requires synchronization for calls to requestBinding and linkAll.
    // We know statically that we're single threaded, but we synchronize anyway
    // to make the linker happy.
    synchronized (linker) {
      Map<String, Binding<?>> baseBindings = new LinkedHashMap<String, Binding<?>>();
      Map<String, Binding<?>> overrideBindings = new LinkedHashMap<String, Binding<?>>();
      for (TypeElement module : allModules.values()) {
        Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, module);
        boolean overrides = (Boolean) annotation.get("overrides");
        Map<String, Binding<?>> addTo = overrides ? overrideBindings : baseBindings;

        // Gather the entry points from the annotation.
        for (Object entryPoint : (Object[]) annotation.get("entryPoints")) {
          linker.requestBinding(GeneratorKeys.rawMembersKey((TypeMirror) entryPoint),
              module.getQualifiedName().toString(), false);
        }

        // Gather the static injections.
        // TODO.

        // Gather the enclosed @Provides methods.
        for (Element enclosed : module.getEnclosedElements()) {
          Provides provides = enclosed.getAnnotation(Provides.class);
          if (provides == null) {
            continue;
          }
          ExecutableElement providerMethod = (ExecutableElement) enclosed;
          String key = GeneratorKeys.get(providerMethod);
          ProviderMethodBinding binding = new ProviderMethodBinding(key, providerMethod);
          switch (provides.type()) {
            case UNIQUE:
              ProviderMethodBinding clobbered = (ProviderMethodBinding) addTo.put(key, binding);
              if (clobbered != null) {
                String msg = "Duplicate bindings for " + key;
                if (overrides) {
                  msg += " in override module(s) - cannot override an override";
                }
                msg += ": " + shortMethodName(clobbered.method)
                    + ", " + shortMethodName(binding.method);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg,
                    binding.method);
              }
              break;

            case SET:
              String elementKey = GeneratorKeys.getElementKey(providerMethod);
              SetBinding.add(addTo, elementKey, binding);
              break;

            default:
              throw new AssertionError("Unknown @Provides type " + provides.type());
          }
        }
      }

      linker.installBindings(baseBindings);
      linker.installBindings(overrideBindings);

      // Link the bindings. This will traverse the dependency graph, and report
      // errors if any dependencies are missing.
      return linker.linkAll();
    }
  }

  private String shortMethodName(ExecutableElement method) {
    return method.getEnclosingElement().getSimpleName().toString()
        + "." + method.getSimpleName() + "()";
  }

  void collectIncludesRecursively(
      TypeElement module, Map<String, TypeElement> result, Deque<String> path) {
    Map<String, Object> annotation = CodeGen.getAnnotation(Module.class, module);
    if (annotation == null) {
      // TODO(tbroyer): pass annotation information
      throw new ModuleValidationException("No @Module on " + module, module);
    }

    // Add the module.
    String name = module.getQualifiedName().toString();
    if (path.contains(name)) {
      StringBuilder message = new StringBuilder("Module Inclusion Cycle: ");
      if (path.size() == 1) {
        message.append(name).append(" includes itself directly.");
      } else {
        String current = null;
        String includer = name;
        for (int i = 0; path.size() > 0; i++) {
          current = includer;
          includer = path.pop();
          message.append("\n").append(i).append(". ")
              .append(current).append(" included by ").append(includer);
        }
        message.append("\n0. ").append(name);
      }
      throw new ModuleValidationException(message.toString(), module);
    }
    result.put(name, module);

    // Recurse for each included module.
    Types typeUtils = processingEnv.getTypeUtils();
    List<Object> seedModules = new ArrayList<Object>();
    seedModules.addAll(Arrays.asList((Object[]) annotation.get("includes")));
    if (!annotation.get("addsTo").equals(Void.class)) seedModules.add(annotation.get("addsTo"));
    for (Object include : seedModules) {
      if (!(include instanceof TypeMirror)) {
        // TODO(tbroyer): pass annotation information
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value for include: " + include + " in " + module, module);
        continue;
      }
      TypeElement includedModule = (TypeElement) typeUtils.asElement((TypeMirror) include);
      path.push(name);
      collectIncludesRecursively(includedModule, result, path);
      path.pop();
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

    @Override public Object get() {
      throw new AssertionError("Compile-time binding should never be called to inject.");
    }

    @Override public void injectMembers(Object t) {
      throw new AssertionError("Compile-time binding should never be called to inject.");
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      Collections.addAll(get, parameters);
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

  static class ModuleValidationException extends IllegalStateException {
    final TypeElement source;

    public ModuleValidationException(String message, TypeElement source) {
      super(message);
      this.source = source;
    }
  }
}
