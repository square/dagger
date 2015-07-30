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
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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

    assertWithMessage("requiresSetOfObjects")
        .that(requiresMultibindingsInChild.requiresSetOfObjects().setOfObjects())
        .containsExactly("object provided by parent", "object provided by child");

    assertWithMessage("requiresSetOfStrings")
        .that(requiresMultibindingsInChild.requiresSetOfStrings().setOfStrings())
        .containsExactly("string provided by parent");
  }

}
