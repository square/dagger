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
package test.subcomponent;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.collect.Sets.intersection;
import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class SubcomponentScopeTest {
  @Test
  public void singletonPropagatesUpward() {
    ParentComponent parentComponent = Dagger_ParentComponent.create();
    SingletonType singletonType = parentComponent.getSingletonType();
    assertThat(parentComponent.newChildComponent().requiresSingleton().singletonType())
        .isSameAs(singletonType);
    assertThat(parentComponent.newChildComponent()
        .newGrandchildComponent()
        .requiresSingleton().singletonType())
            .isSameAs(singletonType);
  }

  @Test
  public void multibindingContributions() {
    ParentComponent parentComponent = Dagger_ParentComponent.create();
    Set<Object> parentObjectSet = parentComponent.objectSet();
    assertThat(parentObjectSet).hasSize(2);
    Set<Object> childObjectSet = parentComponent.newChildComponent().objectSet();
    assertThat(childObjectSet).hasSize(3);
    Set<Object> grandchildObjectSet =
        parentComponent.newChildComponent().newGrandchildComponent().objectSet();
    assertThat(grandchildObjectSet).hasSize(4);
    assertThat(intersection(parentObjectSet, childObjectSet)).hasSize(1);
    assertThat(intersection(parentObjectSet, grandchildObjectSet)).hasSize(1);
    assertThat(intersection(childObjectSet, grandchildObjectSet)).hasSize(1);
  }

  @Test
  public void unscopedProviders() {
    ParentComponent parentComponent = Dagger_ParentComponent.create();
    assertThat(parentComponent.getUnscopedTypeProvider())
        .isSameAs(parentComponent.newChildComponent().getUnscopedTypeProvider());
    assertThat(parentComponent.getUnscopedTypeProvider())
        .isSameAs(parentComponent.newChildComponent()
            .newGrandchildComponent()
            .getUnscopedTypeProvider());
  }
}
