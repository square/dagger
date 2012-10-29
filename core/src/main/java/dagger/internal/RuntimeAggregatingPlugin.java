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
 * Aggregates provided plugins and delegates its operations to them in order.  Also provides some
 * specific runtime facilities needed by the runtime.
 */
public class RuntimeAggregatingPlugin implements Plugin {

  /** A list of {@code Linker.Plugin}s which will be consulted in-order to resolve requests. */
  private final Plugin[] plugins;

  public RuntimeAggregatingPlugin(Plugin ... plugins) {
    if (plugins == null || plugins.length == 0) {
      throw new IllegalArgumentException("Must provide at least one plugin.");
    }
    this.plugins = plugins;
  }

  /**
   * Returns a full set of module adapters, including module adapters for included
   * modules.
   */
  public static Map<Class<?>, ModuleAdapter<?>> getAllModuleAdapters(Plugin plugin,
      Object[] seedModules) {
    // Create a module adapter for each seed module.
    ModuleAdapter<?>[] seedAdapters = new ModuleAdapter<?>[seedModules.length];
    int s = 0;
    for (Object module : seedModules) {
      if (module instanceof Class) {
        seedAdapters[s++] = plugin.getModuleAdapter((Class<?>) module, null); // Plugin constructs.
      } else {
        seedAdapters[s++] = plugin.getModuleAdapter(module.getClass(), module);
      }
    }

    Map<Class<?>, ModuleAdapter<?>> adaptersByModuleType
        = new LinkedHashMap<Class<?>, ModuleAdapter<?>>();

    // Add the adapters that we have module instances for. This way we won't
    // construct module objects when we have a user-supplied instance.
    for (ModuleAdapter<?> adapter : seedAdapters) {
      adaptersByModuleType.put(adapter.getModule().getClass(), adapter);
    }

    // Next add adapters for the modules that we need to construct. This creates
    // instances of modules as necessary.
    for (ModuleAdapter<?> adapter : seedAdapters) {
      collectIncludedModulesRecursively(plugin, adapter, adaptersByModuleType);
    }

    return adaptersByModuleType;
  }

  /**
   * Fills {@code result} with the module adapters for the includes of {@code
   * adapter}, and their includes recursively.
   */
  private static void collectIncludedModulesRecursively(Plugin plugin, ModuleAdapter<?> adapter,
      Map<Class<?>, ModuleAdapter<?>> result) {
    for (Class<?> include : adapter.includes) {
      if (!result.containsKey(include)) {
        ModuleAdapter<Object> includedModuleAdapter = plugin.getModuleAdapter(include, null);
        result.put(include, includedModuleAdapter);
        collectIncludedModulesRecursively(plugin, includedModuleAdapter, result);
      }
    }
  }

  /**
   * Obtains a module adapter for {@code module} from the first responding resolver.
   */
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
    for (Plugin plugin : plugins) {
      ModuleAdapter<T> result = plugin.getModuleAdapter(moduleClass, module);
      if (result != null) {
        result.module = (module != null) ? module : result.newModule();
        return result;
      }
    }
    throw new IllegalStateException("Could not find any valid ModuleAdapter for "
        + ((module != null) ? module.getClass().getName() : moduleClass.getName()));
  }

  @Override public Binding<?> getAtInjectBinding(String key, String className,
      boolean mustBeInjectable) {
    for (Plugin plugin : plugins) {
      try {
        Binding<?> binding = plugin.getAtInjectBinding(key, className, mustBeInjectable);
        if (binding != null) {
          return binding;
        }
      } catch (Exception e) {
        // Let later resolvers try to fulfill this.
      }
    }
    throw new IllegalStateException("No available @Inject handlers could be found "
        + "for key " + key + " in class " + className);
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    for (Plugin plugin : plugins) {
      StaticInjection injection = plugin.getStaticInjection(injectedClass);
      if (injection != null) {
        return injection;
      }
    }
    throw new IllegalStateException("No available static injection handlers could be found "
        + "for requested class " + injectedClass.getName());
  }
}
