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

import com.google.auto.common.MoreElements;
import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

/**
 * Tests {@link Util}.
 */
@RunWith(JUnit4.class)
public class UtilTest {

  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;

  @Before public void setUp() {
    this.elements = compilationRule.getElements();
  }

  private interface TestType {}

  @Test public void isTypeOf_DeclaredType() {
    assertThat(Util.isTypeOf(TestType.class, typeElementFor(TestType.class).asType()))
        .named("mirror represents the TestType")
        .isTrue();
    assertThat(Util.isTypeOf(String.class, typeElementFor(TestType.class).asType()))
        .named("mirror does not represent a String")
        .isFalse();
  }

  private interface ArrayType {
    String[] array();
  }

  @Test public void isTypeOf_ArrayType() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(ArrayType.class));
    assertThat(Util.isTypeOf(new String[] {}.getClass(), type))
        .named("array mirror represents an array Class object")
        .isTrue();
  }

  private interface PrimitiveBoolean {
    boolean method();
  }

  @Test public void isTypeOf_PrimitiveBoolean() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveBoolean.class));
    assertThat(Util.isTypeOf(Boolean.TYPE, type)).named("mirror of a boolean").isTrue();
  }

  private interface PrimitiveByte {
    byte method();
  }

  @Test public void isTypeOf_PrimitiveByte() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveByte.class));
    assertThat(Util.isTypeOf(Byte.TYPE, type)).named("mirror of a byte").isTrue();
  }

  private interface PrimitiveChar {
    char method();
  }

  @Test public void isTypeOf_PrimitiveChar() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveChar.class));
    assertThat(Util.isTypeOf(Character.TYPE, type)).named("mirror of a char").isTrue();
  }

  private interface PrimitiveDouble {
    double method();
  }

  @Test public void isTypeOf_PrimitiveDouble() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveDouble.class));
    assertThat(Util.isTypeOf(Double.TYPE, type)).named("mirror of a double").isTrue();
  }

  private interface PrimitiveFloat {
    float method();
  }

  @Test public void isTypeOf_PrimitiveFloat() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveFloat.class));
    assertThat(Util.isTypeOf(Float.TYPE, type)).named("mirror of a float").isTrue();
  }

  private interface PrimitiveInt {
    int method();
  }

  @Test public void isTypeOf_PrimitiveInt() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveInt.class));
    assertThat(Util.isTypeOf(Integer.TYPE, type)).named("mirror of a int").isTrue();
  }

  private interface PrimitiveLong {
    long method();
  }

  @Test public void isTypeOf_PrimitiveLong() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveLong.class));
    assertThat(Util.isTypeOf(Long.TYPE, type)).named("mirror of a long").isTrue();
  }

  private interface PrimitiveShort {
    short method();
  }

  @Test public void isTypeOf_PrimitiveShort() {
    TypeMirror type = extractReturnTypeFromHolder(typeElementFor(PrimitiveShort.class));
    assertThat(Util.isTypeOf(Short.TYPE, type)).named("mirror of a short").isTrue();
  }

  private interface PrimitiveVoid {
    void method();
  }

  @Test public void isTypeOf_void() {
    TypeMirror primitive = extractReturnTypeFromHolder(typeElementFor(PrimitiveVoid.class));
    assertThat(Util.isTypeOf(Void.TYPE, primitive)).named("mirror of a void").isTrue();
  }

  private interface DeclaredVoid {
    Void method();
  }

  @Test public void isTypeOf_Void() {
    TypeMirror declared = extractReturnTypeFromHolder(typeElementFor(DeclaredVoid.class));
    assertThat(Util.isTypeOf(Void.class, declared)).named("mirror of a void").isTrue();
  }

  @Test public void isTypeOf_fail() {
    TypeMirror method =
        getOnlyElement(typeElementFor(DeclaredVoid.class).getEnclosedElements()).asType();
    try {
      Util.isTypeOf(String.class, method);
      assert_().fail();
    } catch (IllegalArgumentException expected) {}
  }

  // Utility methods for this test.

  private TypeMirror extractReturnTypeFromHolder(TypeElement typeElement) {
    Element element = Iterables.getOnlyElement(typeElement.getEnclosedElements());
    TypeMirror arrayType = MoreElements.asExecutable(element).getReturnType();
    return arrayType;
  }

  private TypeElement typeElementFor(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }
}
