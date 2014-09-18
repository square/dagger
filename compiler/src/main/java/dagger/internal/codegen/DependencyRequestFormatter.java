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
import com.google.common.base.Optional;
import com.google.common.collect.Queues;
import java.util.Deque;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor6;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ErrorMessages.INDENT;

/**
 * Formats a {@link DependencyRequest} into a {@link String} suitable for an error message listing
 * a chain of dependencies.
 *
 * @author Christian Gruber
 * @since 2.0
 */
final class DependencyRequestFormatter extends Formatter<DependencyRequest> {
  private static final DependencyRequestFormatter INSTANCE = new DependencyRequestFormatter();

  static DependencyRequestFormatter instance() {
    return INSTANCE;
  }

  // TODO(user): Sweep this class for TypeMirror.toString() usage and do some preventive format.
  // TODO(user): consider returning a small structure containing strings to be indented later.
  @Override public String format(DependencyRequest request) {
    Element requestElement = request.requestElement();
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(requestElement);
    return requestElement.accept(new SimpleElementVisitor6<String, Optional<AnnotationMirror>>(){

      /* Handle component methods */
      @Override public String visitExecutable(
          ExecutableElement method, Optional<AnnotationMirror> qualifier) {
        StringBuilder builder = new StringBuilder(INDENT);
        if (method.getParameters().isEmpty()) {
          // some.package.name.MyComponent.myMethod()
          //     [component method with return type: @other.package.Qualifier some.package.name.Foo]
          appendMember(method, builder).append("()\n")
              .append(INDENT).append(INDENT).append("[component method with return type: ");
          if (qualifier.isPresent()) {
            // TODO(user) use chenying's annotation mirror stringifier
            builder.append(qualifier.get()).append(' ');
          }
          builder.append(method.getReturnType()).append(']');
        } else {
          // some.package.name.MyComponent.myMethod(some.package.name.Foo foo)
          //     [component injection method for type: some.package.name.Foo]
          VariableElement componentMethodParameter = getOnlyElement(method.getParameters());
          appendMember(method, builder).append("(");
          appendParameter(componentMethodParameter, builder);
          builder.append(")\n");
          builder.append(INDENT).append(INDENT).append("[component injection method for type: ")
              .append(componentMethodParameter.asType())
              .append(']');
        }
        return builder.toString();
      }

      /* Handle injected fields or method/constructor parameter injection. */
      @Override public String visitVariable(
          VariableElement variable, Optional<AnnotationMirror> qualifier) {
        StringBuilder builder = new StringBuilder(INDENT);
        if (variable.getKind().equals(ElementKind.PARAMETER)) {
          // some.package.name.MyClass.myMethod(some.package.name.Foo arg0, some.package.Bar arg1)
          //     [parameter: @other.package.Qualifier some.package.name.Foo arg0]
          ExecutableElement methodOrConstructor =
              MoreElements.asExecutable(variable.getEnclosingElement());
          appendMember(methodOrConstructor, builder).append('(');
          Deque<VariableElement> parameters =
              Queues.newArrayDeque(methodOrConstructor.getParameters());
          if (!parameters.isEmpty()) {
            appendParameter(parameters.poll(), builder);
          }
          for(VariableElement current : parameters) {
            appendParameter(current, builder.append(", "));
          }
          builder.append(")\n").append(INDENT).append(INDENT).append("[parameter: ");
        } else {
          // some.package.name.MyClass.myField
          //     [injected field of type: @other.package.Qualifier some.package.name.Foo myField]
          appendMember(variable, builder).append("()\n")
              .append(INDENT).append(INDENT).append("[injected field of type: ");
        }
        if (qualifier.isPresent()) {
          // TODO(user) use chenying's annotation mirror stringifier
          builder.append(qualifier.get()).append(' ');
        }
        builder.append(variable.asType()).append(' ').append(variable.getSimpleName()).append(']');
        return builder.toString();
      }

      @Override protected String defaultAction(Element element, Optional<AnnotationMirror> ignore) {
        throw new IllegalStateException(
            "Invalid request " + element.getKind() +  " element " + element);
      }
    }, qualifier);
  }

  private StringBuilder appendParameter(VariableElement parameter, StringBuilder builder) {
    return builder.append(parameter.asType()).append(' ').append(parameter.getSimpleName());
  }

  private StringBuilder appendMember(Element member, StringBuilder builder) {
    TypeElement type = MoreElements.asType(member.getEnclosingElement());
    return builder.append(type.getQualifiedName())
        .append('.')
        .append(member.getSimpleName());
  }
}
