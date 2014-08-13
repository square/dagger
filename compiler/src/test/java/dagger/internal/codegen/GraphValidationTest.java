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

import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

@RunWith(JUnit4.class)
public class GraphValidationTest {
  @Test public void componentOnConcreteClass() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface MyComponent {",
        "  Foo getFoo();",
        "}");
    JavaFileObject injectable = JavaFileObjects.forSourceLines("test.Foo",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Bar bar) {}",
        "}");
    JavaFileObject nonInjectable = JavaFileObjects.forSourceLines("test.Bar",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface Bar {}");
    assert_().about(javaSources()).that(Arrays.asList(component, injectable, nonInjectable))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("test.Bar cannot be provided without an @Inject constructor")
            .in(component).onLine(7);
  }

  @Test public void membersInjectWithoutProvision() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  static class A {",
        "    @Inject A() {}",
        "  }",
        "",
        "  static class B {",
        "    @Inject A a;",
        "  }",
        "",
        "  @Component()",
        "  interface AComponent {",
        "    B getB();",
        "  }",
        "}");
    String expectedError = "test.TestClass.B cannot be provided without an "
        + "@Inject constructor or from an @Provides-annotated method. "
        + "This type supports members injection but cannot be implicitly provided.";
    assert_().about(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(19);
  }

  @Test public void cyclicDependency() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class Outer {",
        "  static class A {",
        "    @Inject A(C cParam) {}",
        "  }",
        "",
        "  static class B {",
        "    @Inject B(A aParam) {}",
        "  }",
        "",
        "  static class C {",
        "    @Inject C(B bParam) {}",
        "  }",
        "",
        "  @Component()",
        "  interface CComponent {",
        "    C getC();",
        "  }",
        "}");

    String expectedError = "test.Outer.CComponent.getC() contains a dependency cycle:\n"
        + "      test.Outer.C.<init>(test.Outer.B bParam)\n"
        + "          [parameter: test.Outer.B bParam]\n"
        + "      test.Outer.B.<init>(test.Outer.A aParam)\n"
        + "          [parameter: test.Outer.A aParam]\n"
        + "      test.Outer.A.<init>(test.Outer.C cParam)\n"
        + "          [parameter: test.Outer.C cParam]";

    assert_().about(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(23);
  }

  @Test public void longChainOfDependencies() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  interface A {}",
        "",
        "  static class B {",
        "    @Inject B(A a) {}",
        "  }",
        "",
        "  static class C {",
        "    @Inject B b;",
        "    @Inject C(B b) {}",
        "  }",
        "",
        "  interface D { }",
        "",
        "  static class DImpl implements D {",
        "    @Inject DImpl(C c, B b) {}",
        "  }",
        "",
        "  @Module",
        "  static class DModule {",
        "    @Provides D d(DImpl impl) { return impl; }",
        "  }",
        "",
        "  @Component(modules = { DModule.class })",
        "  interface AComponent {",
        "    D getFoo();",
        "    C injectC(C c);",
        "  }",
        "}");
    String errorText = "test.TestClass.A cannot be provided without "
        + "an @Inject constructor or from an @Provides-annotated method.\n";
    String firstError = errorText
        + "      test.TestClass.DModule.d(test.TestClass.DImpl impl)\n"
        + "          [parameter: test.TestClass.DImpl impl]\n"
        + "      test.TestClass.DImpl.<init>(test.TestClass.C c, test.TestClass.B b)\n"
        + "          [parameter: test.TestClass.C c]\n"
        + "      test.TestClass.C.<init>(test.TestClass.B b)\n"
        + "          [parameter: test.TestClass.B b]\n"
        + "      test.TestClass.B.<init>(test.TestClass.A a)\n"
        + "          [parameter: test.TestClass.A a]";
    String secondError = errorText
        + "      test.TestClass.C.b()\n"
        + "          [injected field of type: test.TestClass.B b]\n"
        + "      test.TestClass.B.<init>(test.TestClass.A a)\n"
        + "          [parameter: test.TestClass.A a]";
    assert_().about(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(firstError).in(component).onLine(33)
        .and().withErrorContaining(secondError).in(component).onLine(34);
  }
}