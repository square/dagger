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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.internal.BindingsGroup;
import dagger.internal.Linker;
import dagger.internal.ModuleAdapter;
import dagger.internal.ProvidesBinding;
import dagger.internal.SetBinding;
import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.inject.Provider;
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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static dagger.internal.codegen.AdapterJavadocs.bindingTypeDocs;
import static dagger.internal.codegen.Util.ARRAY_OF_CLASS;
import static dagger.internal.codegen.Util.bindingOf;
import static dagger.internal.codegen.Util.elementToString;
import static dagger.internal.codegen.Util.getAnnotation;
import static dagger.internal.codegen.Util.getNoArgsConstructor;
import static dagger.internal.codegen.Util.isCallableConstructor;
import static dagger.internal.codegen.Util.isInterface;
import static dagger.internal.codegen.Util.typeToString;
import static dagger.internal.loaders.GeneratedAdapters.MODULE_ADAPTER_SUFFIX;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates an implementation of {@link ModuleAdapter} that includes a binding
 * for each {@code @Provides} method of a target class.
 */
@SupportedAnnotationTypes({ "*" })
public final class ModuleAdapterProcessor extends AbstractProcessor {
  private static final List<String> INVALID_RETURN_TYPES =
      Arrays.asList(Provider.class.getCanonicalName(), Lazy.class.getCanonicalName());

  private final LinkedHashMap<String, List<ExecutableElement>> remainingTypes =
      new LinkedHashMap<String, List<ExecutableElement>>();

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    remainingTypes.putAll(providerMethodsByClass(env));
    for (Iterator<String> i = remainingTypes.keySet().iterator(); i.hasNext();) {
      String typeName = i.next();
      TypeElement type = processingEnv.getElementUtils().getTypeElement(typeName);
      List<ExecutableElement> providesTypes = remainingTypes.get(typeName);

      try {
        // Attempt to get the annotation. If types are missing, this will throw
        // CodeGenerationIncompleteException.
        Map<String, Object> parsedAnnotation = getAnnotation(Module.class, type);
        if (parsedAnnotation == null) {
          error(type + " has @Provides methods but no @Module annotation", type);
          continue;
        }
        JavaFile javaFile = generateModuleAdapter(type, parsedAnnotation, providesTypes);
        javaFile.writeTo(processingEnv.getFiler());
      } catch (CodeGenerationIncompleteException e) {
        continue; // A dependent type was not defined, we'll try to catch it on another pass.
      } catch (IOException e) {
        error("Code gen failed: " + e, type);
      }
      i.remove();
    }
    if (env.processingOver() && remainingTypes.size() > 0) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Could not find types required by provides methods for " + remainingTypes.keySet());
    }
    return false; // FullGraphProcessor needs an opportunity to process.
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }

  /**
   * Returns a map containing all {@code @Provides} methods, indexed by class.
   */
  private Map<String, List<ExecutableElement>> providerMethodsByClass(RoundEnvironment env) {
    Elements elementUtils = processingEnv.getElementUtils();
    Types types = processingEnv.getTypeUtils();

    Map<String, List<ExecutableElement>> result = new HashMap<String, List<ExecutableElement>>();

    provides:
    for (Element providerMethod : findProvidesMethods(env)) {
      switch (providerMethod.getEnclosingElement().getKind()) {
        case CLASS:
          break; // valid, move along
        default:
          // TODO(tbroyer): pass annotation information
          error("Unexpected @Provides on " + elementToString(providerMethod), providerMethod);
          continue;
      }
      TypeElement type = (TypeElement) providerMethod.getEnclosingElement();
      Set<Modifier> typeModifiers = type.getModifiers();
      if (typeModifiers.contains(PRIVATE)
          || typeModifiers.contains(ABSTRACT)) {
        error("Classes declaring @Provides methods must not be private or abstract: "
                + type.getQualifiedName(), type);
        continue;
      }

      Set<Modifier> methodModifiers = providerMethod.getModifiers();
      if (methodModifiers.contains(PRIVATE)
          || methodModifiers.contains(ABSTRACT)
          || methodModifiers.contains(STATIC)) {
        error("@Provides methods must not be private, abstract or static: "
                + type.getQualifiedName() + "." + providerMethod, providerMethod);
        continue;
      }

      ExecutableElement providerMethodAsExecutable = (ExecutableElement) providerMethod;
      if (!providerMethodAsExecutable.getThrownTypes().isEmpty()) {
        error("@Provides methods must not have a throws clause: "
            + type.getQualifiedName() + "." + providerMethod, providerMethod);
        continue;
      }

      // Invalidate return types.
      TypeMirror returnType = types.erasure(providerMethodAsExecutable.getReturnType());
      if (!returnType.getKind().equals(TypeKind.ERROR)) {
        // Validate if we have a type to validate (a type yet to be generated by other
        // processors is not "invalid" in this way, so ignore).
        for (String invalidTypeName : INVALID_RETURN_TYPES) {
          TypeElement invalidTypeElement = elementUtils.getTypeElement(invalidTypeName);
          if (invalidTypeElement != null && types.isSameType(returnType,
              types.erasure(invalidTypeElement.asType()))) {
            error(String.format("@Provides method must not return %s directly: %s.%s",
                invalidTypeElement, type.getQualifiedName(), providerMethod), providerMethod);
            continue provides; // Skip to next provides method.
          }
        }
      }

      List<ExecutableElement> methods = result.get(type.getQualifiedName().toString());
      if (methods == null) {
        methods = new ArrayList<ExecutableElement>();
        result.put(type.getQualifiedName().toString(), methods);
      }
      methods.add(providerMethodAsExecutable);
    }

    TypeMirror objectType = elementUtils.getTypeElement("java.lang.Object").asType();

    // Catch any stray modules without @Provides since their injectable types
    // should still be registered and a ModuleAdapter should still be written.
    for (Element module : env.getElementsAnnotatedWith(Module.class)) {
      if (!module.getKind().equals(ElementKind.CLASS)) {
        error("Modules must be classes: " + elementToString(module), module);
        continue;
      }

      TypeElement moduleType = (TypeElement) module;

      // Verify that all modules do not extend from non-Object types.
      if (!types.isSameType(moduleType.getSuperclass(), objectType)) {
        error("Modules must not extend from other classes: " + elementToString(module), module);
      }

      String moduleName = moduleType.getQualifiedName().toString();
      if (result.containsKey(moduleName)) continue;
      result.put(moduleName, new ArrayList<ExecutableElement>());
    }
    return result;
  }

  private Set<? extends Element> findProvidesMethods(RoundEnvironment env) {
    Set<Element> result = new LinkedHashSet<Element>();
    result.addAll(env.getElementsAnnotatedWith(Provides.class));
    return result;
  }

  /**
   * Write a companion class for {@code type} that implements {@link
   * ModuleAdapter} to expose its provider methods.
   */
  private JavaFile generateModuleAdapter(TypeElement type,
      Map<String, Object> module, List<ExecutableElement> providerMethods) {
    Object[] staticInjections = (Object[]) module.get("staticInjections");
    Object[] injects = (Object[]) module.get("injects");
    Object[] includes = (Object[]) module.get("includes");
    boolean overrides = (Boolean) module.get("overrides");
    boolean complete = (Boolean) module.get("complete");
    boolean library = (Boolean) module.get("library");

    List<Object> duplicateInjects = extractDuplicates(injects);
    if (!duplicateInjects.isEmpty()) {
      error("'injects' list contains duplicate entries: " + duplicateInjects, type);
    }
    List<Object> duplicateIncludes = extractDuplicates(includes);
    if (!duplicateIncludes.isEmpty()) {
      error("'includes' list contains duplicate entries: " + duplicateIncludes, type);
    }

    ClassName moduleClassName = ClassName.get(type);
    ClassName adapterClassName = Util.adapterName(moduleClassName, MODULE_ADAPTER_SUFFIX);

    TypeSpec.Builder adapterBuilder = TypeSpec.classBuilder(adapterClassName.simpleName())
        .addOriginatingElement(type)
        .addJavadoc(AdapterJavadocs.MODULE_TYPE, Provides.class)
        .superclass(ParameterizedTypeName.get(ClassName.get(ModuleAdapter.class), moduleClassName))
        .addModifiers(PUBLIC, FINAL);

    adapterBuilder.addField(FieldSpec.builder(String[].class, "INJECTS")
        .addModifiers(PRIVATE, STATIC, FINAL)
        .initializer("$L", injectsInitializer(injects))
        .build());
    adapterBuilder.addField(FieldSpec.builder(ARRAY_OF_CLASS, "STATIC_INJECTIONS")
        .addModifiers(PRIVATE, STATIC, FINAL)
        .initializer("$L", staticInjectionsInitializer(staticInjections))
        .build());
    adapterBuilder.addField(FieldSpec.builder(ARRAY_OF_CLASS, "INCLUDES")
        .addModifiers(PRIVATE, STATIC, FINAL)
        .initializer("$L", includesInitializer(type, includes))
        .build());
    adapterBuilder.addMethod(MethodSpec.constructorBuilder()
        .addModifiers(PUBLIC)
        .addStatement("super($T.class, INJECTS, STATIC_INJECTIONS, $L /*overrides*/, "
                + "INCLUDES, $L /*complete*/, $L /*library*/)",
            type.asType(), overrides, complete, library)
        .build());

    ExecutableElement noArgsConstructor = getNoArgsConstructor(type);
    if (noArgsConstructor != null && isCallableConstructor(noArgsConstructor)) {
      adapterBuilder.addMethod(MethodSpec.methodBuilder("newModule")
          .addAnnotation(Override.class)
          .addModifiers(PUBLIC)
          .returns(moduleClassName)
          .addStatement("return new $T()", type.asType())
          .build());
    }

    // Caches.
    Map<ExecutableElement, ClassName> methodToClassName
        = new LinkedHashMap<ExecutableElement, ClassName>();
    Map<String, AtomicInteger> methodNameToNextId = new LinkedHashMap<String, AtomicInteger>();

    if (!providerMethods.isEmpty()) {
      MethodSpec.Builder getBindings = MethodSpec.methodBuilder("getBindings")
          .addJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD)
          .addAnnotation(Override.class)
          .addModifiers(PUBLIC)
          .addParameter(BindingsGroup.class, "bindings")
          .addParameter(moduleClassName, "module");

      for (ExecutableElement providerMethod : providerMethods) {
        Provides provides = providerMethod.getAnnotation(Provides.class);
        switch (provides.type()) {
          case UNIQUE: {
            getBindings.addStatement("bindings.contributeProvidesBinding($S, new $T(module))",
                GeneratorKeys.get(providerMethod),
                bindingClassName(adapterClassName, providerMethod, methodToClassName,
                    methodNameToNextId));
            break;
          }
          case SET: {
            getBindings.addStatement("$T.add(bindings, $S, new $T(module))",
                SetBinding.class,
                GeneratorKeys.getSetKey(providerMethod),
                bindingClassName(adapterClassName, providerMethod, methodToClassName,
                    methodNameToNextId));
            break;
          }
          case SET_VALUES: {
            getBindings.addStatement("$T.add(bindings, $S, new $T(module))",
                SetBinding.class,
                GeneratorKeys.get(providerMethod),
                bindingClassName(adapterClassName, providerMethod, methodToClassName,
                    methodNameToNextId));
            break;
          }
          default:
            throw new AssertionError("Unknown @Provides type " + provides.type());
        }
      }
      adapterBuilder.addMethod(getBindings.build());
    }

    for (ExecutableElement providerMethod : providerMethods) {
      adapterBuilder.addType(generateProvidesAdapter(moduleClassName, adapterClassName,
          providerMethod, methodToClassName, methodNameToNextId, library));
    }

    return JavaFile.builder(adapterClassName.packageName(), adapterBuilder.build())
        .addFileComment(AdapterJavadocs.GENERATED_BY_DAGGER)
        .build();
  }

  private static List<Object> extractDuplicates(Object[] items) {
    List<Object> itemsList = Arrays.asList(items);
    List<Object> duplicateItems = new ArrayList<Object>(itemsList);
    for (Object item : new LinkedHashSet<Object>(itemsList)) {
      duplicateItems.remove(item); // Not using removeAll since we only want one element removed.
    }
    return duplicateItems;
  }

  private CodeBlock injectsInitializer(Object[] injects) {
    CodeBlock.Builder result = CodeBlock.builder()
        .add("{ ");
    for (Object injectableType : injects) {
      TypeMirror typeMirror = (TypeMirror) injectableType;
      String key = isInterface(typeMirror)
          ? GeneratorKeys.get(typeMirror)
          : GeneratorKeys.rawMembersKey(typeMirror);
      result.add("$S, ", key);
    }
    result.add("}");
    return result.build();
  }

  private CodeBlock staticInjectionsInitializer(Object[] staticInjections) {
    CodeBlock.Builder result = CodeBlock.builder()
        .add("{ ");
    for (Object staticInjection : staticInjections) {
      result.add("$T.class, ", staticInjection);
    }
    result.add("}");
    return result.build();
  }

  private CodeBlock includesInitializer(TypeElement type, Object[] includes) {
    CodeBlock.Builder result = CodeBlock.builder();
    result.add("{ ");
    for (Object include : includes) {
      if (!(include instanceof TypeMirror)) {
        // TODO(tbroyer): pass annotation information
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value: " + include + " in includes of " + type, type);
        continue;
      }
      TypeMirror typeMirror = (TypeMirror) include;
      result.add("$T.class, ", typeMirror);
    }
    result.add("}");
    return result.build();
  }

  private ClassName bindingClassName(ClassName adapterName, ExecutableElement providerMethod,
      Map<ExecutableElement, ClassName> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId) {
    ClassName className = methodToClassName.get(providerMethod);
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
    className = adapterName.nestedClass(uppercaseMethodName + "ProvidesAdapter" + suffix);
    methodToClassName.put(providerMethod, className);
    return className;
  }

  private TypeSpec generateProvidesAdapter(ClassName moduleClassName, ClassName adapterName,
      ExecutableElement providerMethod, Map<ExecutableElement, ClassName> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId, boolean library) {
    String methodName = providerMethod.getSimpleName().toString();
    TypeMirror moduleType = providerMethod.getEnclosingElement().asType();
    ClassName className = bindingClassName(
        adapterName, providerMethod, methodToClassName, methodNameToNextId);
    TypeName returnType = Util.injectableType(providerMethod.getReturnType());
    List<? extends VariableElement> parameters = providerMethod.getParameters();
    boolean dependent = !parameters.isEmpty();

    TypeSpec.Builder result = TypeSpec.classBuilder(className.simpleName())
        .addJavadoc("$L", bindingTypeDocs(returnType, false, false, dependent))
        .addModifiers(PUBLIC, STATIC, FINAL)
        .superclass(ParameterizedTypeName.get(ClassName.get(ProvidesBinding.class), returnType));

    result.addField(moduleClassName, "module", PRIVATE, FINAL);
    for (Element parameter : parameters) {
      result.addField(bindingOf(parameter.asType()), parameterName(parameter), PRIVATE);
    }

    boolean singleton = providerMethod.getAnnotation(Singleton.class) != null;
    String key = GeneratorKeys.get(providerMethod);
    result.addMethod(MethodSpec.constructorBuilder()
        .addModifiers(PUBLIC)
        .addParameter(moduleClassName, "module")
        .addStatement("super($S, $L, $S, $S)",
            key,
            (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"),
            typeToString(moduleType),
            methodName)
        .addStatement("this.module = module")
        .addStatement("setLibrary($L)", library)
        .build());

    if (dependent) {
      MethodSpec.Builder attachBuilder = MethodSpec.methodBuilder("attach")
          .addJavadoc(AdapterJavadocs.ATTACH_METHOD)
          .addAnnotation(Override.class)
          .addAnnotation(Util.UNCHECKED)
          .addModifiers(PUBLIC)
          .addParameter(Linker.class, "linker");
      for (VariableElement parameter : parameters) {
        String parameterKey = GeneratorKeys.get(parameter);
        attachBuilder.addStatement(
            "$N = ($T) linker.requestBinding($S, $T.class, getClass().getClassLoader())",
            parameterName(parameter),
            bindingOf(parameter.asType()),
            parameterKey,
            moduleClassName);
      }
      result.addMethod(attachBuilder.build());

      MethodSpec.Builder getDependenciesBuilder = MethodSpec.methodBuilder("getDependencies")
          .addJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD)
          .addAnnotation(Override.class)
          .addModifiers(PUBLIC)
          .addParameter(Util.SET_OF_BINDINGS, "getBindings")
          .addParameter(Util.SET_OF_BINDINGS, "injectMembersBindings");
      for (Element parameter : parameters) {
        getDependenciesBuilder.addStatement("getBindings.add($N)", parameterName(parameter));
      }
      result.addMethod(getDependenciesBuilder.build());
    }

    MethodSpec.Builder getBuilder = MethodSpec.methodBuilder("get")
        .addJavadoc(AdapterJavadocs.GET_METHOD, returnType)
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .returns(returnType)
        .addCode("return module.$N(", methodName);
    boolean first = true;
    for (Element parameter : parameters) {
      if (!first) getBuilder.addCode(", ");
      getBuilder.addCode("$N.get()", parameterName(parameter));
      first = false;
    }
    getBuilder.addCode(");\n");
    result.addMethod(getBuilder.build());

    return result.build();
  }

  private String parameterName(Element parameter) {
    if (parameter.getSimpleName().contentEquals("module")) {
      return "parameter_" + parameter.getSimpleName().toString();
    }
    return parameter.getSimpleName().toString();
  }
}
