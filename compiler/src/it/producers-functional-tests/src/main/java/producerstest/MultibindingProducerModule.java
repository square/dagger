/*
* Copyright (C) 2015 Google, Inc.
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
package producerstest;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.Set;
import javax.inject.Qualifier;

import static dagger.producers.Produces.Type.SET;
import static dagger.producers.Produces.Type.SET_VALUES;

@ProducerModule
final class MultibindingProducerModule {
  @Qualifier
  @interface PossiblyThrowingSet {}

  @Produces(type = SET)
  static ListenableFuture<String> futureStr() {
    return Futures.immediateFuture("foo");
  }

  @Produces(type = SET)
  static String str() {
    return "bar";
  }

  @Produces(type = SET_VALUES)
  static ListenableFuture<Set<String>> futureStrs() {
    return Futures.<Set<String>>immediateFuture(ImmutableSet.of("foo1", "foo2"));
  }

  @Produces(type = SET_VALUES)
  static Set<String> strs() {
    return ImmutableSet.of("bar1", "bar2");
  }

  @Produces
  static int strCount(Set<String> strs) {
    return strs.size();
  }

  @Produces(type = SET)
  @PossiblyThrowingSet
  static String successfulStringForSet() {
    return "singleton";
  }

  @Produces(type = SET_VALUES)
  @PossiblyThrowingSet
  static Set<String> successfulStringsForSet() {
    return ImmutableSet.of("double", "ton");
  }

  @Produces(type = SET)
  @PossiblyThrowingSet
  static String throwingStringForSet() {
    throw new RuntimeException("monkey");
  }
}
