/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.ObjectGraph;
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
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import static dagger.internal.codegen.AdapterJavadocs.bindingTypeDocs;
import static dagger.internal.codegen.Util.SET_OF_BINDINGS;
import static dagger.internal.codegen.Util.adapterName;
import static dagger.internal.codegen.Util.bindingOf;
import static dagger.internal.codegen.Util.elementToString;
import static dagger.internal.codegen.Util.getApplicationSupertype;
import static dagger.internal.codegen.Util.getNoArgsConstructor;
import static dagger.internal.codegen.Util.getPackage;
import static dagger.internal.codegen.Util.injectableType;
import static dagger.internal.codegen.Util.isCallableConstructor;
import static dagger.internal.codegen.Util.rawTypeToString;
import static dagger.internal.loaders.GeneratedAdapters.INJECT_ADAPTER_SUFFIX;
import static dagger.internal.loaders.GeneratedAdapters.STATIC_INJECTION_SUFFIX;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates an implementation of {@link Binding} that injects the
 * {@literal @}{@code Inject}-annotated members of a class.
 */
@SupportedAnnotationTypes("javax.inject.Inject")
public final class InjectAdapterProcessor extends AbstractProcessor {
  private final Set<String> remainingTypeNames = new LinkedHashSet<String>();

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    remainingTypeNames.addAll(findInjectedClassNames(env));
    for (Iterator<String> i = remainingTypeNames.iterator(); i.hasNext();) {
      InjectedClass injectedClass = createInjectedClass(i.next());
      // Verify that we have access to all types to be injected on this pass.
      boolean missingDependentClasses =
          !allTypesExist(injectedClass.fields)
          || (injectedClass.constructor != null && !allTypesExist(injectedClass.constructor
              .getParameters()))
          || !allTypesExist(injectedClass.staticFields);
      if (!missingDependentClasses) {
        try {
          generateInjectionsForClass(injectedClass);
        } catch (IOException e) {
          error("Code gen failed: " + e, injectedClass.type);
        }
        i.remove();
      }
    }
    if (env.processingOver() && !remainingTypeNames.isEmpty()) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Could not find injection type required by " + remainingTypeNames);
    }
    return false;
  }

  private void generateInjectionsForClass(InjectedClass injectedClass) throws IOException {
    if (injectedClass.constructor != null || !injectedClass.fields.isEmpty()) {
      generateInjectAdapter(injectedClass.type, injectedClass.constructor, injectedClass.fields);
    }
    if (!injectedClass.staticFields.isEmpty()) {
      generateStaticInjection(injectedClass.type, injectedClass.staticFields);
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

  private Set<String> findInjectedClassNames(RoundEnvironment env) {
    // First gather the set of classes that have @Inject-annotated members.
    Set<String> injectedTypeNames = new LinkedHashSet<String>();
    for (Element element : env.getElementsAnnotatedWith(Inject.class)) {
      if (!validateInjectable(element)) {
        continue;
      }
      injectedTypeNames.add(rawTypeToString(element.getEnclosingElement().asType(), '.'));
    }
    return injectedTypeNames;
  }

  private boolean validateInjectable(Element injectable) {
    Element injectableType = injectable.getEnclosingElement();

    if (injectable.getKind() == ElementKind.CLASS) {
      error("@Inject is not valid on a class: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.METHOD) {
      error("Method injection is not supported: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.FIELD
        && injectable.getModifiers().contains(FINAL)) {
      error("Can't inject a final field: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.FIELD
        && injectable.getModifiers().contains(PRIVATE)) {
      error("Can't inject a private field: " + elementToString(injectable), injectable);
      return false;
    }

    if (injectable.getKind() == ElementKind.CONSTRUCTOR
        && injectable.getModifiers().contains(PRIVATE)) {
      error("Can't inject a private constructor: " + elementToString(injectable), injectable);
      return false;
    }

    ElementKind elementKind = injectableType.getEnclosingElement().getKind();
    boolean isClassOrInterface = elementKind.isClass() || elementKind.isInterface();
    boolean isStatic = injectableType.getModifiers().contains(STATIC);

    if (isClassOrInterface && !isStatic) {
      error("Can't inject a non-static inner class: " + elementToString(injectable),
          injectableType);
      return false;
    }

    return true;
  }

  /**
   * @param injectedClassName the name of a class with an @Inject-annotated member.
   */
  private InjectedClass createInjectedClass(String injectedClassName) {
    TypeElement type = processingEnv.getElementUtils().getTypeElement(injectedClassName);
    boolean isAbstract = type.getModifiers().contains(ABSTRACT);
    List<Element> staticFields = new ArrayList<Element>();
    ExecutableElement constructor = null;
    List<Element> fields = new ArrayList<Element>();
    for (Element member : type.getEnclosedElements()) {
      if (member.getAnnotation(Inject.class) == null) {
        continue;
      }

      switch (member.getKind()) {
        case FIELD:
          if (member.getModifiers().contains(STATIC)) {
            staticFields.add(member);
          } else {
            fields.add(member);
          }
          break;
        case CONSTRUCTOR:
          if (constructor != null) {
            // TODO(tbroyer): pass annotation information
            error("Too many injectable constructors on " + type.getQualifiedName(), member);
          } else if (isAbstract) {
            // TODO(tbroyer): pass annotation information
            error("Abstract class " + type.getQualifiedName()
                + " must not have an @Inject-annotated constructor.", member);
          }
          constructor = (ExecutableElement) member;
          break;
        default:
          // TODO(tbroyer): pass annotation information
          error("Cannot inject " + elementToString(member), member);
          break;
      }
    }

    if (constructor == null && !isAbstract) {
      constructor = getNoArgsConstructor(type);
      if (constructor != null && !isCallableConstructor(constructor)) {
        constructor = null;
      }
    }

    return new InjectedClass(type, staticFields, constructor, fields);
  }

  /**
   * Write a companion class for {@code type} that extends {@link Binding}.
   *
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  private void generateInjectAdapter(TypeElement type, ExecutableElement constructor,
      List<Element> fields) throws IOException {
    String packageName = getPackage(type).getQualifiedName().toString();
    TypeMirror supertype = getApplicationSupertype(type);
    if (supertype != null) {
      supertype = processingEnv.getTypeUtils().erasure(supertype);
    }
    ClassName injectedClassName = ClassName.get(type);
    ClassName adapterClassName = adapterName(injectedClassName, INJECT_ADAPTER_SUFFIX);

    boolean isAbstract = type.getModifiers().contains(ABSTRACT);
    boolean injectMembers = !fields.isEmpty() || supertype != null;
    boolean disambiguateFields = !fields.isEmpty()
        && (constructor != null)
        && !constructor.getParameters().isEmpty();
    boolean dependent = injectMembers
        || ((constructor != null) && !constructor.getParameters().isEmpty());

    TypeSpec.Builder result = TypeSpec.classBuilder(adapterClassName.simpleName())
        .addOriginatingElement(type)
        .addModifiers(PUBLIC, FINAL)
        .superclass(ParameterizedTypeName.get(ClassName.get(Binding.class), injectedClassName))
        .addJavadoc("$L", bindingTypeDocs(injectableType(type.asType()), isAbstract,
            injectMembers, dependent).toString());

    for (Element field : fields) {
      result.addField(memberBindingField(disambiguateFields, field));
    }
    if (constructor != null) {
      for (VariableElement parameter : constructor.getParameters()) {
        result.addField(parameterBindingField(disambiguateFields, parameter));
      }
    }
    if (supertype != null) {
      result.addField(supertypeBindingField(supertype));
    }

    result.addMethod(writeInjectAdapterConstructor(constructor, type, injectedClassName));
    if (dependent) {
      result.addMethod(attachMethod(
          constructor, fields, disambiguateFields, injectedClassName, supertype, true));
      result.addMethod(getDependenciesMethod(
          constructor, fields, disambiguateFields, supertype, true));
    }
    if (constructor != null) {
      result.addMethod(
          getMethod(constructor, disambiguateFields, injectMembers, injectedClassName));
    }
    if (injectMembers) {
      result.addMethod(
          membersInjectMethod(fields, disambiguateFields, injectedClassName, supertype));
    }

    JavaFile javaFile = JavaFile.builder(packageName, result.build())
        .addFileComment(AdapterJavadocs.GENERATED_BY_DAGGER)
        .build();
    javaFile.writeTo(processingEnv.getFiler());
  }

  /**
   * Write a companion class for {@code type} that extends {@link StaticInjection}.
   */
  private void generateStaticInjection(TypeElement type, List<Element> fields) throws IOException {
    ClassName typeName = ClassName.get(type);
    ClassName adapterClassName = adapterName(ClassName.get(type), STATIC_INJECTION_SUFFIX);

    TypeSpec.Builder result = TypeSpec.classBuilder(adapterClassName.simpleName())
        .addOriginatingElement(type)
        .addJavadoc(AdapterJavadocs.STATIC_INJECTION_TYPE, type)
        .addModifiers(PUBLIC, FINAL)
        .superclass(StaticInjection.class);
    for (Element field : fields) {
      result.addField(memberBindingField(false, field));
    }
    result.addMethod(attachMethod(null, fields, false, typeName, null, true));
    result.addMethod(staticInjectMethod(fields, typeName));

    String packageName = getPackage(type).getQualifiedName().toString();
    JavaFile javaFile = JavaFile.builder(packageName, result.build())
        .addFileComment(AdapterJavadocs.GENERATED_BY_DAGGER)
        .build();
    javaFile.writeTo(processingEnv.getFiler());
  }

  private FieldSpec memberBindingField(boolean disambiguateFields, Element field) {
    return FieldSpec.builder(bindingOf(field.asType()), fieldName(disambiguateFields, field),
        PRIVATE).build();
  }

  private FieldSpec parameterBindingField(boolean disambiguateFields, VariableElement parameter) {
    return FieldSpec.builder(bindingOf(parameter.asType()),
        parameterName(disambiguateFields, parameter), PRIVATE).build();
  }

  private FieldSpec supertypeBindingField(TypeMirror supertype) {
    return FieldSpec.builder(bindingOf(supertype), "supertype", PRIVATE).build();
  }

  private MethodSpec writeInjectAdapterConstructor(ExecutableElement constructor, TypeElement type,
      ClassName strippedTypeName) {
    String key = (constructor != null)
        ? GeneratorKeys.get(type.asType())
        : null;
    String membersKey = GeneratorKeys.rawMembersKey(type.asType());
    boolean singleton = type.getAnnotation(Singleton.class) != null;

    return MethodSpec.constructorBuilder()
        .addModifiers(PUBLIC)
        .addStatement("super($S, $S, $N, $T.class)",
            key, membersKey, (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"), strippedTypeName)
        .build();
  }

  private MethodSpec attachMethod(ExecutableElement constructor,
      List<Element> fields, boolean disambiguateFields, ClassName typeName, TypeMirror supertype,
      boolean extendsBinding) throws IOException {
    MethodSpec.Builder result = MethodSpec.methodBuilder("attach")
        .addJavadoc(AdapterJavadocs.ATTACH_METHOD)
        .addModifiers(PUBLIC)
        .addParameter(Linker.class, "linker");

    if (extendsBinding) {
      result.addAnnotation(Override.class);
    }
    result.addAnnotation(Util.UNCHECKED);
    if (constructor != null) {
      for (VariableElement parameter : constructor.getParameters()) {
        result.addStatement(
            "$N = ($T) linker.requestBinding($S, $T.class, getClass().getClassLoader())",
            parameterName(disambiguateFields, parameter), bindingOf(parameter.asType()),
            GeneratorKeys.get(parameter), typeName);
      }
    }
    for (Element field : fields) {
      result.addStatement(
          "$N = ($T) linker.requestBinding($S, $T.class, getClass().getClassLoader())",
          fieldName(disambiguateFields, field), bindingOf(field.asType()),
          GeneratorKeys.get((VariableElement) field), typeName);
    }
    if (supertype != null) {
      result.addStatement(
          "$N = ($T) linker.requestBinding($S, $T.class, getClass().getClassLoader()"
              + ", false, true)",
          "supertype",
          bindingOf(supertype),
          GeneratorKeys.rawMembersKey(supertype), typeName);
    }
    return result.build();
  }

  private MethodSpec getDependenciesMethod(ExecutableElement constructor,
      List<Element> fields, boolean disambiguateFields, TypeMirror supertype,
      boolean extendsBinding) throws IOException {
    MethodSpec.Builder result = MethodSpec.methodBuilder("getDependencies")
        .addJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD)
        .addModifiers(PUBLIC)
        .addParameter(SET_OF_BINDINGS, "getBindings")
        .addParameter(SET_OF_BINDINGS, "injectMembersBindings");

    if (extendsBinding) {
      result.addAnnotation(Override.class);
    }
    if (constructor != null) {
      for (Element parameter : constructor.getParameters()) {
        result.addStatement("getBindings.add($N)", parameterName(disambiguateFields, parameter));
      }
    }
    for (Element field : fields) {
      result.addStatement("injectMembersBindings.add($N)", fieldName(disambiguateFields, field));
    }
    if (supertype != null) {
      result.addStatement("injectMembersBindings.add($N)", "supertype");
    }
    return result.build();
  }

  private MethodSpec getMethod(ExecutableElement constructor, boolean disambiguateFields,
      boolean injectMembers, ClassName injectedClassName) {
    MethodSpec.Builder result = MethodSpec.methodBuilder("get")
        .addJavadoc(AdapterJavadocs.GET_METHOD, injectedClassName)
        .addAnnotation(Override.class)
        .returns(injectedClassName)
        .addModifiers(PUBLIC);

    result.addCode("$T result = new $T(", injectedClassName, injectedClassName);
    boolean first = true;
    for (VariableElement parameter : constructor.getParameters()) {
      if (!first) result.addCode(", ");
      else first = false;
      result.addCode("$N.get()", parameterName(disambiguateFields, parameter));
    }
    result.addCode(");\n");
    if (injectMembers) {
      result.addStatement("injectMembers(result)");
    }
    result.addStatement("return result");
    return result.build();
  }

  private MethodSpec membersInjectMethod(List<Element> fields, boolean disambiguateFields,
      ClassName injectedClassName, TypeMirror supertype) {
    MethodSpec.Builder result = MethodSpec.methodBuilder("injectMembers")
        .addJavadoc(AdapterJavadocs.MEMBERS_INJECT_METHOD, injectedClassName)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .addParameter(injectedClassName, "object");
    for (Element field : fields) {
      result.addStatement("object.$N = $N.get()",
          field.getSimpleName(),
          fieldName(disambiguateFields, field));
    }
    if (supertype != null) {
      result.addStatement("supertype.injectMembers(object)");
    }
    return result.build();
  }

  private MethodSpec staticInjectMethod(List<Element> fields, ClassName typeName) {
    MethodSpec.Builder result = MethodSpec.methodBuilder("inject")
        .addJavadoc(AdapterJavadocs.STATIC_INJECT_METHOD, ObjectGraph.class)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC);
    for (Element field : fields) {
      result.addStatement("$T.$N = $N.get()",
          typeName,
          field.getSimpleName().toString(),
          fieldName(false, field));
    }
    return result.build();
  }

  private String fieldName(boolean disambiguateFields, Element field) {
    return (disambiguateFields ? "field_" : "") + field.getSimpleName().toString();
  }

  private String parameterName(boolean disambiguateFields, Element parameter) {
    return (disambiguateFields ? "parameter_" : "") + parameter.getSimpleName().toString();
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
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
