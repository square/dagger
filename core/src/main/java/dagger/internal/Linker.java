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
package dagger.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Links bindings to their dependencies.
 */
public abstract class Linker {
  private static final Object UNINITIALIZED = new Object();

  /** Bindings requiring a call to attach(). May contain deferred bindings. */
  private final Queue<Binding<?>> toLink = new LinkedList<Binding<?>>();

  /** True unless calls to requestBinding() were unable to satisfy the binding. */
  private boolean attachSuccess = true;

  /** All errors encountered during injection. */
  private final List<String> errors = new ArrayList<String>();

  /** All of the object graph's bindings. This may contain unlinked bindings. */
  private final Map<String, Binding<?>> bindings = new HashMap<String, Binding<?>>();

  /**
   * Adds all bindings in {@code toInstall}. The caller must call either {@link
   * #linkAll} or {@link #requestBinding} and {@link #linkRequested} before the
   * bindings can be used.
   */
  public final void installBindings(Map<String, ? extends Binding<?>> toInstall) {
    for (Map.Entry<String, ? extends Binding<?>> entry : toInstall.entrySet()) {
      bindings.put(entry.getKey(), scope(entry.getValue()));
    }
  }

  /**
   * Links requested bindings and installed bindings, plus all of their
   * transitive dependencies. This creates JIT bindings as necessary to fill in
   * the gaps.
   *
   * @return all bindings known by this linker, which will all be linked.
   */
  public final Collection<Binding<?>> linkAll() {
    for (Binding<?> binding : bindings.values()) {
      if (!binding.linked) {
        toLink.add(binding);
      }
    }
    linkRequested();
    return bindings.values();
  }

  /**
   * Links all requested bindings plus their transitive dependencies. This
   * creates JIT bindings as necessary to fill in the gaps.
   */
  public final void linkRequested() {
    Binding binding;
    while ((binding = toLink.poll()) != null) {
      if (binding instanceof DeferredBinding) {
        String key = ((DeferredBinding<?>) binding).deferredKey;
        if (bindings.containsKey(key)) {
          continue; // A binding for this key has since been linked.
        }
        try {
          Binding<?> jitBinding = createJitBinding(key, binding.requiredBy);
          // Fail if the type of binding we got wasn't capable of what was requested.
          if (!key.equals(jitBinding.provideKey) && !key.equals(jitBinding.membersKey)) {
            throw new IllegalStateException("Unable to create binding for " + key);
          }
          // Enqueue the JIT binding so its own dependencies can be linked.
          toLink.add(jitBinding);
          putBinding(jitBinding);
        } catch (Exception e) {
          addError(e.getMessage() + " required by " + binding.requiredBy);
          bindings.put(key, Binding.UNRESOLVED);
        }
      } else {
        // Attempt to attach the binding to its dependencies. If any dependency
        // is not available, the attach will fail. We'll enqueue creation of
        // that dependency and retry the attachment later.
        attachSuccess = true;
        binding.attach(this);
        if (attachSuccess) {
          binding.linked = true;
        } else {
          toLink.add(binding);
        }
      }
    }

    try {
      reportErrors(errors);
    } finally {
      errors.clear();
    }
  }

  /**
   * Creates a just-in-time binding for the key in {@code deferred}. The type of binding
   * to be created depends on the key's type:
   * <ul>
   *   <li>Injections of {@code Provider<Foo>}, {@code MembersInjector<Bar>}, and
   *       {@code Lazy<Blah>} will delegate to the bindings of {@code Foo}, {@code Bar}, and
   *       {@code Blah} respectively.
   *   <li>Injections of other types will use the injectable constructors of those classes.
   * </ul>
   */
  private Binding<?> createJitBinding(String key, Object requiredBy) throws ClassNotFoundException {
    String builtInBindingsKey = Keys.getBuiltInBindingsKey(key);
    if (builtInBindingsKey != null) {
      return new BuiltInBinding<Object>(key, requiredBy, builtInBindingsKey);
    }
    String lazyKey = Keys.getLazyKey(key);
    if (lazyKey != null) {
      return new LazyBinding<Object>(key, requiredBy, lazyKey);
    }

    String className = Keys.getClassName(key);
    if (className != null && !Keys.isAnnotated(key)) {
      Binding<?> atInjectBinding = createAtInjectBinding(key, className);
      if (atInjectBinding != null) {
        return atInjectBinding;
      }
    }

    throw new IllegalArgumentException("No binding for " + key);
  }

  /**
   * Returns a binding that uses {@code @Inject} annotations, or null if no such
   * binding can be created.
   */
  protected abstract Binding<?> createAtInjectBinding(String key, String className)
      throws ClassNotFoundException;

  /**
   * Returns the binding if it exists immediately. Otherwise this returns
   * null. If the returned binding didn't exist or was unlinked, it will be
   * enqueued to be linked.
   */
  public final Binding<?> requestBinding(String key, Object requiredBy) {
    Binding<?> binding = bindings.get(key);
    if (binding == null) {
      // We can't satisfy this binding. Make sure it'll work next time!
      DeferredBinding<Object> deferredBinding = new DeferredBinding<Object>(key, requiredBy);
      toLink.add(deferredBinding);
      attachSuccess = false;
      return null;
    }

    if (!binding.linked) {
      toLink.add(binding); // This binding was never linked; link it now!
    }

    return binding;
  }

  private <T> void putBinding(Binding<T> binding) {
    binding = scope(binding);

    // At binding insertion time it's possible that another binding for the same
    // key to already exist. This occurs when an @Provides method returns a type T
    // and we also inject the members of that type.
    if (binding.provideKey != null) {
      putIfAbsent(bindings, binding.provideKey, binding);
    }
    if (binding.membersKey != null) {
      putIfAbsent(bindings, binding.membersKey, binding);
    }
  }

  /**
   * Returns a scoped binding for {@code binding}.
   */
  static <T> Binding<T> scope(final Binding<T> binding) {
    if (!binding.singleton) {
      return binding;
    }
    if (binding instanceof SingletonBinding) throw new AssertionError();
    return new SingletonBinding<T>(binding);
  }

  /**
   * Puts the mapping {@code key, value} in {@code map} if no mapping for {@code
   * key} already exists.
   */
  private <K, V> void putIfAbsent(Map<K, V> map, K key, V value) {
    V replaced = map.put(key, value); // Optimistic: prefer only one hash operation lookup.
    if (replaced != null) {
      map.put(key, replaced);
    }
  }

  /** Enqueue {@code message} as a fatal error to be reported to the user. */
  private void addError(String message) {
    errors.add(message);
  }

  /**
   * Fail if any errors have been enqueued and clear the list of errors.
   * Implementations may throw exceptions or report the errors through another
   * channel.
   *
   * @param errors a potentially empty list of error messages.
   */
  protected abstract void reportErrors(List<String> errors);

  /**
   * A Binding that implements singleton behaviour around an existing binding.
   */
  private static class SingletonBinding<T> extends Binding<T> {
    private final Binding<T> binding;
    private Object onlyInstance = UNINITIALIZED;

    private SingletonBinding(Binding<T> binding) {
      super(binding.provideKey, binding.membersKey, true, binding.requiredBy);
      this.binding = binding;
    }

    @Override public void attach(Linker linker) {
      binding.attach(linker);
    }

    @Override public void injectMembers(T t) {
      binding.injectMembers(t);
    }

    @SuppressWarnings("unchecked") // onlyInstance is either 'UNINITIALIZED' or a 'T'.
    @Override public T get() {
      // TODO (cgruber): Fix concurrency risk.
      if (onlyInstance == UNINITIALIZED) {
        onlyInstance = binding.get();
      }
      return (T) onlyInstance;
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      binding.getDependencies(get, injectMembers);
    }

    @Override public String toString() {
      return "@Singleton/" + binding.toString();
    }
  }

  private static class DeferredBinding<T> extends Binding<T> {
    final String deferredKey;
    private DeferredBinding(String deferredKey, Object requiredBy) {
      super(null, null, false, requiredBy);
      this.deferredKey = deferredKey;
    }
  }
}
