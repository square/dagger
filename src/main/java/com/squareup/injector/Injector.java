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

import com.squareup.injector.internal.Binding;
import com.squareup.injector.internal.InternalInjector;
import com.squareup.injector.internal.Keys;
import java.util.Map;

/**
 * Dependency injector.
 *
 * <p>The following injection features are supported:
 * <ul>
 *   <li>Field injection. A class may have any number of field injections, and
 *       fields may be of any visibility. Static fields will be injected each
 *       time an instance is injected.
 *   <li>Constructor injection. A class may have a single {@code
 *       @Inject}-annotated constructor. Classes that have fields injected
 *       may omit the {@link @Inject} annotation if they have a public
 *       no-arguments constructor.
 *   <li>Injection of {@code @Provides} method parameters.
 *   <li>{@code @Provides} methods annotated {@code @Singleton}.
 *   <li>Constructor-injected classes annotated {@code @Singleton}.
 *   <li>Injection of {@link javax.inject.Provider}s.
 *   <li>Qualifier annotations on injected parameters and fields.
 *   <li>JSR 330 annotations.
 * </ul>
 *
 * <p>The following injection features are not currently supported:
 * <ul>
 *   <li>Method injection.</li>
 *   <li>Circular dependencies.</li>
 * </ul>
 *
 * @author Jesse Wilson
 */
public final class Injector {
  /**
   * Creates an injector defined by {@code modules} and immediately uses it to
   * create an instance of {@code type}. The modules can be of any type, and
   * must contain {@code @Provides} methods.
   */
  public <T> T inject(Class<T> type, Object... modules) {
    InternalInjector injector = new InternalInjector();
    Map<String, Binding<?>> combined = Modules.moduleToMap(Modules.combine(modules));
    for (Binding<?> binding : combined.values()) {
      injector.putBinding(binding);
    }
    return (T) injector.inject(Keys.get(type));
  }
}
