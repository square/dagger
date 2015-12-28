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
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static java.util.Arrays.asList;

/**
 * Integration tests for the validation processors related to the use
 * of Scoping Annotations.
 */
// TODO(cgruber): Audit this class when http://github.com/google/compile-testing
//                has error/warning counts and other warning predicates available.
@RunWith(JUnit4.class)
public class ScopeAnnotationUseTest {
  private static final String ABSTRACTION_SCOPING_TEXT =
      "Scoping annotations are only allowed on concrete types and @Provides methods:";

  // TODO(cgruber): uncomment when http://github.com/google/compile-testing supports warnings.
  //private static final String MISUSED_SCOPE_TEXT =
  //    "Dagger will ignore scoping annotations on methods that are not @Provides methods:";

  @Test public void compileSucceedsScopeOnConcreteType() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import javax.inject.Inject;\n"
        + "import javax.inject.Singleton;\n"
        + "@Singleton\n"
        + "class Test {\n"
        + "  @Inject public Test() { }\n"
        + "}\n"
    );

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileSucceedsScopeOnProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Singleton;\n"
        + "@Module(library = true, injects = String.class)\n"
        + "class Test {\n"
        + "  @Provides @Singleton public String provideString() { return \"\"; }\n"
        + "}\n"
    );

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileSucceedsWithScopedSuppressedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @SuppressWarnings(\"scoping\")\n"
        + "  @Singleton void method() { }\n"
        + "}\n"
    );

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileSucceedsWithScopedMultiplySuppressedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @SuppressWarnings({\"blah\", \"scoping\", \"foo\"})\n"
        + "  @Singleton void method() { }\n"
        + "}\n"
    );

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileWarnsWithScopedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @Singleton void method() { }\n"
        + "}\n"
    );

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing supports warnings.
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
        //.withWarningContaining(MISUSED_SCOPE_TEXT).in(sourceFile).onLine(3).atColumn(49).and()
        //.withWarningContaining("Test.method()").in(sourceFile).onLine(3).atColumn(49);
  }

  @Test public void compileWarnsWithScopedIncorrectlySuppressedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @SuppressWarnings(\"some string other than 'scoping'\")\n"
        + "  @Singleton void method() { }\n"
        + "}\n"
    );

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing supports warnings.
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError();
        //.withWarningContaining(MISUSED_SCOPE_TEXT).in(sourceFile).onLine(4).atColumn(49).and()
        //.withWarningContaining("Test.method()").in(sourceFile).onLine(4).atColumn(49);
  }

  @Test public void compileFailsWithScopeOnInterface() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @Module(injects = TestType.class) class TestModule { }\n"
        + "  @Singleton interface TestType { }\n"
        + "}\n"
    );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile)
        .onLine(5).atColumn(14).and()
        .withErrorContaining("Test.TestType")
        .in(sourceFile).onLine(5).atColumn(14);
  }

  @Test public void compileFailsWithScopeOnAbstractClass() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @Module(injects = TestType.class) class TestModule { }\n"
        + "  @Singleton abstract class TestType { }\n"
        + "}\n"
    );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT)
        .in(sourceFile).onLine(5).atColumn(23).and()
        .withErrorContaining("Test.TestType")
        .in(sourceFile).onLine(5).atColumn(23);
  }

  @Test public void compileFailsWithScopeOnField() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Inject;\n"
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @Singleton String field;\n"
        + "  @Inject public Test() { }\n"
        + "  @Module(injects = Test.class) class TestModule { }\n"
        + "}\n"
    );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT)
        .in(sourceFile).onLine(5).atColumn(21).and()
        .withErrorContaining("Test.field")
        .in(sourceFile).onLine(5).atColumn(21);
  }

  @Test public void compileFailsWithScopeOnMethodParameter() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Singleton;\n"
        + "@Module(library = true, injects = String.class)\n"
        + "class Test {\n"
        + "  @Provides int provideInteger() { return 0; }\n"
        + "  @Provides String provideString(@Singleton int intParam) { return \"\"; }\n"
        + "}\n"
    );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT)
        .in(sourceFile).onLine(7).atColumn(49).and()
        .withErrorContaining("intParam")
        .in(sourceFile).onLine(7).atColumn(49);
  }

  @Test public void compileFailsWithMultipleScopeAnnotations() {
    JavaFileObject annotation = JavaFileObjects.forSourceString("MyScope", ""
        + "import java.lang.annotation.Retention;\n"
        + "import javax.inject.Scope;\n"
        + "import static java.lang.annotation.RetentionPolicy.RUNTIME;\n"
        + "@Scope @Retention(RUNTIME)\n"
        + "public @interface MyScope { }\n"
    );

    JavaFileObject module = JavaFileObjects.forSourceString("MyModule", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Singleton;\n"
        + "@Module(library = true, injects = Injectable.class)\n"
        + "class MyModule {\n"
        + "  @Provides @Singleton @MyScope String method() { return \"\"; }\n"
        + "}\n"
    );

    JavaFileObject injectable = JavaFileObjects.forSourceString("Test", ""
        + "import javax.inject.Inject;\n"
        + "import javax.inject.Singleton;\n"
        + "@Singleton @MyScope\n"
        + "class Injectable {\n"
        + "  @Inject String string;\n"
        + "}\n"
    );

    String error = "Only one scoping annotation is allowed per element: ";

    assertAbout(javaSources())
        .that(asList(annotation, module, injectable))
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining(error + "MyModule.method()")
        .in(module).onLine(6).atColumn(40).and()
        .withErrorContaining(error + "Injectable")
        .in(injectable).onLine(4).atColumn(1);
  }

  @Test public void compileFailsWithScopeOnConstructor() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Inject;\n"
        + "import javax.inject.Singleton;\n"
        + "class Test {\n"
        + "  @Singleton @Inject public Test() { }\n"
        + "  @Module(injects = Test.class) class TestModule { }\n"
        + "}\n"
    );

   String singletonErrorText = ""
        + "Singleton annotations have no effect on constructors. "
        + "Did you mean to annotate the class?";

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT)
        .in(sourceFile).onLine(5).atColumn(29).and()
        .withErrorContaining("Test.Test()")
        .in(sourceFile).onLine(5).atColumn(29).and()
        .withErrorContaining(singletonErrorText)
        .in(sourceFile).onLine(6).atColumn(33);
  }
}

