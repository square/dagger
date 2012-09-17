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
import dagger.internal.Linker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
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
import static java.lang.reflect.Modifier.PUBLIC;

/**
 * Generates an implementation of {@link Binding} that injects the
 * {@literal @}{@code Inject}-annotated members of a class.
 */
@SupportedAnnotationTypes("javax.inject.Inject")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public final class InjectProcessor extends AbstractProcessor {
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      for (InjectedClass injectedClass : getInjectedClasses(env)) {
        writeInjectAdapter(injectedClass.type, injectedClass.constructor, injectedClass.fields);
      }
    } catch (IOException e) {
      error("Code gen failed: %s", e);
    }
    return true;
  }

  private Set<InjectedClass> getInjectedClasses(RoundEnvironment env) {
    // First gather the set of classes that have @Inject-annotated members.
    Set<TypeElement> injectedTypes = new LinkedHashSet<TypeElement>();
    for (Element element : env.getElementsAnnotatedWith(Inject.class)) {
      injectedTypes.add((TypeElement) element.getEnclosingElement());
    }

    // Next get the InjectedClass for each of those.
    Set<InjectedClass> result = new LinkedHashSet<InjectedClass>();
    for (TypeElement type : injectedTypes) {
      result.add(getInjectedClass(type));
    }

    return result;
  }

  /**
   * @param type a type with an @Inject-annotated member.
   */
  private InjectedClass getInjectedClass(TypeElement type) {
    boolean isAbstract = type.getModifiers().contains(Modifier.ABSTRACT);
    ExecutableElement constructor = null;
    List<Element> fields = new ArrayList<Element>();
    for (Element member : type.getEnclosedElements()) {
      if (member.getAnnotation(Inject.class) == null
          || member.getModifiers().contains(Modifier.STATIC)) {
        continue;
      }

      switch (member.getKind()) {
        case FIELD:
          fields.add(member);
          break;
        case CONSTRUCTOR:
          if (constructor != null) {
            error("Too many injectable constructors on %s.", type.getQualifiedName());
          } else if (isAbstract) {
            error("Abstract class %s must not have an @Inject-annotated constructor.",
                type.getQualifiedName());
          }
          constructor = (ExecutableElement) member;
          break;
        default:
          error("Cannot inject %s", member);
          break;
      }
    }

    if (constructor == null && !isAbstract) {
      constructor = findNoArgsConstructor(type);
    }

    return new InjectedClass(type, constructor, fields);
  }

  /**
   * Returns the no args constructor for {@code typeElement}, or null if no such
   * constructor exists.
   */
  private ExecutableElement findNoArgsConstructor(TypeElement typeElement) {
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

  private void error(String format, Object... args) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, args));
  }

  /**
   * Write a companion class for {@code type} that extends {@link Binding}.
   *
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  private void writeInjectAdapter(TypeElement type, ExecutableElement constructor,
      List<Element> fields) throws IOException {
    String typeName = type.getQualifiedName().toString();
    TypeMirror supertype = CodeGen.getApplicationSupertype(type);
    String adapterName = CodeGen.adapterName(type, "$InjectAdapter");
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.addPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.addImport(Binding.class);
    writer.addImport(Linker.class);
    writer.addImport(Set.class);

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
    if (supertype != null) {
      writer.field(CodeGen.parameterizedType(Binding.class,
          CodeGen.rawTypeToString(supertype, '.')), "supertype", PRIVATE);
    }

    writer.beginMethod(null, adapterName, PUBLIC);
    String key = (constructor != null)
        ? JavaWriter.stringLiteral(GeneratorKeys.get(type.asType()))
        : null;
    String membersKey = JavaWriter.stringLiteral(GeneratorKeys.rawMembersKey(type.asType()));
    boolean singleton = type.getAnnotation(Singleton.class) != null;
    writer.statement("super(%s, %s, %s /*singleton*/, %s.class)",
        key, membersKey, singleton, typeName);
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    if (constructor != null) {
      for (int p = 0; p < constructor.getParameters().size(); p++) {
        TypeMirror parameterType = constructor.getParameters().get(p).asType();
        writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
            constructorParameterName(p),
            CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(parameterType)),
            JavaWriter.stringLiteral(GeneratorKeys.get(constructor.getParameters().get(p))),
            typeName);
      }
    }
    for (int f = 0; f < fields.size(); f++) {
      TypeMirror fieldType = fields.get(f).asType();
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          fieldName(f),
          CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(fieldType)),
          JavaWriter.stringLiteral(GeneratorKeys.get((VariableElement) fields.get(f))),
          typeName);
    }
    if (supertype != null) {
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          "supertype",
          CodeGen.parameterizedType(Binding.class, CodeGen.rawTypeToString(supertype, '.')),
          JavaWriter.stringLiteral(GeneratorKeys.rawMembersKey(supertype)),
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
    if (supertype != null) {
      writer.statement("supertype.injectMembers(object)");
    }
    writer.endMethod();

    writer.annotation(Override.class);
    String setOfBindings = CodeGen.parameterizedType(Set.class, "Binding<?>");
    writer.beginMethod("void", "getDependencies", PUBLIC, setOfBindings, "getBindings",
        setOfBindings, "injectMembersBindings");
    if (constructor != null) {
      for (int p = 0; p < constructor.getParameters().size(); p++) {
        writer.statement("getBindings.add(%s)", constructorParameterName(p));
      }
    }
    for (int f = 0; f < fields.size(); f++) {
      writer.statement("injectMembersBindings.add(%s)", fieldName(f));
    }
    if (supertype != null) {
      writer.statement("injectMembersBindings.add(%s)", "supertype");
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
    final TypeElement type;
    final ExecutableElement constructor;
    final List<Element> fields;

    InjectedClass(TypeElement type, ExecutableElement constructor, List<Element> fields) {
      this.type = type;
      this.constructor = constructor;
      this.fields = fields;
    }
  }
}
