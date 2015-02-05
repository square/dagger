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
package dagger;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * A component that inherits the bindings from a parent {@link Component} or {@link Subcomponent}.
 *
 * <p>Subcomponent implementations only exist in the context of a parent and are associated with
 * parents using factory methods on the component.  Simply add a method that returns the
 * subcomponent on the parent.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): add missing spec for @Scope, validation, etc.
@Target(TYPE)
@Documented
public @interface Subcomponent {
  /**
   * A list of classes annotated with {@link Module} whose bindings are used to generate the
   * component implementation.
   *
   * <p>At the moment, only modules without arguments are supported.
   */
  Class<?>[] modules() default {};
}
