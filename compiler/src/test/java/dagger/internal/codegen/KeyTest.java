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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import dagger.Module;
import dagger.Provides;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.ASSERT;
import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;

/**
 * Tests {@link Key}.
 */
@RunWith(JUnit4.class)
public class KeyTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;
  private Types types;
  private Key.Factory keyFactory;

  @Before public void setUp() {
    this.types = compilationRule.getTypes();
    this.elements = compilationRule.getElements();
    this.keyFactory = new Key.Factory(types, elements);
  }

  @Test public void forInjectConstructor() {
    TypeElement typeElement =
        compilationRule.getElements().getTypeElement(InjectedClass.class.getCanonicalName());
    ExecutableElement constructor =
        Iterables.getOnlyElement(ElementFilter.constructorsIn(typeElement.getEnclosedElements()));
    ASSERT.that(keyFactory.forInjectConstructor(constructor))
        .isEqualTo(keyFactory.forType(typeElement.asType()));
  }

  static final class InjectedClass {
    @SuppressWarnings("unused")
    @Inject InjectedClass(String s, int i) {}
  }

  @Test public void forProvidesMethod() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement moduleElement =
        elements.getTypeElement(ProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    ASSERT.that(keyFactory.forProvidesMethod(providesMethod))
        .isEqualTo(keyFactory.forType(stringType));
  }

  @Module(library = true)
  static final class ProvidesMethodModule {
    @Provides String provideString() {
      return null;
    }
  }

  @Test public void forProvidesMethod_qualified() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement qualifierElement =
        elements.getTypeElement(TestQualifier.class.getCanonicalName());
    TypeElement moduleElement =
        elements.getTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key key = keyFactory.forProvidesMethod(providesMethod);
    ASSERT.that(MoreTypes.equivalence().wrap(key.qualifier().get().getAnnotationType()))
        .isEqualTo(MoreTypes.equivalence().wrap(qualifierElement.asType()));
    ASSERT.that(key.wrappedType()).isEqualTo(MoreTypes.equivalence().wrap(stringType));
  }

  @Test public void qualifiedKeyEquivalents() {
    TypeElement moduleElement =
        elements.getTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key provisionKey = keyFactory.forProvidesMethod(providesMethod);

    TypeMirror type = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement injectableElement =
        elements.getTypeElement(QualifiedFieldHolder.class.getCanonicalName());
    Element injectionField =
        Iterables.getOnlyElement(ElementFilter.fieldsIn(injectableElement.getEnclosedElements()));
    AnnotationMirror qualifier = Iterables.getOnlyElement(injectionField.getAnnotationMirrors());
    Key injectionKey = keyFactory.forQualifiedType(Optional.<AnnotationMirror>of(qualifier), type);

    ASSERT.that(provisionKey).isEqualTo(injectionKey);
  }

  @Module(library = true)
  static final class QualifiedProvidesMethodModule {
    @Provides
    @TestQualifier(@InnerAnnotation)
    String provideQualifiedString() {
      return null;
    }
  }

  static final class QualifiedFieldHolder {
    @TestQualifier(@InnerAnnotation) String aString;
  }

  @Qualifier
  @interface TestQualifier {
    InnerAnnotation[] value();
  }

  @interface InnerAnnotation {}

  @Test public void forProvidesMethod_sets() {
    TypeElement setElement = elements.getTypeElement(Set.class.getCanonicalName());
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    DeclaredType setOfStringsType = types.getDeclaredType(setElement, stringType);
    TypeElement moduleElement =
        elements.getTypeElement(SetProvidesMethodsModule.class.getCanonicalName());
    for (ExecutableElement providesMethod
        : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
      ASSERT.that(keyFactory.forProvidesMethod(providesMethod))
          .isEqualTo(keyFactory.forType(setOfStringsType));
    }
  }

  @Module(library = true)
  static final class SetProvidesMethodsModule {
    @Provides(type = SET) String provideString() {
      return null;
    }

    @Provides(type = SET_VALUES) Set<String> provideStrings() {
      return null;
    }
  }

  interface PrimitiveTypes {
    int foo();
    Integer bar();
  }

  @Test public void primitiveKeysMatchBoxedKeys() {
    TypeElement holder = elements.getTypeElement(PrimitiveTypes.class.getCanonicalName());
    List<ExecutableElement> methods = (List<ExecutableElement>) holder.getEnclosedElements();

    // TODO(cgruber): Truth subject for TypeMirror and TypeElement
    TypeMirror intType = methods.get(0).getReturnType();
    ASSERT.that(intType.getKind().isPrimitive()).isTrue();
    TypeMirror integerType = methods.get(1).getReturnType();
    ASSERT.that(integerType.getKind().isPrimitive()).isFalse();
    ASSERT.that(types.isSameType(intType, integerType)).named("type equality").isFalse();

    Key intKey = keyFactory.forType(intType);
    Key integerKey = keyFactory.forType(integerType);
    ASSERT.that(intKey).isEqualTo(integerKey);
  }
}
