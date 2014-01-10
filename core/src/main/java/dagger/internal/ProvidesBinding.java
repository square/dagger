/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

/**
 * A {@code Binding<T>} which delegates to a module method.
 */
public abstract class ProvidesBinding<T> extends Binding<T> {
  protected final String moduleClass;

  protected final String methodName;

  /**
   * Creates a new {@code ProvidesBinding} with the given "provides" key, a flag as to whether
   * this binding should be scoped, and the requiredBy object for traceability.
   */
  public ProvidesBinding(String key, boolean singleton, String moduleClass, String methodName) {
    // Set requiredBy as fullMethodName to preserve older debugging meaning.
    super(key, null, singleton, moduleClass + "." + methodName + "()");
    this.moduleClass = moduleClass;
    this.methodName = methodName;
  }

  /**
   * A provides binding is responsible for implementing storage of the module instance, and
   * delegation to that module instance's method.
   */
  @Override
  public abstract T get();

  @Override public String toString() {
    return getClass().getName() + "[key=" + provideKey
        + " method=" + moduleClass + "." + methodName + "()" + "]";
  }
}
