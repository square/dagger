/*
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
package dagger.internal;

import java.util.List;

/**
 * Handles errors by throwing an exception containing all the available errors.
 */
public final class ThrowingErrorHandler implements Linker.ErrorHandler {

  @Override public void handleErrors(List<String> errors) {
    if (errors.isEmpty()) {
      return;
    }
    StringBuilder message = new StringBuilder();
    message.append("Errors creating object graph:");
    for (String error : errors) {
      message.append("\n  ").append(error);
    }
    throw new IllegalStateException(message.toString());
  }
}
