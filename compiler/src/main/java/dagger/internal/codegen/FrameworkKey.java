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
import com.google.common.base.Function;
import dagger.MembersInjector;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A value object that pairs a {@link Key} with a framework class (e.g.: {@link Provider},
 * {@link MembersInjector}) related to that key.
 *
 *  @author Gregory Kick
 *  @since 2.0
 */
@AutoValue
abstract class FrameworkKey {
  static final Function<DependencyRequest, FrameworkKey> REQUEST_TO_FRAMEWORK_KEY =
      new Function<DependencyRequest, FrameworkKey>() {
        @Override public FrameworkKey apply(DependencyRequest input) {
          return forDependencyRequest(input);
        }
      };

  // TODO(gak): maybe just put this on DependencyRequest?
  static FrameworkKey forDependencyRequest(DependencyRequest dependencyRequest) {
    final Class<?> frameworkClass;
    switch (dependencyRequest.kind()) {
      case INSTANCE:
      case LAZY:
      case PROVIDER:
        frameworkClass = Provider.class;
        break;
      case MEMBERS_INJECTOR:
        checkArgument(!dependencyRequest.key().qualifier().isPresent());
        frameworkClass = MembersInjector.class;
        break;
      default:
        throw new AssertionError();
    }
    return new AutoValue_FrameworkKey(dependencyRequest.key(), frameworkClass);
  }

  abstract Key key();
  abstract Class<?> frameworkClass();
}
