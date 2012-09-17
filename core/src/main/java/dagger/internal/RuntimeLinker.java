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

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Linker suitable for application use at runtime. This looks for generated code
 * and falls back to reflection.
 */
public final class RuntimeLinker extends Linker {
  @Override protected Binding<?> createAtInjectBinding(String key, String className)
      throws ClassNotFoundException {
    try {
      Class<?> c = Class.forName(className + "$InjectAdapter");
      Constructor<?> constructor = c.getConstructor();
      constructor.setAccessible(true);
      return (Binding<?>) constructor.newInstance();
    } catch (Exception ignored) {
      // Fall back to reflection.
    }

    // Handle class bindings by injecting @Inject-annotated members.
    Class<?> c = Class.forName(className);
    if (c.isInterface()) {
      return null;
    }

    return AtInjectBinding.create(c, Keys.isMembersInjection(key));
  }

  @Override protected void reportErrors(List<String> errors) {
    if (errors.isEmpty()) {
      return;
    }
    StringBuilder message = new StringBuilder();
    message.append("Errors creating object graph:");
    for (String error : errors) {
      message.append("\n  ").append(error);
    }
    throw new IllegalArgumentException(message.toString());
  }
}
