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
package test;

import java.util.Map;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class MultibindingTest {
  @Test public void testMultibindings() {
    MultibindingComponent multibindingComponent = DaggerMultibindingComponent.create();
    Map<String, String> map = multibindingComponent.map();
    assertThat(map).hasSize(2);
    assertThat(map).containsEntry("foo", "foo value");
    assertThat(map).containsEntry("bar", "bar value");
    Map<String, Provider<String>> mapOfProviders = multibindingComponent.mapOfProviders();
    assertThat(mapOfProviders).hasSize(2);
    assertThat(mapOfProviders.get("foo").get()).isEqualTo("foo value");
    assertThat(mapOfProviders.get("bar").get()).isEqualTo("bar value");
    assertThat(multibindingComponent.mapKeys()).containsExactly("foo", "bar");
    assertThat(multibindingComponent.mapValues()).containsExactly("foo value", "bar value");
    assertThat(multibindingComponent.set()).containsExactly(5, 6);
  }
}
