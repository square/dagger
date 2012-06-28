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

import com.squareup.injector.internal.Binding;
import com.squareup.injector.internal.Linker;
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
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;

/**
 * Generates an implementation of {@link Binding} that binds an injectable
 * class.
 *
 * @author Jesse Wilson
 */
@SupportedAnnotationTypes("javax.inject.Inject")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class InjectProcessor extends AbstractProcessor {
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      Map<TypeElement, InjectedClass> injectedClasses = getInjectedClasses(env);
      for (Map.Entry<TypeElement, InjectedClass> entry : injectedClasses.entrySet()) {
        InjectedClass injectedClass = entry.getValue();
        writeInjectAdapter(entry.getKey(), injectedClass.constructor, injectedClass.fields);
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Code gen failed: " + e);
    }
    return !types.isEmpty();
  }

  private Map<TypeElement, InjectedClass> getInjectedClasses(RoundEnvironment env) {
    Map<TypeElement, InjectedClass> classes = new HashMap<TypeElement, InjectedClass>();
    for (Element element : env.getElementsAnnotatedWith(Inject.class)) {
      TypeElement declaringType = (TypeElement) element.getEnclosingElement();
      InjectedClass injectedClass = classes.get(declaringType);
      if (injectedClass == null) {
        injectedClass = new InjectedClass();
        classes.put(declaringType, injectedClass);
      }
      if (element.getKind() == ElementKind.FIELD) {
        injectedClass.fields.add(element);
      }
      if (element.getKind() == ElementKind.CONSTRUCTOR) {
        injectedClass.constructor = (ExecutableElement) element;
      }
    }
    return classes;
  }

  /**
   * Write a companion class for {@code type} that implements {@link
   * com.squareup.injector.internal.ModuleAdapter} to expose its provider methods.
   */
  private void writeInjectAdapter(TypeElement type, ExecutableElement constructor,
      List<Element> fields) throws IOException {
    String key = GeneratorKeys.get(type);
    String typeName = type.getQualifiedName().toString();
    String adapterName = typeName + "$InjectAdapter";
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.addPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.addImport(Binding.class);
    writer.addImport(Linker.class);

    writer.beginType(adapterName, "class", FINAL,
        CodeGen.parameterizedType(Binding.class, typeName));

    List<? extends VariableElement> parameters = constructor.getParameters();
    for (int p = 0; p < parameters.size(); p++) {
      TypeMirror parameterType = parameters.get(p).asType();
      writer.field(CodeGen.parameterizedType(Binding.class, parameterType.toString()),
          "c" + p, PRIVATE);
    }
    for (int f = 0; f < fields.size(); f++) {
      TypeMirror fieldType = fields.get(f).asType();
      writer.field(CodeGen.parameterizedType(Binding.class, fieldType.toString()),
          "f" + f, PRIVATE);
    }

    writer.beginMethod(null, type.getSimpleName() + "$InjectAdapter", PUBLIC);
    writer.statement("super(%s.class, %s)", typeName, JavaWriter.stringLiteral(key));
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    for (int p = 0; p < constructor.getParameters().size(); p++) {
      TypeMirror parameterType = constructor.getParameters().get(p).asType();
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          "c" + p,
          CodeGen.parameterizedType(Binding.class, parameterType.toString()),
          JavaWriter.stringLiteral(GeneratorKeys.get(constructor.getParameters().get(p))),
          typeName);
    }
    for (int p = 0; p < fields.size(); p++) {
      TypeMirror parameterType = fields.get(p).asType();
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          "f" + p,
          CodeGen.parameterizedType(Binding.class, parameterType.toString()),
          JavaWriter.stringLiteral(GeneratorKeys.get((VariableElement) fields.get(p))),
          typeName);
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod(typeName, "get", PUBLIC);
    StringBuilder newInstance = new StringBuilder();
    newInstance.append(typeName).append(" result = new ").append(typeName).append('(');
    for (int p = 0; p < constructor.getParameters().size(); p++) {
      if (p != 0) {
        newInstance.append(", ");
      }
      newInstance.append("c").append(p).append(".get()");
    }
    newInstance.append(')');
    writer.statement(newInstance.toString());
    writer.statement("injectMembers(result)");
    writer.statement("return result");
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "injectMembers", PUBLIC, typeName, "object");
    for (int f = 0; f < fields.size(); f++) {
      writer.statement("object.%s = %s.get()",
          fields.get(f).getSimpleName().toString(),
          "f" + f);
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod(boolean.class.getName(), "isSingleton", PUBLIC);
    writer.statement("return true");
    writer.endMethod();

    writer.endType();
    writer.close();
  }

  static class InjectedClass {
    ExecutableElement constructor;
    List<Element> fields = new ArrayList<Element>();
  }
}
