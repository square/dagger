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
package dagger.producers.monitoring.internal;

import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Provider;

/**
 * A class that provides a {@link ProductionComponentMonitor} for use in production components.
 *
 * <p>This caches the underlying the monitor, since we want a single instance for each component.
 */
public final class MonitorCache {
  private static final Logger logger = Logger.getLogger(MonitorCache.class.getName());

  private volatile ProductionComponentMonitor monitor;

  /**
   * Returns the underlying monitor. This will only actually compute the monitor the first time it
   * is called; subsequent calls will simply return the cached value, so the arguments to this
   * method are ignored. It is expected (though not checked) that this method is called with
   * equivalent arguments each time (like a {@link dagger.Provides @Provides} method would).
   */
  public ProductionComponentMonitor monitor(
      Provider<?> componentProvider,
      Provider<Set<ProductionComponentMonitor.Factory>> monitorFactorySetProvider) {
    ProductionComponentMonitor result = monitor;
    if (result == null) {
      synchronized (this) {
        result = monitor;
        if (result == null) {
          try {
            ProductionComponentMonitor.Factory factory =
                Monitors.delegatingProductionComponentMonitorFactory(
                    monitorFactorySetProvider.get());
            result = monitor = factory.create(componentProvider.get());
          } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "RuntimeException while constructing monitor factories.", e);
            result = monitor = Monitors.noOpProductionComponentMonitor();
          }
        }
      }
    }
    return result;
  }
}
