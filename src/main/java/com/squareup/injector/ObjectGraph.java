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
import com.squareup.injector.internal.StaticInjection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A graph of objects linked by their dependencies.
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
 */
public final class ObjectGraph {
  private final List<StaticInjection> staticInjections;
  private final Map<Class<?>, Binding<?>> bindings;

  private ObjectGraph(List<StaticInjection> staticInjections, Map<Class<?>, Binding<?>> bindings) {
    this.staticInjections = staticInjections;
    this.bindings = bindings;
  }

  /**
   * Returns a new dependency graph using the {@literal @}{@link
   * Module}-annotated modules.
   *
   * <p>This <strong>does not</strong> inject any members. Most applications
   * should call {@link #injectStatics} to inject static members and/or {@link
   * #inject} to inject instance members when this method has returned.
   */
  public static ObjectGraph get(Object... modules) {
    Map<Class<?>, Class<?>> entryPoints = new LinkedHashMap<Class<?>, Class<?>>();
    Set<Class<?>> staticInjectionClasses = new LinkedHashSet<Class<?>>();

    List<Object> baseModules = new ArrayList<Object>();
    List<Object> overrideModules = new ArrayList<Object>();
    for (Object module : modules) {
      Class<?> moduleClass = module.getClass();
      Module annotation = moduleClass.getAnnotation(Module.class);
      if (annotation == null) {
        throw new IllegalArgumentException("No @Module on " + moduleClass.getName());
      }
      for (Class<?> entryPoint : annotation.entryPoints()) {
        entryPoints.put(entryPoint, moduleClass);
      }
      for (Class<?> staticInjection : annotation.staticInjections()) {
        staticInjectionClasses.add(staticInjection);
      }
      if (annotation.overrides()) {
        overrideModules.add(module);
      } else {
        baseModules.add(module);
      }
    }

    // Create static injections.
    List<StaticInjection> staticInjections = new ArrayList<StaticInjection>();
    for (Class<?> c : staticInjectionClasses) {
      staticInjections.add(StaticInjection.get(c));
    }

    // Create a linker and install all of the user's modules. Modules provided
    // at runtime may override modules provided in the @Module annotation.
    Linker linker = new Linker();
    linker.installModules(baseModules);
    linker.installModules(overrideModules);

    // Request the bindings we'll need from the linker. This will cause the
    // linker to link these bindings in the link step.
    getEntryPointsMap(linker, entryPoints);
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.attach(linker);
    }

    // Fill out the graph, creating JIT bindings as necessary.
    linker.link();

    // Attach all necessary injections. Now that we've linked, all bindings will be available.
    Map<Class<?>, Binding<?>> entryPointsMap =
        getEntryPointsMap(linker, entryPoints);
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.attach(linker);
    }

    // Link success. Return a new linked dependency graph.
    return new ObjectGraph(staticInjections, entryPointsMap);
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
  private static Map<Class<?>, Binding<?>> getEntryPointsMap(Linker linker,
      Map<Class<?>, Class<?>> entryPoints) {
    Map<Class<?>, Binding<?>> result = new HashMap<Class<?>, Binding<?>>();
    for (Map.Entry<Class<?>, Class<?>> entry : entryPoints.entrySet()) {
      Class<?> entryPoint = entry.getKey();
      Class<?> moduleClass = entry.getValue();
      result.put(entryPoint, linker.requestBinding(Keys.get(entryPoint), moduleClass, false));
    }
    return result;
  }

  /**
   * Injects the static fields of the classes listed in the injector's {@code
   * staticInjections} property.
   */
  public void injectStatics() {
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.inject();
    }
  }

  /**
   * Injects the members of {@code instance}, including injectable members
   * inherited from its supertypes.
   *
   * @throws IllegalArgumentException if the runtime type of {@code instance} is
   *     not the injector's type or one of its entry point types.
   */
  @SuppressWarnings("unchecked") // bindings is a typesafe heterogeneous container
  public void inject(Object instance) {
    Binding<Object> binding = (Binding<Object>) bindings.get(instance.getClass());
    if (binding == null) {
      throw new IllegalArgumentException("No binding for " + instance.getClass().getName() + ". "
          + "You must explicitly add it as an entry point.");
    }
    binding.injectMembers(instance);
  }
}
