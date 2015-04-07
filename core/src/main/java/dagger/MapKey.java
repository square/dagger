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

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * It enables to define customized key type annotation for map binding by
 * annotating an annotation of a {@code Map}'s key type. The defined key type
 * annotation can be later applied to the key of the {@code Map}. Currently
 * {@code String} and {@code enum} key types are supported for map binding.
 *
 * <h2>Example</h2> For example, if you want to define a key type annotation
 * called StringKey, you can define it the following way:
 *
 * <pre><code>
 *&#64;MapKey(unwrapValue = true)
 *&#64;Retention(RUNTIME)
 *public &#64;interface StringKey {
 *String value();
 *}
 *</code></pre>
 *
 * if {@code unwrapValue} is false, then the whole annotation will be the key
 * type for the map and annotation instances will be the keys. If
 * {@code unwrapValue} is true, the value() type of key type annotation will be
 * the key type for injected map and the value instances will be the keys.
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Beta
public @interface MapKey {
  /**
   * if {@code unwrapValue} is false, then the whole annotation will be the type and annotation
   * instances will be the keys. If {@code unwrapValue} is true, the value() type of key type
   * annotation will be the key type for injected map and the value instances will be the keys.
   * Currently only support {@code unwrapValue} to be true.
   */
  boolean unwrapValue() default true;
}
