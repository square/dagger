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
package dagger;

import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import dagger.internal.ModuleAdapter;
import dagger.internal.Plugin;
import dagger.internal.ProblemDetector;
import dagger.internal.RuntimeAggregatingPlugin;
import dagger.internal.StaticInjection;
import dagger.internal.ThrowingErrorHandler;
import dagger.internal.UniqueMap;
import dagger.internal.plugins.loading.ClassloadingPlugin;
import dagger.internal.plugins.reflect.ReflectivePlugin;
import java.util.LinkedHashMap;
import java.util.Map;

import static dagger.internal.RuntimeAggregatingPlugin.getAllModuleAdapters;

/**
 * A graph of objects linked by their dependencies.
 *
 * <p>The following injection features are supported:
 * <ul>
 *   <li>Field injection. A class may have any number of field injections, and
 *       fields may be of any visibility. Static fields will be injected each
 *       time an instance is injected.
 *   <li>Constructor injection. A class may have a single
 *       {@code @Inject}-annotated constructor. Classes that have fields
 *       injected may omit the {@code @Inject} annotation if they have a public
 *       no-arguments constructor.
 *   <li>Injection of {@code @Provides} method parameters.
 *   <li>{@code @Provides} methods annotated {@code @Singleton}.
 *   <li>Constructor-injected classes annotated {@code @Singleton}.
 *   <li>Injection of {@code Provider}s.
 *   <li>Injection of {@code MembersInjector}s.
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
  private final ObjectGraph base;
  private final Linker linker;
  private final Map<Class<?>, StaticInjection> staticInjections;
  private final Map<String, Class<?>> entryPoints;
  private final Plugin plugin;

  ObjectGraph(ObjectGraph base,
      Linker linker,
      Plugin plugin,
      Map<Class<?>, StaticInjection> staticInjections,
      Map<String, Class<?>> entryPoints) {
    if (linker == null) throw new NullPointerException("linker");
    if (plugin == null) throw new NullPointerException("plugin");
    if (staticInjections == null) throw new NullPointerException("staticInjections");
    if (entryPoints == null) throw new NullPointerException("entryPoints");

    this.base = base;
    this.linker = linker;
    this.plugin = plugin;
    this.staticInjections = staticInjections;
    this.entryPoints = entryPoints;
  }

  /**
   * Returns a new dependency graph using the {@literal @}{@link
   * Module}-annotated modules.
   *
   * <p>This <strong>does not</strong> inject any members. Most applications
   * should call {@link #injectStatics} to inject static members and {@link
   * #inject} or get {@link #get(Class)} to inject instance members when this
   * method has returned.
   *
   * <p>This <strong>does not</strong> validate the graph. Rely on build time
   * tools for graph validation, or call {@link #validate} to find problems in
   * the graph at runtime.
   */
  public static ObjectGraph create(Object... modules) {
    RuntimeAggregatingPlugin plugin = new RuntimeAggregatingPlugin(
            new ClassloadingPlugin(), new ReflectivePlugin());
    return makeGraph(null, plugin, modules);
  }

  private static ObjectGraph makeGraph(ObjectGraph base, Plugin plugin, Object... modules) {
    Map<String, Class<?>> entryPoints = new LinkedHashMap<String, Class<?>>();
    Map<Class<?>, StaticInjection> staticInjections
        = new LinkedHashMap<Class<?>, StaticInjection>();

    // Extract bindings in the 'base' and 'overrides' set. Within each set no
    // duplicates are permitted.
    Map<String, Binding<?>> baseBindings = new UniqueMap<String, Binding<?>>();
    Map<String, Binding<?>> overrideBindings = new UniqueMap<String, Binding<?>>();
    for (ModuleAdapter<?> moduleAdapter : getAllModuleAdapters(plugin, modules).values()) {
      for (String key : moduleAdapter.entryPoints) {
        entryPoints.put(key, moduleAdapter.getModule().getClass());
      }
      for (Class<?> c : moduleAdapter.staticInjections) {
        staticInjections.put(c, null);
      }
      Map<String, Binding<?>> addTo = moduleAdapter.overrides ? overrideBindings : baseBindings;
      moduleAdapter.getBindings(addTo);
    }

    // Create a linker and install all of the user's bindings
    Linker linker = new Linker((base != null) ? base.linker : null, plugin,
        new ThrowingErrorHandler());
    linker.installBindings(baseBindings);
    linker.installBindings(overrideBindings);

    return new ObjectGraph(base, linker, plugin, staticInjections, entryPoints);
  }

  /**
   * Returns a new object graph that includes all of the objects in this graph,
   * plus additional objects in the {@literal @}{@link Module}-annotated
   * modules. This graph is a subgraph of the returned graph.
   *
   * <p>The current graph is not modified by this operation: its objects and the
   * dependency links between them are unchanged. But this graph's objects may
   * be shared by both graphs. For example, the singletons of this graph may be
   * injected and used by the returned graph.
   *
   * <p>This <strong>does not</strong> inject any members or validate the graph.
   * See {@link #create} for guidance on injection and validation.
   */
  public ObjectGraph plus(Object... modules) {
    linkEverything();
    return makeGraph(this, plugin, modules);
  }


  private void linkStaticInjections() {
    for (Map.Entry<Class<?>, StaticInjection> entry : staticInjections.entrySet()) {
      StaticInjection staticInjection = entry.getValue();
      if (staticInjection == null) {
        staticInjection = plugin.getStaticInjection(entry.getKey());
        entry.setValue(staticInjection);
      }
      staticInjection.attach(linker);
    }
  }

  private void linkEntryPoints() {
    for (Map.Entry<String, Class<?>> entry : entryPoints.entrySet()) {
      linker.requestEntryPoint(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Do runtime graph problem detection. For fastest graph creation, rely on
   * build time tools for graph validation.
   *
   * @throws IllegalStateException if this graph has problems.
   */
  public void validate() {
    Map<String, Binding<?>> allBindings = linkEverything();
    new ProblemDetector().detectProblems(allBindings.values());
  }

  /**
   * Links all bindings, entry points and static injections.
   */
  private Map<String, Binding<?>> linkEverything() {
    synchronized (linker) {
      linkStaticInjections();
      linkEntryPoints();
      return linker.linkAll();
    }
  }

  /**
   * Injects the static fields of the classes listed in the object graph's
   * {@code staticInjections} property.
   */
  public void injectStatics() {
    // We call linkStaticInjections() twice on purpose. The first time through
    // we request all of the bindings we need. The linker returns null for
    // bindings it doesn't have. Then we ask the linker to link all of those
    // requested bindings. Finally we call linkStaticInjections() again: this
    // time the linker won't return null because everything has been linked.
    synchronized (linker) {
      linkStaticInjections();
      linker.linkRequested();
      linkStaticInjections();
    }

    for (Map.Entry<Class<?>, StaticInjection> entry : staticInjections.entrySet()) {
      entry.getValue().inject();
    }
  }

  /**
   * Returns an instance of {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not one of this object
   *     graph's entry point types.
   */
  public <T> T get(Class<T> type) {
    String key = Keys.get(type);
    @SuppressWarnings("unchecked") // The linker matches keys to bindings by their type.
    Binding<T> binding = (Binding<T>) getEntryPointBinding(key, key);
    return binding.get();
  }

  /**
   * Injects the members of {@code instance}, including injectable members
   * inherited from its supertypes.
   *
   * @throws IllegalArgumentException if the runtime type of {@code instance} is
   *     not one of this object graph's entry point types.
   */
  public void inject(Object instance) {
    String entryPointKey = Keys.get(instance.getClass());
    String membersKey = Keys.getMembersKey(instance.getClass());
    @SuppressWarnings("unchecked") // The linker matches keys to bindings by their type.
    Binding<Object> binding = (Binding<Object>) getEntryPointBinding(entryPointKey, membersKey);
    binding.injectMembers(instance);
  }

  /**
   * @param entryPointKey the key used to store the entry point. This is always
   *     a regular (provider) key.
   * @param key the key to use when retrieving the binding. This may be a
   *     regular (provider) key or a members key.
   */
  private Binding<?> getEntryPointBinding(String entryPointKey, String key) {
    Class<?> moduleClass = null;
    for (ObjectGraph graph = this; graph != null; graph = graph.base) {
      moduleClass = graph.entryPoints.get(entryPointKey);
      if (moduleClass != null) break;
    }
    if (moduleClass == null) {
      throw new IllegalArgumentException("No entry point for " + entryPointKey
          + ". You must explicitly add an entry point to one of your modules.");
    }

    synchronized (linker) {
      Binding<?> binding = linker.requestBinding(key, moduleClass);
      if (binding == null || !binding.isLinked()) {
        linker.linkRequested();
        binding = linker.requestBinding(key, moduleClass);
      }
      return binding;
    }
  }
}
