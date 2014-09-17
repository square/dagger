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
package dagger.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import dagger.Lazy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class DoubleCheckLazyTest {
  @Test public void get() throws Exception {
    int numThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    final CountDownLatch latch = new CountDownLatch(numThreads);
    LatchedProvider provider = new LatchedProvider(latch);
    final Lazy<Object> lazy = DoubleCheckLazy.create(provider);

    List<Callable<Object>> tasks = Lists.newArrayListWithCapacity(numThreads);
    for (int i = 0; i < numThreads; i++) {
      tasks.add(new Callable<Object>() {
        @Override public Object call() throws Exception {
          latch.countDown();
          return lazy.get();
        }
      });
    }

    List<Future<Object>> futures = executor.invokeAll(tasks);

    assert_().that(provider.provisions.get()).is(1);
    Set<Object> results = Sets.newIdentityHashSet();
    for (Future<Object> future : futures) {
      results.add(future.get());
    }
    assert_().that(results.size()).is(1);
  }

  @Test public void get_null() {
    Lazy<Object> lazy = DoubleCheckLazy.create(new Provider<Object> () {
      @Override public Object get() {
        return null;
      }
    });
    try {
      lazy.get();
      fail();
    } catch (NullPointerException expected) {}
  }

  private static class LatchedProvider implements Provider<Object> {
    final AtomicInteger provisions;
    final CountDownLatch latch;

    LatchedProvider(CountDownLatch latch) {
      this.latch = latch;
      this.provisions = new AtomicInteger();
    }

    @Override
    public Object get() {
      if (latch != null) {
        Uninterruptibles.awaitUninterruptibly(latch);
      }
      provisions.incrementAndGet();
      return new Object();
    }
  }
}
