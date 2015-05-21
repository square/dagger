/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package test;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class MultibindingTest {
  private MultibindingComponent multibindingComponent;

  @Before public void setUp() {
    multibindingComponent = DaggerMultibindingComponent.builder()
        .multibindingDependency(new MultibindingDependency() {
          @Override public double doubleDependency() {
            return 0.0;
          }
        })
        .build();
  }

  @Test public void map() {
    Map<String, String> map = multibindingComponent.map();
    assertThat(map).hasSize(2);
    assertThat(map).containsEntry("foo", "foo value");
    assertThat(map).containsEntry("bar", "bar value");
  }

  @Test public void mapOfProviders() {
    Map<String, Provider<String>> mapOfProviders = multibindingComponent.mapOfProviders();
    assertThat(mapOfProviders).hasSize(2);
    assertThat(mapOfProviders.get("foo").get()).isEqualTo("foo value");
    assertThat(mapOfProviders.get("bar").get()).isEqualTo("bar value");
  }

  @Test public void mapKeysAndValues() {
    assertThat(multibindingComponent.mapKeys()).containsExactly("foo", "bar");
    assertThat(multibindingComponent.mapValues()).containsExactly("foo value", "bar value");
  }

  @Test public void nestedKeyMap() {
    assertThat(multibindingComponent.nestedKeyMap()).isEqualTo(
        ImmutableMap.of(
            nestedWrappedKey(Integer.class), "integer",
            nestedWrappedKey(Long.class), "long"));
  }

  @Test public void setBindings() {
    assertThat(multibindingComponent.set()).containsExactly(-90, -17, -1, 5, 6, 832, 1742);
  }

  @AutoAnnotation
  static TestKey.NestedWrappedKey nestedWrappedKey(Class<?> value) {
    return new AutoAnnotation_MultibindingTest_nestedWrappedKey(value);
  }
}
