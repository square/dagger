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
package com.squareup.objectgraph.internal;

import com.squareup.objectgraph.Module;
import com.squareup.objectgraph.ObjectGraph;
import com.squareup.objectgraph.Provides;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts bindings from an {@code @Module}-annotated class.
 */
public abstract class ModuleAdapter<T> {
  private static final Logger LOGGER = Logger.getLogger(ObjectGraph.class.getName());

  public final String[] entryPoints;
  public final Class<?>[] staticInjections;
  public final boolean overrides;
  public final Class<?>[] children;
  public final boolean complete;
  protected T module;

  protected ModuleAdapter(String[] entryPoints, Class<?>[] staticInjections, boolean overrides,
      Class<?>[] children, boolean complete) {
    this.entryPoints = entryPoints;
    this.staticInjections = staticInjections;
    this.overrides = overrides;
    this.children = children;
    this.complete = complete;
  }

  /**
   * Returns bindings for the {@code @Provides} methods of {@code module}. The
   * returned bindings must be linked before they can be used to inject values.
   */
  public abstract void getBindings(Map<String, Binding<?>> map);

  /**
   * Returns a new instance of the module class created using a no-args
   * constructor. Only used when a manually-constructed module is not supplied.
   */
  protected abstract T newModule();

  public T getModule() {
    return module;
  }

  /**
   * Returns a module adapter for {@code module}, preferring a code-generated
   * implementation and falling back to a reflective implementation.
   */
  @SuppressWarnings("unchecked") // Runtime checks validate that the result type matches 'T'.
  public static <T> ModuleAdapter<T> get(Class<? extends T> moduleClass, T module) {
    ModuleAdapter<T> result;
    try {
      String adapter = moduleClass.getName() + "$ModuleAdapter";
      Class<?> c = Class.forName(adapter);
      Constructor<?> constructor = c.getConstructor();
      constructor.setAccessible(true);
      result = (ModuleAdapter) constructor.newInstance();
    } catch (Exception e) {
      LOGGER.log(Level.FINE, "No generated module for " + moduleClass.getName()
          + ". Falling back to reflection.", e);
      Module annotation = moduleClass.getAnnotation(Module.class);
      if (annotation == null) {
        throw new IllegalArgumentException("No @Module on " + moduleClass.getName());
      }
      result = (ModuleAdapter) new ReflectiveModuleAdapter(moduleClass, annotation);
    }
    result.module = (module != null) ? module : result.newModule();
    return result;
  }

  static class ReflectiveModuleAdapter extends ModuleAdapter<Object> {
    final Class<?> moduleClass;

    ReflectiveModuleAdapter(Class<?> moduleClass, Module annotation) {
      super(toMemberKeys(annotation.entryPoints()), annotation.staticInjections(),
          annotation.overrides(), annotation.children(), annotation.complete());
      this.moduleClass = moduleClass;
    }

    private static String[] toMemberKeys(Class<?>[] entryPoints) {
      String[] result = new String[entryPoints.length];
      for (int i = 0; i < entryPoints.length; i++) {
        result[i] = Keys.getMembersKey(entryPoints[i]);
      }
      return result;
    }

    @Override public void getBindings(Map<String, Binding<?>> bindings) {
      // Fall back to runtime reflection.
      for (Class<?> c = moduleClass; c != Object.class; c = c.getSuperclass()) {
        for (Method method : c.getDeclaredMethods()) {
          if (!method.isAnnotationPresent(Provides.class)) {
            continue;
          }
          Binding<?> binding = methodToBinding(method);
          bindings.put(binding.provideKey, binding);
        }
      }
    }

    private <T> Binding<T> methodToBinding(Method method) {
      String key = Keys.get(method.getGenericReturnType(), method.getAnnotations(), method);
      return new ProviderMethodBinding<T>(method, key, module);
    }

    @Override protected Object newModule() {
      try {
        Constructor<?> childConstructor = moduleClass.getDeclaredConstructor();
        childConstructor.setAccessible(true);
        return childConstructor.newInstance();
      } catch (Exception e) {
        throw new IllegalArgumentException("Unable to instantiate " + moduleClass.getName(), e);
      }
    }
  }
}
