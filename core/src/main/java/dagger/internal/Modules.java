/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2012 Google, Inc.
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


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static helper for organizing modules.
 */
public final class Modules {

  private Modules() { }

  /**
   * Returns a full set of module adapters, including module adapters for included
   * modules.
   */
  public static Map<ModuleAdapter<?>, Object> loadModules(Loader loader,
      Object[] seedModulesOrClasses) {
    Map<ModuleAdapter<?>, Object> seedAdapters =
        new LinkedHashMap<ModuleAdapter<?>, Object>(seedModulesOrClasses.length);
    for (int i = 0; i < seedModulesOrClasses.length; i++) {
      if (seedModulesOrClasses[i] instanceof Class<?>) {
        ModuleAdapter<?> adapter = loader.getModuleAdapter((Class<?>) seedModulesOrClasses[i]);
        seedAdapters.put(adapter, adapter.newModule());
      } else {
        ModuleAdapter<?> adapter = loader.getModuleAdapter(seedModulesOrClasses[i].getClass());
        seedAdapters.put(adapter, seedModulesOrClasses[i]);
      }
    }

    // Add the adapters that we have module instances for. This way we won't
    // construct module objects when we have a user-supplied instance.
    Map<ModuleAdapter<?>, Object> result =
        new LinkedHashMap<ModuleAdapter<?>, Object>(seedAdapters);

    // Next collect included modules
    Map<Class<?>, ModuleAdapter<?>> transitiveInclusions =
        new LinkedHashMap<Class<?>, ModuleAdapter<?>>();
    for (ModuleAdapter<?> adapter : seedAdapters.keySet()) {
      collectIncludedModulesRecursively(loader, adapter, transitiveInclusions);
    }
    // and create them if necessary
    for (ModuleAdapter<?> dependency : transitiveInclusions.values()) {
      if (!result.containsKey(dependency)) {
        result.put(dependency, dependency.newModule());
      }
    }
    return result;
  }

  /**
   * Fills {@code result} with the module adapters for the includes of {@code
   * adapter}, and their includes recursively.
   */
  private static void collectIncludedModulesRecursively(Loader plugin, ModuleAdapter<?> adapter,
      Map<Class<?>, ModuleAdapter<?>> result) {
    for (Class<?> include : adapter.includes) {
      if (!result.containsKey(include)) {
        ModuleAdapter<?> includedModuleAdapter = plugin.getModuleAdapter(include);
        result.put(include, includedModuleAdapter);
        collectIncludedModulesRecursively(plugin, includedModuleAdapter, result);
      }
    }
  }

}
