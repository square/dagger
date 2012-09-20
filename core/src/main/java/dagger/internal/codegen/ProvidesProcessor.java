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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

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
  private static final String BINDINGS_MAP = CodeGen.parameterizedType(
      Map.class, String.class.getName(), Binding.class.getName() + "<?>");

  // TODO: include @Provides methods from the superclass

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      Map<TypeElement, List<ExecutableElement>> providerMethods = providerMethodsByClass(env);
      for (Map.Entry<TypeElement, List<ExecutableElement>> module : providerMethods.entrySet()) {
        writeModuleAdapter(module.getKey(), module.getValue());
      }
    } catch (IOException e) {
      error("Code gen failed: " + e);
    }
    return true;
  }

  private void error(String message) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
  }

  /**
   * Returns a map containing all {@code @Provides} methods, indexed by class.
   */
  private Map<TypeElement, List<ExecutableElement>> providerMethodsByClass(RoundEnvironment env) {
    Map<TypeElement, List<ExecutableElement>> result
        = new HashMap<TypeElement, List<ExecutableElement>>();
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

      if (providerMethod.getAnnotation(dagger.Element.class) != null) {
        error("@Element binding not supported with code generation: "
            + type.getQualifiedName() + "." + providerMethod);
        continue;
      }

      List<ExecutableElement> methods = result.get(type);
      if (methods == null) {
        methods = new ArrayList<ExecutableElement>();
        result.put(type, methods);
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
  private void writeModuleAdapter(TypeElement type, List<ExecutableElement> providerMethods)
      throws IOException {
    Map<String, Object> module = CodeGen.getAnnotation(Module.class, type);
    if (module == null) {
      error(type + " has @Provides methods but no @Module annotation");
      return;
    }

    Object[] staticInjections = (Object[]) module.get("staticInjections");
    Object[] entryPoints = (Object[]) module.get("entryPoints");
    @SuppressWarnings("deprecation") // use deprecated until children removed.
    Object[] includes = ArrayUtil.concatenate(
        (Object[]) module.get("includes"),
        (Object[]) module.get("children"));

    boolean overrides = (Boolean) module.get("overrides");
    boolean complete = (Boolean) module.get("complete");

    String adapterName = CodeGen.adapterName(type, "$ModuleAdapter");
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.addPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.addImport(Binding.class);
    writer.addImport(ModuleAdapter.class);
    writer.addImport(Map.class);
    writer.addImport(Linker.class);

    String typeName = type.getQualifiedName().toString();
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
            "Unexpected value: " + include + " in includes or children attribute of " + type);
        continue;
      }
      TypeMirror typeMirror = (TypeMirror) include;
      includesField.append(CodeGen.typeToString(typeMirror)).append(".class, ");
    }
    includesField.append("}");
    writer.field("Class<?>[]", "INCLUDES", PRIVATE | STATIC | FINAL, includesField.toString());

    writer.beginMethod(null, adapterName, PUBLIC);
    writer.statement("super(ENTRY_POINTS, STATIC_INJECTIONS, %s /*overrides*/, "
        + "INCLUDES, %s /*complete*/)", overrides, complete);
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "getBindings", PUBLIC, BINDINGS_MAP, "map");
    for (ExecutableElement providerMethod : providerMethods) {
      String key = GeneratorKeys.get(providerMethod);
      writer.statement("map.put(%s, new %s(module))", JavaWriter.stringLiteral(key),
          providerMethod.getSimpleName().toString() + "Binding");
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod(typeName, "newModule", PROTECTED);
    ExecutableElement noArgsConstructor = CodeGen.getNoArgsConstructor(type);
    if (noArgsConstructor != null && CodeGen.isCallableConstructor(noArgsConstructor)) {
      writer.statement("return new %s()", typeName);
    } else {
      writer.statement("throw new UnsupportedOperationException(%s)",
          JavaWriter.stringLiteral("No no-args constructor on " + type));
    }
    writer.endMethod();

    for (ExecutableElement providerMethod : providerMethods) {
      writeBindingClass(writer, providerMethod);
    }

    writer.endType();
    writer.close();
  }

  private void writeBindingClass(JavaWriter writer, ExecutableElement providerMethod)
      throws IOException {
    String methodName = providerMethod.getSimpleName().toString();
    String moduleType = CodeGen.typeToString(providerMethod.getEnclosingElement().asType());
    String className = providerMethod.getSimpleName() + "Binding";
    String returnType = CodeGen.typeToString(providerMethod.getReturnType());

    writer.beginType(className, "class", PRIVATE | STATIC,
        CodeGen.parameterizedType(Binding.class, returnType));
    writer.field(moduleType, "module", PRIVATE | FINAL);
    List<? extends VariableElement> parameters = providerMethod.getParameters();
    for (int p = 0; p < parameters.size(); p++) {
      TypeMirror parameterType = parameters.get(p).asType();
      writer.field(CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(parameterType)),
          parameterName(p), PRIVATE);
    }

    writer.beginMethod(null, className, PUBLIC, moduleType, "module");
    boolean singleton = providerMethod.getAnnotation(Singleton.class) != null;
    String key = JavaWriter.stringLiteral(GeneratorKeys.get(providerMethod));
    String membersKey = null;
    writer.statement("super(%s, %s, %s /*singleton*/, %s.class)",
        key, membersKey, singleton, moduleType);
    writer.statement("this.module = module");
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    for (int p = 0; p < parameters.size(); p++) {
      VariableElement parameter = parameters.get(p);
      String parameterKey = GeneratorKeys.get(parameter);
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          parameterName(p),
          CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(parameter.asType())),
          JavaWriter.stringLiteral(parameterKey), moduleType);
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod(returnType, "get", PUBLIC);
    StringBuilder args = new StringBuilder();
    for (int p = 0; p < parameters.size(); p++) {
      if (p != 0) {
        args.append(", ");
      }
      args.append(String.format("%s.get()", parameterName(p)));
    }
    writer.statement("return module.%s(%s)", methodName, args.toString());
    writer.endMethod();

    writer.annotation(Override.class);
    String setOfBindings = CodeGen.parameterizedType(Set.class, "Binding<?>");
    writer.beginMethod("void", "getDependencies", PUBLIC, setOfBindings, "getBindings",
        setOfBindings, "injectMembersBindings");
    for (int p = 0; p < parameters.size(); p++) {
      writer.statement("getBindings.add(%s)", parameterName(p));
    }
    writer.endMethod();

    writer.endType();
  }

  private String parameterName(int index) {
    return "p" + index;
  }
}
