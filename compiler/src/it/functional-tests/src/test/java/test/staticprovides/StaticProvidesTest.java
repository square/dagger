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
package test.staticprovides;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StaticProvidesTest {
  private final StaticTestComponent component = DaggerStaticTestComponent.create();

  @Test public void setMultibinding() {
    assertThat(component.getMultiboundStrings()).isEqualTo(ImmutableSet.of(
        AllStaticModule.class + ".contributeString",
        SomeStaticModule.class + ".contributeStringFromAStaticMethod",
        SomeStaticModule.class + ".contributeStringFromAnInstanceMethod"));
  }
}
