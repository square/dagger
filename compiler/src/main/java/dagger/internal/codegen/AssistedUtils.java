package dagger.internal.codegen;

import dagger.Assisted;
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
