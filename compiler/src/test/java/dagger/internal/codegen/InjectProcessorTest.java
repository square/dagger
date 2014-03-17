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

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_GENERIC_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static org.truth0.Truth.ASSERT;

import com.google.testing.compile.JavaFileObjects;

import javax.tools.JavaFileObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InjectProcessorTest {
  @Test public void injectOnPrivateConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateConstructor {",
        "  @Inject private PrivateConstructor() {}",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(INJECT_ON_PRIVATE_CONSTRUCTOR)
        .in(file).onLine(6);
  }

  @Test public void injectConstructorOnInnerClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class OuterClass {",
        "  class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(INJECT_CONSTRUCTOR_ON_INNER_CLASS).in(file).onLine(7);
  }

  @Test public void injectConstructorOnAbstractClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AbstractClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "abstract class AbstractClass {",
        "  @Inject AbstractClass() {}",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS)
        .in(file).onLine(6);
  }

  @Test public void injectConstructorOnGenericClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class GenericClass<T> {",
        "  @Inject GenericClass() {}",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(INJECT_CONSTRUCTOR_ON_GENERIC_CLASS)
        .in(file).onLine(6);
  }

  @Test public void multipleInjectConstructors() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.TooManyInjectConstructors",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class TooManyInjectConstructors {",
        "  @Inject TooManyInjectConstructors() {}",
        "  TooManyInjectConstructors(int i) {}",
        "  @Inject TooManyInjectConstructors(String s) {}",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(MULTIPLE_INJECT_CONSTRUCTORS).in(file).onLine(6)
        .and().withErrorContaining(MULTIPLE_INJECT_CONSTRUCTORS).in(file).onLine(8);
  }

  @Test public void finalInjectField() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FinalInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class FinalInjectField {",
        "  @Inject final String s;",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(FINAL_INJECT_FIELD).in(file).onLine(6);
  }

  @Test public void privateInjectField() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateInjectField {",
        "  @Inject private String s;",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(PRIVATE_INJECT_FIELD).in(file).onLine(6);
  }

  @Test public void abstractInjectMethod() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AbstractInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "abstract class AbstractInjectMethod {",
        "  @Inject abstract void method();",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(ABSTRACT_INJECT_METHOD).in(file).onLine(6);
  }

  @Test public void privateInjectMethod() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateInjectMethod {",
        "  @Inject private void method();",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(PRIVATE_INJECT_METHOD).in(file).onLine(6);
  }

  @Test public void genericInjectMethod() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class AbstractInjectMethod {",
        "  @Inject <T> void method();",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new InjectProcessor()).failsToCompile()
        .withErrorContaining(GENERIC_INJECT_METHOD).in(file).onLine(6);
  }
}
