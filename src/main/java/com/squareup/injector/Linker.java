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
package com.squareup.injector;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import javax.inject.Provider;

/**
 * Links bindings to their dependencies.
 *
 * @author Jesse Wilson
 */
final class Linker {
  private final Injector injector;

  /** Bindings requiring a call to attach(). May contain deferred bindings. */
  private final Queue<Binding<?>> unattachedBindings = new LinkedList<Binding<?>>();

  /** True unless calls to requestBinding() were unable to satisfy the binding. */
  private boolean currentAttachSuccess = true;

  public Linker(Injector injector) {
    this.injector = injector;
  }

  /**
   * Links the bindings in {@code bindings}, creating JIT bindings as necessary
   * to fill in the gaps.
   */
  public void link(Collection<Binding<?>> bindings) {
    unattachedBindings.addAll(bindings);

    Binding binding;
    while ((binding = unattachedBindings.poll()) != null) {
      if (binding instanceof DeferredBinding) {
        promoteDeferredBinding((DeferredBinding<?>) binding);
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
   * Once the just-in-time binding has been created, it is enqueued to be
   * attached until its own dependencies have been satisfied.
   */
  private <T> void promoteDeferredBinding(DeferredBinding<T> deferred) {
    try {
      Binding<T> promoted;
      if (deferred.key.type instanceof ParameterizedType) {
        Type rawType = ((ParameterizedType) deferred.key.type).getRawType();
        if (rawType == Provider.class || rawType == MembersInjector.class) {
          // Handle injections like Provider<Foo> and MembersInjector<Foo> by delegating.
          promoted = new BuiltInBinding<T>(deferred.key, deferred.requiredBy);
        } else {
          throw new IllegalArgumentException("No binding for " + deferred.key);
        }
      } else {
        // Handle all other injections with constructor bindings.
        promoted = ConstructorBinding.create(deferred.key);
      }
      unattachedBindings.add(promoted);
      injector.putBinding(promoted);
    } catch (Exception e) {
      injector.addError(e.getMessage() + " required by " + deferred.requiredBy);
      injector.putBinding(new UnresolvedBinding<T>(deferred.requiredBy, deferred.key));
    }
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
  public <T> Binding<T> requestBinding(final Key<T> key, final Object requiredBy) {
    Binding<T> binding = injector.getBinding(key);
    if (binding == null) {
      // We can't satisfy this binding. Make sure it'll work next time!
      unattachedBindings.add(new DeferredBinding<T>(requiredBy, key));
      currentAttachSuccess = false;
    }
    return binding;
  }

  private static class DeferredBinding<T> extends Binding<T> {
    private DeferredBinding(Object requiredBy, Key<T> key) {
      super(requiredBy, key);
    }
  }

  private static class UnresolvedBinding<T> extends Binding<T> {
    private UnresolvedBinding(Object definedBy, Key<T> key) {
      super(definedBy, key);
    }
  }
}
