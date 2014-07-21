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

import static com.google.common.truth.Truth.ASSERT;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_STATIC;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_WITH_SAME_NAME;

@RunWith(JUnit4.class)
public class ModuleProcessorTest {
  // TODO(gak): add tests for invalid combinations of scope and qualifier annotations like we have
  // for @Inject

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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_NOT_IN_MODULE);
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_ABSTRACT);
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_PRIVATE);
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_STATIC);
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_MUST_RETURN_A_VALUE);
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_TYPE_PARAMETER);
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
    ASSERT.about(javaSource()).that(moduleFile)
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_SET_VALUES_RAW_SET);
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
    ASSERT.about(javaSource()).that(moduleFile)
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
    ASSERT.about(javaSource()).that(moduleFile)
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
    ASSERT.about(javaSources()).that(ImmutableList.of(moduleFile, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(listFactoryFile);
  }

  @Test public void proviesSetElement() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET;",
        "",
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
    ASSERT.about(javaSource()).that(moduleFile)
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
    ASSERT.about(javaSource()).that(moduleFile)
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_WITH_SAME_NAME).in(moduleFile).onLine(8)
        .and().withErrorContaining(PROVIDES_METHOD_WITH_SAME_NAME).in(moduleFile).onLine(12);
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
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }
}
