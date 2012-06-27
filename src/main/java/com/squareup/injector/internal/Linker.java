/**
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
package com.squareup.injector.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Links bindings to their dependencies.
 *
 * @author Jesse Wilson
 */
public final class Linker {
  private final InternalInjector injector;

  /** Bindings requiring a call to attach(). May contain deferred bindings. */
  private final Queue<Binding<?>> unattachedBindings = new LinkedList<Binding<?>>();

  /** True unless calls to requestBinding() were unable to satisfy the binding. */
  private boolean currentAttachSuccess = true;

  public Linker(InternalInjector injector) {
    this.injector = injector;
  }

  /**
   * Links the bindings in {@code bindings}, creating JIT bindings as necessary
   * to fill in the gaps. When this returns all bindings and their dependencies
   * will be attached.
   */
  public void link(Collection<Binding<?>> bindings) {
    unattachedBindings.addAll(bindings);

    Binding binding;
    while ((binding = unattachedBindings.poll()) != null) {
      if (binding instanceof DeferredBinding) {
        if (injector.getBinding(binding.key) != null) {
          continue; // A binding for this key has already been promoted.
        }
        try {
          Binding<?> jitBinding = createJitBinding((DeferredBinding<?>) binding);
          // Enqueue the JIT binding so its own dependencies can be linked.
          unattachedBindings.add(jitBinding);
          injector.putBinding(jitBinding);
        } catch (Exception e) {
          injector.addError(e.getMessage() + " required by " + binding.requiredBy);
          injector.putBinding(new UnresolvedBinding<Object>(binding.requiredBy, binding.key));
        }
      } else {
        attachBinding(binding);
      }
    }
  }

  /**
   * Creates a just-in-time binding for the key in {@code deferred}. The type of
   * binding to be created depends on the key's type:
   * <ul>
   *   <li>Injections of {@code Provider<Foo>} and {@code MembersInjector<Bar>}
   *       will delegate to the bindings of {@code Foo} and {@code Bar}
   *       respectively.
   *   <li>Injections of other types will use the injectable constructors of
   *       those classes.
   * </ul>
   */
  private Binding<?> createJitBinding(DeferredBinding<?> deferred) throws ClassNotFoundException {
    String delegateKey = Keys.getDelegateKey(deferred.key);
    if (delegateKey != null) {
      return new BuiltInBinding<Object>(deferred.key, deferred.requiredBy, delegateKey);
    }

    String className = Keys.getClassName(deferred.key);
    if (className != null && !Keys.isAnnotated(deferred.key)) {
      // Handle all other injections with constructor bindings.
      return ConstructorBinding.create(Class.forName(className));
    }

    throw new IllegalArgumentException("No binding for " + deferred.key);
  }

  /**
   * Attempts to attach {@code binding} to its dependencies. If any dependency
   * is not available, the attach will fail. We'll enqueue creation of that
   * dependency and retry the attachment later.
   */
  private void attachBinding(Binding binding) {
    currentAttachSuccess = true;
    binding.attach(this);
    if (!currentAttachSuccess) {
      unattachedBindings.add(binding);
    }
  }

  /**
   * Returns the binding if it exists immediately. Otherwise this returns
   * null. The injector will create that binding later and reattach the
   * caller's binding.
   */
  public Binding<?> requestBinding(String key, final Object requiredBy) {
    Binding<?> binding = injector.getBinding(key);
    if (binding == null) {
      // We can't satisfy this binding. Make sure it'll work next time!
      unattachedBindings.add(new DeferredBinding<Object>(requiredBy, key));
      currentAttachSuccess = false;
    }
    return binding;
  }

  private static class DeferredBinding<T> extends Binding<T> {
    private DeferredBinding(Object requiredBy, String key) {
      super(requiredBy, key);
    }
  }

  private static class UnresolvedBinding<T> extends Binding<T> {
    private UnresolvedBinding(Object definedBy, String key) {
      super(definedBy, key);
    }
  }
}
