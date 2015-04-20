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
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;

import static com.google.common.collect.Sets.intersection;
import static com.google.common.truth.Truth.assertThat;

@RunWith(Theories.class)
public class SubcomponentTest {
  private static final ParentComponent parentComponent = DaggerParentComponent.create();
  @DataPoint
  public static final ChildComponent childComponent = parentComponent.newChildComponent();;
  @DataPoint
  public static final ChildComponent childAbstractClassComponent =
      parentComponent.newChildAbstractClassComponent();

  @Theory
  public void scopePropagatesUpward_class(ChildComponent childComponent) {
    assertThat(childComponent.requiresSingleton().singletonType())
        .isSameAs(childComponent.requiresSingleton().singletonType());
    assertThat(childComponent.requiresSingleton().singletonType())
        .isSameAs(childComponent.newGrandchildComponent().requiresSingleton().singletonType());
  }

  @Theory
  public void scopePropagatesUpward_provides(ChildComponent childComponent) {
    assertThat(childComponent
        .requiresSingleton().unscopedTypeBoundAsSingleton())
            .isSameAs(childComponent
                .requiresSingleton().unscopedTypeBoundAsSingleton());
    assertThat(childComponent
        .requiresSingleton().unscopedTypeBoundAsSingleton())
            .isSameAs(childComponent.newGrandchildComponent()
                .requiresSingleton().unscopedTypeBoundAsSingleton());
  }

  @Theory
  public void multibindingContributions(ChildComponent childComponent) {
    Set<Object> parentObjectSet = parentComponent.objectSet();
    assertThat(parentObjectSet).hasSize(2);
    Set<Object> childObjectSet = childComponent.objectSet();
    assertThat(childObjectSet).hasSize(3);
    Set<Object> grandchildObjectSet =
        childComponent.newGrandchildComponent().objectSet();
    assertThat(grandchildObjectSet).hasSize(4);
    assertThat(intersection(parentObjectSet, childObjectSet)).hasSize(1);
    assertThat(intersection(parentObjectSet, grandchildObjectSet)).hasSize(1);
    assertThat(intersection(childObjectSet, grandchildObjectSet)).hasSize(1);
  }

  @Theory
  public void unscopedProviders(ChildComponent childComponent) {
    assertThat(parentComponent.getUnscopedTypeProvider())
        .isSameAs(childComponent.getUnscopedTypeProvider());
    assertThat(parentComponent.getUnscopedTypeProvider())
        .isSameAs(childComponent
            .newGrandchildComponent()
            .getUnscopedTypeProvider());
  }

  @Theory
  public void passedModules(ChildComponent childComponent) {
    ChildModuleWithState childModuleWithState = new ChildModuleWithState();
    ChildComponentRequiringModules childComponent1 =
        parentComponent.newChildComponentRequiringModules(
            new ChildModuleWithParameters(new Object()),
            childModuleWithState);
    ChildComponentRequiringModules childComponent2 =
        parentComponent.newChildComponentRequiringModules(
            new ChildModuleWithParameters(new Object()),
            childModuleWithState);
    assertThat(childComponent1.getInt()).isEqualTo(0);
    assertThat(childComponent2.getInt()).isEqualTo(1);
  }

  @Theory
  public void dependenceisInASubcomponent(ChildComponent childComponent) {
    assertThat(childComponent.newGrandchildComponent().needsAnInterface()).isNotNull();
  }
}
