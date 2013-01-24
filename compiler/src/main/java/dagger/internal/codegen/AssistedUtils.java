package dagger.internal.codegen;

import dagger.Assisted;
import dagger.Factory;
import dagger.internal.IndexedUniqueSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AssistedUtils {
  private AssistedUtils() {
  }

  public static List<VariableElement> getAssistedFields(TypeElement type) {
    List<VariableElement> elements = new ArrayList<VariableElement>();

    addAssistedFields(type, elements);

    return elements;
  }

  public static TypeMirror extractFactoryType(ExecutableElement provideMethod) {
    for (AnnotationMirror annotation : provideMethod.getAnnotationMirrors()) {
      if (!annotation.getAnnotationType().toString().equals(Factory.class.getName())) {
        continue;
      }

      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
          : annotation.getElementValues().entrySet()) {
        if ("value".equals(e.getKey().getSimpleName().toString())) {
          return (TypeMirror) e.getValue().getValue();
        }
      }
    }
    throw new AssertionError("@Factory annotation not found");
  }

  public static String factoryKey(ExecutableElement provideMethod) {
    TypeMirror factoryType = extractFactoryType(provideMethod);
    return GeneratorKeys.get(factoryType, provideMethod);
  }

  public static boolean isFactoryProvider(ExecutableElement provideMethod) {
    for (AnnotationMirror annotation : provideMethod.getAnnotationMirrors()) {
      if (annotation.getAnnotationType().toString().equals(Factory.class.getName())) {
        return true;
      }
    }
    return false;
  }

  public static FactoryMethod findFactoryMethod(ProcessingEnvironment env,
      ExecutableElement providerMethod) {

    TypeElement factory = mirrorToElement(extractFactoryType(providerMethod));

    if (factory.getKind() != ElementKind.INTERFACE) {
      throw new AssertionError("Factory must be an interface");
    }

    TypeMirror returnType = providerMethod.getReturnType();

    List<? extends VariableElement> params = providerMethod.getParameters();
    if (params.size() != 1) {
      throw new AssertionError("@Factory method " + providerMethod
          + " must have only one parameter");
    }

    if (!env.getTypeUtils().isAssignable(params.get(0).asType(), returnType)) {
      throw new AssertionError("@Factory method " + providerMethod
          + " must have parameter which is assignable to return type");
    }

    TypeElement type = mirrorToElement(params.get(0).asType());

    return findFactoryMethod(factory, type, returnType);
  }

  public static FactoryMethod findFactoryMethod(TypeElement factory, TypeElement type,
      TypeMirror returnType) {
    List<VariableElement> parameters = getAllAssistedParams(type);
    IndexedUniqueSet<String> keys = new IndexedUniqueSet<String>();

    for (VariableElement param : parameters) {
      keys.add(GeneratorKeys.get(param));
    }

    FactoryMethod factoryMethod = null;
    int methodCount = 0;

    findMethod:
    for (Element element : factory.getEnclosedElements()) {
      if (element.getKind() != ElementKind.METHOD) {
        continue;
      }

      methodCount++;
      if (methodCount > 1) {
        throw new AssertionError("Factory interface must have only one method");
      }

      ExecutableElement method = (ExecutableElement) element;
      List<? extends VariableElement> methodParameters = method.getParameters();
      if (methodParameters.size() != parameters.size()
          && !returnType.equals(method.getReturnType())) {
        continue;
      }

      List<Integer> transposition = new ArrayList<Integer>();
      for (VariableElement param : methodParameters) {
        String paramKey = GeneratorKeys.get(param);
        if (!GeneratorKeys.isAssisted(paramKey)) {
          paramKey = GeneratorKeys.getWithDefaultAssisted(param.asType());
        }
        int index = keys.indexOf(paramKey);
        if (index == -1) {
          continue findMethod;
        }

        transposition.add(index);
      }

      factoryMethod = new FactoryMethod(method, transposition, type, factory);
    }
    if (factoryMethod != null) {
      return factoryMethod;
    }
    throw new AssertionError("Factory method for " + returnType.toString()
        + " in " + factory.getQualifiedName().toString() + " not found");
  }

  public static List<VariableElement> getAllAssistedParams(TypeElement type) {
    List<VariableElement> elements = new ArrayList<VariableElement>();

    addAssistedFields(type, elements);

    for (Element member : type.getEnclosedElements()) {
      if (member.getAnnotation(Inject.class) == null
          || member.getKind() != ElementKind.CONSTRUCTOR) {
        continue;
      }

      ExecutableElement constructor = (ExecutableElement) member;
      for (VariableElement parameter : constructor.getParameters()) {
        if (parameter.getAnnotation(Assisted.class) != null) {
          elements.add(parameter);
        }
      }
    }
    return elements;
  }

  public static TypeElement mirrorToElement(TypeMirror typeMirror) {
    return (TypeElement) ((DeclaredType) typeMirror).asElement();
  }

  public static TypeElement getSuperclassElement(TypeElement type) {
    TypeMirror typeMirror = CodeGen.getApplicationSupertype(type);
    if (typeMirror == null || typeMirror.getKind() == TypeKind.NONE) {
      return null;
    }
    return mirrorToElement(typeMirror);
  }

  private static void addAssistedFields(TypeElement type, Collection<VariableElement> elements) {
    TypeElement superclass = getSuperclassElement(type);
    if (superclass != null) {
      addAssistedFields(superclass, elements);
    }

    for (Element member : type.getEnclosedElements()) {
      if (member.getAnnotation(Inject.class) != null
          && member.getKind() == ElementKind.FIELD
          && member.getAnnotation(Assisted.class) != null) {
        elements.add((VariableElement) member);
      }
    }
  }
}
