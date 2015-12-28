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
package dagger.tests.integration.validation;

import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;

@RunWith(JUnit4.class)
public final class LibraryModuleTest {
  @Test public void unusedProviderMethodsPassOnLibrary() {
    JavaFileObject source = JavaFileObjects.forSourceString("Library", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import java.lang.Override;\n"
        + "@Module(library = true)\n"
        + "class TestModule {\n"
        + "  @Provides String string() {\n"
        + "    return \"string\";\n"
        + "  }\n"
        + "}\n"
    );
    assertAbout(javaSource())
        .that(source)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
  }

  @Test public void unusedProviderMethodsFailOnNonLibrary() {
    JavaFileObject source = JavaFileObjects.forSourceString("Library", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import java.lang.Override;\n"
        + "@Module(library = false)\n"
        + "class TestModule {\n"
        + "  @Provides String string() {\n"
        + "    return \"string\";\n"
        + "  }\n"
        + "}\n"
    );
    assertAbout(javaSource())
        .that(source)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("Graph validation failed:").in(source).onLine(5).and()
        .withErrorContaining("You have these unused @Provider methods:").in(source).onLine(5).and()
        .withErrorContaining("1. TestModule.string()").in(source).onLine(5).and()
        .withErrorContaining("Set library=true in your module").in(source).onLine(5);
  }

  @Test public void injectsOfInterfaceMakesProvidesBindingNotAnOrphan() {
    JavaFileObject foo = JavaFileObjects.forSourceString("Foo", "interface Foo {}");
    JavaFileObject module = JavaFileObjects.forSourceString("TestModule", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Singleton;\n"
        + "@Module(injects = Foo.class, library = false)\n"
        + "class TestModule {\n"
        + "  @Singleton @Provides Foo provideFoo() {\n"
        + "    return new Foo() {};\n"
        + "  }\n"
        + "}\n"
    );
    assertAbout(javaSources())
        .that(Arrays.asList(foo, module))
        .processedWith(daggerProcessors())
        .compilesWithoutError();
  }

  @Test public void injectsOfClassMakesProvidesBindingNotAnOrphan() {
    JavaFileObject foo = JavaFileObjects.forSourceString("Foo", "class Foo {}");
    JavaFileObject module = JavaFileObjects.forSourceString("TestModule", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Singleton;\n"
        + "@Module(injects = Foo.class, library = false)\n"
        + "class TestModule {\n"
        + "  @Singleton @Provides Foo provideFoo() {\n"
        + "    return new Foo() {};\n"
        + "  }\n"
        + "}\n"
    );
    assertAbout(javaSources())
        .that(Arrays.asList(foo, module))
        .processedWith(daggerProcessors())
        .compilesWithoutError();
  }

}
