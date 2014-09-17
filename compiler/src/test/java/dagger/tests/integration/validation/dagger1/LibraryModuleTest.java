/**
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
package dagger.tests.integration.validation.dagger1;

import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.dagger1.ProcessorTestUtils.daggerProcessors;

@RunWith(JUnit4.class)
public final class LibraryModuleTest {
  @Test public void unusedProviderMethodsPassOnLibrary() {
    JavaFileObject source = JavaFileObjects.forSourceString("Library", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.lang.Override;",
        "@Module(library = true)",
        "class TestModule {",
        "  @Provides String string() {",
        "    return \"string\";",
        "  }",
        "}"));
    assert_().about(javaSource())
        .that(source).processedWith(daggerProcessors()).compilesWithoutError();
  }

  @Test public void unusedProviderMethodsFailOnNonLibrary() {
    JavaFileObject source = JavaFileObjects.forSourceString("Library", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.lang.Override;",
        "@Module(library = false)",
        "class TestModule {",
        "  @Provides String string() {",
        "    return \"string\";",
        "  }",
        "}"));
    assert_().about(javaSource()).that(source).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining("Graph validation failed:").in(source).onLine(5).and()
        .withErrorContaining("You have these unused @Provider methods:").in(source).onLine(5).and()
        .withErrorContaining("1. TestModule.string()").in(source).onLine(5).and()
        .withErrorContaining("Set library=true in your module").in(source).onLine(5);
  }

  @Test public void injectsOfInterfaceMakesProvidesBindingNotAnOrphan() {
    JavaFileObject foo = JavaFileObjects.forSourceString("Foo", "interface Foo {}");
    JavaFileObject module = JavaFileObjects.forSourceString("TestModule", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Singleton;",
        "@Module(injects = Foo.class, library = false)",
        "class TestModule {",
        "  @Singleton @Provides Foo provideFoo() {",
        "    return new Foo() {};",
        "  }",
        "}"));
    assert_().about(javaSources()).that(Arrays.asList(foo, module))
        .processedWith(daggerProcessors())
        .compilesWithoutError();
  }

  @Test public void injectsOfClassMakesProvidesBindingNotAnOrphan() {
    JavaFileObject foo = JavaFileObjects.forSourceString("Foo", "class Foo {}");
    JavaFileObject module = JavaFileObjects.forSourceString("TestModule", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Singleton;",
        "@Module(injects = Foo.class, library = false)",
        "class TestModule {",
        "  @Singleton @Provides Foo provideFoo() {",
        "    return new Foo() {};",
        "  }",
        "}"));
    assert_().about(javaSources()).that(Arrays.asList(foo, module))
        .processedWith(daggerProcessors())
        .compilesWithoutError();
  }

}
