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
import dagger.internal.StaticInjection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static dagger.internal.plugins.loading.ClassloadingPlugin.INJECT_ADAPTER_SUFFIX;
import static dagger.internal.plugins.loading.ClassloadingPlugin.STATIC_INJECTION_SUFFIX;
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
  private final Set<String> remainingTypeNames = new LinkedHashSet<String>();

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
      remainingTypeNames.addAll(getInjectedClassNames(env));
      for (Iterator<String> i = remainingTypeNames.iterator(); i.hasNext();) {
        InjectedClass injectedClass = getInjectedClass(i.next());
        // Verify that we have access to all types to be injected on this pass.
        boolean missingDependentClasses =
            !allTypesExist(injectedClass.fields)
            || (injectedClass.constructor != null && !allTypesExist(injectedClass.constructor
                .getParameters()))
            || !allTypesExist(injectedClass.staticFields);
        if (!missingDependentClasses) {
          writeInjectionsForClass(injectedClass);
          i.remove();
        }
      }
    } catch (IOException e) {
      error("Code gen failed: %s", e);
    }
    if (env.processingOver() && !remainingTypeNames.isEmpty()) {
      error("Could not find injection type required by %s!", remainingTypeNames);
    }
    return true;
  }

  private void writeInjectionsForClass(InjectedClass injectedClass) throws IOException {
    if (injectedClass.constructor != null || !injectedClass.fields.isEmpty()) {
      writeInjectAdapter(injectedClass.type, injectedClass.constructor, injectedClass.fields);
    }
    if (!injectedClass.staticFields.isEmpty()) {
      writeStaticInjection(injectedClass.type, injectedClass.staticFields);
    }
  }

  /**
   * Return true if all element types are currently available in this code
   * generation pass. Unavailable types will be of kind {@link TypeKind#ERROR}.
   */
  private boolean allTypesExist(Collection<? extends Element> elements) {
    for (Element element : elements) {
      if (element.asType().getKind() == TypeKind.ERROR) {
        return false;
      }
    }
    return true;
  }

  private Set<String> getInjectedClassNames(RoundEnvironment env) {
    // First gather the set of classes that have @Inject-annotated members.
    Set<String> injectedTypeNames = new LinkedHashSet<String>();
    for (Element element : env.getElementsAnnotatedWith(Inject.class)) {
      injectedTypeNames.add(element.getEnclosingElement().asType().toString());
    }
    return injectedTypeNames;
  }

  /**
   * @param injectedClassName the name of a class with an @Inject-annotated member.
   */
  private InjectedClass getInjectedClass(String injectedClassName) {
    TypeElement type = processingEnv.getElementUtils().getTypeElement(injectedClassName);
    boolean isAbstract = type.getModifiers().contains(Modifier.ABSTRACT);
    List<Element> staticFields = new ArrayList<Element>();
    ExecutableElement constructor = null;
    List<Element> fields = new ArrayList<Element>();
    for (Element member : type.getEnclosedElements()) {
      if (member.getAnnotation(Inject.class) == null) {
        continue;
      }

      switch (member.getKind()) {
        case FIELD:
          if (member.getModifiers().contains(Modifier.STATIC)) {
            staticFields.add(member);
          } else {
            fields.add(member);
          }
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

    return new InjectedClass(type, staticFields, constructor, fields);
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
        Set<Modifier> modifiers = constructor.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
          return null;
        } else {
          return constructor;
        }
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
    String adapterName = CodeGen.adapterName(type, INJECT_ADAPTER_SUFFIX);
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
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class, false)",
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

  /**
   * Write a companion class for {@code type} that extends {@link StaticInjection}.
   */
  private void writeStaticInjection(TypeElement type, List<Element> fields) throws IOException {
    String typeName = type.getQualifiedName().toString();
    String adapterName = CodeGen.adapterName(type, STATIC_INJECTION_SUFFIX);
    JavaFileObject sourceFile = processingEnv.getFiler()
        .createSourceFile(adapterName, type);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.addPackage(CodeGen.getPackage(type).getQualifiedName().toString());
    writer.addImport(StaticInjection.class);
    writer.addImport(Binding.class);
    writer.addImport(Linker.class);

    writer.beginType(adapterName, "class", PUBLIC | FINAL, StaticInjection.class.getName());

    for (int f = 0; f < fields.size(); f++) {
      TypeMirror fieldType = fields.get(f).asType();
      writer.field(CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(fieldType)),
          fieldName(f), PRIVATE);
    }

    writer.annotation(Override.class);
    writer.beginMethod("void", "attach", PUBLIC, Linker.class.getName(), "linker");
    for (int f = 0; f < fields.size(); f++) {
      TypeMirror fieldType = fields.get(f).asType();
      writer.statement("%s = (%s) linker.requestBinding(%s, %s.class)",
          fieldName(f),
          CodeGen.parameterizedType(Binding.class, CodeGen.typeToString(fieldType)),
          JavaWriter.stringLiteral(GeneratorKeys.get((VariableElement) fields.get(f))),
          typeName);
    }
    writer.endMethod();

    writer.annotation(Override.class);
    writer.beginMethod("void", "inject", PUBLIC);
    for (int f = 0; f < fields.size(); f++) {
      writer.statement("%s.%s = %s.get()",
          typeName,
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
    final TypeElement type;
    final List<Element> staticFields;
    final ExecutableElement constructor;
    final List<Element> fields;

    InjectedClass(TypeElement type, List<Element> staticFields, ExecutableElement constructor,
        List<Element> fields) {
      this.type = type;
      this.staticFields = staticFields;
      this.constructor = constructor;
      this.fields = fields;
    }
  }
}
