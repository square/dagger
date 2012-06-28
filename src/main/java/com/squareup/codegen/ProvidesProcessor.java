/**
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
package com.squareup.codegen;

import com.squareup.injector.Provides;
import com.squareup.injector.internal.Binding;
import com.squareup.injector.internal.Linker;
import com.squareup.injector.internal.ModuleAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
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
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

/**
 * Generates an implementation of {@link ModuleAdapter} that includes a binding
 * for each {@code @Provides} method of a target class.
 *
 * @author Jesse Wilson
 */
@SupportedAnnotationTypes("com.squareup.injector.Provides")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class ProvidesProcessor extends AbstractProcessor {
  private static final String BINDINGS_MAP = CodeGen.parameterizedType(
      Map.class, String.class.getName(), Binding.class.getName() + "<?>");
  private static final String BINDINGS_HASH_MAP = CodeGen.parameterizedType(
      HashMap.class, String.class.getName(), Binding.class.getName() + "<?>");

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      Map<TypeElement, List<ExecutableElement>> providerMethods = providerMethodsByClass(env);
      for (Map.Entry<TypeElement, List<ExecutableElement>> module : providerMethods.entrySet()) {
        writeModuleAdapter(module.getKey(), module.getValue());
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Code gen failed: " + e);
    }
    return !types.isEmpty();
  }

  /**
   * Returns a map containing all {@code @Provides} methods, indexed by class.
   */
  private Map<TypeElement, List<ExecutableElement>> providerMethodsByClass(RoundEnvironment env) {
    Map<TypeElement, List<ExecutableElement>> result
        = new HashMap<TypeElement, List<ExecutableElement>>();
    for (Element providerMethod : env.getElementsAnnotatedWith(Provides.class)) {
      TypeElement type = (TypeElement) providerMethod.getEnclosingElement();
      Set<Modifier> typeModifiers = type.getModifiers();
      if (type.getKind() != ElementKind.CLASS) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Unexpected @Provides on " + providerMethod);
        continue;
      }
      if (typeModifiers.contains(Modifier.PRIVATE)
          || typeModifiers.contains(Modifier.ABSTRACT)) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Unexpected modifiers on type declaring @Provides method: " + providerMethod);
      }

      Set<Modifier> methodModifiers = providerMethod.getModifiers();
      if (methodModifiers.contains(Modifier.PRIVATE)
          || methodModifiers.contains(Modifier.ABSTRACT)
          || methodModifiers.contains(Modifier.STATIC)) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
            "Unexpected modifiers on @Provides method: " + providerMethod);
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

  /**
   * Write a companion class for {@code type} that implements {@link
   * ModuleAdapter} to expose its provider methods.
   */
  private void writeModuleAdapter(TypeElement type, List<ExecutableElement> providerMethods)
      throws IOException {
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(type.getQualifiedName() + "$ModuleAdapter", type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.addPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.addImport(Binding.class);
    writer.addImport(ModuleAdapter.class);
    writer.addImport(Map.class);
    writer.addImport(Linker.class);

    String typeName = type.getQualifiedName().toString();
    writer.beginType(typeName + "$ModuleAdapter", "class", PUBLIC | FINAL, null,
        CodeGen.parameterizedType(ModuleAdapter.class, typeName));

    writer.annotation(Override.class);
    writer.beginMethod(BINDINGS_MAP, "getBindings", PUBLIC, typeName, "module");
    writer.statement("%s result = new %s()", BINDINGS_MAP, BINDINGS_HASH_MAP);
    for (ExecutableElement providerMethod : providerMethods) {
      String key = GeneratorKeys.get(providerMethod);
      writer.statement("result.put(%s, new %s(module))", JavaWriter.stringLiteral(key),
          providerMethod.getSimpleName().toString() + "Binding");
    }
    writer.statement("return result");
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
    String key = GeneratorKeys.get(providerMethod);
    String moduleType = providerMethod.getEnclosingElement().asType().toString();
    String className = providerMethod.getSimpleName() + "Binding";
    String returnType = providerMethod.getReturnType().toString();

    writer.beginType(className, "class", PRIVATE | STATIC,
        CodeGen.parameterizedType(Binding.class, returnType));
    writer.field(moduleType, "module", PRIVATE | FINAL);
    List<? extends VariableElement> parameters = providerMethod.getParameters();
    for (int p = 0; p < parameters.size(); p++) {
      TypeMirror parameterType = parameters.get(p).asType();
      writer.field(CodeGen.parameterizedType(Binding.class, parameterType.toString()),
          parameterName(p), PRIVATE);
    }

    writer.beginMethod(null, className, PUBLIC, moduleType, "module");
    writer.statement("super(%s.class, %s)", moduleType, JavaWriter.stringLiteral(key));
    writer.statement("this.module = module");
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    for (int p = 0; p < parameters.size(); p++) {
      VariableElement parameter = parameters.get(p);
      String parameterKey = GeneratorKeys.get(parameter);
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          parameterName(p),
          CodeGen.parameterizedType(Binding.class, parameter.asType().toString()),
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
    writer.beginMethod(boolean.class.getName(), "isSingleton", PUBLIC);
    writer.statement("return %s", true);
    writer.endMethod();

    writer.endType();
  }

  private String parameterName(int index) {
    return "p" + index;
  }
}
