/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;

@RunWith(JUnit4.class)
public class ProducerModuleFactoryGeneratorTest {
  @Test public void multipleProducesMethodsWithSameName() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces Object produce(int i) {",
        "    return i;",
        "  }",
        "",
        "  @Produces String produce() {",
        "    return \"\";",
        "  }",
        "}");
    String errorMessage = String.format(BINDING_METHOD_WITH_SAME_NAME, "Produces");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage).in(moduleFile).onLine(8)
        .and().withErrorContaining(errorMessage).in(moduleFile).onLine(12);
  }

  @Test
  public void privateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "final class Enclosing {",
        "  @ProducerModule private static final class PrivateModule {",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be private.")
        .in(moduleFile).onLine(6);
  }

  @Test
  public void enclosedInPrivateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "final class Enclosing {",
        "  private static final class PrivateEnclosing {",
        "    @ProducerModule static final class TestModule {",
        "    }",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be enclosed in private types.")
        .in(moduleFile).onLine(7);
  }

  @Test
  public void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile = JavaFileObjects.forSourceLines("test.PublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule(includes = {",
        "    NonPublicModule1.class, OtherPublicModule.class, NonPublicModule2.class",
        "})",
        "public final class PublicModule {",
        "}");
    JavaFileObject nonPublicModule1File = JavaFileObjects.forSourceLines("test.NonPublicModule1",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
        "final class NonPublicModule1 {",
        "}");
    JavaFileObject nonPublicModule2File = JavaFileObjects.forSourceLines("test.NonPublicModule2",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
        "final class NonPublicModule2 {",
        "}");
    JavaFileObject otherPublicModuleFile = JavaFileObjects.forSourceLines("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
        "public final class OtherPublicModule {",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(
            publicModuleFile, nonPublicModule1File, nonPublicModule2File, otherPublicModuleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("This module is public, but it includes non-public "
            + "(or effectively non-public) modules. "
            + "Either reduce the visibility of this module or make "
            + "test.NonPublicModule1 and test.NonPublicModule2 public.")
        .in(publicModuleFile).onLine(8);
  }
}
