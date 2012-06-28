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

import java.util.List;
import java.util.Map;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Creates keys using javac's mirror APIs.
 *
 * @author Jesse Wilson
 */
public final class GeneratorKeys {
  GeneratorKeys() {
  }

  public static String get(ExecutableElement method) {
    StringBuilder result = new StringBuilder();
    AnnotationMirror qualifier = getQualifier(method.getAnnotationMirrors(), method);
    if (qualifier != null) {
      qualifierToString(qualifier, result);
    }
    typeToString(method.getReturnType(), result);
    return result.toString();
  }

  public static String get(VariableElement parameter) {
    StringBuilder result = new StringBuilder();
    AnnotationMirror qualifier = getQualifier(parameter.getAnnotationMirrors(), parameter);
    if (qualifier != null) {
      qualifierToString(qualifier, result);
    }
    typeToString(parameter.asType(), result);
    return result.toString();
  }

  private static void qualifierToString(AnnotationMirror qualifier, StringBuilder result) {
    // TODO: guarantee that element values are sorted by name (if there are multiple)
    result.append('@');
    result.append(((TypeElement) qualifier.getAnnotationType().asElement()).getQualifiedName());
    result.append('(');
    for (Map.Entry<? extends ExecutableElement,? extends AnnotationValue> entry
        : qualifier.getElementValues().entrySet()) {
      result.append(entry.getKey().getSimpleName());
      result.append('=');
      result.append(entry.getValue().getValue());
    }
    result.append(")/");
  }

  private static AnnotationMirror getQualifier(
      List<? extends AnnotationMirror> annotations, Object member) {
    AnnotationMirror qualifier = null;
    for (AnnotationMirror annotation : annotations) {
      if (annotation.getAnnotationType().asElement().getAnnotation(Qualifier.class) == null) {
        continue;
      }
      if (qualifier != null) {
        throw new IllegalArgumentException("Too many qualifier annotations on " + member);
      }
      qualifier = annotation;
    }
    return qualifier;
  }

  private static void typeToString(TypeMirror type, StringBuilder result) {
    if (type instanceof DeclaredType) {
      DeclaredType declaredType = (DeclaredType) type;
      result.append(((TypeElement) declaredType.asElement()).getQualifiedName().toString());
      List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
      if (!typeArguments.isEmpty()) {
        result.append("<");
        for (int i = 0; i < typeArguments.size(); i++) {
          if (i != 0) {
            result.append(", ");
          }
          typeToString(typeArguments.get(i), result);
        }
        result.append(">");
      }
    } else {
      throw new UnsupportedOperationException("Uninjectable type " + type);
    }
  }
}
