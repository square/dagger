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
import static dagger.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_GENERIC_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_SCOPES;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.QUALIFIER_ON_INJECT_CONSTRUCTOR;

@RunWith(JUnit4.class)
// TODO(gak): add tests for generation in the default package.
public final class InjectConstructorFactoryGeneratorTest {
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
  private static final JavaFileObject SCOPE_A =
      JavaFileObjects.forSourceLines("test.ScopeA",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");
  private static final JavaFileObject SCOPE_B =
      JavaFileObjects.forSourceLines("test.ScopeB",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Test public void injectOnPrivateConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateConstructor {",
        "  @Inject private PrivateConstructor() {}",
        "}");
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(INJECT_ON_PRIVATE_CONSTRUCTOR).in(file).onLine(6);
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS).in(file).onLine(6);
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(INJECT_CONSTRUCTOR_ON_GENERIC_CLASS).in(file).onLine(6);
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MULTIPLE_INJECT_CONSTRUCTORS).in(file).onLine(6)
        .and().withErrorContaining(MULTIPLE_INJECT_CONSTRUCTORS).in(file).onLine(8);
  }

  @Test public void multipleQualifiersOnInjectConstructorParameter() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierConstructorParam",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleQualifierConstructorParam {",
        "  @Inject MultipleQualifierConstructorParam(@QualifierA @QualifierB String s) {}",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(file, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor()).failsToCompile()
        // for whatever reason, javac only reports the error once on the constructor
        .withErrorContaining(MULTIPLE_QUALIFIERS).in(file).onLine(6);
  }

  @Test public void injectConstructorOnClassWithMultipleScopes() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleScopeClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "@ScopeA @ScopeB class MultipleScopeClass {",
        "  @Inject MultipleScopeClass() {}",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(file, SCOPE_A, SCOPE_B))
        .processedWith(new ComponentProcessor()).failsToCompile()
        .withErrorContaining(MULTIPLE_SCOPES).in(file).onLine(5).atColumn(1)
        .and().withErrorContaining(MULTIPLE_SCOPES).in(file).onLine(5).atColumn(9);
  }

  @Test public void injectConstructorWithQualifier() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleScopeClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleScopeClass {",
        "  @Inject",
        "  @QualifierA",
        "  @QualifierB",
        "  MultipleScopeClass() {}",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(file, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor()).failsToCompile()
        .withErrorContaining(QUALIFIER_ON_INJECT_CONSTRUCTOR).in(file).onLine(7)
        .and().withErrorContaining(QUALIFIER_ON_INJECT_CONSTRUCTOR).in(file).onLine(8);
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRIVATE_INJECT_FIELD).in(file).onLine(6);
  }

  @Test public void multipleQualifiersOnField() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleQualifierInjectField {",
        "  @Inject @QualifierA @QualifierB String s;",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(file, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor()).failsToCompile()
        .withErrorContaining(MULTIPLE_QUALIFIERS).in(file).onLine(6).atColumn(11)
        .and().withErrorContaining(MULTIPLE_QUALIFIERS).in(file).onLine(6).atColumn(23);
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
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
    assert_().about(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(GENERIC_INJECT_METHOD).in(file).onLine(6);
  }

  @Test public void multipleQualifiersOnInjectMethodParameter() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierMethodParam",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleQualifierMethodParam {",
        "  @Inject void method(@QualifierA @QualifierB String s) {}",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(file, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        // for whatever reason, javac only reports the error once on the method
        .withErrorContaining(MULTIPLE_QUALIFIERS).in(file).onLine(6);
  }

  @Test public void fieldInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FieldInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "class FieldInjection {",
        "  @Inject String string;",
        "  @Inject Lazy<String> lazyString;",
        "  @Inject Provider<String> stringProvider;",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines("test.FieldInjection$$MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.internal.DoubleCheckLazy;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class FieldInjection$$MembersInjector ",
        "    implements MembersInjector<FieldInjection> {",
        "",
        "  private final Provider<String> stringProvider;",
        "",
        "  public FieldInjection$$MembersInjector(Provider<String> stringProvider) {",
        "    assert stringProvider != null;",
        "    this.stringProvider = stringProvider;",
        "  }",
        "",
        "  @Override public void injectMembers(FieldInjection instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.string = stringProvider.get();",
        "    instance.lazyString = DoubleCheckLazy.create(stringProvider);",
        "    instance.stringProvider = stringProvider;",
        "  }",
        "}");
    assert_().about(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void methodInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MethodInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "class MethodInjection {",
        "  @Inject void noArgs() {}",
        "  @Inject void oneArg(String string) {}",
        "  @Inject void manyArgs(",
        "      String string, Lazy<String> lazyString, Provider<String> stringProvider) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.MethodInjection$$MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.internal.DoubleCheckLazy;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class MethodInjection$$MembersInjector ",
        "    implements MembersInjector<MethodInjection> {",
        "",
        "  private final Provider<String> stringProvider;",
        "",
        "  public MethodInjection$$MembersInjector(Provider<String> stringProvider) {",
        "    assert stringProvider != null;",
        "    this.stringProvider = stringProvider;",
        "  }",
        "",
        "  @Override public void injectMembers(MethodInjection instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.noArgs();",
        "    instance.oneArg(stringProvider.get());",
        "    instance.manyArgs(stringProvider.get(), DoubleCheckLazy.create(stringProvider),",
        "        stringProvider);",
        "  }",
        "}");
    assert_().about(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void mixedMemberInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MixedMemberInjection",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "class MixedMemberInjection {",
        "  @Inject String string;",
        "  @Inject void setString(String s) {}",
        "  @Inject Object object;",
        "  @Inject void setObject(Object o) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.MixedMemberInjection$$MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class MixedMemberInjection$$MembersInjector ",
        "    implements MembersInjector<MixedMemberInjection> {",
        "",
        "  private final Provider<String> stringAndSProvider;",
        "  private final Provider<Object> objectAndOProvider;",
        "",
        "  public MixedMemberInjection$$MembersInjector(Provider<String> stringAndSProvider,",
        "      Provider<Object> objectAndOProvider) {",
        "    assert stringAndSProvider != null;",
        "    this.stringAndSProvider = stringAndSProvider;",
        "    assert objectAndOProvider != null;",
        "    this.objectAndOProvider = objectAndOProvider;",
        "  }",
        "",
        "  @Override public void injectMembers(MixedMemberInjection instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.string = stringAndSProvider.get();",
        "    instance.object = objectAndOProvider.get();",
        "    instance.setString(stringAndSProvider.get());",
        "    instance.setObject(objectAndOProvider.get());",
        "  }",
        "}");
    assert_().about(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void injectConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(String s) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.InjectConstructor$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class InjectConstructor$$Factory ",
        "    implements Factory<InjectConstructor> {",
        "",
        "  private final Provider<String> sProvider;",
        "",
        "  public InjectConstructor$$Factory(Provider<String> sProvider) {",
        "    assert sProvider != null;",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  @Override public InjectConstructor get() {",
        "    return new InjectConstructor(sProvider.get());",
        "  }",
        "}");
    assert_().about(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void injectConstructorAndMembersInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AllInjections",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class AllInjections {",
        "  @Inject String s;",
        "  @Inject AllInjections(String s) {}",
        "  @Inject void s(String s) {}",
        "}");
    JavaFileObject expectedFactory = JavaFileObjects.forSourceLines(
        "test.AllInjections$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class AllInjections$$Factory ",
        "    implements Factory<AllInjections> {",
        "",
        "  private final MembersInjector<AllInjections> membersInjector;",
        "  private final Provider<String> sProvider;",
        "",
        "  public AllInjections$$Factory(MembersInjector<AllInjections> membersInjector, ",
        "      Provider<String> sProvider) {",
        "    assert membersInjector != null;",
        "    this.membersInjector = membersInjector;",
        "    assert sProvider != null;",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  @Override public AllInjections get() {",
        "    AllInjections instance = new AllInjections(sProvider.get());",
        "    membersInjector.injectMembers(instance);",
        "    return instance;",
        "  }",
        "}");
    JavaFileObject expectedMembersInjector = JavaFileObjects.forSourceLines(
        "test.AllInjections$$MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class AllInjections$$MembersInjector ",
        "    implements MembersInjector<AllInjections> {",
        "",
        "  private final Provider<String> sProvider;",
        "",
        "  public AllInjections$$MembersInjector(Provider<String> sProvider) {",
        "    assert sProvider != null;",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  @Override public void injectMembers(AllInjections instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.s = sProvider.get();",
        "    instance.s(sProvider.get());",
        "  }",
        "}");
    assert_().about(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedFactory, expectedMembersInjector);
  }

  @Test public void supertypeRequiresMemberInjection() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "class A {}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class B extends A {",
        "  @Inject B() {}",
        "}");
    JavaFileObject expectedFactory = JavaFileObjects.forSourceLines(
        "test.B$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class B$$Factory implements Factory<B> {",
        "",
        "  private final MembersInjector<B> membersInjector;",
        "",
        "  public B$$Factory(MembersInjector<B> membersInjector) {",
        "    assert membersInjector != null;",
        "    this.membersInjector = membersInjector;",
        "  }",
        "",
        "  @Override public B get() {",
        "    B instance = new B();",
        "    membersInjector.injectMembers(instance);",
        "    return instance;",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(aFile, bFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedFactory);
  }

  @Test public void supertypeMembersInjection() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "class A {}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class B extends A {",
        "  @Inject String s;",
        "}");
    JavaFileObject expectedMembersInjector = JavaFileObjects.forSourceLines(
        "test.AllInjections$$MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class B$$MembersInjector ",
        "    implements MembersInjector<B> {",
        "",
        "  private final MembersInjector<A> supertypeInjector;",
        "  private final Provider<String> sProvider;",
        "",
        "  public B$$MembersInjector(MembersInjector<A> supertypeInjector,",
        "      Provider<String> sProvider) {",
        "    assert supertypeInjector != null;",
        "    this.supertypeInjector = supertypeInjector;",
        "    assert sProvider != null;",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  @Override public void injectMembers(B instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    supertypeInjector.injectMembers(instance);",
        "    instance.s = sProvider.get();",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(aFile, bFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedMembersInjector);
  }

  @Test
  public void wildcardDependency() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import java.util.List;",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(List<? extends Object> objects) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.InjectConstructor$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import java.util.List;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class InjectConstructor$$Factory ",
        "    implements Factory<InjectConstructor> {",
        "",
        "  private final Provider<List<? extends Object>> objectsProvider;",
        "",
        "  public InjectConstructor$$Factory(Provider<List<? extends Object>> objectsProvider) {",
        "    assert objectsProvider != null;",
        "    this.objectsProvider = objectsProvider;",
        "  }",
        "",
        "  @Override public InjectConstructor get() {",
        "    return new InjectConstructor(objectsProvider.get());",
        "  }",
        "}");
    assert_().about(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void basicNameCollision() {
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("other.pkg.Factory",
        "package other.pkg;",
        "",
        "public class Factory {}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import other.pkg.Factory;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Factory factory) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.InjectConstructor$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class InjectConstructor$$Factory ",
        "    implements Factory<InjectConstructor> {",
        "",
        "  private final Provider<other.pkg.Factory> factoryProvider;",
        "",
        "  public InjectConstructor$$Factory(Provider<other.pkg.Factory> factoryProvider) {",
        "    assert factoryProvider != null;",
        "    this.factoryProvider = factoryProvider;",
        "  }",
        "",
        "  @Override public InjectConstructor get() {",
        "    return new InjectConstructor(factoryProvider.get());",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(factoryFile, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void nestedNameCollision() {
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("other.pkg.Outer",
        "package other.pkg;",
        "",
        "public class Outer {",
        "  public class Factory {}",
        "}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import other.pkg.Outer;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Outer.Factory factory) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.InjectConstructor$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "import other.pkg.Outer;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class InjectConstructor$$Factory ",
        "    implements Factory<InjectConstructor> {",
        "",
        "  private final Provider<Outer.Factory> factoryProvider;",
        "",
        "  public InjectConstructor$$Factory(Provider<Outer.Factory> factoryProvider) {",
        "    assert factoryProvider != null;",
        "    this.factoryProvider = factoryProvider;",
        "  }",
        "",
        "  @Override public InjectConstructor get() {",
        "    return new InjectConstructor(factoryProvider.get());",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(factoryFile, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void samePackageNameCollision() {
    JavaFileObject samePackageInterface = JavaFileObjects.forSourceLines("test.CommonName",
        "package test;",
        "",
        "public interface CommonName {}");
    JavaFileObject differentPackageInterface = JavaFileObjects.forSourceLines(
        "other.pkg.CommonName",
        "package other.pkg;",
        "",
        "public interface CommonName {}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor implements CommonName {",
        "  @Inject InjectConstructor(other.pkg.CommonName otherPackage, CommonName samePackage) {}",
        "}");
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.InjectConstructor$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "import other.pkg.CommonName;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class InjectConstructor$$Factory ",
        "    implements Factory<InjectConstructor> {",
        "",
        "  private final Provider<CommonName> otherPackageProvider;",
        "  private final Provider<test.CommonName> samePackageProvider;",
        "",
        "  public InjectConstructor$$Factory(Provider<CommonName> otherPackageProvider,",
        "      Provider<test.CommonName> samePackageProvider) {",
        "    assert otherPackageProvider != null;",
        "    this.otherPackageProvider = otherPackageProvider;",
        "    assert samePackageProvider != null;",
        "    this.samePackageProvider = samePackageProvider;",
        "  }",
        "",
        "  @Override public InjectConstructor get() {",
        "    return new InjectConstructor(otherPackageProvider.get(), samePackageProvider.get());",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(samePackageInterface, differentPackageInterface, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void noDeps() {
    JavaFileObject simpleType = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SimpleType {",
        "  @Inject SimpleType() {}",
        "}");
    JavaFileObject factory = JavaFileObjects.forSourceLines("test.SimpleType$$Factory",
        "package test;",
        "",
        "import dagger.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public enum SimpleType$$Factory implements Factory<SimpleType> {",
        "  INSTANCE;",
        "",
        "  @Override public SimpleType get() {",
        "    return new SimpleType();",
        "  }",
        "}");
    assert_().about(javaSource())
        .that(simpleType)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factory);
  }
}
