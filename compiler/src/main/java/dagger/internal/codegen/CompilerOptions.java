/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.base.Ascii;
import dagger.producers.Produces;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/** A collection of options that dictate how the compiler will run. */
final class CompilerOptions {
  private final boolean usesProducers;
  private final Diagnostic.Kind nullableValidationKind;
  private final Diagnostic.Kind privateMemberValidationKind;
  private final Diagnostic.Kind staticMemberValidationKind;
  private final ValidationType scopeCycleValidationType;

  CompilerOptions(ProcessingEnvironment processingEnv, Elements elements) {
    this(
        elements.getTypeElement(Produces.class.getCanonicalName()) != null,
        nullableValidationType(processingEnv).diagnosticKind().get(),
        privateMemberValidationType(processingEnv).diagnosticKind().get(),
        staticMemberValidationType(processingEnv).diagnosticKind().get(),
        scopeValidationType(processingEnv));
  }

  CompilerOptions(
      boolean usesProducers,
      Diagnostic.Kind nullableValidationKind,
      Diagnostic.Kind privateMemberValidationKind,
      Diagnostic.Kind staticMemberValidationKind,
      ValidationType scopeCycleValidationType) {
    this.usesProducers = usesProducers;
    this.nullableValidationKind = nullableValidationKind;
    this.privateMemberValidationKind = privateMemberValidationKind;
    this.staticMemberValidationKind = staticMemberValidationKind;
    this.scopeCycleValidationType = scopeCycleValidationType;
  }

  boolean usesProducers() {
    return usesProducers;
  }

  Diagnostic.Kind nullableValidationKind() {
    return nullableValidationKind;
  }

  Diagnostic.Kind privateMemberValidationKind() {
    return privateMemberValidationKind;
  }

  Diagnostic.Kind staticMemberValidationKind() {
    return staticMemberValidationKind;
  }

  ValidationType scopeCycleValidationType() {
    return scopeCycleValidationType;
  }

  static final String DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY =
      "dagger.disableInterComponentScopeValidation";

  static final String NULLABLE_VALIDATION_KEY = "dagger.nullableValidation";

  static final String PRIVATE_MEMBER_VALIDATION_TYPE_KEY = "dagger.privateMemberValidation";

  static final String STATIC_MEMBER_VALIDATION_TYPE_KEY = "dagger.staticMemberValidation";

  private static ValidationType scopeValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.allOf(ValidationType.class));
  }

  private static ValidationType nullableValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        NULLABLE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType privateMemberValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        PRIVATE_MEMBER_VALIDATION_TYPE_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType staticMemberValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(
        processingEnv,
        STATIC_MEMBER_VALIDATION_TYPE_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static <T extends Enum<T>> T valueOf(
      ProcessingEnvironment processingEnv, String key, T defaultValue, Set<T> validValues) {
    Map<String, String> options = processingEnv.getOptions();
    if (options.containsKey(key)) {
      try {
        T type =
            Enum.valueOf(defaultValue.getDeclaringClass(), Ascii.toUpperCase(options.get(key)));
        if (!validValues.contains(type)) {
          throw new IllegalArgumentException(); // let handler below print out good msg.
        }
        return type;
      } catch (IllegalArgumentException e) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Processor option -A"
                    + key
                    + " may only have the values "
                    + validValues
                    + " (case insensitive), found: "
                    + options.get(key));
      }
    }
    return defaultValue;
  }
}
