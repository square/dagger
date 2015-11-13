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

import com.google.common.base.Optional;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreTypes.asDeclared;

/**
 * Formats a {@link ContributionBinding} into a {@link String} suitable for use in error messages.
 *
 * @author Christian Gruber
 * @since 2.0
 */
final class ContributionBindingFormatter extends Formatter<ContributionBinding> {
  private final MethodSignatureFormatter methodSignatureFormatter;
  
  ContributionBindingFormatter(MethodSignatureFormatter methodSignatureFormatter) { 
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override public String format(ContributionBinding binding) {
    switch (binding.bindingKind()) {
      case COMPONENT_PROVISION:
      case COMPONENT_PRODUCTION:
        return methodSignatureFormatter.format(asExecutable(binding.bindingElement()));

      case PROVISION:
      case SUBCOMPONENT_BUILDER:
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return methodSignatureFormatter.format(
            asExecutable(binding.bindingElement()),
            Optional.of(asDeclared(binding.contributedBy().get().asType())));

      default:
        throw new UnsupportedOperationException(
            "Not yet supporting " + binding.bindingKind() + " binding types.");
    }
  }
}
