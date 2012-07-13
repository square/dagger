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
package com.squareup.injector.internal;

import com.squareup.injector.MembersInjector;
import javax.inject.Provider;

/**
 * Injects a value of a specific type.
 */
public class Binding<T> implements Provider<T>, MembersInjector<T>,
    com.google.inject.Provider<T>, com.google.inject.MembersInjector<T> {
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

  // TODO: split up dependencies for get() and injectMembers().
  public Binding<?>[] getDependencies() {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
