/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.Util.AS_DECLARED_TYPE;

import com.google.auto.common.MoreElements;
import javax.lang.model.element.Element;

/**
 * Formats a {@link BindingDeclaration} into a {@link String} suitable for use in error messages.
 */
final class BindingDeclarationFormatter extends Formatter<BindingDeclaration> {
  private final MethodSignatureFormatter methodSignatureFormatter;

  BindingDeclarationFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override
  public String format(BindingDeclaration bindingDeclaration) {
    checkArgument(
        bindingDeclaration.bindingElement().isPresent(),
        "Cannot format bindings without source elements: %s",
        bindingDeclaration);
    Element bindingElement = bindingDeclaration.bindingElement().get();
    switch (bindingElement.asType().getKind()) {
      case EXECUTABLE:
        return methodSignatureFormatter.format(
            MoreElements.asExecutable(bindingElement),
            bindingDeclaration.contributingModule().transform(AS_DECLARED_TYPE));
      case DECLARED:
        return stripCommonTypePrefixes(bindingElement.asType().toString());
      default:
        throw new IllegalArgumentException("Formatting unsupported for element: " + bindingElement);
    }
  }
}
