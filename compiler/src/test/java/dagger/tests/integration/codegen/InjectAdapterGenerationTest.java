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

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;

@RunWith(JUnit4.class)
public final class InjectAdapterGenerationTest {
  @Test public void basicInjectAdapter() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Basic", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Inject;",
        "class Basic {",
        "  static class A { @Inject A() { } }",
        "  @Module(injects = A.class)",
        "  static class AModule { }",
        "}"));

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Basic$AModule$$ModuleAdapter", Joiner.on("\n").join(
            "import dagger.internal.ModuleAdapter;",
            "public final class Basic$AModule$$ModuleAdapter",
            "    extends ModuleAdapter<Basic.AModule> {",
            "  private static final String[] INJECTS = {\"members/Basic$A\"};",
            "  private static final Class<?>[] STATIC_INJECTIONS = {};",
            "  private static final Class<?>[] INCLUDES = {};",
            "  public Basic$AModule$$ModuleAdapter() {",
            "    super(Basic.AModule.class, INJECTS, STATIC_INJECTIONS, false, INCLUDES,",
            "      true, false);",
            "  }",
            "  @Override public Basic.AModule newModule() {",
            "    return new Basic.AModule();",
            "  }",
            "}"));

    JavaFileObject expectedInjectAdapter =
        JavaFileObjects.forSourceString("Basic$A$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.internal.Binding;",
            "import javax.inject.Provider;",
            "public final class Basic$A$$InjectAdapter",
            "    extends Binding<Basic.A> implements Provider<Basic.A> {",
            "  public Basic$A$$InjectAdapter() {",
            "    super(\"Basic$A\", \"members/Basic$A\", NOT_SINGLETON, Basic.A.class);",
            "  }",
            "  @Override public Basic.A get() {",
            "    Basic.A result = new Basic.A();",
            "    return result;",
            "  }",
            "}"));

    assert_().about(javaSource()).that(sourceFile).processedWith(daggerProcessors())
        .compilesWithoutError().and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapter);

  }
}
