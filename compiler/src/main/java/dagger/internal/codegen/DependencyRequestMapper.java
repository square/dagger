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

import dagger.MembersInjector;
import dagger.producers.Producer;
import javax.inject.Provider;

/**
 * A mapper for associating a {@link DependencyRequest} to a framework class, dependent on
 * the type of code to be generated (e.g., for {@link Provider} or {@link Producer}).
 *
 *  @author Jesse Beder
 *  @since 2.0
 */
abstract class DependencyRequestMapper {
  abstract Class<?> getFrameworkClass(DependencyRequest request);

  private static final class MapperForProvider extends DependencyRequestMapper {
    @Override public Class<?> getFrameworkClass(DependencyRequest request) {
      switch (request.kind()) {
        case INSTANCE:
        case PROVIDER:
        case LAZY:
          return Provider.class;
        case MEMBERS_INJECTOR:
          return MembersInjector.class;
        case PRODUCED:
        case PRODUCER:
          throw new IllegalArgumentException();
        default:
          throw new AssertionError();
      }
    }
  }

  static final DependencyRequestMapper FOR_PROVIDER = new MapperForProvider();

  private static final class MapperForProducer extends DependencyRequestMapper {
    @Override public Class<?> getFrameworkClass(DependencyRequest request) {
      switch (request.kind()) {
        case INSTANCE:
        case PRODUCED:
        case PRODUCER:
          return Producer.class;
        case PROVIDER:
        case LAZY:
          return Provider.class;
        case MEMBERS_INJECTOR:
          return MembersInjector.class;
        default:
          throw new AssertionError();
      }
    }
  }

  static final DependencyRequestMapper FOR_PRODUCER = new MapperForProducer();
}
