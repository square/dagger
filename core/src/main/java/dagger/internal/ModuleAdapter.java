/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2012 Google, Inc.
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


import java.util.Map;

/**
 * Extracts bindings from an {@code @Module}-annotated class.
 */
public abstract class ModuleAdapter<T> {
  public final String[] injectableTypes;
  public final Class<?>[] staticInjections;
  public final boolean overrides;
  public final Class<?>[] includes;
  public final boolean complete;
  public final boolean library;
  protected T module;

  protected ModuleAdapter(String[] injectableTypes, Class<?>[] staticInjections, boolean overrides,
      Class<?>[] includes, boolean complete, boolean library) {
    this.injectableTypes = injectableTypes;
    this.staticInjections = staticInjections;
    this.overrides = overrides;
    this.includes = includes;
    this.complete = complete;
    this.library = library;
  }

  /**
   * Returns bindings for the {@code @Provides} methods of {@code module}. The
   * returned bindings must be linked before they can be used to inject values.
   */
  public void getBindings(@SuppressWarnings("unused") Map<String, Binding<?>> map) {
    // no-op;
  }

  /**
   * Returns a new instance of the module class created using a no-args
   * constructor. Only used when a manually-constructed module is not supplied.
   */
  protected T newModule() {
    throw new UnsupportedOperationException("No no-args constructor on " + getClass().getName());
  }

  public T getModule() {
    return module;
  }
}
