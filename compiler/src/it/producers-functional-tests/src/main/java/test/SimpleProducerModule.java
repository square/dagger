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
package test;

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
final class SimpleProducerModule {
  @Qualifier @interface Qual {
    int value();
  }

  @Produces @Qual(0) String str() {
    return "str";
  }

  @Produces @Qual(1) ListenableFuture<String> futureStr() {
    return Futures.immediateFuture("future str");
  }

  @Produces @Qual(2) String strWithArg(int i) {
    return "str with arg";
  }

  @Produces @Qual(3) ListenableFuture<String> futureStrWithArg(int i) {
    return Futures.immediateFuture("future str with arg");
  }

  @Produces(type = SET) String setOfStrElement() {
    return "set of str element";
  }

  @Produces(type = SET) ListenableFuture<String> setOfStrFutureElement() {
    return Futures.immediateFuture("set of str element");
  }

  @Produces(type = SET) String setOfStrElementWithArg(int i) {
    return "set of str element with arg";
  }

  @Produces(type = SET) ListenableFuture<String> setOfStrFutureElementWithArg(int i) {
    return Futures.immediateFuture("set of str element with arg");
  }

  @Produces(type = SET_VALUES) Set<String> setOfStrValues() {
    return ImmutableSet.of("set of str 1", "set of str 2");
  }

  @Produces(type = SET_VALUES) ListenableFuture<Set<String>> setOfStrFutureValues() {
    return Futures.<Set<String>>immediateFuture(ImmutableSet.of("set of str 1", "set of str 2"));
  }

  @Produces(type = SET_VALUES) Set<String> setOfStrValuesWithArg(int i) {
    return ImmutableSet.of("set of str with arg 1", "set of str with arg 2");
  }

  @Produces(type = SET_VALUES) ListenableFuture<Set<String>> setOfStrFutureValuesWithArg(int i) {
    return Futures.<Set<String>>immediateFuture(ImmutableSet.of(
        "set of str with arg 1", "set of str with arg 2"));
  }
}
