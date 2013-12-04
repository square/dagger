/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2013 Square Inc.
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
package dagger.tests.integration.codegen;

import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static java.util.Arrays.asList;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public final class ModuleAdapterGenerationTest {

  @Test public void providesHasParameterNamedModule() {
    JavaFileObject a = JavaFileObjects.forSourceString("A", Joiner.on("\n").join(
        "import javax.inject.Inject;",
        "class A { @Inject A(){ }}"));
    JavaFileObject b = JavaFileObjects.forSourceString("B", Joiner.on("\n").join(
        "import javax.inject.Inject;",
        "class B { @Inject B(){ }}"));

    JavaFileObject module = JavaFileObjects.forSourceString("BModule", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "@Module(injects = B.class)",
        "class BModule { @Provides B b(A module) { return new B(); }}"));

    ASSERT.about(javaSources()).that(asList(a, b, module)).processedWith(daggerProcessors())
        .compilesWithoutError();
  }

}
