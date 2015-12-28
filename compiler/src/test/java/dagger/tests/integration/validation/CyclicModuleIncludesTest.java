/**
 * Copyright (c) 2013 Google, Inc.
 * Copyright (c) 2013 Square, Inc.
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
package dagger.tests.integration.validation;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;

@RunWith(JUnit4.class)
public class CyclicModuleIncludesTest {
  private final JavaFileObject javaFile =
      JavaFileObjects.forSourceString("CyclicModules", ""
          + "import dagger.Module;\n"
          + "class CyclicModules {\n"
          + "  @Module(includes = SelfReferencingModule.class)\n"
          + "  static class SelfReferencingModule { }\n"
          + "  @Module(includes = Spock.class)\n"
          + "  static class Rock {}\n"
          + "  @Module(includes = Rock.class)\n"
          + "  static class Paper {}\n"
          + "  @Module(includes = Paper.class)\n"
          + "  static class Scissors {}\n"
          + "  @Module(includes = Scissors.class)\n"
          + "  static class Lizard {}\n"
          + "  @Module(includes = Lizard.class)\n"
          + "  static class Spock {}\n"
          + "}"
      );

  @Test public void cyclicModuleSelfIncludes() {
    assertAbout(javaSource())
        .that(javaFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("CyclicModules.SelfReferencingModule includes itself directly")
        .in(javaFile).onLine(4);
  }

  @Test public void cyclicModuleIncludes_full_cycle() {
    assertAbout(javaSource())
        .that(javaFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("0. CyclicModules.Rock included by CyclicModules.Paper")
        .in(javaFile).onLine(6).and()
        .withErrorContaining("1. CyclicModules.Paper included by CyclicModules.Scissors")
        .in(javaFile).onLine(6).and()
        .withErrorContaining("2. CyclicModules.Scissors included by CyclicModules.Lizard")
        .in(javaFile).onLine(6).and()
        .withErrorContaining("3. CyclicModules.Lizard included by CyclicModules.Spock")
        .in(javaFile).onLine(6).and()
        .withErrorContaining("4. CyclicModules.Spock included by CyclicModules.Rock")
        .in(javaFile).onLine(6);
  }

  @Test public void cyclicModuleIncludes_initial_inclusion() {
    assertAbout(javaSource())
        .that(javaFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("0. CyclicModules.Rock included by CyclicModules.Paper")
            .in(javaFile).onLine(6).and()
        .withErrorContaining("0. CyclicModules.Paper included by CyclicModules.Scissors")
            .in(javaFile).onLine(8).and()
        .withErrorContaining("0. CyclicModules.Scissors included by CyclicModules.Lizard")
            .in(javaFile).onLine(10).and()
        .withErrorContaining("0. CyclicModules.Lizard included by CyclicModules.Spock")
            .in(javaFile).onLine(12).and()
        .withErrorContaining("0. CyclicModules.Spock included by CyclicModules.Rock")
            .in(javaFile).onLine(14);
  }


}
