/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2016 Square Inc.
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
public final class GenericInjectAdapterGenerationTest {

  @Test public void basicInjectAdapter() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Basic", ""
        + "import dagger.Module;\n"
        + "import javax.inject.Inject;\n"
        + "class Basic {\n"
        + "  static class Simple {\n"
        + "    @Inject Simple() { }\n"
        + "  }\n"
        + "  static class A<T> { }\n"
        + "  static class B<T extends CharSequence> extends A<T> {\n"
        + "    @Inject Simple simple;\n"
        + "  }\n"
        + "  static class C extends B<String> { \n"
        + "    @Inject C() { }\n"
        + "  }\n"
        + "  @Module(injects = { C.class })\n"
        + "  static class AModule { }\n"
        + "}\n"
    );

    JavaFileObject expectedInjectAdapterC =
        JavaFileObjects.forSourceString("Basic$B$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import dagger.internal.Linker;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.SuppressWarnings;\n"
            + "import java.util.Set;\n"
            + "public final class Basic$B$$InjectAdapter extends Binding<Basic.B> {\n"
            + "  private Binding<Basic.Simple> simple;\n"
            + "  private Binding<Basic.A> supertype;\n"
            + "  public Basic$B$$InjectAdapter() {\n"
            + "    super(\"Basic$B<T>\", \"members/Basic$B\", NOT_SINGLETON, Basic.B.class);\n"
            + "  }\n"
            + "  @Override\n"
            + "  @SuppressWarnings(\"unchecked\")\n"
            + "  public void attach(Linker linker) {\n"
            + "    simple = (Binding<Basic.Simple>) linker.requestBinding(\"Basic$Simple\", Basic.B.class, getClass().getClassLoader());\n"
            + "    supertype = (Binding<Basic.A>) linker.requestBinding(\"members/Basic$A\", Basic.B.class, getClass().getClassLoader(), false, true);\n"
            + "  }\n"
            + "  @Override\n"
            + "  public void getDependencies(Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {\n"
            + "    injectMembersBindings.add(simple);\n"
            + "    injectMembersBindings.add(supertype);\n"
            + "  }\n"
            + "  @Override\n"
            + "  public Basic.B get() {\n"
            + "    Basic.B result = new Basic.B();\n"
            + "    injectMembers(result);\n"
            + "    return result;\n"
            + "  }\n"
            + "  @Override\n"
            + "  public void injectMembers(Basic.B object) {\n"
            + "    object.simple = simple.get();\n"
            + "    supertype.injectMembers(object);\n"
            + "  }\n"
            + "}"
        );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedInjectAdapterC);
  }
}
