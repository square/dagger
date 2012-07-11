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
public abstract class Binding<T> implements Provider<T>, MembersInjector<T>,
    com.google.inject.Provider<T>, com.google.inject.MembersInjector<T> {
  public final String key;
  public final boolean singleton;
  public final boolean injectMembersOnly;
  public final Object requiredBy;

  protected Binding(String key, boolean singleton, boolean injectMembersOnly, Object requiredBy) {
    if (singleton && injectMembersOnly) {
      throw new IllegalArgumentException();
    }
    this.requiredBy = requiredBy;
    this.singleton = singleton;
    this.injectMembersOnly = injectMembersOnly;
    this.key = key;
  }

  /**
   * Links this binding to its dependencies.
   */
  public void attach(Linker linker) {
  }

  @Override public void injectMembers(T t) {
    throw new UnsupportedOperationException();
  }

  @Override public T get() {
    throw new UnsupportedOperationException();
  }

  public Binding<?>[] getDependencies() {
    throw new UnsupportedOperationException();
  }
}
