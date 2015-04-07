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
package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility methods for use in generated producer code.
 *
 * @author Jesse Beder
 * @since 2.0
 */
public final class Producers {
  /**
   * Returns a future of {@link Produced} that represents the completion (either success or failure)
   * of the given future. If the input future succeeds, then the resulting future also succeeds with
   * a successful {@code Produced}; if the input future fails, then the resulting future succeeds
   * with a failing {@code Produced}.
   *
   * <p>Cancelling the resulting future will propagate the cancellation to the input future; but
   * cancelling the input future will trigger the resulting future to succeed with a failing
   * {@code Produced}.
   */
  // TODO(user): Document what happens with an InterruptedException after you figure out how to
  // trigger one in a test.
  public static <T> ListenableFuture<Produced<T>> createFutureProduced(ListenableFuture<T> future) {
    return Futures.catching(
        Futures.transform(future, Producers.<T>wrapValueInProduced()),
        Throwable.class,
        Producers.<T>futureFallbackForProduced());

  }

  private static final Function<Object, Produced<Object>> WRAP_VALUE_IN_PRODUCED =
      new Function<Object, Produced<Object>>() {
    @Override public Produced<Object> apply(final Object value) {
      return new Produced<Object>() {
        @Override public Object get() {
          return value;
        }
      };
    }
  };

  @SuppressWarnings({"unchecked", "rawtypes"})  // bivariant implementation
  private static <T> Function<T, Produced<T>> wrapValueInProduced() {
    return (Function) WRAP_VALUE_IN_PRODUCED;
  }

  private static final Function<Throwable, Produced<Object>> FUTURE_FALLBACK_FOR_PRODUCED =
      new Function<Throwable, Produced<Object>>() {
    @Override public Produced<Object> apply(final Throwable t) {
      return new Produced<Object>() {
        @Override public Object get() throws ExecutionException {
          throw new ExecutionException(t);
        }
      };
    }
  };

  @SuppressWarnings({"unchecked", "rawtypes"})  // bivariant implementation
  private static <T> Function<Throwable, Produced<T>> futureFallbackForProduced() {
    return (Function) FUTURE_FALLBACK_FOR_PRODUCED;
  }

  /**
   * Returns a future of a {@code Set} that contains a single element: the result of the input
   * future.
   */
  public static <T> ListenableFuture<Set<T>> createFutureSingletonSet(ListenableFuture<T> future) {
    return Futures.transform(future, new Function<T, Set<T>>() {
      @Override public Set<T> apply(T value) {
        return ImmutableSet.of(value);
      }
    });
  }

  /**
   * Submits a callable to an executor, returning the future representing the task. This mirrors
   * {@link com.google.common.util.concurrent.ListeningExecutorService#submit}, but only requires an
   * {@link Executor}.
   *
   * @throws RejectedExecutionException if this task cannot be accepted for execution.
   */
  public static <T> ListenableFuture<T> submitToExecutor(Callable<T> callable, Executor executor) {
    ListenableFutureTask<T> future = ListenableFutureTask.create(callable);
    executor.execute(future);
    return future;
  }

  /**
   * Returns a producer that immediately executes the binding logic for the given provider every
   * time it is called.
   */
  public static <T> Producer<T> producerFromProvider(final Provider<T> provider) {
    checkNotNull(provider);
    return new AbstractProducer<T>() {
      @Override protected ListenableFuture<T> compute() {
        return Futures.immediateFuture(provider.get());
      }
    };
  }

  private Producers() {}
}
