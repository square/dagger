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

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;

@RunWith(JUnit4.class)
public final class InjectAdapterGenerationTest {
  @Test public void basicInjectAdapter() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Basic", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Inject;\n"
        + "class Basic {\n"
        + "  static class A { @Inject A() { } }\n"
        + "  static class Foo$Bar {\n"
        + "    @Inject Foo$Bar() { }\n"
        + "    static class Baz { @Inject Baz() { } }\n"
        + "  }\n"
        + "  @Module(injects = { A.class, Foo$Bar.class, Foo$Bar.Baz.class })\n"
        + "  static class AModule { }\n"
        + "}\n"
    );

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Basic$AModule$$ModuleAdapter", ""
            + "import dagger.internal.ModuleAdapter;\n"
            + "import java.lang.Class;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.String;\n"
            + "public final class Basic$AModule$$ModuleAdapter\n"
            + "    extends ModuleAdapter<Basic.AModule> {\n"
            + "  private static final String[] INJECTS = {\n"
            + "      \"members/Basic$A\", \"members/Basic$Foo$Bar\", \"members/Basic$Foo$Bar$Baz\"};\n"
            + "  private static final Class<?>[] STATIC_INJECTIONS = {};\n"
            + "  private static final Class<?>[] INCLUDES = {};\n"
            + "  public Basic$AModule$$ModuleAdapter() {\n"
            + "    super(Basic.AModule.class, INJECTS, STATIC_INJECTIONS, false, INCLUDES,\n"
            + "      true, false);\n"
            + "  }\n"
            + "  @Override public Basic.AModule newModule() {\n"
            + "    return new Basic.AModule();\n"
            + "  }\n"
            +"}\n"
    );

    JavaFileObject expectedInjectAdapterA =
        JavaFileObjects.forSourceString("Basic$A$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import java.lang.Override;\n"
            + "public final class Basic$A$$InjectAdapter\n"
            + "    extends Binding<Basic.A> {\n"
            + "  public Basic$A$$InjectAdapter() {\n"
            + "    super(\"Basic$A\", \"members/Basic$A\", NOT_SINGLETON, Basic.A.class);\n"
            + "  }\n"
            + "  @Override public Basic.A get() {\n"
            + "    Basic.A result = new Basic.A();\n"
            + "    return result;\n"
            + "  }\n"
            + "}\n"
        );

    JavaFileObject expectedInjectAdapterFooBar =
        JavaFileObjects.forSourceString("Basic$Foo$Bar$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import java.lang.Override;\n"
            + "public final class Basic$Foo$Bar$$InjectAdapter\n"
            + "    extends Binding<Basic.Foo$Bar> {\n"
            + "  public Basic$Foo$Bar$$InjectAdapter() {\n"
            + "    super(\"Basic$Foo$Bar\", \"members/Basic$Foo$Bar\",\n"
            + "        NOT_SINGLETON, Basic.Foo$Bar.class);\n"
            + "  }\n"
            + "  @Override public Basic.Foo$Bar get() {\n"
            + "    Basic.Foo$Bar result = new Basic.Foo$Bar();\n"
            + "    return result;\n"
            + "  }\n"
            + "}\n"
        );

    JavaFileObject expectedInjectAdapterFooBarBaz =
        JavaFileObjects.forSourceString("Basic$Foo$Bar$Baz$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import java.lang.Override;\n"
            + "public final class Basic$Foo$Bar$Baz$$InjectAdapter\n"
            + "    extends Binding<Basic.Foo$Bar.Baz> {\n"
            + "  public Basic$Foo$Bar$Baz$$InjectAdapter() {\n"
            + "    super(\"Basic$Foo$Bar$Baz\", \"members/Basic$Foo$Bar$Baz\",\n"
            + "        NOT_SINGLETON, Basic.Foo$Bar.Baz.class);\n"
            + "  }\n"
            + "  @Override public Basic.Foo$Bar.Baz get() {\n"
            + "    Basic.Foo$Bar.Baz result = new Basic.Foo$Bar.Baz();\n"
            + "    return result;\n"
            + "  }\n"
            + "}\n"
        );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapterA,
            expectedInjectAdapterFooBar, expectedInjectAdapterFooBarBaz);

  }
}
