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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Static helper for organizing modules.
 */
public final class Modules {

  private Modules() { }

  /**
   * Returns a full set of module adapters, including module adapters for included
   * modules.
   */
  public static ArrayList<ModuleWithAdapter> loadModules(Loader loader,
      Object[] seedModulesOrClasses) {
    int seedModuleCount = seedModulesOrClasses.length;
    ArrayList<ModuleWithAdapter> result = new ArrayList<ModuleWithAdapter>(seedModuleCount);
    HashSet<Class<?>> visitedClasses = new HashSet<Class<?>>(seedModuleCount);
    // Add all seed classes to visited classes right away, so that we won't instantiate modules for
    // them in collectIncludedModulesRecursively
    // Iterate over seedModulesOrClasses in reverse, so that if multiple instances/classes of the
    // same module are provided, the later one is used (this matches previous behavior which some
    // code came to depend on.)
    for (int i = seedModuleCount-1; i >= 0; i--) {
      Object moduleOrClass = seedModulesOrClasses[i];
      if (moduleOrClass instanceof Class<?>) {
        if (visitedClasses.add((Class<?>) moduleOrClass)) {
          ModuleAdapter<?> moduleAdapter = loader.getModuleAdapter((Class<?>) moduleOrClass);
          result.add(new ModuleWithAdapter(moduleAdapter, moduleAdapter.newModule()));
        }
      } else {
        if (visitedClasses.add(moduleOrClass.getClass())) {
          ModuleAdapter<?> moduleAdapter = loader.getModuleAdapter(moduleOrClass.getClass());
          result.add(new ModuleWithAdapter(moduleAdapter, moduleOrClass));
        }
      }
    }
    int dedupedSeedModuleCount = result.size();
    for (int i = 0; i < dedupedSeedModuleCount; i++) {
      ModuleAdapter<?> seedAdapter = result.get(i).getModuleAdapter();
      collectIncludedModulesRecursively(loader, seedAdapter, result, visitedClasses);
    }
    return result;
  }

  /**
   * Wrapper around a module adapter and an instance of the corresponding module.
   */
  public static class ModuleWithAdapter {
    private final ModuleAdapter<?> moduleAdapter;
    private final Object module;

    ModuleWithAdapter(ModuleAdapter<?> moduleAdapter, Object module) {
      this.moduleAdapter = moduleAdapter;
      this.module = module;
    }

    public ModuleAdapter<?> getModuleAdapter() {
      return moduleAdapter;
    }

    public Object getModule() {
      return module;
    }
  }

  /**
   * Fills {@code result} with the module adapters for the includes of {@code
   * adapter}, and their includes recursively.
   */
  private static void collectIncludedModulesRecursively(Loader plugin, ModuleAdapter<?> adapter,
      List<ModuleWithAdapter> result, HashSet<Class<?>> visitedClasses) {
    for (Class<?> include : adapter.includes) {
      if (!visitedClasses.contains(include)) {
        ModuleAdapter<?> includedModuleAdapter = plugin.getModuleAdapter(include);
        result.add(new ModuleWithAdapter(includedModuleAdapter, includedModuleAdapter.newModule()));
        visitedClasses.add(include);
        collectIncludedModulesRecursively(plugin, includedModuleAdapter, result, visitedClasses);
      }
    }
  }

}
