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

import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import java.util.List;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test case for {@link FrameworkKey}.
 */
@RunWith(JUnit4.class)
public class FrameworkKeyTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;
  private Types types;
  private Key.Factory keyFactory;
  private DependencyRequest.Factory dependencyRequestFactory;

  @Before public void setUp() {
    this.types = compilationRule.getTypes();
    this.elements = compilationRule.getElements();
    this.keyFactory = new Key.Factory(types, elements);
    this.dependencyRequestFactory = new DependencyRequest.Factory(elements, types, keyFactory);
  }

  private List<? extends VariableElement> sampleProviderParameters() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement moduleElement =
        elements.getTypeElement(ProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    return providesMethod.getParameters();
  }

  private DependencyRequest dependencyRequestForInstance() {
    return dependencyRequestFactory.forRequiredVariable(sampleProviderParameters().get(0));
  }

  private DependencyRequest dependencyRequestForLazy() {
    return dependencyRequestFactory.forRequiredVariable(sampleProviderParameters().get(1));
  }

  private DependencyRequest dependencyRequestForProvider() {
    return dependencyRequestFactory.forRequiredVariable(sampleProviderParameters().get(2));
  }

  private DependencyRequest dependencyRequestForMembersInjector() {
    return dependencyRequestFactory.forRequiredVariable(sampleProviderParameters().get(3));
  }

  @Test public void forDependencyRequest() {
    assertThat(FrameworkKey.forDependencyRequest(dependencyRequestForInstance()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(FrameworkKey.forDependencyRequest(dependencyRequestForLazy()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(FrameworkKey.forDependencyRequest(dependencyRequestForProvider()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(FrameworkKey.forDependencyRequest(dependencyRequestForMembersInjector()).kind())
        .isEqualTo(FrameworkKey.Kind.MEMBERS_INJECTOR);
  }

  @Test public void frameworkType() {
    assertThat(FrameworkKey.forDependencyRequest(dependencyRequestForInstance()).frameworkType())
        .isEqualTo(ParameterizedTypeName.create(
            ClassName.fromClass(Provider.class), ClassName.fromClass(Integer.class)));
    assertThat(FrameworkKey.forDependencyRequest(dependencyRequestForMembersInjector())
        .frameworkType())
        .isEqualTo(ParameterizedTypeName.create(
            ClassName.fromClass(MembersInjector.class), ClassName.fromClass(Integer.class)));
  }

  @Module(library = true)
  static final class ProvidesMethodModule {
    @Provides String provideString(
        Integer a, Lazy<Integer> b, Provider<Integer> c, MembersInjector<Integer> d) {
      return null;
    }
  }
}
