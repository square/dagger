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
package com.squareup.injector;

import com.squareup.injector.internal.Binding;
import com.squareup.injector.internal.Keys;
import com.squareup.injector.internal.Linker;
import java.util.HashMap;
import java.util.Map;

/**
 * A dependency graph.
 *
 * <p>The following injection features are supported:
 * <ul>
 *   <li>Field injection. A class may have any number of field injections, and
 *       fields may be of any visibility. Static fields will be injected each
 *       time an instance is injected.
 *   <li>Constructor injection. A class may have a single {@code
 *       @Inject}-annotated constructor. Classes that have fields injected
 *       may omit the {@link @Inject} annotation if they have a public
 *       no-arguments constructor.
 *   <li>Injection of {@code @Provides} method parameters.
 *   <li>{@code @Provides} methods annotated {@code @Singleton}.
 *   <li>Constructor-injected classes annotated {@code @Singleton}.
 *   <li>Injection of {@link javax.inject.Provider}s.
 *   <li>Qualifier annotations on injected parameters and fields.
 *   <li>JSR 330 annotations.
 * </ul>
 *
 * <p>The following injection features are not currently supported:
 * <ul>
 *   <li>Method injection.</li>
 *   <li>Circular dependencies.</li>
 * </ul>
 *
 * @author Jesse Wilson
 */
public final class DependencyGraph {
  private final Class<?> injectorClass;
  private final Map<Class<?>, Binding<?>> bindings;

  private DependencyGraph(Class<?> injectorClass, Map<Class<?>, Binding<?>> bindings) {
    this.injectorClass = injectorClass;
    this.bindings = bindings;
  }

  /**
   * Returns a new dependency graph using the {@literal @}{@link
   * Injector}-annotated object and {@code modules}.
   */
  public static DependencyGraph get(Object injector, Object... overrides) {
    Class<?> injectorClass = injector.getClass();
    Injector annotation = injectorClass.getAnnotation(Injector.class);
    if (annotation == null) {
      throw new IllegalArgumentException("No @Injector on " + injectorClass.getName());
    }
    Class<?>[] entryPoints = annotation.entryPoints();
    Class<?>[] modules = annotation.modules();

    /*
     * Create a linker and install all of the user's modules. Modules provided
     * at runtime may override modules provided in the @Injector annotation.
     */
    Linker linker = new Linker();
    linker.installModules(classesToObjects(modules));
    linker.installModules(overrides);

    /*
     * Request the bindings we'll need from the linker. This will cause the
     * linker to link these bindings in the link step.
     */
    getEntryPointsMap(linker, injectorClass, entryPoints);

    /*
     * Fill out the graph, creating JIT bindings as necessary.
     */
    linker.link();

    /*
     * Link success. Return a new linked dependency graph.
     */
    return new DependencyGraph(injectorClass,
        getEntryPointsMap(linker, injectorClass, entryPoints));
  }

  private static Object[] classesToObjects(Class<?>[] moduleClasses) {
    try {
      Object[] moduleObjects = new Object[moduleClasses.length];
      for (int i = 0; i < moduleClasses.length; i++) {
        Class<?> module = moduleClasses[i];
        moduleObjects[i] = module.newInstance();
      }
      return moduleObjects;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a map from class to entry point.
   *
   * <p>If executed before {@code link()}, this tells the linker which keys are
   * required. Since the bindings haven't been linked, the returned map may
   * contain null bindings and should not be used.
   *
   * <p>If executed after {@code link()}, the bindings will not be null and the
   * map can be used.
   */
  private static Map<Class<?>, Binding<?>> getEntryPointsMap(Linker linker, Class<?> injectorClass,
      Class<?>[] entryPoints) {
    Map<Class<?>, Binding<?>> result = new HashMap<Class<?>, Binding<?>>();
    result.put(injectorClass, linker.requestBinding(Keys.get(injectorClass), "injector"));
    for (Class<?> entryPoint : entryPoints) {
      result.put(entryPoint, linker.requestBinding(Keys.get(entryPoint), "entry point"));
    }
    return result;
  }

  @SuppressWarnings("unchecked") // bindings is a typesafe heterogeneous container
  public void inject(Object instance) {
    Binding<Object> binding = (Binding<Object>) bindings.get(instance.getClass());
    if (binding == null) {
      throw new IllegalArgumentException("No binding for " + instance.getClass().getName() + ". "
          + "You must explicitly add it as an entry point of " + injectorClass.getName() + ".");
    }
    binding.injectMembers(instance);
  }
}
