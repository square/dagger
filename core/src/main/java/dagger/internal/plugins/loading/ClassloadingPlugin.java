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
package dagger.internal.plugins.loading;

import dagger.ObjectGraph;
import dagger.internal.Binding;
import dagger.internal.ModuleAdapter;
import dagger.internal.Plugin;
import dagger.internal.StaticInjection;
import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A run-time {@code Binding.Resolver} which finds bindings by loading appropriately named adapter
 * classes.
 */
public final class ClassloadingPlugin implements Plugin {
  private static final Logger LOGGER = Logger.getLogger(ObjectGraph.class.getName());

  public static final String INJECT_ADAPTER_SUFFIX = "$InjectAdapter";
  public static final String MODULE_ADAPTER_SUFFIX = "$ModuleAdapter";
  public static final String STATIC_INJECTION_SUFFIX = "$StaticInjection";

  /**
   * Returns a module adapter loaded from the appropriately named class.
   */
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
    return instantiate(moduleClass.getName(), MODULE_ADAPTER_SUFFIX, "module");
  }

  /**
   * Returns an {@code @Inject} binding loaded from the appropriately named class.
   */
  @Override public Binding<?> getAtInjectBinding(String key, String className,
      boolean mustBeInjectable) throws ClassNotFoundException {
    return instantiate(className, INJECT_ADAPTER_SUFFIX, "@Inject");
  }

  /**
   * Returns a {@code StaticInjection} binding loaded from the appropriately named class.
   */
  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    return instantiate(injectedClass.getName(), STATIC_INJECTION_SUFFIX, "static injection");
  }

  private <T> T instantiate(String className, String suffix, String kind) {
    try {
      Class<?> c = Class.forName(className + suffix);
      Constructor<?> constructor = c.getConstructor();
      constructor.setAccessible(true);
      return (T) constructor.newInstance();
    } catch (Exception e) {
      LOGGER.log(Level.FINE, String.format("No %s adapter for %s found.", kind, className), e);
      return null;
    }
  }
}
