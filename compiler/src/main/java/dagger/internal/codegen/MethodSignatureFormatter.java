/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;

/**
 * Formats the signature of an {@link ExecutableElement} suitable for use in error messages.
 *
 * @author Christian Gruber
 * @since 2.0
 */
final class MethodSignatureFormatter extends Formatter<ExecutableElement> {
  private static final MethodSignatureFormatter INSTANCE = new MethodSignatureFormatter();

  static MethodSignatureFormatter instance() {
    return INSTANCE;
  }

  @Override public String format(ExecutableElement method) {
    StringBuilder builder = new StringBuilder();
    TypeElement type = MoreElements.asType(method.getEnclosingElement());

    // TODO(user): AnnotationMirror formatter.
    List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();
    if (!annotations.isEmpty()) {
      Iterator<? extends AnnotationMirror> annotationIterator = annotations.iterator();
      for (int i = 0; annotationIterator.hasNext(); i++) {
        if (i > 0) {
          builder.append(' ');
        }
        builder.append(ErrorMessages.format(annotationIterator.next()));
      }
      builder.append(' ');
    }
    builder.append(nameOfType(method.getReturnType()));
    builder.append(' ');
    builder.append(type.getQualifiedName());
    builder.append('.');
    builder.append(method.getSimpleName());
    builder.append('(');
    Iterator<? extends VariableElement> parameters = method.getParameters().iterator();
    for (int i = 0; parameters.hasNext(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      appendParameter(builder, parameters.next());
    }
    builder.append(')');
    return builder.toString();
  }

  private static void appendParameter(StringBuilder builder, VariableElement parameter) {
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(parameter);
    if (qualifier.isPresent()) {
      builder.append(ErrorMessages.format(qualifier.get())).append(' ');
    }
    builder.append(nameOfType(parameter.asType()));
  }

  private static String nameOfType(TypeMirror type) {
    if (type.getKind().isPrimitive()) {
      return MoreTypes.asPrimitiveType(type).toString();
    }
    return stripCommonTypePrefixes(MoreTypes.asDeclared(type).toString());
  }
}
