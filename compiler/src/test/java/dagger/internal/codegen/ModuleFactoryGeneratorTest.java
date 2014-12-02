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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_STATIC;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RETURN_SET;

@RunWith(JUnit4.class)
public class ModuleFactoryGeneratorTest {
  // TODO(gak): add tests for invalid combinations of scope and qualifier annotations like we have
  // for @Inject

  private String formatErrorMessage(String msg) {
    return String.format(msg, "Provides");
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, "Provides", "Module");
  }

  @Test public void providesMethodNotInModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "",
        "final class TestModule {",
        "  @Provides String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE));
  }

  @Test public void providesMethodAbstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "abstract class TestModule {",
        "  @Provides abstract String provideString();",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_ABSTRACT));
  }

  @Test public void providesMethodPrivate() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides private String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_PRIVATE));
  }

  @Test public void providesMethodStatic() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides static String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_STATIC));
  }

  @Test public void providesMethodReturnVoid() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides void provideNothing() {}",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE));
  }

  @Test public void providesMethodWithTypeParameter() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides <T> String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER));
  }

  @Test public void providesMethodSetValuesWildcard() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set<?> provideWildcard() {",
        "    return null;",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_RETURN_TYPE);
  }

  @Test public void providesMethodSetValuesRawSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set provideSomething() {",
        "    return null;",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void providesMethodSetValuesNotASet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) List<String> provideStrings() {",
        "    return null;",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_SET_VALUES_RETURN_SET);
  }

  @Test public void singleProvidesMethodNoArgs() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule$$ProvideStringFactory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule$$ProvideStringFactory implements Factory<String> {",
        "  private final TestModule module;",
        "",
        "  public TestModule$$ProvideStringFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public String get() {",
        "    return module.provideString();",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  private static final JavaFileObject QUALIFIER_A =
      JavaFileObjects.forSourceLines("test.QualifierA",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");
  private static final JavaFileObject QUALIFIER_B =
      JavaFileObjects.forSourceLines("test.QualifierB",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");

  @Test public void multipleProvidesMethods() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Arrays;",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides List<Object> provideObjects(@QualifierA Object a, @QualifierB Object b) {",
        "    return Arrays.asList(a, b);",
        "  }",
        "",
        "  @Provides @QualifierA Object provideAObject() {",
        "    return new Object();",
        "  }",
        "",
        "  @Provides @QualifierB Object provideBObject() {",
        "    return new Object();",
        "  }",
        "}");
    JavaFileObject listFactoryFile = JavaFileObjects.forSourceLines(
        "TestModule$$ProvideObjectsFactory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import java.util.List;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule$$ProvideObjectsFactory implements Factory<List<Object>> {",
        "  private final TestModule module;",
        "  private final Provider<Object> aProvider;",
        "  private final Provider<Object> bProvider;",
        "",
        "  public TestModule$$ProvideObjectsFactory(TestModule module,",
        "       Provider<Object> aProvider, Provider<Object> bProvider) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert aProvider != null;",
        "    this.aProvider = aProvider;",
        "    assert bProvider != null;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  @Override public List<Object> get() {",
        "    return module.provideObjects(aProvider.get(), bProvider.get());",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(moduleFile, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(listFactoryFile);
  }

  @Test public void providesSetElement() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET;",
        "",
        "import java.util.logging.Logger;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET) String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule$$ProvideStringFactory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule$$ProvideStringFactory implements Factory<Set<String>> {",
        "  private final TestModule module;",
        "",
        "  public TestModule$$ProvideStringFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public Set<String> get() {",
        "    return Collections.singleton(module.provideString());",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void proviesSetValues() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set<String> provideStrings() {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule$$ProvideStringsFactory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import java.util.Set;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule$$ProvideStringsFactory implements Factory<Set<String>> {",
        "  private final TestModule module;",
        "",
        "  public TestModule$$ProvideStringsFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public Set<String> get() {",
        "    return module.provideStrings();",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void multipleProvidesMethodsWithSameName() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides Object provide(int i) {",
        "    return i;",
        "  }",
        "",
        "  @Provides String provide() {",
        "    return \"\";",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_WITH_SAME_NAME)).in(moduleFile).onLine(8)
        .and().withErrorContaining(formatErrorMessage(BINDING_METHOD_WITH_SAME_NAME))
        .in(moduleFile).onLine(12);
  }

  @Test
  public void providedTypes() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.io.Closeable;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String string() {",
        "    return null;",
        "  }",
        "",
        "  @Provides Set<String> strings() {",
        "    return null;",
        "  }",
        "",
        "  @Provides Set<? extends Closeable> closeables() {",
        "    return null;",
        "  }",
        "",
        "  @Provides String[] stringArray() {",
        "    return null;",
        "  }",
        "",
        "  @Provides int integer() {",
        "    return 0;",
        "  }",
        "",
        "  @Provides int[] integers() {",
        "    return null;",
        "  }",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }

  @Test
  public void privateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "final class Enclosing {",
        "  @Module private static final class PrivateModule {",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be private.")
        .in(moduleFile).onLine(6);
  }

  @Test
  public void enclosedInPrivateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "final class Enclosing {",
        "  private static final class PrivateEnclosing {",
        "    @Module static final class TestModule {",
        "    }",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be enclosed in private types.")
        .in(moduleFile).onLine(7);
  }

  @Test
  public void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile = JavaFileObjects.forSourceLines("test.PublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {",
        "    NonPublicModule1.class, OtherPublicModule.class, NonPublicModule2.class",
        "})",
        "public final class PublicModule {",
        "}");
    JavaFileObject nonPublicModule1File = JavaFileObjects.forSourceLines("test.NonPublicModule1",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class NonPublicModule1 {",
        "}");
    JavaFileObject nonPublicModule2File = JavaFileObjects.forSourceLines("test.NonPublicModule2",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class NonPublicModule2 {",
        "}");
    JavaFileObject otherPublicModuleFile = JavaFileObjects.forSourceLines("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "public final class OtherPublicModule {",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(
            publicModuleFile, nonPublicModule1File, nonPublicModule2File, otherPublicModuleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("This module is public, but it includes non-public "
            + "(or effectively non-public) modules. "
            + "Either reduce the visibility of this module or make "
            + "test.NonPublicModule1 and test.NonPublicModule2 public.")
        .in(publicModuleFile).onLine(8);
  }
}
