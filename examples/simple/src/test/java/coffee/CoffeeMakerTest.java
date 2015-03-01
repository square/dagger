/**
 * Copyright (C) 2015 Square, Inc.
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
package coffee;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

public class CoffeeMakerTest {
  @Inject CoffeeMaker coffeeMaker;
  @Inject Heater heater;

  @Before public void setUp() {
    ObjectGraph.create(new TestModule()).inject(this);
  }

  @Module(
      includes = DripCoffeeModule.class,
      injects = CoffeeMakerTest.class,
      overrides = true
  )
  static class TestModule {
    @Provides @Singleton Heater provideHeater() {
      return Mockito.mock(Heater.class);
    }
  }

  @Test public void testHeaterIsTurnedOnAndThenOff() {
    Mockito.when(heater.isHot()).thenReturn(true);
    coffeeMaker.brew();
    Mockito.verify(heater, Mockito.times(1)).on();
    Mockito.verify(heater, Mockito.times(1)).off();
  }
}
