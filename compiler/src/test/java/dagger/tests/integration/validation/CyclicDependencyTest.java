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
public class CyclicDependencyTest {

  @Test public void cyclicDepsWithInjectables() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("CyclicDeps", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Inject;\n"
        + "class CyclicDeps {\n"
        + "  static class Foo {\n"
        + "    @Inject Foo(Bar b) { }\n"
        + "  }\n"
        + "  static class Bar {\n"
        + "    @Inject Bar(Blah b) { }\n"
        + "  }\n"
        + "  static class Blah {\n"
        + "    @Inject Blah(Foo f) { }\n"
        + "  }\n"
        + "  static class EntryPoint {\n"
        + "    @Inject Foo f;\n"
        + "  }\n"
        + "  @Module(injects = EntryPoint.class)\n"
        + "  static class TestModule { }\n"
        + "}\n"
    );

    assertAbout(javaSource()).that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining("Dependency cycle:").in(sourceFile).onLine(17);
  }

  @Test public void cyclicDepsWithProvidesMethods() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("CyclicDeps", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "class CyclicDeps {\n"
        + "  static class A { }\n"
        + "  static class B { }\n"
        + "  static class C { }\n"
        + "  static class D { }\n"
        + "  @Module(injects = D.class)\n"
        + "  static class CyclicModule {\n"
        + "    @Provides A a(D d) { return null; }\n"
        + "    @Provides B b(A a) { return null; }\n"
        + "    @Provides C c(B b) { return null; }\n"
        + "    @Provides D d(C c) { return null; }\n"
        + "  }\n"
        + "}\n"
    );

    assertAbout(javaSource()).that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining("Dependency cycle:").in(sourceFile).onLine(9);
  }

}
