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
package com.squareup.objectgraph.internal;

import java.util.Set;

/**
 * Injects a Provider or a MembersInjector.
 */
final class BuiltInBinding<T> extends Binding<T> {
  private final String delegateKey;
  private Binding<?> delegate;

  public BuiltInBinding(String key, Object requiredBy, String delegateKey) {
    super(key, null, false, requiredBy);
    this.delegateKey = delegateKey;
  }

  @Override public void attach(Linker linker) {
    delegate = linker.requestBinding(delegateKey, requiredBy);
  }

  @Override public void injectMembers(T t) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked") // At runtime we know 'T' is a Provider or MembersInjector.
  @Override public T get() {
    return (T) delegate;
  }

  public Binding<?> getDelegate() {
    return delegate;
  }

  @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    // We don't add 'delegate' because it isn't actually used by get() or injectMembers().
  }
}
