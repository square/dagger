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

import dagger.producers.Produces;
import dagger.producers.ProductionComponent;

/**
 * A hook for monitoring execution of {@linkplain ProductionComponent production components}. To
 * install a {@code ProductionComponentMonitor}, contribute to a set binding of
 * {@code ProductionComponentMonitor.Factory}. The factory will be asked to create one monitor for
 * the component, and the resulting single instance will be used to create individual monitors for
 * producers.
 *
 * <p>For example: <pre><code>
 *   {@literal @Module}
 *   final class MyMonitorModule {
 *     {@literal @Provides(type = SET)} ProductionComponentMonitor.Factory provideMonitorFactory(
 *         MyProductionComponentMonitor.Factory monitorFactory) {
 *       return monitorFactory;
 *     }
 *   }
 *
 *   {@literal @ProductionComponent(modules = {MyMonitorModule.class, MyProducerModule.class})}
 *   interface MyComponent {
 *     {@literal ListenableFuture<SomeType>} someType();
 *   }
 * </code></pre>
 *
 * <p>If any of these methods throw, then the exception will be logged, and the framework will act
 * as though a no-op monitor was returned.
 *
 * @author Jesse Beder
 */
public interface ProductionComponentMonitor {
  /** Returns a monitor for an individual {@linkplain Produces producer method}. */
  ProducerMonitor producerMonitorFor(ProducerToken token);

  public interface Factory {
    /** Creates a component-specific monitor when the component is created. */
    ProductionComponentMonitor create(Object component);
  }
}
