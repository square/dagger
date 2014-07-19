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

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import java.util.Map;
import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor.
 *
 * @author Gregory Kick
 */
final class InjectBindingRegistry {
  private final Map<Key, ProvisionBinding> provisionBindingsByKey;
  private final Map<Key, MembersInjectionBinding> membersInjectionBindingsByKey;
  private final Key.Factory keyFactory;

  InjectBindingRegistry(Key.Factory keyFactory) {
    this.provisionBindingsByKey = Maps.newLinkedHashMap();
    this.membersInjectionBindingsByKey = Maps.newLinkedHashMap();
    this.keyFactory = keyFactory;
  }

  void registerProvisionBinding(ProvisionBinding binding) {
    ProvisionBinding previousValue = provisionBindingsByKey.put(binding.providedKey(), binding);
    checkState(previousValue == null);
  }

  void registerMembersInjectionBinding(MembersInjectionBinding binding) {
    MembersInjectionBinding previousValue = membersInjectionBindingsByKey.put(
        keyFactory.forType(binding.bindingElement().asType()), binding);
    checkState(previousValue == null);
  }

  Optional<ProvisionBinding> getProvisionBindingForKey(Key key) {
    return Optional.fromNullable(provisionBindingsByKey.get(checkNotNull(key)));
  }

  Optional<MembersInjectionBinding> getMembersInjectionBindingForKey(Key key) {
    return Optional.fromNullable(membersInjectionBindingsByKey.get(checkNotNull(key)));
  }
}
