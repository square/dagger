/*
 * Copyright (C) 2016 The Dagger Authors.
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

package test.optional;

import static com.google.common.truth.Truth.assertThat;
import static test.optional.OptionalBindingComponents.Value.QUALIFIED_VALUE;
import static test.optional.OptionalBindingComponents.Value.VALUE;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import test.optional.OptionalBindingComponents.AbsentOptionalBindingComponent;
import test.optional.OptionalBindingComponents.PresentOptionalBindingComponent;
import test.optional.OptionalBindingComponents.PresentOptionalBindingSubcomponent;

/** Tests for optional bindings. */
@RunWith(JUnit4.class)
public final class OptionalBindingComponentsTest {
  private AbsentOptionalBindingComponent absent;
  private PresentOptionalBindingComponent present;
  private PresentOptionalBindingSubcomponent presentChild;

  @Before
  public void setUp() {
    absent = DaggerOptionalBindingComponents_AbsentOptionalBindingComponent.create();
    present = DaggerOptionalBindingComponents_PresentOptionalBindingComponent.create();
    presentChild = absent.presentChild();
  }

  @Test
  public void absentOptional() {
    assertThat(absent.optionalInstance()).isAbsent();
  }

  @Test
  public void absentOptionalProvider() {
    assertThat(absent.optionalProvider()).isAbsent();
  }

  @Test
  public void absentOptionalLazy() {
    assertThat(absent.optionalLazy()).isAbsent();
  }

  @Test
  public void absentOptionalLazyProvider() {
    assertThat(absent.optionalLazyProvider()).isAbsent();
  }

  @Test
  public void absentQualifiedOptional() {
    assertThat(absent.qualifiedOptionalInstance()).isAbsent();
  }

  @Test
  public void absentQualifiedOptionalProvider() {
    assertThat(absent.qualifiedOptionalProvider()).isAbsent();
  }

  @Test
  public void absentQualifiedOptionalLazy() {
    assertThat(absent.qualifiedOptionalLazy()).isAbsent();
  }

  @Test
  public void absentQualifiedOptionalLazyProvider() {
    assertThat(absent.qualifiedOptionalLazyProvider()).isAbsent();
  }

  @Test
  public void presentOptional() {
    assertThat(present.optionalInstance()).hasValue(VALUE);
  }

  @Test
  public void presentOptionalProvider() {
    assertThat(present.optionalProvider().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void presentOptionalLazy() {
    assertThat(present.optionalLazy().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void presentOptionalLazyProvider() {
    assertThat(present.optionalLazyProvider().get().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void presentQualifiedOptional() {
    assertThat(present.qualifiedOptionalInstance()).hasValue(QUALIFIED_VALUE);
  }

  @Test
  public void presentQualifiedOptionalProvider() {
    assertThat(present.qualifiedOptionalProvider().get().get()).isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void presentQualifiedOptionalLazy() {
    assertThat(present.qualifiedOptionalLazy().get().get()).isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void presentQualifiedOptionalLazyProvider() {
    assertThat(present.qualifiedOptionalLazyProvider().get().get().get())
        .isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void presentChildOptional() {
    assertThat(presentChild.optionalInstance()).hasValue(VALUE);
  }

  @Test
  public void presentChildOptionalProvider() {
    assertThat(presentChild.optionalProvider().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void presentChildOptionalLazy() {
    assertThat(presentChild.optionalLazy().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void presentChildOptionalLazyProvider() {
    assertThat(presentChild.optionalLazyProvider().get().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void presentChildQualifiedOptional() {
    assertThat(presentChild.qualifiedOptionalInstance()).hasValue(QUALIFIED_VALUE);
  }

  @Test
  public void presentChildQualifiedOptionalProvider() {
    assertThat(presentChild.qualifiedOptionalProvider().get().get()).isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void presentChildQualifiedOptionalLazy() {
    assertThat(presentChild.qualifiedOptionalLazy().get().get()).isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void presentChildQualifiedOptionalLazyProvider() {
    assertThat(presentChild.qualifiedOptionalLazyProvider().get().get().get())
        .isEqualTo(QUALIFIED_VALUE);
  }
}
