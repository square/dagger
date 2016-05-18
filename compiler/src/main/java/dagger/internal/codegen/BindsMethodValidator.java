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

import com.google.common.collect.ImmutableList;
import dagger.Binds;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;
import static dagger.internal.codegen.ErrorMessages.BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER;

/**
 * A validator for {@link Binds} methods.
 */
final class BindsMethodValidator extends BindingMethodValidator {
  private final Types types;

  BindsMethodValidator(Elements elements, Types types) {
    super(
        elements,
        types,
        Binds.class,
        ImmutableList.of(Module.class, ProducerModule.class),
        MUST_BE_ABSTRACT,
        RUNTIME_EXCEPTION);
    this.types = checkNotNull(types);
  }

  @Override
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    super.checkMethod(builder);
    checkParameters(builder);
  }

  @Override // TODO(dpb, ronshapiro): When @Binds methods support multibindings, stop overriding.
  protected void checkReturnType(ValidationReport.Builder<ExecutableElement> builder) {
    checkFrameworkType(builder);
    checkKeyType(builder, builder.getSubject().getReturnType());
  }

  @Override // TODO(dpb, ronshapiro): When @Binds methods support multibindings, stop overriding.
  protected void checkMapKeys(ValidationReport.Builder<ExecutableElement> builder) {
    // no-op
  }

  @Override // TODO(dpb, ronshapiro): When @Binds methods support multibindings, stop overriding.
  protected void checkMultibindings(ValidationReport.Builder<ExecutableElement> builder) {
    // no-op
  }

  private void checkParameters(ValidationReport.Builder<ExecutableElement> builder) {
    List<? extends VariableElement> parameters = builder.getSubject().getParameters();
    if (parameters.size() == 1) {
      VariableElement parameter = getOnlyElement(parameters);
      if (!types.isAssignable(parameter.asType(), builder.getSubject().getReturnType())) {
        builder.addError(formatErrorMessage(BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER));
      }
    } else {
      builder.addError(formatErrorMessage(BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER));
    }
  }
}
