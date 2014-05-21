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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import java.util.Map;

import javax.inject.Inject;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors known to the
 * annotation processor.
 *
 * @author Gregory Kick
 */
final class InjectBindingRegistry {
  private final Map<Key, ProvisionBinding> bindingsByKey;

  InjectBindingRegistry() {
    this.bindingsByKey = Maps.newLinkedHashMap();
  }

  boolean isRegistered(Key key) {
    return bindingsByKey.containsKey(key);
  }

  void registerBinding(ProvisionBinding binding) {
    ProvisionBinding previousValue = bindingsByKey.put(binding.providedKey(), binding);
    checkState(previousValue == null);
  }

  Optional<ProvisionBinding> getBindingForKey(Key key) {
    return Optional.fromNullable(bindingsByKey.get(checkNotNull(key)));
  }
}
