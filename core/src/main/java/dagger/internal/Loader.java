/*
 * Copyright (C) 2012 Square, Inc.
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

/**
 * Provides a point of configuration of the basic resolving functions within Dagger, namely
 * that of Module handling, injection binding creation, and static injection.  A plugin must
 * provide all resolution methods
 */
public abstract class Loader {

  private final ClassLoader classLoader;

  protected Loader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  protected Class<?> load(String className) {
    try {
      return classLoader.loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Could not find or load " + className, e);
    }
  }

  /**
   * Returns a binding that uses {@code @Inject} annotations.
   */
  public abstract Binding<?> getAtInjectBinding(
      String key, String className, boolean mustHaveInjections);

  /**
   * Returns a module adapter for {@code module}.
   */
  public abstract <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module);

  /**
   * Returns the static injection for {@code injectedClass}.
   */
  public abstract StaticInjection getStaticInjection(Class<?> injectedClass);
}