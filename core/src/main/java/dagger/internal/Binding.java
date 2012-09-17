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

import dagger.MembersInjector;
import java.util.Set;
import javax.inject.Provider;

/**
 * Injects a value of a specific type.
 */
public class Binding<T> implements Provider<T>, MembersInjector<T> {
  public static final Binding<Object> UNRESOLVED = new Binding<Object>(null, null, false, null);

  /** The key used to provide instances of 'T', or null if this binding cannot provide instances. */
  public final String provideKey;

  /** The key used to inject members of 'T', or null if this binding cannot inject members. */
  public final String membersKey;

  /** True if the provided instance is always the same object. */
  public final boolean singleton;

  public final Object requiredBy;
  public boolean linked;

  protected Binding(String provideKey, String membersKey, boolean singleton, Object requiredBy) {
    if (singleton && provideKey == null) {
      throw new IllegalArgumentException();
    }
    this.provideKey = provideKey;
    this.membersKey = membersKey;
    this.singleton = singleton;
    this.requiredBy = requiredBy;
  }

  /**
   * Links this binding to its dependencies.
   */
  public void attach(Linker linker) {
  }

  @Override public void injectMembers(T t) {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override public T get() {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Populates {@code getBindings} and {@code injectMembersBindings} with the
   * bindings used by this binding to satisfy {@link #get} and {@link
   * #injectMembers} calls, respectively.
   *
   * @param getBindings the bindings required by this binding's {@code get}
   *     method. Although {@code get} usually calls into {@code injectMembers},
   *     this <i>does not</i> contain the injectMembers bindings.
   * @param injectMembersBindings the bindings required by this binding's {@code
   *     injectMembers} method.
   */
  public void getDependencies(Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
