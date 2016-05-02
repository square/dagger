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

import dagger.internal.Binding.InvalidBindingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Links bindings to their dependencies.
 */
public final class Linker {
  static final Object UNINITIALIZED = new Object();

  /**
   * The base {@code Linker} which will be consulted to satisfy bindings not
   * otherwise satisfiable from this {@code Linker}. The top-most {@code Linker}
   * in a chain will have a null base linker.
   */
  private final Linker base;

  /** Bindings requiring a call to attach(). May contain deferred bindings. */
  private final Queue<Binding<?>> toLink = new ArrayQueue<Binding<?>>();

  /** True unless calls to requestBinding() were unable to satisfy the binding. */
  private boolean attachSuccess = true;

  /** All errors encountered during injection. */
  private final List<String> errors = new ArrayList<String>();

  /** All of the object graph's bindings. This may contain unlinked bindings. */
  private final Map<String, Binding<?>> bindings = new HashMap<String, Binding<?>>();

  /**
   * An unmodifiable map containing all of the bindings available in this linker, fully linked.
   * This will be null if the bindings are not yet fully linked. It provides both a signal
   * of completion of the {@link #linkAll()} method, as well as a place to reference the final,
   * fully linked map of bindings.
   */
  private volatile Map<String, Binding<?>> linkedBindings = null;

  private final Loader plugin;

  private final ErrorHandler errorHandler;

  public Linker(Linker base, Loader plugin, ErrorHandler errorHandler) {
    if (plugin == null) throw new NullPointerException("plugin");
    if (errorHandler == null) throw new NullPointerException("errorHandler");

    this.base = base;
    this.plugin = plugin;
    this.errorHandler = errorHandler;
  }

  /**
   * Adds all bindings in {@code toInstall}. The caller must call either {@link
   * #linkAll} or {@link #requestBinding} and {@link #linkRequested} before the
   * bindings can be used.
   *
   * This method may only be called before {@link #linkAll()}. Subsequent calls to
   * {@link #installBindings(BindingsGroup)} will throw an {@link IllegalStateException}.
   */
  public void installBindings(BindingsGroup toInstall) {
    if (linkedBindings != null) {
      throw new IllegalStateException("Cannot install further bindings after calling linkAll().");
    }
    for (Map.Entry<String, ? extends Binding<?>> entry : toInstall.entrySet()) {
      bindings.put(entry.getKey(), scope(entry.getValue()));
    }
  }

  /**
   * Links all known bindings (whether requested or installed), plus all of their
   * transitive dependencies. This loads injectable types' bindings as necessary to fill in
   * the gaps.  If this method has returned successfully at least once, all further
   * work is short-circuited.
   *
   * @throws AssertionError if this method is not called within a synchronized block which
   *     holds this {@link Linker} as the lock object.
   */
  public Map<String, Binding<?>> linkAll() {
    assertLockHeld();
    if (linkedBindings != null) {
      return linkedBindings;
    }
    for (Binding<?> binding : bindings.values()) {
      if (!binding.isLinked()) {
        toLink.add(binding);
      }
    }
    linkRequested(); // This method throws if bindings are not resolvable/linkable.
    linkedBindings = Collections.unmodifiableMap(bindings);
    return linkedBindings;
  }

  /**
   * Returns the map of all bindings available to this {@link Linker}, if and only if
   * {@link #linkAll()} has successfully returned at least once, otherwise it returns null;
   */
  public Map<String, Binding<?>> fullyLinkedBindings() {
    return linkedBindings;
  }

  /**
   * Links all requested bindings plus their transitive dependencies. This
   * creates JIT bindings as necessary to fill in the gaps.
   *
   * @throws AssertionError if this method is not called within a synchronized block which
   *     holds this {@link Linker} as the lock object.
   */
  public void linkRequested() {
    assertLockHeld();
    Binding<?> binding;
    while ((binding = toLink.poll()) != null) {
      if (binding instanceof DeferredBinding) {
        DeferredBinding deferred = (DeferredBinding) binding;
        String key = deferred.deferredKey;
        boolean mustHaveInjections = deferred.mustHaveInjections;
        if (bindings.containsKey(key)) {
          continue; // A binding for this key has since been linked.
        }
        try {
          Binding<?> resolvedBinding =
              createBinding(key, binding.requiredBy, deferred.classLoader, mustHaveInjections);
          resolvedBinding.setLibrary(binding.library());
          resolvedBinding.setDependedOn(binding.dependedOn());
          // Fail if the type of binding we got wasn't capable of what was requested.
          if (!key.equals(resolvedBinding.provideKey) && !key.equals(resolvedBinding.membersKey)) {
            throw new IllegalStateException("Unable to create binding for " + key);
          }
          // Enqueue the JIT binding so its own dependencies can be linked.
          Binding<?> scopedBinding = scope(resolvedBinding);
          toLink.add(scopedBinding);
          putBinding(scopedBinding);
        } catch (InvalidBindingException e) {
          addError(e.type + " " + e.getMessage() + " required by " + binding.requiredBy);
          bindings.put(key, Binding.UNRESOLVED);
        } catch (UnsupportedOperationException e) {
          addError("Unsupported: " + e.getMessage() + " required by " + binding.requiredBy);
          bindings.put(key, Binding.UNRESOLVED);
        } catch (IllegalArgumentException e) {
          addError(e.getMessage() + " required by " + binding.requiredBy);
          bindings.put(key, Binding.UNRESOLVED);
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else {
        // Attempt to attach the binding to its dependencies. If any dependency
        // is not available, the attach will fail. We'll enqueue creation of
        // that dependency and retry the attachment later.
        attachSuccess = true;
        binding.attach(this);
        if (attachSuccess) {
          binding.setLinked();
        } else {
          toLink.add(binding);
        }
      }
    }

    try {
      errorHandler.handleErrors(errors);
    } finally {
      errors.clear();
    }
  }

  /**
   * Don't permit bindings to be linked without a lock. Callers should lock
   * before requesting any bindings, link the requested bindings, retrieve
   * the linked bindings, and then release the lock.
   */
  private void assertLockHeld() {
    if (!Thread.holdsLock(this)) throw new AssertionError();
  }

  /**
   * Returns a binding for the key in {@code deferred}. The type of binding
   * to be created depends on the key's type:
   * <ul>
   *   <li>Injections of {@code Provider<Foo>}, {@code MembersInjector<Bar>}, and
   *       {@code Lazy<Blah>} will delegate to the bindings of {@code Foo}, {@code Bar}, and
   *       {@code Blah} respectively.
   *   <li>Injections of raw types will use the injectable constructors of those classes.
   *   <li>Any other injection types require @Provides bindings and will error out.
   * </ul>
   */
  private Binding<?> createBinding(String key, Object requiredBy, ClassLoader classLoader,
      boolean mustHaveInjections) {
    String builtInBindingsKey = Keys.getBuiltInBindingsKey(key);
    if (builtInBindingsKey != null) {
      return new BuiltInBinding<Object>(key, requiredBy, classLoader, builtInBindingsKey);
    }
    String lazyKey = Keys.getLazyKey(key);
    if (lazyKey != null) {
      return new LazyBinding<Object>(key, requiredBy, classLoader, lazyKey);
    }

    String className = Keys.getClassName(key);
    if (className == null) {
      throw new InvalidBindingException(key,
          "is a generic class or an array and can only be bound with concrete type parameter(s) "
          + "in a @Provides method.");
    }
    if (Keys.isAnnotated(key)) {
      throw new InvalidBindingException(key,
          "is a @Qualifier-annotated type and must be bound by a @Provides method.");
    }
    Binding<?> binding =
        plugin.getAtInjectBinding(key, className, classLoader, mustHaveInjections);
    if (binding != null) {
      return binding;
    }
    throw new InvalidBindingException(className, "could not be bound with key " + key);
  }

  /** @deprecated Older, generated code still using this should be re-generated. */
  @Deprecated
  public Binding<?> requestBinding(String key, Object requiredBy) {
    return requestBinding(
        key, requiredBy, getClass().getClassLoader(), true, true);
  }

  /**
   * Returns the binding if it exists immediately. Otherwise this returns
   * null. If the returned binding didn't exist or was unlinked, it will be
   * enqueued to be linked.
   */
  public Binding<?> requestBinding(String key, Object requiredBy, ClassLoader classLoader) {
    return requestBinding(key, requiredBy, classLoader, true, true);
  }

  /** @deprecated Older, generated code still using this should be re-generated. */
  @Deprecated
  public Binding<?> requestBinding(String key, Object requiredBy,
      boolean mustHaveInjections, boolean library) {
    return requestBinding(key, requiredBy, getClass().getClassLoader(),
        mustHaveInjections, library);
  }

  /**
   * Returns the binding if it exists immediately. Otherwise this returns
   * null. If the returned binding didn't exist or was unlinked, it will be
   * enqueued to be linked.
   *
   * @param mustHaveInjections true if the the referenced key requires either an
   *     {@code @Inject} annotation is produced by a {@code @Provides} method.
   *     This isn't necessary for Module.injects types because frameworks need
   *     to inject arbitrary classes like JUnit test cases and Android
   *     activities. It also isn't necessary for supertypes.
   */
  public Binding<?> requestBinding(String key, Object requiredBy, ClassLoader classLoader,
      boolean mustHaveInjections, boolean library) {
    assertLockHeld();

    Binding<?> binding = null;
    for (Linker linker = this; linker != null; linker = linker.base) {
      binding = linker.bindings.get(key);
      if (binding != null) {
        if (linker != this && !binding.isLinked()) throw new AssertionError();
        break;
      }
    }

    if (binding == null) {
      // We can't satisfy this binding. Make sure it'll work next time!
      Binding<?> deferredBinding =
          new DeferredBinding(key, classLoader, requiredBy, mustHaveInjections);
      deferredBinding.setLibrary(library);
      deferredBinding.setDependedOn(true);
      toLink.add(deferredBinding);
      attachSuccess = false;
      return null;
    }

    if (!binding.isLinked()) {
      toLink.add(binding); // This binding was never linked; link it now!
    }

    binding.setLibrary(library);
    binding.setDependedOn(true);
    return binding;
  }

  private <T> void putBinding(final Binding<T> binding) {

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
    if (!binding.isSingleton() || binding instanceof SingletonBinding) {
      return binding; // Default scoped binding or already a scoped binding.
    }
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
   * A Binding that implements singleton behaviour around an existing binding.
   */
  private static class SingletonBinding<T> extends Binding<T> {
    private final Binding<T> binding;
    private volatile Object onlyInstance = UNINITIALIZED;

    SingletonBinding(Binding<T> binding) {
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
      if (onlyInstance == UNINITIALIZED) {
        synchronized (this) {
          if (onlyInstance == UNINITIALIZED) {
            onlyInstance = binding.get();
          }
        }
      }
      return (T) onlyInstance;
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      binding.getDependencies(get, injectMembers);
    }

    @Override public boolean isCycleFree() {
      return binding.isCycleFree();
    }

    @Override public boolean isLinked() {
      return binding.isLinked();
    }

    @Override public boolean isVisiting() {
      return binding.isVisiting();
    }

    @Override public boolean library() {
      return binding.library();
    }

    @Override public boolean dependedOn() {
      return binding.dependedOn();
    }

    @Override public void setCycleFree(final boolean cycleFree) {
      binding.setCycleFree(cycleFree);
    }

    @Override public void setVisiting(final boolean visiting) {
      binding.setVisiting(visiting);
    }

    @Override public void setLibrary(boolean library) {
      binding.setLibrary(true);
    }

    @Override public void setDependedOn(boolean dependedOn) {
      binding.setDependedOn(dependedOn);
    }

    @Override protected boolean isSingleton() {
      return true;
    }

    @Override protected void setLinked() {
      binding.setLinked();
    }

    @Override public String toString() {
      return "@Singleton/" + binding.toString();
    }
  }

  /** Handles linker errors appropriately. */
  public interface ErrorHandler {
    ErrorHandler NULL = new ErrorHandler() {
      @Override public void handleErrors(List<String> errors) {
      }
    };

    /**
     * Fail if any errors have been enqueued.
     * Implementations may throw exceptions or report the errors through another
     * channel.  Callers are responsible for clearing enqueued errors.
     *
     * @param errors a potentially empty list of error messages.
     */
    void handleErrors(List<String> errors);
  }

  private static class DeferredBinding extends Binding<Object> {
    /** Loader originally intended to load this binding, to be used in loading the actual one */
    final ClassLoader classLoader;
    final String deferredKey;
    final boolean mustHaveInjections;

    DeferredBinding(String deferredKey, ClassLoader classLoader, Object requiredBy,
        boolean mustHaveInjections) {
      super(null, null, false, requiredBy);
      this.deferredKey = deferredKey;
      this.classLoader = classLoader;
      this.mustHaveInjections = mustHaveInjections;
    }

    @Override public void injectMembers(Object t) {
      throw new UnsupportedOperationException("Deferred bindings must resolve first.");
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      throw new UnsupportedOperationException("Deferred bindings must resolve first.");
    }

    @Override public String toString() {
      return "DeferredBinding[deferredKey=" + deferredKey + "]";
    }
  }
}
