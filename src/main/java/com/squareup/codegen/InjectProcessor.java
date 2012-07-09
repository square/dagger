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
import javax.lang.model.element.Modifier;
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
 */
@SupportedAnnotationTypes("javax.inject.Inject")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class InjectProcessor extends AbstractProcessor {
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      // TODO: inject superclass fields
      Map<TypeElement, InjectedClass> injectedClasses = getInjectedClasses(env);
      for (Map.Entry<TypeElement, InjectedClass> entry : injectedClasses.entrySet()) {
        InjectedClass injectedClass = entry.getValue();
        writeInjectAdapter(entry.getKey(), injectedClass.constructor, injectedClass.fields);
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Code gen failed: " + e);
    }
    return true;
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
        // TODO: explode if there are multiple @Inject-annotated constructors
        injectedClass.constructor = (ExecutableElement) element;
      }
    }

    // Find no-args constructors for non-abstract classes that don't have @Inject constructors.
    for (Map.Entry<TypeElement, InjectedClass> entry : classes.entrySet()) {
      TypeElement typeElement = entry.getKey();
      InjectedClass injectedClass = entry.getValue();
      if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
        if (injectedClass.constructor != null) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Abstract class "
              + typeElement.getQualifiedName() + " must not have an @Injectable constructor.");
          injectedClass.constructor = null;
        }
        continue;
      }
      if (injectedClass.constructor == null) {
        injectedClass.constructor = getNoArgsConstructor(typeElement);
      }
    }

    return classes;
  }

  /**
   * Returns the no args constructor for {@code typeElement}, or null if no such
   * constructor exists.
   */
  private ExecutableElement getNoArgsConstructor(TypeElement typeElement) {
    for (Element element : typeElement.getEnclosedElements()) {
      if (element.getKind() != ElementKind.CONSTRUCTOR) {
        continue;
      }
      ExecutableElement constructor = (ExecutableElement) element;
      if (constructor.getParameters().isEmpty()) {
        return constructor;
      }
    }
    return null;
  }

  /**
   * Write a companion class for {@code type} that extends {@link Binding}.
   *
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  private void writeInjectAdapter(TypeElement type, ExecutableElement constructor,
      List<Element> fields) throws IOException {
    String key = GeneratorKeys.get(type);
    String typeName = type.getQualifiedName().toString();
    String adapterName = CodeGen.adapterName(type, "$InjectAdapter");
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.addPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.addImport(Binding.class);
    writer.addImport(Linker.class);

    writer.beginType(adapterName, "class", FINAL,
        CodeGen.parameterizedType(Binding.class, typeName));

    if (constructor != null) {
      List<? extends VariableElement> parameters = constructor.getParameters();
      for (int p = 0; p < parameters.size(); p++) {
        TypeMirror parameterType = parameters.get(p).asType();
        writer.field(CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(parameterType)),
            constructorParameterName(p), PRIVATE);
      }
    }
    for (int f = 0; f < fields.size(); f++) {
      TypeMirror fieldType = fields.get(f).asType();
      writer.field(CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(fieldType)),
          fieldName(f), PRIVATE);
    }

    writer.beginMethod(null, adapterName, PUBLIC);
    boolean singleton = true; // TODO
    boolean injectMembersOnly = constructor == null;
    writer.statement("super(%s, %s, %s, %s.class)",
        JavaWriter.stringLiteral(key), singleton, injectMembersOnly, typeName);
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    if (constructor != null) {
      for (int p = 0; p < constructor.getParameters().size(); p++) {
        TypeMirror parameterType = constructor.getParameters().get(p).asType();
        writer.statement("%s = (%s) linker.requestBinding(%s, %s.class, false)",
            constructorParameterName(p),
            CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(parameterType)),
            JavaWriter.stringLiteral(GeneratorKeys.get(constructor.getParameters().get(p))),
            typeName);
      }
    }
    for (int f = 0; f < fields.size(); f++) {
      TypeMirror fieldType = fields.get(f).asType();
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class, false)",
          fieldName(f),
          CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(fieldType)),
          JavaWriter.stringLiteral(GeneratorKeys.get((VariableElement) fields.get(f))),
          typeName);
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod(typeName, "get", PUBLIC);
    if (constructor != null) {
      StringBuilder newInstance = new StringBuilder();
      newInstance.append(typeName).append(" result = new ").append(typeName).append('(');
      for (int p = 0; p < constructor.getParameters().size(); p++) {
        if (p != 0) {
          newInstance.append(", ");
        }
        newInstance.append(constructorParameterName(p)).append(".get()");
      }
      newInstance.append(')');
      writer.statement(newInstance.toString());
      writer.statement("injectMembers(result)");
      writer.statement("return result");
    } else {
      writer.statement("throw new UnsupportedOperationException()");
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "injectMembers", PUBLIC, typeName, "object");
    for (int f = 0; f < fields.size(); f++) {
      writer.statement("object.%s = %s.get()",
          fields.get(f).getSimpleName().toString(),
          fieldName(f));
    }
    writer.endMethod();

    writer.endType();
    writer.close();
  }

  private String fieldName(int index) {
    return "f" + index;
  }

  private String constructorParameterName(int index) {
    return "c" + index;
  }

  static class InjectedClass {
    ExecutableElement constructor;
    List<Element> fields = new ArrayList<Element>();
  }
}
