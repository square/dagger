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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertWithMessage;

@RunWith(Parameterized.class)
public class SubcomponentMultibindingsTest {

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return ImmutableList.of(
        new Object[] {DaggerParentComponentWithMultibindings.create()},
        new Object[] {DaggerParentComponentWithoutMultibindings.create()});
  }

  private ParentComponentWithoutMultibindings parent;

  public SubcomponentMultibindingsTest(ParentComponentWithoutMultibindings parentComponent) {
    this.parent = parentComponent;
  }

  @Test
  public void testMultibindingsInSubcomponents() {
    RequiresMultibindingsInChild requiresMultibindingsInChild =
        parent.childComponent().requiresMultibindingsInChild();

    assertWithMessage("requiresMultiboundObjects.setOfObjects")
        .that(requiresMultibindingsInChild.requiresMultiboundObjects().setOfObjects())
        .containsExactly("object provided by parent", "object provided by child");

    assertWithMessage("requiresMultiboundObjects.mapOfObjects")
        .that(requiresMultibindingsInChild.requiresMultiboundObjects().mapOfObjects())
        .isEqualTo(
            ImmutableMap.of("parent key", "object in parent", "child key", "object in child"));

    assertWithMessage("requiresMultiboundStrings")
        .that(requiresMultibindingsInChild.requiresMultiboundStrings().setOfStrings())
        .containsExactly("string provided by parent");

    assertWithMessage("requiresMultiboundStrings.mapOfStrings")
        .that(requiresMultibindingsInChild.requiresMultiboundStrings().mapOfStrings())
        .isEqualTo(ImmutableMap.of("parent key", "string in parent"));
  }

  @Test
  public void testOverriddenMultibindingsInSubcomponents() {
    RequiresMultibindingsInChild requiresMultibindingsInChild =
        parent.childComponent().requiresMultibindingsInChild();

    assertWithMessage("setOfRequiresMultiboundObjects")
        .that(requiresMultibindingsInChild.setOfRequiresMultiboundObjects())
        .hasSize(1);

    RequiresMultiboundObjects onlyElementInMultiboundRequiresMultiboundObjects =
        getOnlyElement(requiresMultibindingsInChild.setOfRequiresMultiboundObjects());

    assertWithMessage("setOfRequiresMultiboundObjects[only].setOfObjects")
        .that(onlyElementInMultiboundRequiresMultiboundObjects.setOfObjects())
        .containsExactly("object provided by parent", "object provided by child");

    assertWithMessage("setOfRequiresMultiboundObjects[only].mapOfObjects")
        .that(onlyElementInMultiboundRequiresMultiboundObjects.mapOfObjects())
        .isEqualTo(
            ImmutableMap.of("parent key", "object in parent", "child key", "object in child"));
  }
}
