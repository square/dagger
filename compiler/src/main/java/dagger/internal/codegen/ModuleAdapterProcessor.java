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

import com.squareup.javawriter.JavaWriter;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Linker;
import dagger.internal.ModuleAdapter;
import dagger.internal.SetBinding;
import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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
import javax.tools.JavaFileObject;

import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.AdapterJavadocs.bindingTypeDocs;
import static dagger.internal.codegen.Util.adapterName;
import static dagger.internal.codegen.Util.elementToString;
import static dagger.internal.codegen.Util.getAnnotation;
import static dagger.internal.codegen.Util.getNoArgsConstructor;
import static dagger.internal.codegen.Util.getPackage;
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
  private static final String BINDINGS_MAP = JavaWriter.type(
      Map.class, String.class.getCanonicalName(), Binding.class.getCanonicalName() + "<?>");
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

        //TODO(cgruber): Figure out an initial sizing of the StringWriter.
        StringWriter stringWriter = new StringWriter();
        String adapterName = adapterName(type, MODULE_ADAPTER_SUFFIX);
        generateModuleAdapter(stringWriter, adapterName, type, parsedAnnotation, providesTypes);
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(adapterName, type);
        Writer sourceWriter = sourceFile.openWriter();
        sourceWriter.append(stringWriter.getBuffer());
        sourceWriter.close();
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
      if (!moduleType.getSuperclass().equals(objectType)) {
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
  private void generateModuleAdapter(Writer ioWriter, String adapterName, TypeElement type,
      Map<String, Object> module, List<ExecutableElement> providerMethods) throws IOException {
    if (module == null) {
      error(type + " has @Provides methods but no @Module annotation", type);
      return;
    }

    Object[] staticInjections = (Object[]) module.get("staticInjections");
    Object[] injects = (Object[]) module.get("injects");
    Object[] includes = (Object[]) module.get("includes");

    boolean overrides = (Boolean) module.get("overrides");
    boolean complete = (Boolean) module.get("complete");
    boolean library = (Boolean) module.get("library");

    JavaWriter writer = new JavaWriter(ioWriter);

    boolean multibindings = checkForMultibindings(providerMethods);
    boolean providerMethodDependencies = checkForDependencies(providerMethods);

    writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
    writer.emitPackage(getPackage(type).getQualifiedName().toString());
    writer.emitImports(
        findImports(multibindings, !providerMethods.isEmpty(), providerMethodDependencies));

    String typeName = type.getQualifiedName().toString();
    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.MODULE_TYPE);
    writer.beginType(adapterName, "class", EnumSet.of(PUBLIC, FINAL),
        JavaWriter.type(ModuleAdapter.class, typeName));

    StringBuilder injectsField = new StringBuilder().append("{ ");
    for (Object injectableType : injects) {
      TypeMirror typeMirror = (TypeMirror) injectableType;
      String key = isInterface(typeMirror)
          ? GeneratorKeys.get(typeMirror)
          : GeneratorKeys.rawMembersKey(typeMirror);
      injectsField.append(JavaWriter.stringLiteral(key)).append(", ");
    }
    injectsField.append("}");
    writer.emitField("String[]", "INJECTS", EnumSet.of(PRIVATE, STATIC, FINAL),
        injectsField.toString());

    StringBuilder staticInjectionsField = new StringBuilder().append("{ ");
    for (Object staticInjection : staticInjections) {
      TypeMirror typeMirror = (TypeMirror) staticInjection;
      staticInjectionsField.append(typeToString(typeMirror)).append(".class, ");
    }
    staticInjectionsField.append("}");
    writer.emitField("Class<?>[]", "STATIC_INJECTIONS", EnumSet.of(PRIVATE, STATIC, FINAL),
        staticInjectionsField.toString());

    StringBuilder includesField = new StringBuilder().append("{ ");
    for (Object include : includes) {
      if (!(include instanceof TypeMirror)) {
        // TODO(tbroyer): pass annotation information
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value: " + include + " in includes of " + type, type);
        continue;
      }
      TypeMirror typeMirror = (TypeMirror) include;
      includesField.append(typeToString(typeMirror)).append(".class, ");
    }
    includesField.append("}");
    writer.emitField(
        "Class<?>[]", "INCLUDES", EnumSet.of(PRIVATE, STATIC, FINAL), includesField.toString());

    writer.emitEmptyLine();
    writer.beginMethod(null, adapterName, EnumSet.of(PUBLIC));
    writer.emitStatement("super(INJECTS, STATIC_INJECTIONS, %s /*overrides*/, "
        + "INCLUDES, %s /*complete*/, %s /*library*/)", overrides, complete, library);
    writer.endMethod();

    ExecutableElement noArgsConstructor = getNoArgsConstructor(type);
    if (noArgsConstructor != null && isCallableConstructor(noArgsConstructor)) {
      writer.emitEmptyLine();
      writer.emitAnnotation(Override.class);
      writer.beginMethod(typeName, "newModule", EnumSet.of(PUBLIC));
      writer.emitStatement("return new %s()", typeName);
      writer.endMethod();
    }
    // caches
    Map<ExecutableElement, String> methodToClassName
        = new LinkedHashMap<ExecutableElement, String>();
    Map<String, AtomicInteger> methodNameToNextId = new LinkedHashMap<String, AtomicInteger>();

    if (!providerMethods.isEmpty()) {
      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD);
      writer.emitAnnotation(Override.class);
      writer.beginMethod("void", "getBindings", EnumSet.of(PUBLIC), BINDINGS_MAP, "map");

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
            String key = GeneratorKeys.getSetKey(providerMethod);
            writer.emitStatement("SetBinding.add(map, %s, new %s(module))",
                JavaWriter.stringLiteral(key),
                bindingClassName(providerMethod, methodToClassName, methodNameToNextId));
            break;
          }
          case SET_VALUES: {
            String key = GeneratorKeys.get(providerMethod);
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
    }

    for (ExecutableElement providerMethod : providerMethods) {
      generateProvidesAdapter(
          writer, providerMethod, methodToClassName, methodNameToNextId, library);
    }

    writer.endType();
    writer.close();
  }

  private Set<String> findImports(boolean multibindings, boolean providers, boolean dependencies) {
    Set<String> imports = new LinkedHashSet<String>();
    imports.add(ModuleAdapter.class.getCanonicalName());
    if (providers) {
      imports.add(Binding.class.getCanonicalName());
      imports.add(Map.class.getCanonicalName());
      imports.add(Provider.class.getCanonicalName());
    }
    if (dependencies) {
      imports.add(Linker.class.getCanonicalName());
      imports.add(Set.class.getCanonicalName());
    }
    if (multibindings) {
      imports.add(SetBinding.class.getCanonicalName());
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
      Provides.Type providesType = element.getAnnotation(Provides.class).type();
      if (providesType == SET || providesType == SET_VALUES) {
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

  private void generateProvidesAdapter(JavaWriter writer, ExecutableElement providerMethod,
      Map<ExecutableElement, String> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId, boolean library)
      throws IOException {
    String methodName = providerMethod.getSimpleName().toString();
    String moduleType = typeToString(providerMethod.getEnclosingElement().asType());
    String className =
        bindingClassName(providerMethod, methodToClassName, methodNameToNextId);
    String returnType = typeToString(providerMethod.getReturnType());
    List<? extends VariableElement> parameters = providerMethod.getParameters();
    boolean dependent = !parameters.isEmpty();

    writer.emitEmptyLine();
    writer.emitJavadoc(bindingTypeDocs(returnType, false, false, dependent));
    writer.beginType(className, "class", EnumSet.of(PUBLIC, STATIC, FINAL),
        JavaWriter.type(Binding.class, returnType),
        JavaWriter.type(Provider.class, returnType));
    writer.emitField(moduleType, "module", EnumSet.of(PRIVATE, FINAL));
    for (Element parameter : parameters) {
      TypeMirror parameterType = parameter.asType();
      writer.emitField(JavaWriter.type(Binding.class, typeToString(parameterType)),
          parameterName(parameter), EnumSet.of(PRIVATE));
    }

    writer.emitEmptyLine();
    writer.beginMethod(null, className, EnumSet.of(PUBLIC), moduleType, "module");
    boolean singleton = providerMethod.getAnnotation(Singleton.class) != null;
    String key = JavaWriter.stringLiteral(GeneratorKeys.get(providerMethod));
    String membersKey = null;
    writer.emitStatement("super(%s, %s, %s, %s)",
        key, membersKey, (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"),
        JavaWriter.stringLiteral(moduleType + "." + methodName + "()"));
    writer.emitStatement("this.module = module");
    writer.emitStatement("setLibrary(%s)", library);
    writer.endMethod();

    if (dependent) {
      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.ATTACH_METHOD);
      writer.emitAnnotation(Override.class);
      writer.emitAnnotation(SuppressWarnings.class, JavaWriter.stringLiteral("unchecked"));
      writer.beginMethod(
          "void", "attach", EnumSet.of(PUBLIC), Linker.class.getCanonicalName(), "linker");
      for (VariableElement parameter : parameters) {
        String parameterKey = GeneratorKeys.get(parameter);
        writer.emitStatement(
            "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader())",
            parameterName(parameter),
            writer.compressType(JavaWriter.type(Binding.class, typeToString(parameter.asType()))),
            JavaWriter.stringLiteral(parameterKey),
            writer.compressType(moduleType));
      }
      writer.endMethod();

      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD);
      writer.emitAnnotation(Override.class);
      String setOfBindings = JavaWriter.type(Set.class, "Binding<?>");
      writer.beginMethod("void", "getDependencies", EnumSet.of(PUBLIC), setOfBindings,
          "getBindings", setOfBindings, "injectMembersBindings");
      for (Element parameter : parameters) {
        writer.emitStatement("getBindings.add(%s)", parameter.getSimpleName().toString());
      }
      writer.endMethod();
    }

    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.GET_METHOD, returnType);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(returnType, "get", EnumSet.of(PUBLIC));
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
    if (parameter.getSimpleName().contentEquals("module")) {
      return "parameter_" + parameter.getSimpleName().toString();
    }
    return parameter.getSimpleName().toString();
  }
}
