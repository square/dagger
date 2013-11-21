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
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static java.util.Arrays.asList;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public final class ModuleAdapterGenerationTest {
  @Test public void injectsMembersInjectedAndProvidedAndConstructedTypes() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Field", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "class Field {",
        "  static class A { final String name; @Inject A(String name) { this.name = name; }}",
        "  static class B { @Inject String name; }",
        "  @Module(injects = { A.class, String.class, B.class })",
        "  static class AModule { @Provides String name() { return \"foo\"; }}",
        "}"));

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Field$AModule$$ModuleAdapter", Joiner.on("\n").join(
        "import dagger.internal.Binding;",
        "import dagger.internal.ModuleAdapter;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "public final class Field$AModule$$ModuleAdapter extends ModuleAdapter<Field.AModule> {",
        "  private static final String[] INJECTS = ",
        "      {\"Field$A\", \"Field$B\", \"java.lang.String\", \"members/Field$B\"};",
        "  private static final Class<?>[] STATIC_INJECTIONS = {};",
        "  private static final Class<?>[] INCLUDES = {};",
        "  public Field$AModule$$ModuleAdapter() {",
        "    super(Field.AModule.class, INJECTS, STATIC_INJECTIONS, false, INCLUDES, true, false);",
        "  }",
        "  @Override public Field.AModule newModule() {",
        "    return new Field.AModule();",
        "  }",
        "  @Override public void getBindings(Map<String, Binding<?>> map, Field.AModule module) {",
        "    map.put(\"java.lang.String\", new NameProvidesAdapter(module));", // eager new!
        "  }",
        "  public static final class NameProvidesAdapter", // corresponds to method name
        "      extends Binding<String> implements Provider<String> {",
        "    private final Field.AModule module;",
        "    public NameProvidesAdapter(Field.AModule module) {",
        "      super(\"java.lang.String\", null, NOT_SINGLETON, \"Field.AModule.name()\");",
        "      this.module = module;",
        "      setLibrary(false);",
        "    }",
        "    @Override public String get() {",
        "      return module.name();", // corresponds to @Provides method
        "    }",
        "  }",
        "}"));

    JavaFileObject expectedInjectAdapterA =
        JavaFileObjects.forSourceString("Field$A$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.internal.Binding;",
            "import dagger.internal.Linker;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "public final class Field$A$$InjectAdapter",
            "    extends Binding<Field.A> implements Provider<Field.A> {",
            "  private Binding<String> name;", // For Constructor.
            "  public Field$A$$InjectAdapter() {",
            "    super(\"Field$A\", \"members/Field$A\", NOT_SINGLETON, Field.A.class);",
            "  }",
            "  @Override @SuppressWarnings(\"unchecked\")",
            "  public void attach(Linker linker) {",
            "    name = (Binding<String>)linker.requestBinding(",
            "      \"java.lang.String\", Field.A.class, getClass().getClassLoader());",
            "  }",
            "  @Override public void getDependencies(",
            "      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {",
            "    getBindings.add(name);", // Name is added to dependencies.
            "  }",
            "  @Override public Field.A get() {",
            "    Field.A result = new Field.A(name.get());", // Adds constructor parameter.
            "    return result;",
            "  }",
            "}"));

    JavaFileObject expectedInjectAdapterB =
        JavaFileObjects.forSourceString("Field$B$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.MembersInjector;",
            "import dagger.internal.Binding;",
            "import dagger.internal.Linker;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "public final class Field$B$$InjectAdapter",
            "    extends Binding<Field.B> implements Provider<Field.B>, MembersInjector<Field.B> {",
            "  private Binding<String> name;", // For field.
            "  public Field$B$$InjectAdapter() {",
            "    super(\"Field$B\", \"members/Field$B\", NOT_SINGLETON, Field.B.class);",
            "  }",
            "  @Override @SuppressWarnings(\"unchecked\")",
            "  public void attach(Linker linker) {",
            "    name = (Binding<String>)linker.requestBinding(",
            "      \"java.lang.String\", Field.B.class, getClass().getClassLoader());",
            "  }",
            "  @Override public void getDependencies(",
            "      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {",
            "    injectMembersBindings.add(name);", // Name is added to dependencies.
            "  }",
            "  @Override public Field.B get() {",
            "    Field.B result = new Field.B();",
            "    injectMembers(result);",
            "    return result;",
            "  }",
            "  @Override public void injectMembers(Field.B object) {",
            "    object.name = name.get();", // Inject field.
            "  }",
            "}"));
    ASSERT.about(javaSource()).that(sourceFile).processedWith(daggerProcessors())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapterA, expectedInjectAdapterB);
  }

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
        "@Module(injects = B.class)",
        "class BModule { @Provides B b(A module) { return new B(); }}"));

    ASSERT.about(javaSources()).that(asList(a, b, module)).processedWith(daggerProcessors())
        .compilesWithoutError();
  }
}
