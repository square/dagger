/*
 * Copyright (C) 2015 Google Inc.
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
package dagger.producers.monitoring;

/**
 * A hook for monitoring the execution of individual {@linkplain Produces producer methods}. See
 * {@link ProductionComponentMonitor} for how to install these monitors.
 *
 * <p>The lifecycle of the monitor is:
 * <ul>
 *   <li>{@link #methodStarting}
 *   <li>The method is called
 *   <li>{@link #methodFinished}
 *   <li>If the method returns a value, then:
 *   <ul>
 *     <li>{#succeeded} if the method returned normally; or
 *     <li>{#failed} if the method threw an exception.
 *   </ul>
 *   <li>If the method returns a future, then:
 *   <ul>
 *     <li>{#succeeded} if the method returned normally, and the future succeeded; or
 *     <li>{#failed} if the method threw an exception, or returned normally and the future failed.
 *   </ul>
 * </ul>
 *
 * <p>If any of the monitor's methods throw, then the exception will be logged and processing will
 * continue unaffected.
 *
 * @author Jesse Beder
 */
public abstract class ProducerMonitor {
  /**
   * Called when the producer method is about to start executing.
   *
   * <p>When multiple monitors are installed, the order that each monitor will call
   * {@code methodWillStart} is unspecified, but will remain consistent throughout the course of the
   * execution of a component.
   */
  public void methodStarting() {}

  /**
   * Called when the producer method has finished executing.
   *
   * <p>When multiple monitors are installed, the {@code methodFinished} calls will be in the
   * reverse order from the {@link #methodWillStart} calls.
   */
  public void methodFinished() {}

  /**
   * Called when the producer’s future has completed successfully with a value.
   *
   * <p>When multiple monitors are installed, the {@code futureSucceeded} calls will be in the
   * reverse order from the {@link #methodWillStart} calls.
   */
  public void succeeded(Object o) {}

  /**
   * Called when the producer's future has failed with an exception.
   *
   * <p>When multiple monitors are installed, the {@code futureFailed} calls will be in the reverse
   * order from the {@link #methodWillStart} calls.
   */
  public void failed(Throwable t) {}
}
