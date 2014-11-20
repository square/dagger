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

import com.google.auto.common.Visibility;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.Visibility.PRIVATE;
import static com.google.auto.common.Visibility.PUBLIC;
import static com.google.auto.common.Visibility.effectiveVisibilityOfElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleIncludes;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;

/**
 * A {@link Validator} for {@link Module}s or {@link ProducerModule}s.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ModuleValidator implements Validator<TypeElement> {
  private final Types types;
  private final Class<? extends Annotation> moduleClass;
  private final Class<? extends Annotation> methodClass;

  ModuleValidator(
      Types types,
      Class<? extends Annotation> moduleClass,
      Class<? extends Annotation> methodClass) {
    this.types = types;
    this.moduleClass = moduleClass;
    this.methodClass = methodClass;
  }

  @Override
  public ValidationReport<TypeElement> validate(TypeElement subject) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.Builder.about(subject);

    validateModuleVisibility(subject, builder);

    List<ExecutableElement> moduleMethods = ElementFilter.methodsIn(subject.getEnclosedElements());
    ImmutableListMultimap.Builder<String, ExecutableElement> bindingMethodsByName =
        ImmutableListMultimap.builder();
    for (ExecutableElement moduleMethod : moduleMethods) {
      if (isAnnotationPresent(moduleMethod, methodClass)) {
        bindingMethodsByName.put(moduleMethod.getSimpleName().toString(), moduleMethod);
      }
    }
    for (Entry<String, Collection<ExecutableElement>> entry :
        bindingMethodsByName.build().asMap().entrySet()) {
      if (entry.getValue().size() > 1) {
        for (ExecutableElement offendingMethod : entry.getValue()) {
          builder.addItem(String.format(BINDING_METHOD_WITH_SAME_NAME, methodClass.getSimpleName()),
              offendingMethod);
        }
      }
    }
    // TODO(gak): port the dagger 1 module validation?
    return builder.build();
  }

  private void validateModuleVisibility(final TypeElement moduleElement,
      final ValidationReport.Builder<?> reportBuilder) {
    Visibility moduleVisibility = Visibility.ofElement(moduleElement);
    if (moduleVisibility.equals(PRIVATE)) {
      reportBuilder.addItem("Modules cannot be private.", moduleElement);
    } else if (effectiveVisibilityOfElement(moduleElement).equals(PRIVATE)) {
      reportBuilder.addItem("Modules cannot be enclosed in private types.", moduleElement);
    }

    switch (moduleElement.getNestingKind()) {
      case ANONYMOUS:
        throw new IllegalStateException("Can't apply @Module to an anonymous class");
      case LOCAL:
        throw new IllegalStateException("Local classes shouldn't show up in the processor");
      case MEMBER:
      case TOP_LEVEL:
        if (moduleVisibility.equals(PUBLIC)) {
          ImmutableSet<Element> nonPublicModules = FluentIterable.from(getModuleIncludes(
              getAnnotationMirror(moduleElement, moduleClass).get()))
                  .transform(new Function<TypeMirror, Element>() {
                    @Override public Element apply(TypeMirror input) {
                      return types.asElement(input);
                    }
                  })
                  .filter(new Predicate<Element>() {
                    @Override public boolean apply(Element input) {
                      return effectiveVisibilityOfElement(input).compareTo(PUBLIC) < 0;
                    }
                  })
                  .toSet();
          if (!nonPublicModules.isEmpty()) {
            reportBuilder.addItem(
                String.format(
                    "This module is public, but it includes non-public "
                        + "(or effectively non-public) modules. "
                        + "Either reduce the visibility of this module or make %s public.",
                    formatListForErrorMessage(nonPublicModules.asList())),
                moduleElement);
          }
        }
        break;
      default:
        throw new AssertionError();
    }
  }

  private static String formatListForErrorMessage(List<?> things) {
    switch (things.size()) {
      case 0:
        return "";
      case 1:
        return things.get(0).toString();
      default:
        StringBuilder output = new StringBuilder();
        Joiner.on(", ").appendTo(output, things.subList(0, things.size() - 1));
        output.append(" and ").append(things.get(things.size() - 1));
        return output.toString();
    }
  }
}
