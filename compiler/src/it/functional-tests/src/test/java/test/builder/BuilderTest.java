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
package test.builder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class BuilderTest {

  @Test public void testInterfaceBuilder() {
    TestComponentWithBuilderInterface.Builder builder =
        DaggerTestComponentWithBuilderInterface.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.intModule(new IntModuleIncludingDoubleAndFloat(1))
        .stringModule(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.
    
    TestComponentWithBuilderInterface component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isWithin(0).of(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test public void testAbstractClassBuilder() {
    TestComponentWithBuilderAbstractClass.Builder builder =
        TestComponentWithBuilderAbstractClass.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.intModule(new IntModuleIncludingDoubleAndFloat(1))
        .stringModule(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.
    
    TestComponentWithBuilderAbstractClass component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isWithin(0).of(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test public void testInterfaceGenericBuilder() {
    TestComponentWithGenericBuilderInterface.Builder builder =
        DaggerTestComponentWithGenericBuilderInterface.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.setM2(new IntModuleIncludingDoubleAndFloat(1))
        .setM1(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.
    
    TestComponentWithGenericBuilderInterface component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isWithin(0).of(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }

  @Test public void testAbstractClassGenericBuilder() {
    TestComponentWithGenericBuilderAbstractClass.Builder builder =
        DaggerTestComponentWithGenericBuilderAbstractClass.builder();

    // Make sure things fail if we don't set our required modules.
    try {
      builder.build();
      fail();
    } catch(IllegalStateException expected) {}
    
    builder.setM2(new IntModuleIncludingDoubleAndFloat(1))
        .setM1(new StringModule("sam"))
        .depComponent(new DepComponent() {});
    builder.doubleModule(new DoubleModule());
    // Don't set other modules -- make sure it works.
    
    TestComponentWithGenericBuilderAbstractClass component = builder.build();
    assertThat(component.s()).isEqualTo("sam");
    assertThat(component.i()).isEqualTo(1);
    assertThat(component.d()).isWithin(0).of(4.2d);
    assertThat(component.f()).isEqualTo(5.5f);
    assertThat(component.l()).isEqualTo(6L);
  }
  
}
