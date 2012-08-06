/**
 * Copyright (C) 2012 Square, Inc.
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
package com.squareup.injector.internal;

import com.squareup.injector.MembersInjector;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Provider;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Jesse Wilson
 */
public final class KeysTest {
  int primitive;
  @Test public void lonePrimitiveGetsBoxed() throws NoSuchFieldException {
    assertThat(fieldKey("primitive"))
        .isEqualTo("java.lang.Integer");
  }

  Map<String, List<Integer>> mapStringListInteger;
  @Test public void parameterizedTypes() throws NoSuchFieldException {
    assertThat(fieldKey("mapStringListInteger"))
        .isEqualTo("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
  }

  Map<String, int[]> mapStringArrayInt;
  @Test public void parameterizedTypeOfPrimitiveArray() throws NoSuchFieldException {
    assertThat(fieldKey("mapStringArrayInt"))
        .isEqualTo("java.util.Map<java.lang.String, int[]>");
  }

  @Named("foo") String annotatedType;
  @Test public void annotatedType() throws NoSuchFieldException {
    assertThat(fieldKey("annotatedType"))
        .isEqualTo("@javax.inject.Named(value=foo)/java.lang.String");
  }

  String className;
  @Test public void testGetClassName() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("className")))
        .isEqualTo("java.lang.String");
  }

  @Named("foo") String classNameWithAnnotation;
  @Test public void testGetClassNameWithoutAnnotation() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("classNameWithAnnotation")))
        .isEqualTo("java.lang.String");
  }

  String[] classNameArray;
  @Test public void testGetClassNameArray() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("classNameArray"))).isNull();
  }

  List<String> classNameParameterized;
  @Test public void testGetClassParameterized() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("classNameParameterized"))).isNull();
  }

  @Named("foo") String annotated;
  @Test public void testAnnotated() throws NoSuchFieldException {
    assertThat(fieldKey("annotated")).isEqualTo("@javax.inject.Named(value=foo)/java.lang.String");
    assertThat(Keys.isAnnotated(fieldKey("annotated"))).isTrue();
  }

  String notAnnotated;
  @Test public void testIsAnnotatedFalse() throws NoSuchFieldException {
    assertThat(Keys.isAnnotated(fieldKey("notAnnotated"))).isFalse();
  }

  Provider<String> providerOfType;
  String providedType;
  @Test public void testGetDelegateKey() throws NoSuchFieldException {
    assertThat(Keys.getDelegateKey(fieldKey("providerOfType")))
        .isEqualTo(fieldKey("providedType"));
  }

  @Named("/@") Provider<String> providerOfTypeAnnotated;
  @Named("/@") String providedTypeAnnotated;
  @Test public void testGetDelegateKeyWithAnnotation() throws NoSuchFieldException {
    assertThat(Keys.getDelegateKey(fieldKey("providerOfTypeAnnotated")))
        .isEqualTo(fieldKey("providedTypeAnnotated"));
  }

  @Named("/@") MembersInjector<String> membersInjectorOfType;
  @Named("/@") String injectedType;
  @Test public void testGetDelegateKeyWithMembersInjector() throws NoSuchFieldException {
    assertThat(Keys.getDelegateKey(fieldKey("membersInjectorOfType")))
        .isEqualTo("members/java.lang.String");
  }

  private String fieldKey(String fieldName) throws NoSuchFieldException {
    Field field = KeysTest.class.getDeclaredField(fieldName);
    return Keys.get(field.getGenericType(), field.getAnnotations(), field);
  }
}
