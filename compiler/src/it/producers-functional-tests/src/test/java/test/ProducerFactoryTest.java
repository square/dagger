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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import dagger.producers.Producer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ProducerFactoryTest {
  @Test public void noArgMethod() throws Exception {
    SimpleProducerModule module = new SimpleProducerModule();
    Producer<String> producer =
        new SimpleProducerModule_StrFactory(module, MoreExecutors.directExecutor());
    assertThat(producer.get().get()).isEqualTo("str");
  }

  @Test public void singleArgMethod() throws Exception {
    SimpleProducerModule module = new SimpleProducerModule();
    SettableFuture<Integer> intFuture = SettableFuture.create();
    Producer<Integer> intProducer = producerOfFuture(intFuture);
    Producer<String> producer = new SimpleProducerModule_StrWithArgFactory(
        module, MoreExecutors.directExecutor(), intProducer);
    assertThat(producer.get().isDone()).isFalse();
    intFuture.set(42);
    assertThat(producer.get().get()).isEqualTo("str with arg");
  }

  private static <T> Producer<T> producerOfFuture(final ListenableFuture<T> future) {
    return new Producer<T>() {
      @Override public ListenableFuture<T> get() {
        return future;
      }
    };
  }
}
