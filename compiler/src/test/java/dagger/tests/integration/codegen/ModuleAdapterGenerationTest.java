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
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static java.util.Arrays.asList;

@RunWith(JUnit4.class)
public final class ModuleAdapterGenerationTest {
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
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Field", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Inject;\n"
        + "class Field {\n"
        + "  static class A { final String name; @Inject A(String name) { this.name = name; }}\n"
        + "  @Module(injects = { A.class, String.class })\n"
        + "  static class AModule { @Provides String name() { return \"foo\"; }}\n"
        + "}\n"
    );

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Field$AModule$$ModuleAdapter", ""
            + "import dagger.internal.BindingsGroup;\n"
            + "import dagger.internal.ModuleAdapter;\n"
            + "import dagger.internal.ProvidesBinding;\n"
            + "import java.lang.Class;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.String;\n"
            + "public final class Field$AModule$$ModuleAdapter\n"
            + "    extends ModuleAdapter<Field.AModule> {\n"
            + "  private static final String[] INJECTS = \n"
            + "      {\"members/Field$A\", \"members/java.lang.String\"};\n"
            + "  private static final Class<?>[] STATIC_INJECTIONS = {};\n"
            + "  private static final Class<?>[] INCLUDES = {};\n"
            + "  public Field$AModule$$ModuleAdapter() {\n"
            + "    super(Field.AModule.class, INJECTS, STATIC_INJECTIONS, false, INCLUDES, true, false);\n"
            + "  }\n"
            + "  @Override public Field.AModule newModule() {\n"
            + "    return new Field.AModule();\n"
            + "  }\n"
            + "  @Override public void getBindings(BindingsGroup bindings, Field.AModule module) {\n"
            + "    bindings.contributeProvidesBinding(\"java.lang.String\",\n"
            + "        new NameProvidesAdapter(module));\n" // eager new!
            + "  }\n"
            + "  public static final class NameProvidesAdapter\n" // corresponds to method name
            + "      extends ProvidesBinding<String> {\n"
            + "    private final Field.AModule module;\n"
            + "    public NameProvidesAdapter(Field.AModule module) {\n"
            + "      super(\"java.lang.String\", NOT_SINGLETON, \"Field.AModule\", \"name\");\n"
            + "      this.module = module;\n"
            + "      setLibrary(false);\n"
            + "    }\n"
            + "    @Override public String get() {\n"
            + "      return module.name();\n" // corresponds to @Provides method
            + "    }\n"
            + "  }\n"
            + "}\n"
        );

    JavaFileObject expectedInjectAdapter =
        JavaFileObjects.forSourceString("Field$A$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import dagger.internal.Linker;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.String;\n"
            + "import java.lang.SuppressWarnings;\n"
            + "import java.util.Set;\n"
            + "public final class Field$A$$InjectAdapter\n"
            + "    extends Binding<Field.A> {\n"
            + "  private Binding<String> name;\n" // for ctor
            + "  public Field$A$$InjectAdapter() {\n"
            + "    super(\"Field$A\", \"members/Field$A\", NOT_SINGLETON, Field.A.class);\n"
            + "  }\n"
            + "  @Override @SuppressWarnings(\"unchecked\")\n"
            + "  public void attach(Linker linker) {\n"
            + "    name = (Binding<String>)linker.requestBinding(\n" // binding key is not a class
            + "      \"java.lang.String\", Field.A.class, getClass().getClassLoader());\n"
            + "  }\n"
            + "  @Override public void getDependencies(\n"
            + "      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {\n"
            + "    getBindings.add(name);\n" // name is added to dependencies
            + "  }\n"
            + "  @Override public Field.A get() {\n"
            + "    Field.A result = new Field.A(name.get());\n" // adds ctor param
            + "    return result;\n"
            + "  }\n"
            + "}\n"
        );

    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapter);

  }

  @Test public void injectsMembersInjectedAndProvidedAndConstructedTypes() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Field", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Inject;\n"
        + "class Field {\n"
        + "  static class A { final String name; @Inject A(String name) { this.name = name; }}\n"
        + "  static class B { @Inject String name; }\n"
        + "  @Module(injects = { A.class, String.class, B.class })\n"
        + "  static class AModule { @Provides String name() { return \"foo\"; }}\n"
        + "}\n"
    );

    JavaFileObject expectedModuleAdapter =
        JavaFileObjects.forSourceString("Field$AModule$$ModuleAdapter", ""
            + "import dagger.internal.BindingsGroup;\n"
            + "import dagger.internal.ModuleAdapter;\n"
            + "import dagger.internal.ProvidesBinding;\n"
            + "import java.lang.Class;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.String;\n"
            + "public final class Field$AModule$$ModuleAdapter extends ModuleAdapter<Field.AModule> {\n"
            + "  private static final String[] INJECTS = \n"
            + "      {\"members/Field$A\", \"members/java.lang.String\", \"members/Field$B\"};\n"
            + "  private static final Class<?>[] STATIC_INJECTIONS = {};\n"
            + "  private static final Class<?>[] INCLUDES = {};\n"
            + "  public Field$AModule$$ModuleAdapter() {\n"
            + "    super(Field.AModule.class, INJECTS, STATIC_INJECTIONS, false, INCLUDES, true, false);\n"
            + "  }\n"
            + "  @Override public Field.AModule newModule() {\n"
            + "    return new Field.AModule();\n"
            + "  }\n"
            + "  @Override public void getBindings(BindingsGroup bindings, Field.AModule module) {\n"
            + "    bindings.contributeProvidesBinding(\"java.lang.String\",\n"
            + "        new NameProvidesAdapter(module));\n" // eager new!
            + "  }\n"
            + "  public static final class NameProvidesAdapter\n" // corresponds to method name
            + "      extends ProvidesBinding<String> {\n"
            + "    private final Field.AModule module;\n"
            + "    public NameProvidesAdapter(Field.AModule module) {\n"
            + "      super(\"java.lang.String\", NOT_SINGLETON, \"Field.AModule\", \"name\");\n"
            + "      this.module = module;\n"
            + "      setLibrary(false);\n"
            + "    }\n"
            + "    @Override public String get() {\n"
            + "      return module.name();\n" // corresponds to @Provides method
            + "    }\n"
            + "  }\n"
            + "}\n"
        );

    JavaFileObject expectedInjectAdapterA =
        JavaFileObjects.forSourceString("Field$A$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import dagger.internal.Linker;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.String;\n"
            + "import java.lang.SuppressWarnings;\n"
            + "import java.util.Set;\n"
            + "public final class Field$A$$InjectAdapter\n"
            + "    extends Binding<Field.A> {\n"
            + "  private Binding<String> name;\n" // For Constructor.
            + "  public Field$A$$InjectAdapter() {\n"
            + "    super(\"Field$A\", \"members/Field$A\", NOT_SINGLETON, Field.A.class);\n"
            + "  }\n"
            + "  @Override @SuppressWarnings(\"unchecked\")\n"
            + "  public void attach(Linker linker) {\n"
            + "    name = (Binding<String>)linker.requestBinding(\n"
            + "      \"java.lang.String\", Field.A.class, getClass().getClassLoader());\n"
            + "  }\n"
            + "  @Override public void getDependencies(\n"
            + "      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {\n"
            + "    getBindings.add(name);\n" // Name is added to dependencies.
            + "  }\n"
            + "  @Override public Field.A get() {\n"
            + "    Field.A result = new Field.A(name.get());\n" // Adds constructor parameter.
            + "    return result;\n"
            + "  }\n"
            + "}\n"
        );

    JavaFileObject expectedInjectAdapterB =
        JavaFileObjects.forSourceString("Field$B$$InjectAdapter", ""
            + "import dagger.internal.Binding;\n"
            + "import dagger.internal.Linker;\n"
            + "import java.lang.Override;\n"
            + "import java.lang.String;\n"
            + "import java.lang.SuppressWarnings;\n"
            + "import java.util.Set;\n"
            + "public final class Field$B$$InjectAdapter\n"
            + "    extends Binding<Field.B> {\n"
            + "  private Binding<String> name;\n" // For field.
            + "  public Field$B$$InjectAdapter() {\n"
            + "    super(\"Field$B\", \"members/Field$B\", NOT_SINGLETON, Field.B.class);\n"
            + "  }\n"
            + "  @Override @SuppressWarnings(\"unchecked\")\n"
            + "  public void attach(Linker linker) {\n"
            + "    name = (Binding<String>)linker.requestBinding(\n"
            + "      \"java.lang.String\", Field.B.class, getClass().getClassLoader());\n"
            + "  }\n"
            + "  @Override public void getDependencies(\n"
            + "      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {\n"
            + "    injectMembersBindings.add(name);\n" // Name is added to dependencies.
            + "  }\n"
            + "  @Override public Field.B get() {\n"
            + "    Field.B result = new Field.B();\n"
            + "    injectMembers(result);\n"
            + "    return result;\n"
            + "  }\n"
            + "  @Override public void injectMembers(Field.B object) {\n"
            + "    object.name = name.get();\n" // Inject field.
            + "  }\n"
            + "}\n"
        );
    assertAbout(javaSource())
        .that(sourceFile)
        .processedWith(daggerProcessors())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedModuleAdapter, expectedInjectAdapterA, expectedInjectAdapterB);
  }

  @Test public void providesHasParameterNamedModule() {
    JavaFileObject a = JavaFileObjects.forSourceString("A", ""
        + "import javax.inject.Inject;\n"
        + "class A {\n"
        + "  @Inject A(){ }\n"
        + "}\n"
    );
    JavaFileObject b = JavaFileObjects.forSourceString("B", ""
        + "import javax.inject.Inject;\n"
        + "class B {\n"
        + "  @Inject B(){ }\n"
        + "}\n"
    );

    JavaFileObject module = JavaFileObjects.forSourceString("BModule", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Inject;\n"
        + "@Module(injects = B.class)\n"
        + "class BModule {\n"
        + "  @Provides B b(A module) {\n"
        + "    return new B();\n"
        + "  }\n"
        + "}\n"
    );

    assertAbout(javaSources())
        .that(asList(a, b, module))
        .processedWith(daggerProcessors())
        .compilesWithoutError();
  }

  @Test public void duplicateInjectsFails() {
    JavaFileObject module = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Inject;\n"
        + "class A {}\n"
        + "@Module(injects = { A.class, A.class })\n"
        + "class BModule { }\n"
    );

    assertAbout(javaSource())
        .that(module)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("'injects' list contains duplicate entries: [A]")
        .in(module).onLine(6);
  }

  @Test public void duplicateIncludesFails() {
    JavaFileObject module = JavaFileObjects.forSourceString("Test", ""
        + "import dagger.Module;\n"
        + "import dagger.Provides;\n"
        + "import javax.inject.Inject;\n"
        + "@Module\n"
        + "class AModule {}\n"
        + "@Module(includes = { AModule.class, AModule.class })\n"
        + "class BModule { }\n"
    );

    assertAbout(javaSource())
        .that(module)
        .processedWith(daggerProcessors())
        .failsToCompile()
        .withErrorContaining("'includes' list contains duplicate entries: [AModule]")
        .in(module).onLine(7);
  }
}
