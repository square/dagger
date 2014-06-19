/*
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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


import dagger.internal.loaders.ReflectiveAtInjectBinding;
import dagger.internal.loaders.ReflectiveModuleAdapter;
import dagger.internal.loaders.ReflectiveStaticInjection;

/**
 * Handles loading/finding of modules, injection bindings, and static injections by use of a
 * reflective bindings.
 *
 * @deprecated Provided only to work around proguard obfuscation - obsolete in 2.0.
 */
@Deprecated
public final class ReflectiveLoader extends Loader {
  /**
   * Obtains a {@link ReflectiveModuleAdapter} for {@code module}.
   */
  @SuppressWarnings("unchecked") // cache ensures types match
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<T> type) {
    return ReflectiveModuleAdapter.create(type);
  }

  /**
   * Obtains a {@link ReflectiveAtInjectBinding} for a given key.
   */
  @Override public Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader classLoader, boolean mustHaveInjections) {
    Class<?> type = loadClass(classLoader, className);
    if (type.equals(Void.class)) {
      throw new IllegalStateException(
          String.format("Could not load class %s needed for binding %s", className, key));
    }
    if (type.isInterface()) {
      return null; // Short-circuit since we can't build reflective bindings for interfaces.
    }
    return ReflectiveAtInjectBinding.createFactory(type).create(mustHaveInjections);
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    return ReflectiveStaticInjection.create(injectedClass);
  }
}
