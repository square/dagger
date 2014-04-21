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
package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * An abstract type for classes representing a Dagger binding.  Particularly, contains the
 * {@link Element} that generated the binding and the {@link DependencyRequest} instances that are
 * required to satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding
 * to the subtypes.
 *
 * @author Gregory Kick
 * @since 2.0
 */
abstract class Binding {
  /** The field or method annotated with {@link Inject}. */
  abstract Element bindingElement();

  /** The type enclosing the binding {@link #bindingElement()}. */
  TypeElement enclosingType() {
    return ElementUtil.asTypeElement(bindingElement().getEnclosingElement());
  }

  /**
   * The set of {@link DependencyRequest dependencies} required to satisfy this binding. For fields
   * this will be a single element for the field and for methods this will be an element for each of
   * the method parameters.
   */
  abstract ImmutableSet<DependencyRequest> dependencies();

  /** Returns the {@link #dependencies()} indexed by {@link Key}. */
  ImmutableSetMultimap<Key, DependencyRequest> dependenciesByKey() {
    ImmutableSetMultimap.Builder<Key, DependencyRequest> builder = ImmutableSetMultimap.builder();
    for (DependencyRequest dependency : dependencies()) {
      builder.put(dependency.key(), dependency);
    }
    return builder.build();
  }
}
