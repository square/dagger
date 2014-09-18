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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Formats a {@link Key} into a {@link String} suitable for use in error messages.
 *
 * @author Christian Gruber
 * @since 2.0
 */
final class ProvisionBindingFormatter extends Formatter<ProvisionBinding> {
  private static final ProvisionBindingFormatter INSTANCE = new ProvisionBindingFormatter();

  static ProvisionBindingFormatter instance() {
    return INSTANCE;
  }

  @Override public String format(ProvisionBinding binding) {
    StringBuilder builder = new StringBuilder();
    switch (binding.bindingKind()) {
      case PROVISION:
      case COMPONENT_PROVISION:
        ExecutableElement method = MoreElements.asExecutable(binding.bindingElement());
        TypeElement type = MoreElements.asType(method.getEnclosingElement());
        builder.append(type.getQualifiedName());
        builder.append('.');
        builder.append(method.getSimpleName());
        builder.append('(');
        for (VariableElement parameter : method.getParameters()) {
          builder.append(parameter.asType()); // TODO(user): Use TypeMirrorFormatter.
        }
        builder.append(')');
        return builder.toString();
      default:
        throw new UnsupportedOperationException(
            "Not yet supporting " + binding.bindingKind() + " binding types.");
    }
  }
}
