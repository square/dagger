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
package dagger.internal.loaders;

import dagger.internal.Binding;
import dagger.internal.ModuleAdapter;
import dagger.internal.StaticInjection;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility for loading and initializing generated adapters.
 */
public final class GeneratedAdapters {
  public static final String SEPARATOR = "$$";
  public static final String INJECT_ADAPTER_SUFFIX = SEPARATOR + "InjectAdapter";
  public static final String MODULE_ADAPTER_SUFFIX = SEPARATOR + "ModuleAdapter";
  public static final String STATIC_INJECTION_SUFFIX = SEPARATOR + "StaticInjection";
  private static final Logger logger = Logger.getLogger(GeneratedAdapters.class.getName());

  private GeneratedAdapters() { }

  public static <T> ModuleAdapter<T> initModuleAdapter(Class<? extends T> moduleClass) {
    return instantiate(moduleClass.getName(), MODULE_ADAPTER_SUFFIX, moduleClass.getClassLoader());
  }

  public static Binding<?> initInjectAdapter(String className, ClassLoader classLoader) {
    return instantiate(className, INJECT_ADAPTER_SUFFIX, classLoader);
  }

  public static StaticInjection initStaticInjection(Class<?> injectedClass) {
    return instantiate(injectedClass.getName(), STATIC_INJECTION_SUFFIX,
        injectedClass.getClassLoader());
  }

  private static <T> T instantiate(String className, String suffix, ClassLoader classLoader) {
    String name = className + suffix;
    try {
      // A null classloader is the system classloader.
      classLoader = (classLoader != null) ? classLoader : ClassLoader.getSystemClassLoader();
      Class<?> generatedClass = classLoader.loadClass(name);
      Constructor<?> constructor = generatedClass.getConstructor();
      constructor.setAccessible(true);
      return (T) constructor.newInstance();
    } catch (ClassNotFoundException e) {
      if (logger.isLoggable(Level.FINE)) {
        logger.log(Level.FINE, className + " could not be found.", e);
      }
      return null; // Not finding a class is not inherently an error, unlike finding a bad class.
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("No valid constructor found on " + className, e);
    } catch (InstantiationException e) {
      throw new RuntimeException("Failed to initialize " + className, e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to initialize " + className, e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Error while initializing " + className, e.getCause());
    }
  }
}
