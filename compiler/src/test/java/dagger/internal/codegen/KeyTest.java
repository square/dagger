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

import static org.truth0.Truth.ASSERT;

import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;

import dagger.Module;
import dagger.Provides;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * Tests {@link Key}.
 */
@RunWith(JUnit4.class)
public class KeyTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Test public void forInjectConstructor() {
    TypeElement typeElement =
        compilationRule.getElements().getTypeElement(InjectedClass.class.getCanonicalName());
    ExecutableElement constructor =
        Iterables.getOnlyElement(ElementFilter.constructorsIn(typeElement.getEnclosedElements()));
    ASSERT.that(Key.forInjectConstructor(constructor)).isEqualTo(Key.create(typeElement.asType()));
  }

  static final class InjectedClass {
    @SuppressWarnings("unused")
    @Inject InjectedClass(String s, int i) {}
  }

  @Test public void forProvidesMethod() {
    Elements elements = compilationRule.getElements();
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement moduleElement =
        elements.getTypeElement(ProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    ASSERT.that(Key.forProvidesMethod(providesMethod)).isEqualTo(Key.create(stringType));
  }

  @Module(library = true)
  static final class ProvidesMethodModule {
    @Provides String provideString() {
      return null;
    }
  }

  @Test public void forProvidesMethod_qualified() {
    Elements elements = compilationRule.getElements();
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement qualifierElement =
        elements.getTypeElement(TestQualifier.class.getCanonicalName());
    TypeElement moduleElement =
        elements.getTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key key = Key.forProvidesMethod(providesMethod);
    ASSERT.that(Mirrors.equivalence().wrap(key.qualifier().get().getAnnotationType()))
        .isEqualTo(Mirrors.equivalence().wrap(qualifierElement.asType()));
    ASSERT.that(key.wrappedType()).isEqualTo(Mirrors.equivalence().wrap(stringType));
  }

  @Module(library = true)
  static final class QualifiedProvidesMethodModule {
    @Provides @TestQualifier String provideQualifiedString() {
      return null;
    }
  }

  @Qualifier
  @interface TestQualifier {}
}
