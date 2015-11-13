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
package test.subcomponent.repeat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public final class RepeatedModuleTest {
  private ParentComponent parentComponent;

  @Before
  public void initializeParentComponent() {
    this.parentComponent = DaggerParentComponent.builder().build();
  }

  @Test
  public void repeatedModuleHasSameStateInSubcomponent() {
    SubcomponentWithRepeatedModule childComponent =
        parentComponent.newChildComponentBuilder().build();
    assertThat(parentComponent.state()).isSameAs(childComponent.state());
  }

  @Test
  public void repeatedModuleHasSameStateInGrandchildSubcomponent() {
    SubcomponentWithoutRepeatedModule childComponent =
        parentComponent.newChildComponentWithoutRepeatedModule();
    SubcomponentWithRepeatedModule grandchildComponent =
        childComponent.newGrandchildBuilder().build();
    assertThat(parentComponent.state()).isSameAs(grandchildComponent.state());
  }

  @Test
  public void repeatedModuleBuilderThrowsInSubcomponent() {
    SubcomponentWithRepeatedModule.Builder childComponentBuilder =
        parentComponent.newChildComponentBuilder();
    try {
      childComponentBuilder.repeatedModule(new RepeatedModule());
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessage(
              "test.subcomponent.repeat.RepeatedModule cannot be set "
                  + "because it is inherited from the enclosing component");
    }
  }

  @Test
  public void repeatedModuleBuilderThrowsInGrandchildSubcomponent() {
    SubcomponentWithoutRepeatedModule childComponent =
        parentComponent.newChildComponentWithoutRepeatedModule();
    SubcomponentWithRepeatedModule.Builder grandchildComponentBuilder =
        childComponent.newGrandchildBuilder();
    try {
      grandchildComponentBuilder.repeatedModule(new RepeatedModule());
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessage(
              "test.subcomponent.repeat.RepeatedModule cannot be set "
                  + "because it is inherited from the enclosing component");
    }
  }
}
