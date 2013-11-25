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
        "  @Module(injects = A.class)",
        "  static class AModule { }",
        "}"));

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Basic$AModule$$ModuleAdapter", Joiner.on("\n").join(
            "import dagger.internal.ModuleAdapter;",
            "public final class Basic$AModule$$ModuleAdapter",
            "    extends ModuleAdapter<Basic.AModule> {",
            "  private static final String[] INJECTS = {\"Basic$A\"};",
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

    ASSERT.about(javaSource()).that(sourceFile).processedWith(daggerProcessors())
        .compilesWithoutError().and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapter);

  }

  /**
   * Shows current behavior for a {@link dagger.Provides provides method}
   * used to supply an injected ctor parameter.
   *
   * <ul>
   *   <li>{@code ProvidesAdapter} invokes the module's provides method on
   *   {@code get}</li>
   *   <li>On {@code getBindings}, the above is newed up and linked to its type
   *   key.
   *   <li>{@code InjectAdapter} contains a field for the parameter binding,
   *   referenced in {@code getDependencies} and set on {@code attach}</li>
   *   <li>On {@code get}, the injected constructor is called with the value of
   *   {@link dagger.internal.Binding#get}</li>
   * </ul>
   */
  @Test public void providerForCtorInjection() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Field", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "class Field {",
        "  static class A { final String name; @Inject A(String name) { this.name = name; }}",
        "  @Module(injects = { A.class, String.class })",
        "  static class AModule { @Provides String name() { return \"foo\"; }}",
        "}"));

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Field$AModule$$ModuleAdapter", Joiner.on("\n").join(
        "import dagger.internal.Binding;",
        "import dagger.internal.ModuleAdapter;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "public final class Field$AModule$$ModuleAdapter",
        "    extends ModuleAdapter<Field.AModule> {",
        "  private static final String[] INJECTS = ",
        "      {\"Field$A\", \"java.lang.String\"};",
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

    JavaFileObject expectedInjectAdapter =
        JavaFileObjects.forSourceString("Field$A$$InjectAdapter", Joiner.on("\n").join(
            "import dagger.internal.Binding;",
            "import dagger.internal.Linker;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "public final class Field$A$$InjectAdapter",
            "    extends Binding<Field.A> implements Provider<Field.A> {",
            "  private Binding<String> name;", // for ctor
            "  public Field$A$$InjectAdapter() {",
            "    super(\"Field$A\", \"members/Field$A\", NOT_SINGLETON, Field.A.class);",
            "  }",
            "  @Override @SuppressWarnings(\"unchecked\")",
            "  public void attach(Linker linker) {",
            "    name = (Binding<String>)linker.requestBinding(", // binding key is not a class
            "      \"java.lang.String\", Field.A.class, getClass().getClassLoader());",
            "  }",
            "  @Override public void getDependencies(",
            "      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {",
            "    getBindings.add(name);", // name is added to dependencies
            "  }",
            "  @Override public Field.A get() {",
            "    Field.A result = new Field.A(name.get());", // adds ctor param
            "    return result;",
            "  }",
            "}"));

    ASSERT.about(javaSource()).that(sourceFile).processedWith(daggerProcessors())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapter);

  }
}
