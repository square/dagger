/*
 * Copyright (C) 2014 Google, Inc.
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
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Annotates an interface or abstract class for which a fully-formed, dependency-injected
 * implementation is to be generated from a set of {@linkplain #modules}. The generated class will
 * have the name of the type annotated with {@code @Component} prepended with
 * {@code DaggerComponent_}.  For example, {@code @Component interface MyComponent {...}} will
 * produce an implementation named {@code DaggerComponent_MyComponent}.
 *
 * <h2>Component methods
 *
 * <p>Every type annotated with {@code @Component} must contain at least one abstract component
 * method. Component methods must either represent {@linkplain Provider provision} or
 * {@linkplain MembersInjector member injection}.
 *
 * Provision methods have no arguments and return an {@link Inject injected} or
 * {@link Provides provided} type.  Each may have a {@link Qualifier} annotation as well. The
 * following are all valid provision method declarations: <pre>   {@code
 *   SomeType getSomeType();
 *   Set<SomeType> getSomeTypes();
 *   @PortNumber int getPortNumber();
 *   }</pre>
 *
 * Member injection methods take a single parameter and optionally return that same type. The
 * following are all valid member injection method declarations: <pre>   {@code
 *   void injectSomeType(SomeType someType);
 *   SomeType injectAndReturnSomeType(SomeType someType);
 *   }</pre>
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): add missing spec for @Scope
// TODO(gak): add missing spec for component dependencies
@Target(TYPE)
@Documented
public @interface Component {
  /**
   * A list of classes annotated with {@link Module} whose bindings are used to generate the
   * component implementation.
   */
  Class<?>[] modules() default {};

  /**
   * A list of types that are to be used as component dependencies.
   */
  Class<?>[] dependencies() default {};
}
