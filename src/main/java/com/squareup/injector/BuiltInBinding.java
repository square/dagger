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

/**
 * Injects a Provider or a MembersInjector.
 *
 * @author Jesse Wilson
 */
final class BuiltInBinding<T> extends Binding<T> {
  private Binding<?> delegate;

  public BuiltInBinding(Key<T> key, Object requiredBy) {
    super(requiredBy, key);
  }

  @Override void attach(Linker linker) {
    Type providedType = ((ParameterizedType) key.type).getActualTypeArguments()[0];
    delegate = linker.getBinding(new Key<T>(providedType, key.annotation), requiredBy);
  }

  @Override public void injectMembers(T t) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked") // At runtime we know 'T' is a Provider.
  @Override public T get() {
    return (T) delegate;
  }
}
