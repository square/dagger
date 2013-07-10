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


import dagger.internal.loaders.GeneratedAdapters;
import dagger.internal.loaders.ReflectiveAtInjectBinding;
import dagger.internal.loaders.ReflectiveStaticInjection;

/**
 * Handles loading/finding of modules, injection bindings, and static injections by use of a
 * strategy of "load the appropriate generated code" or, if no such code is found, create a
 * reflective equivalent.
 */
public final class FailoverLoader implements Loader {

  /**
   * Obtains a module adapter for {@code module} from the first responding resolver.
   */
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> type, T instance) {
    ModuleAdapter<T> result = null;
    try {
      result = GeneratedAdapters.initModuleAdapter(type);
    } catch (ClassNotFoundException e) {
      throw new TypeNotPresentException(type + GeneratedAdapters.MODULE_ADAPTER_SUFFIX, e);
    }
    result.module = (instance != null) ? instance : result.newModule();
    return result;
  }

  @Override public Binding<?> getAtInjectBinding(String key, String className,
      ClassLoader classLoader, boolean mustHaveInjections) {
      try {
        return GeneratedAdapters.initInjectAdapter(className, classLoader);
      } catch (ClassNotFoundException ignored /* failover case */) {
        try {
          // A null classloader is the system classloader.
          classLoader = (classLoader != null) ? classLoader : ClassLoader.getSystemClassLoader();
          Class<?> type = classLoader.loadClass(className);
          if (type.isInterface()) {
            return null; // Short-circuit since we can't build reflective bindings for interfaces.
          }
          return ReflectiveAtInjectBinding.create(type, mustHaveInjections);
        } catch (ClassNotFoundException e) {
          throw new TypeNotPresentException(
              String.format("Could not find %s needed for binding %s", className, key), e);
        }
      }
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    try {
      return GeneratedAdapters.initStaticInjection(injectedClass);
    } catch (ClassNotFoundException ignored) {
      return ReflectiveStaticInjection.create(injectedClass);
    }
  }
}
