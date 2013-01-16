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
import dagger.internal.ModuleAdapter;
import dagger.internal.SetBinding;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static dagger.internal.codegen.ProcessorJavadocs.binderTypeDocs;
import static dagger.internal.plugins.loading.ClassloadingPlugin.MODULE_ADAPTER_SUFFIX;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

/**
 * Generates an implementation of {@link ModuleAdapter} that includes a binding
 * for each {@code @Provides} method of a target class.
 */
@SupportedAnnotationTypes("dagger.Provides")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class ProvidesProcessor extends AbstractProcessor {
  private final LinkedHashMap<String, List<ExecutableElement>> remainingTypes =
      new LinkedHashMap<String, List<ExecutableElement>>();
  private static final String BINDINGS_MAP = CodeGen.parameterizedType(
      Map.class, String.class.getName(), Binding.class.getName() + "<?>");

  // TODO: include @Provides methods from the superclass
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      remainingTypes.putAll(providerMethodsByClass(env));
      for (Iterator<String> i = remainingTypes.keySet().iterator(); i.hasNext();) {
        String typeName = i.next();
        TypeElement type = processingEnv.getElementUtils().getTypeElement(typeName);
        List<ExecutableElement> providesTypes = remainingTypes.get(typeName);
        try {
          // Attempt to get the annotation. If types are missing, this will throw
          // IllegalStateException.
          Map<String, Object> parsedAnnotation = CodeGen.getAnnotation(Module.class, type);
          writeModuleAdapter(type, parsedAnnotation, providesTypes);
          i.remove();
        } catch (IllegalStateException e) {
          // a dependent type was not defined, we'll catch it on another pass
        }
      }
    } catch (IOException e) {
      error("Code gen failed: " + e);
    }
    if (env.processingOver() && remainingTypes.size() > 0) {
      error("Could not find types required by provides methods for %s", remainingTypes.keySet()
          .toString());
    }
    return true;
  }

  private void error(String format, Object... args) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, args));
  }

  /**
   * Returns a map containing all {@code @Provides} methods, indexed by class.
   */
  private Map<String, List<ExecutableElement>> providerMethodsByClass(RoundEnvironment env) {
    Map<String, List<ExecutableElement>> result
        = new HashMap<String, List<ExecutableElement>>();
    for (Element providerMethod : providesMethods(env)) {
      TypeElement type = (TypeElement) providerMethod.getEnclosingElement();
      Set<Modifier> typeModifiers = type.getModifiers();
      if (type.getKind() != ElementKind.CLASS) {
        error("Unexpected @Provides on " + providerMethod);
        continue;
      }
      if (typeModifiers.contains(Modifier.PRIVATE)
          || typeModifiers.contains(Modifier.ABSTRACT)) {
        error("Classes declaring @Provides methods must not be private or abstract: "
                + type.getQualifiedName());
        continue;
      }

      Set<Modifier> methodModifiers = providerMethod.getModifiers();
      if (methodModifiers.contains(Modifier.PRIVATE)
          || methodModifiers.contains(Modifier.ABSTRACT)
          || methodModifiers.contains(Modifier.STATIC)) {
        error("@Provides methods must not be private, abstract or static: "
                + type.getQualifiedName() + "." + providerMethod);
        continue;
      }

      List<ExecutableElement> methods = result.get(type.toString());
      if (methods == null) {
        methods = new ArrayList<ExecutableElement>();
        result.put(type.toString(), methods);
      }
      methods.add((ExecutableElement) providerMethod);
    }

    return result;
  }

  private Set<? extends Element> providesMethods(RoundEnvironment env) {
    Set<Element> result = new LinkedHashSet<Element>();
    result.addAll(env.getElementsAnnotatedWith(Provides.class));
    return result;
  }

  /**
   * Write a companion class for {@code type} that implements {@link
   * ModuleAdapter} to expose its provider methods.
   */
  private void writeModuleAdapter(TypeElement type, Map<String, Object> module,
      List<ExecutableElement> providerMethods) throws IOException {
    if (module == null) {
      error(type + " has @Provides methods but no @Module annotation");
      return;
    }

    Object[] staticInjections = (Object[]) module.get("staticInjections");
    Object[] entryPoints = (Object[]) module.get("entryPoints");
    Object[] includes = (Object[]) module.get("includes");

    boolean overrides = (Boolean) module.get("overrides");
    boolean complete = (Boolean) module.get("complete");

    String adapterName = CodeGen.adapterName(type, MODULE_ADAPTER_SUFFIX);
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    boolean multibindings = checkForMultibindings(providerMethods);
    boolean providerMethodDependencies = checkForDependencies(providerMethods);

    writer.emitEndOfLineComment(ProcessorJavadocs.GENERATED_BY_DAGGER);
    writer.emitPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.emitEmptyLine();
    writer.emitImports(getImports(multibindings, providerMethodDependencies));

    String typeName = type.getQualifiedName().toString();
    writer.emitEmptyLine();
    writer.emitJavadoc(ProcessorJavadocs.MODULE_TYPE);
    writer.beginType(adapterName, "class", PUBLIC | FINAL,
        CodeGen.parameterizedType(ModuleAdapter.class, typeName));

    StringBuilder entryPointsField = new StringBuilder().append("{ ");
    for (Object entryPoint : entryPoints) {
      TypeMirror typeMirror = (TypeMirror) entryPoint;
      String key = GeneratorKeys.rawMembersKey(typeMirror);
      entryPointsField.append(JavaWriter.stringLiteral(key)).append(", ");
    }
    entryPointsField.append("}");
    writer.field("String[]", "ENTRY_POINTS", PRIVATE | STATIC | FINAL,
        entryPointsField.toString());

    StringBuilder staticInjectionsField = new StringBuilder().append("{ ");
    for (Object staticInjection : staticInjections) {
      TypeMirror typeMirror = (TypeMirror) staticInjection;
      staticInjectionsField.append(CodeGen.typeToString(typeMirror)).append(".class, ");
    }
    staticInjectionsField.append("}");
    writer.field("Class<?>[]", "STATIC_INJECTIONS", PRIVATE | STATIC | FINAL,
        staticInjectionsField.toString());

    StringBuilder includesField = new StringBuilder().append("{ ");
    for (Object include : includes) {
      if (!(include instanceof TypeMirror)) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value: " + include + " in includes of " + type);
        continue;
      }
      TypeMirror typeMirror = (TypeMirror) include;
      includesField.append(CodeGen.typeToString(typeMirror)).append(".class, ");
    }
    includesField.append("}");
    writer.field("Class<?>[]", "INCLUDES", PRIVATE | STATIC | FINAL, includesField.toString());

    writer.emitEmptyLine();
    writer.beginMethod(null, adapterName, PUBLIC);
    writer.emitStatement("super(ENTRY_POINTS, STATIC_INJECTIONS, %s /*overrides*/, "
        + "INCLUDES, %s /*complete*/)", overrides, complete);
    writer.endMethod();

    writer.emitEmptyLine();
    writer.emitJavadoc(ProcessorJavadocs.GET_DEPENDENCIES_METHOD);
    writer.emitAnnotation(Override.class);
    writer.beginMethod("void", "getBindings", PUBLIC, BINDINGS_MAP, "map");

    Map<ExecutableElement, String> methodToClassName
        = new LinkedHashMap<ExecutableElement, String>();
    Map<String, AtomicInteger> methodNameToNextId = new LinkedHashMap<String, AtomicInteger>();
    for (ExecutableElement providerMethod : providerMethods) {
      Provides provides = providerMethod.getAnnotation(Provides.class);
      switch (provides.type()) {
        case UNIQUE: {
          String key = GeneratorKeys.get(providerMethod);
          writer.emitStatement("map.put(%s, new %s(module))", JavaWriter.stringLiteral(key),
              bindingClassName(providerMethod, methodToClassName, methodNameToNextId));
          break;
        }
        case SET: {
          String key = GeneratorKeys.getElementKey(providerMethod);
          writer.emitStatement("SetBinding.add(map, %s, new %s(module))",
              JavaWriter.stringLiteral(key),
              bindingClassName(providerMethod, methodToClassName, methodNameToNextId));
          break;
        }
        default:
          throw new AssertionError("Unknown @Provides type " + provides.type());
      }
    }
    writer.endMethod();

    writer.emitEmptyLine();
    writer.emitAnnotation(Override.class);
    writer.beginMethod(typeName, "newModule", PROTECTED);
    ExecutableElement noArgsConstructor = CodeGen.getNoArgsConstructor(type);
    if (noArgsConstructor != null && CodeGen.isCallableConstructor(noArgsConstructor)) {
      writer.emitStatement("return new %s()", typeName);
    } else {
      writer.emitStatement("throw new UnsupportedOperationException(%s)",
          JavaWriter.stringLiteral("No no-args constructor on " + type));
    }
    writer.endMethod();

    for (ExecutableElement providerMethod : providerMethods) {
      writeProvidesAdapter(writer, providerMethod, methodToClassName, methodNameToNextId);
    }

    writer.endType();
    writer.close();
  }

  private Set<String> getImports(boolean multibindings, boolean dependencies) {
    Set<String> imports = new LinkedHashSet<String>();
    imports.add(Binding.class.getName());
    imports.add(Map.class.getName());
    imports.add(Provider.class.getName());
    imports.add(ModuleAdapter.class.getName());
    if (dependencies) {
      imports.add(Linker.class.getName());
      imports.add(Set.class.getName());
    }
    if (multibindings) {
      imports.add(SetBinding.class.getName());
    }
    return imports;
  }

  private boolean checkForDependencies(List<ExecutableElement> providerMethods) {
    for (ExecutableElement element : providerMethods) {
      if (!element.getParameters().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean checkForMultibindings(List<ExecutableElement> providerMethods) {
    for (ExecutableElement element : providerMethods) {
      if (element.getAnnotation(Provides.class).type() == Provides.Type.SET) {
        return true;
      }
    }
    return false;
  }

  private String bindingClassName(ExecutableElement providerMethod,
      Map<ExecutableElement, String> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId) {
    String className = methodToClassName.get(providerMethod);
    if (className != null) return className;

    String methodName = providerMethod.getSimpleName().toString();
    String suffix = "";
    AtomicInteger id = methodNameToNextId.get(methodName);
    if (id == null) {
      methodNameToNextId.put(methodName, new AtomicInteger(2));
    } else {
      suffix = id.toString();
      id.incrementAndGet();
    }
    String uppercaseMethodName = Character.toUpperCase(methodName.charAt(0))
        + methodName.substring(1);
    className = uppercaseMethodName + "ProvidesAdapter" + suffix;
    methodToClassName.put(providerMethod, className);
    return className;
  }

  private void writeProvidesAdapter(JavaWriter writer, ExecutableElement providerMethod,
      Map<ExecutableElement, String> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId)
      throws IOException {
    String methodName = providerMethod.getSimpleName().toString();
    String moduleType = CodeGen.typeToString(providerMethod.getEnclosingElement().asType());
    String className = bindingClassName(providerMethod, methodToClassName, methodNameToNextId);
    String returnType = CodeGen.typeToString(providerMethod.getReturnType());
    List<? extends VariableElement> parameters = providerMethod.getParameters();
    boolean dependent = !parameters.isEmpty();

    writer.emitEmptyLine();
    writer.emitJavadoc(binderTypeDocs(returnType, false, false, dependent));
    writer.beginType(className, "class", PUBLIC | FINAL | STATIC,
        CodeGen.parameterizedType(Binding.class, returnType),
        CodeGen.parameterizedType(Provider.class, returnType));
    writer.emitField(moduleType, "module", PRIVATE | FINAL);
    for (Element parameter : parameters) {
      TypeMirror parameterType = parameter.asType();
      writer.emitField(CodeGen.parameterizedType(Binding.class,
          CodeGen.typeToString(parameterType)),
          parameterName(parameter), PRIVATE);
    }

    writer.emitEmptyLine();
    writer.beginMethod(null, className, PUBLIC, moduleType, "module");
    boolean singleton = providerMethod.getAnnotation(Singleton.class) != null;
    String key = JavaWriter.stringLiteral(GeneratorKeys.get(providerMethod));
    String membersKey = null;
    writer.emitStatement("super(%s, %s, %s, %s.class)",
        key, membersKey, (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"), moduleType);
    writer.emitStatement("this.module = module");
    writer.endMethod();

    if (dependent) {
      writer.emitEmptyLine();
      writer.emitJavadoc(ProcessorJavadocs.ATTACH_METHOD);
      writer.emitAnnotation(Override.class);
      writer.emitAnnotation(SuppressWarnings.class, JavaWriter.stringLiteral("unchecked"));
      writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
      for (VariableElement parameter : parameters) {
        String parameterKey = GeneratorKeys.get(parameter);
        writer.emitStatement("%s = (%s) linker.requestBinding(%s, %s.class)",
            parameterName(parameter),
            writer.compressType(CodeGen.parameterizedType(Binding.class,
                CodeGen.typeToString(parameter.asType()))),
            JavaWriter.stringLiteral(parameterKey),
            writer.compressType(moduleType));
      }
      writer.endMethod();

      writer.emitEmptyLine();
      writer.emitJavadoc(ProcessorJavadocs.GET_DEPENDENCIES_METHOD);
      writer.emitAnnotation(Override.class);
      String setOfBindings = CodeGen.parameterizedType(Set.class, "Binding<?>");
      writer.beginMethod("void", "getDependencies", PUBLIC, setOfBindings, "getBindings",
          setOfBindings, "injectMembersBindings");
      for (Element parameter : parameters) {
        writer.emitStatement("getBindings.add(%s)", parameter.getSimpleName().toString());
      }
      writer.endMethod();
    }

    writer.emitEmptyLine();
    writer.emitJavadoc(ProcessorJavadocs.GET_METHOD, returnType);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(returnType, "get", PUBLIC);
    StringBuilder args = new StringBuilder();
    boolean first = true;
    for (Element parameter : parameters) {
      if (!first) args.append(", ");
      else first = false;
      args.append(String.format("%s.get()", parameter.getSimpleName().toString()));
    }
    writer.emitStatement("return module.%s(%s)", methodName, args.toString());
    writer.endMethod();

    writer.endType();
  }

  private String parameterName(Element parameter) {
    if (parameter.getSimpleName().equals("module")) {
      return "parameter_" + parameter.getSimpleName().toString();
    }
    return parameter.getSimpleName().toString();
  }
}
