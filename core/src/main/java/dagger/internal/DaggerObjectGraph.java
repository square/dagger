package dagger.internal;

import dagger.ObjectGraph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DaggerObjectGraph extends ObjectGraph {
    private DaggerObjectGraph base;
    private Linker linker;
    private Loader plugin;
    private Map<Class<?>, StaticInjection> staticInjections;
    private Map<String, Class<?>> injectableTypes;
    private List<SetBinding<?>> setBindings;

    private DaggerObjectGraph() {
        super();
    }

    private static <T> T checkNotNull(T object, String label) {
        if (object == null) throw new NullPointerException(label);
        return object;
    }

    public static ObjectGraph makeGraph(DaggerObjectGraph base, Loader plugin, Object... modules) {
        Map<String, Class<?>> injectableTypes = new LinkedHashMap<String, Class<?>>();
        Map<Class<?>, StaticInjection> staticInjections
                = new LinkedHashMap<Class<?>, StaticInjection>();
        StandardBindings baseBindings =
                (base == null) ? new StandardBindings() : new StandardBindings(base.setBindings);
        BindingsGroup overrideBindings = new OverridesBindings();

        Map<ModuleAdapter<?>, Object> loadedModules = Modules.loadModules(plugin, modules);
        for (Map.Entry<ModuleAdapter<?>, Object> loadedModule : loadedModules.entrySet()) {
            ModuleAdapter<Object> moduleAdapter = (ModuleAdapter<Object>) loadedModule.getKey();
            for (int i = 0; i < moduleAdapter.injectableTypes.length; i++) {
                injectableTypes.put(moduleAdapter.injectableTypes[i], moduleAdapter.moduleClass);
            }
            for (int i = 0; i < moduleAdapter.staticInjections.length; i++) {
                staticInjections.put(moduleAdapter.staticInjections[i], null);
            }
            try {
                BindingsGroup addTo = moduleAdapter.overrides ? overrideBindings : baseBindings;
                moduleAdapter.getBindings(addTo, loadedModule.getValue());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        moduleAdapter.moduleClass.getSimpleName() + ": " + e.getMessage(), e);
            }
        }

        // Create a linker and install all of the user's bindings
        Linker linker =
                new Linker((base != null) ? base.linker : null, plugin, new ThrowingErrorHandler());
        linker.installBindings(baseBindings);
        linker.installBindings(overrideBindings);

        return new DaggerObjectGraphBuilder(
                base, linker, plugin, staticInjections,
                injectableTypes, baseBindings.getSetBindings()
        ).build();
    }

    @Override public ObjectGraph plus(Object... modules) {
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

    private void linkInjectableTypes() {
        for (Map.Entry<String, Class<?>> entry : injectableTypes.entrySet()) {
            linker.requestBinding(entry.getKey(), entry.getValue(),
                    entry.getValue().getClassLoader(), false, true);
        }
    }

    @Override public void validate() {
        Map<String, Binding<?>> allBindings = linkEverything();
        new ProblemDetector().detectProblems(allBindings.values());
    }

    /**
     * Links all bindings, injectable types and static injections.
     */
    private Map<String, Binding<?>> linkEverything() {
        Map<String, Binding<?>> bindings = linker.fullyLinkedBindings();
        if (bindings != null) {
            return bindings;
        }
        synchronized (linker) {
            if ((bindings = linker.fullyLinkedBindings()) != null) {
                return bindings;
            }
            linkStaticInjections();
            linkInjectableTypes();
            return linker.linkAll(); // Linker.linkAll() implicitly does Linker.linkRequested().
        }
    }

    @Override public void injectStatics() {
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

    @Override public <T> T get(Class<T> type) {
        String key = Keys.get(type);
        String injectableTypeKey = type.isInterface() ? key : Keys.getMembersKey(type);
        ClassLoader classLoader = type.getClassLoader();
        @SuppressWarnings("unchecked") // The linker matches keys to bindings by their type.
                Binding<T> binding =
                (Binding<T>) getInjectableTypeBinding(classLoader, injectableTypeKey, key);
        return binding.get();
    }

    @Override public <T> T inject(T instance) {
        String membersKey = Keys.getMembersKey(instance.getClass());
        ClassLoader classLoader = instance.getClass().getClassLoader();
        @SuppressWarnings("unchecked") // The linker matches keys to bindings by their type.
                Binding<T> binding =
                (Binding<T>) getInjectableTypeBinding(classLoader, membersKey, membersKey);
        binding.injectMembers(instance);
        return instance;
    }

    /**
     * @param classLoader the {@code ClassLoader} used to load dependent bindings.
     * @param injectableKey the key used to store the injectable type. This
     *     is a provides key for interfaces and a members injection key for
     *     other types. That way keys can always be created, even if the type
     *     has no injectable constructor.
     * @param key the key to use when retrieving the binding. This may be a
     *     regular (provider) key or a members key.
     */
    private Binding<?> getInjectableTypeBinding(
            ClassLoader classLoader, String injectableKey, String key) {
        Class<?> moduleClass = null;
        for (DaggerObjectGraph graph = this; graph != null; graph = graph.base) {
            moduleClass = graph.injectableTypes.get(injectableKey);
            if (moduleClass != null) break;
        }
        if (moduleClass == null) {
            String firstHalf = String.format("No inject registered for %s. ", injectableKey);
            String secondHalf =
                    "You must explicitly add it to the 'injects' option in one of your modules.";

            throw new IllegalArgumentException(firstHalf + secondHalf);
        }

        synchronized (linker) {
            Binding<?> binding = linker.requestBinding(key, moduleClass, classLoader, false, true);
            if (binding == null || !binding.isLinked()) {
                linker.linkRequested();
                binding = linker.requestBinding(key, moduleClass, classLoader, false, true);
            }
            return binding;
        }
    }

    public static class DaggerObjectGraphBuilder {

        private final DaggerObjectGraph base;
        private final Linker linker;
        private final Loader plugin;
        private final Map<Class<?>, StaticInjection> staticInjections;
        private final Map<String, Class<?>> injectableTypes;
        private final List<SetBinding<?>> setBindings;

        public DaggerObjectGraphBuilder(DaggerObjectGraph base,
                                        Linker linker,
                                        Loader plugin,
                                        Map<Class<?>, StaticInjection> staticInjections,
                                        Map<String, Class<?>> injectableTypes,
                                        List<SetBinding<?>> setBindings) {
            this.base = base;
            this.linker = checkNotNull(linker, "linker");
            this.plugin = checkNotNull(plugin, "plugin");
            this.staticInjections = checkNotNull(staticInjections, "staticInjections");
            this.injectableTypes = checkNotNull(injectableTypes, "injectableTypes");
            this.setBindings = checkNotNull(setBindings, "setBindings");
        }

        public DaggerObjectGraph build() {
            DaggerObjectGraph daggerObjectGraph = new DaggerObjectGraph();

            daggerObjectGraph.base = this.base;
            daggerObjectGraph.linker = this.linker;
            daggerObjectGraph.plugin = this.plugin;
            daggerObjectGraph.staticInjections = this.staticInjections;
            daggerObjectGraph.injectableTypes = this.injectableTypes;
            daggerObjectGraph.setBindings = this.setBindings;

            return daggerObjectGraph;
        }

    }
}
