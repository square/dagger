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
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
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
 * Test case for {@link DependencyRequestMapper}.
 */
@RunWith(JUnit4.class)
public class DependencyRequestMapperTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;
  private Types types;
  private Key.Factory keyFactory;
  private DependencyRequest.Factory dependencyRequestFactory;

  @Before public void setUp() {
    this.types = compilationRule.getTypes();
    this.elements = compilationRule.getElements();
    this.keyFactory = new Key.Factory(types, elements);
    this.dependencyRequestFactory = new DependencyRequest.Factory(types, keyFactory);
  }

  private List<? extends VariableElement> sampleProviderParameters() {
    TypeElement moduleElement =
        elements.getTypeElement(ProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    return providesMethod.getParameters();
  }

  private List<? extends VariableElement> sampleProducerParameters() {
    TypeElement moduleElement =
        elements.getTypeElement(ProducesMethodModule.class.getCanonicalName());
    ExecutableElement producesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    return producesMethod.getParameters();
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

  private DependencyRequest dependencyRequestForProducer() {
    return dependencyRequestFactory.forRequiredVariable(sampleProducerParameters().get(0));
  }

  private DependencyRequest dependencyRequestForProduced() {
    return dependencyRequestFactory.forRequiredVariable(sampleProducerParameters().get(1));
  }

  @Test public void forProvider() {
    DependencyRequestMapper mapper = DependencyRequestMapper.FOR_PROVIDER;
    assertThat(mapper.getFrameworkKey(dependencyRequestForInstance()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForLazy()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForProvider()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForMembersInjector()).kind())
        .isEqualTo(FrameworkKey.Kind.MEMBERS_INJECTOR);
  }

  @Test public void forProducer() {
    DependencyRequestMapper mapper = DependencyRequestMapper.FOR_PRODUCER;
    assertThat(mapper.getFrameworkKey(dependencyRequestForInstance()).kind())
        .isEqualTo(FrameworkKey.Kind.PRODUCER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForLazy()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForProvider()).kind())
        .isEqualTo(FrameworkKey.Kind.PROVIDER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForMembersInjector()).kind())
        .isEqualTo(FrameworkKey.Kind.MEMBERS_INJECTOR);
    assertThat(mapper.getFrameworkKey(dependencyRequestForProducer()).kind())
        .isEqualTo(FrameworkKey.Kind.PRODUCER);
    assertThat(mapper.getFrameworkKey(dependencyRequestForProduced()).kind())
        .isEqualTo(FrameworkKey.Kind.PRODUCER);
  }

  @Module(library = true)
  static final class ProvidesMethodModule {
    @Provides String provideString(
        Integer a, Lazy<Integer> b, Provider<Integer> c, MembersInjector<Integer> d) {
      return null;
    }
  }

  @ProducerModule
  static final class ProducesMethodModule {
    @Produces String produceString(Producer<Integer> a, Produced<Integer> b) {
      return null;
    }
  }
}
