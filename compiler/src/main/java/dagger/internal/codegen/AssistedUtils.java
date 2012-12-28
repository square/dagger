package dagger.internal.codegen;

import dagger.Assisted;
import dagger.Factory;
import dagger.internal.IndexedSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
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

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 28.12.12
 * Time: 10:38
 */
public final class AssistedUtils {
  public static List<VariableElement> getAssistedFields(TypeElement type) {
    List<VariableElement> elements = new ArrayList<VariableElement>();

    addAssistedFields(type, elements);

    return elements;
  }

  public static FactoryMethod findFactoryMethod(ProcessingEnvironment env,
      ExecutableElement provideMethod, Factory factoryAnnotation) {
    TypeElement factory =
        env.getElementUtils().getTypeElement(factoryAnnotation.value().getCanonicalName());

    TypeMirror returnType = provideMethod.getReturnType();

    List<? extends VariableElement> params = provideMethod.getParameters();
    if (params.size() != 1) {
      throw new AssertionError("@Factory method " + provideMethod
          + " must have only one parameter");
    }

    TypeElement type = mirrorToElement(params.get(0).asType());

    return findFactoryMethod(factory, type, returnType);
  }

  public static FactoryMethod findFactoryMethod(TypeElement factory, TypeElement type,
      TypeMirror returnType) {
    String returnTypeKey = GeneratorKeys.get(returnType);
    List<VariableElement> parameters = getAllAssistedParams(type);
    IndexedSet<String> keys = new IndexedSet<String>();

    for (VariableElement param : parameters) {
      keys.add(GeneratorKeys.get(param));
    }

    findMethod:
    for (Element element : factory.getEnclosedElements()) {
      if (element.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) element;
      List<? extends VariableElement> methodParameters = method.getParameters();
      String key = GeneratorKeys.get(method);
      if (methodParameters.size() != parameters.size()
          && !returnTypeKey.equals(key)) {
        continue;
      }

      List<Integer> transposition = new ArrayList<Integer>();
      for (VariableElement param : methodParameters) {
        int index = keys.getIndexOf(GeneratorKeys.get(param));
        if (index == -1) {
          continue findMethod;
        }

        transposition.add(index);
      }

      return new FactoryMethod(method, transposition, type, factory);
    }
    throw new AssertionError("Not found factory method for " + returnType.toString()
        + " in " + factory.getQualifiedName().toString());
  }

  public static class FactoryMethod {
    private final ExecutableElement method;
    private final List<Integer> transposition;
    private final TypeElement type;
    private final TypeElement factory;

    public FactoryMethod(ExecutableElement method, List<Integer> transposition,
        TypeElement type, TypeElement factory) {
      this.method = method;
      this.transposition = transposition;
      this.type = type;
      this.factory = factory;
    }

    public List<Integer> getTransposition() {
      return transposition;
    }

    public ExecutableElement getMethod() {
      return method;
    }

    public TypeElement getType() {
      return type;
    }

    public TypeElement getFactory() {
      return factory;
    }
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
