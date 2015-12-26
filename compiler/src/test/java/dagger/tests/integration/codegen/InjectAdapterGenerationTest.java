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

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public final class InjectAdapterGenerationTest {
  @Test public void basicInjectAdapter() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Basic", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Inject;",
        "class Basic {",
        "  static class A { @Inject A() { } }",
        "  static class Foo$Bar {",
        "    @Inject Foo$Bar() { }",
        "    static class Baz { @Inject Baz() { } }",
        "  }",
        "  @Module(injects = { A.class, Foo$Bar.class, Foo$Bar.Baz.class })",
        "  static class AModule { }",
        "}"));

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Basic$AModule$$ModuleAdapter", Joiner.on("\n").join(
            "import dagger.internal.ModuleAdapter;",
            "import java.lang.Class;",
            "import java.lang.Override;",
            "import java.lang.String;",
            "public final class Basic$AModule$$ModuleAdapter",
            "    extends ModuleAdapter<Basic.AModule> {",
            "  private static final String[] INJECTS = {",
            "      \"members/Basic$A\", \"members/Basic$Foo$Bar\", \"members/Basic$Foo$Bar$Baz\"};",
            "  private static final Class<?>[] INCLUDES = {};",
            "  public Basic$AModule$$ModuleAdapter() {",
            "    super(Basic.AModule.class, INJECTS, false, INCLUDES,",
            "      true, false);",
            "  }",
            "  @Override public Basic.AModule newModule() {",
            "    return new Basic.AModule();",
            "  }",
            "}"));

    JavaFileObject expectedInjectAdapterA =
        JavaFileObjects.forSourceString("Basic$A$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.internal.Binding;",
            "import java.lang.Override;",
            "public final class Basic$A$$InjectAdapter",
            "    extends Binding<Basic.A> {",
            "  public Basic$A$$InjectAdapter() {",
            "    super(\"Basic$A\", \"members/Basic$A\", NOT_SINGLETON, Basic.A.class);",
            "  }",
            "  @Override public Basic.A get() {",
            "    Basic.A result = new Basic.A();",
            "    return result;",
            "  }",
            "}"));

    JavaFileObject expectedInjectAdapterFooBar =
        JavaFileObjects.forSourceString("Basic$Foo$Bar$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.internal.Binding;",
            "import java.lang.Override;",
            "public final class Basic$Foo$Bar$$InjectAdapter",
            "    extends Binding<Basic.Foo$Bar> {",
            "  public Basic$Foo$Bar$$InjectAdapter() {",
            "    super(\"Basic$Foo$Bar\", \"members/Basic$Foo$Bar\",",
            "        NOT_SINGLETON, Basic.Foo$Bar.class);",
            "  }",
            "  @Override public Basic.Foo$Bar get() {",
            "    Basic.Foo$Bar result = new Basic.Foo$Bar();",
            "    return result;",
            "  }",
            "}"));

    JavaFileObject expectedInjectAdapterFooBarBaz =
        JavaFileObjects.forSourceString("Basic$Foo$Bar$Baz$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.internal.Binding;",
            "import java.lang.Override;",
            "public final class Basic$Foo$Bar$Baz$$InjectAdapter",
            "    extends Binding<Basic.Foo$Bar.Baz> {",
            "  public Basic$Foo$Bar$Baz$$InjectAdapter() {",
            "    super(\"Basic$Foo$Bar$Baz\", \"members/Basic$Foo$Bar$Baz\",",
            "        NOT_SINGLETON, Basic.Foo$Bar.Baz.class);",
            "  }",
            "  @Override public Basic.Foo$Bar.Baz get() {",
            "    Basic.Foo$Bar.Baz result = new Basic.Foo$Bar.Baz();",
            "    return result;",
            "  }",
            "}"));

    ASSERT.about(javaSource()).that(sourceFile).processedWith(daggerProcessors())
        .compilesWithoutError().and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapterA,
            expectedInjectAdapterFooBar, expectedInjectAdapterFooBarBaz);

  }

  @Test public void injectStaticFails() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Basic", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Inject;",
        "class Basic {",
        "  static class A { @Inject A() { } }",
        "  static class B { @Inject static A a; }",
        "  @Module(injects = B.class)",
        "  static class AModule { }",
        "}"));

    ASSERT.about(javaSource()).that(sourceFile)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("@Inject not supported on static field Basic.B")
        .in(sourceFile).onLine(5);
  }
}
