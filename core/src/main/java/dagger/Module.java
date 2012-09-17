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
package dagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a class that contributes to the object graph.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Module {
  Class<?>[] entryPoints() default { };
  Class<?>[] staticInjections() default { };

  /**
   * True if {@code @Provides} methods from this module are permitted to
   * override those of other modules. This is a dangerous feature as it permits
   * binding conflicts to go unnoticed. It should only be used in test and
   * development modules.
   */
  boolean overrides() default false;

  /**
   * @deprecated Use module includes vs. children
   */
  @Deprecated
  Class<?>[] children() default { };

  /**
   * Additional {@code @Module}-annotated classes from which this module is
   * composed. The de-duplicated contributions of the modules in
   * {@code includes}, and of their inclusions recursively, are all contributed
   * to the object graph.
   */
  Class<?>[] includes() default { };

  /**
   * True if all of the bindings required by this module can also be satisfied
   * by this module. If a module is complete it is eligible for additional
   * static checking: tools can detect if required bindings are not available.
   * Modules that have external dependencies must use {@code complete = false}.
   */
  boolean complete() default true;
}
