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
public class SimpleMissingDependencyTest {

  @Test public void missingDependency() {
    JavaFileObject file = JavaFileObjects.forSourceString("MissingDep", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Inject;\n"
        + "class MissingDep {\n"
        + "  @Inject Dependency dep;\n"
        + "  static interface Dependency {\n"
        + "    void doit();\n"
        + "  }\n"
        + "  @Module(injects = MissingDep.class)\n"
        + "  static class DaModule {\n"
        + "    /* missing */ // @Provides Dependency a() { return new Dependency(); }\n"
        + "  }\n"
        + "}\n"
    );

    assertAbout(javaSource())
        .that(file)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("MissingDep$Dependency could not be bound")
        .in(file).onLine(9).and()
        .withErrorContaining("required by MissingDep for MissingDep.DaModule")
        .in(file).onLine(9);
  }
}
