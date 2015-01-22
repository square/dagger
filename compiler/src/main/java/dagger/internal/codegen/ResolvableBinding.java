/*
 * Copyright (C) 2015 Google, Inc.
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
package dagger.internal.codegen;

/**
 * A Binding that can be resolved at request time. For example, a ProvisionBinding for
 * {@code List<T>} might be resolved to {@code List<Foo>} or {@code List<Bar>}
 * depending on how it's requested.
 */
interface ResolvableBinding {  
  /**
   * True if this represents a binding that refers to a type with parameters, and the
   * parameters have been resolved based on a requesting key.
   */
  boolean isResolved();
}
