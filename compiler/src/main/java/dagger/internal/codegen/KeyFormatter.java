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

/**
 * Formats a {@link Key} into a {@link String} suitable for use in error messages and JSON keys.
 *
 * @author Christian Gruber
 * @since 2.0
 */
final class KeyFormatter extends Formatter<Key> {
  
  private final MethodSignatureFormatter methodSignatureFormatter;

  KeyFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override public String format(Key request) {
    if (request.bindingMethod().isPresent()) {
      // If there's a binding method, its signature is enough.
      SourceElement bindingMethod = request.bindingMethod().get();
      return methodSignatureFormatter.format(
          MoreElements.asExecutable(bindingMethod.element()),
          Optional.of(MoreTypes.asDeclared(bindingMethod.contributedBy().get().asType())));
    }
    StringBuilder builder = new StringBuilder();
    if (request.qualifier().isPresent()) {
      builder.append(request.qualifier().get());
      builder.append(' ');
    }
    builder.append(request.type());
    return builder.toString();
  }
}
