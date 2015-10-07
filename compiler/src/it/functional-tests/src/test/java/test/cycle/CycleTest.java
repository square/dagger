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
package test.cycle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import test.cycle.Cycles.A;
import test.cycle.Cycles.C;
import test.cycle.Cycles.CycleComponent;
import test.cycle.Cycles.S;
import test.cycle.Cycles.SelfCycleComponent;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class CycleTest {
  @Test
  public void providerIndirectionSelfCycle() {
    SelfCycleComponent selfCycleComponent = DaggerCycles_SelfCycleComponent.create();
    S s = selfCycleComponent.s();
    assertThat(s.sProvider.get()).isNotNull();
  }

  @Test
  public void providerIndirectionCycle() {
    CycleComponent cycleComponent = DaggerCycles_CycleComponent.create();
    A a = cycleComponent.a();
    C c = cycleComponent.c();
    assertThat(c.aProvider.get()).isNotNull();
    assertThat(a.b.c.aProvider.get()).isNotNull();
    assertThat(a.e.d.b.c.aProvider.get()).isNotNull();
  }

  @Test
  public void lazyIndirectionSelfCycle() {
    SelfCycleComponent selfCycleComponent = DaggerCycles_SelfCycleComponent.create();
    S s = selfCycleComponent.s();
    assertThat(s.sLazy.get()).isNotNull();
  }

  @Test
  public void lazyIndirectionCycle() {
    CycleComponent cycleComponent = DaggerCycles_CycleComponent.create();
    A a = cycleComponent.a();
    C c = cycleComponent.c();
    assertThat(c.aLazy.get()).isNotNull();
    assertThat(a.b.c.aLazy.get()).isNotNull();
    assertThat(a.e.d.b.c.aLazy.get()).isNotNull();
  }
}
