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
package dagger.internal.loaders.generated;

import dagger.internal.Binding;
import dagger.internal.Loader;
import dagger.internal.ModuleAdapter;
import dagger.internal.StaticInjection;
import java.lang.reflect.Constructor;

/**
 * A runtime {@link Loader} that loads generated classes.
 */
public final class GeneratedAdapterLoader extends Loader {
  public static final String INJECT_ADAPTER_SUFFIX = "$$InjectAdapter";
  public static final String MODULE_ADAPTER_SUFFIX = "$$ModuleAdapter";
  public static final String STATIC_INJECTION_SUFFIX = "$$StaticInjection";

  public GeneratedAdapterLoader(ClassLoader classLoader) {
    super(classLoader);
  }

  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
    return instantiate(load(moduleClass.getName() + MODULE_ADAPTER_SUFFIX));
  }

  @Override public Binding<?> getAtInjectBinding(
      String key, String className, boolean mustHaveInjections) {
    return instantiate(load(className + INJECT_ADAPTER_SUFFIX));
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    return instantiate(load(injectedClass.getName() + STATIC_INJECTION_SUFFIX));
  }

  @SuppressWarnings("unchecked") // We use a naming convention to defend against mismatches.
  private <T> T instantiate(Class<?> generatedClass) {
    try {
      Constructor<?> constructor = generatedClass.getConstructor();
      constructor.setAccessible(true);
      return (T) constructor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Unexpected failure instantiating " + generatedClass.getName(), e);
    }
  }
}
