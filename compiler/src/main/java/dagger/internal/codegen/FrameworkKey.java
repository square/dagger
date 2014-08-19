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

import com.google.auto.value.AutoValue;
import dagger.MembersInjector;
import javax.inject.Provider;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A value object that pairs a {@link Key} with a framework class (e.g.: {@link Provider},
 * {@link MembersInjector}) related to that key.
 *
 *  @author Gregory Kick
 *  @since 2.0
 */
@AutoValue
abstract class FrameworkKey {
  static FrameworkKey create(Key key, Class<?> frameworkClass) {
    return new AutoValue_FrameworkKey(checkNotNull(key), checkNotNull(frameworkClass));
  }

  static FrameworkKey forProvisionBinding(ProvisionBinding binding) {
    return new AutoValue_FrameworkKey(binding.providedKey(), Provider.class);
  }

  abstract Key key();
  abstract Class<?> frameworkClass();
}
