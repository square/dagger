/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_RAWTYPES;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.TypeNames.PROVIDER;
import static dagger.internal.codegen.TypeNames.lazyOf;
import static dagger.internal.codegen.TypeNames.optionalOf;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.Optional;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.InstanceFactory;
import dagger.internal.Preconditions;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Provider;

/**
 * The nested class and static methods required by the component to implement optional bindings.
 */
// TODO(dpb): Name classes correctly if a component uses both Guava and JDK Optional.
final class OptionalFactories {

  /**
   * A field specification for a {@link Provider<Optional<T>>} that always returns {@code
   * Optional.absent()}.
   */
  private static final FieldSpec ABSENT_OPTIONAL_PROVIDER_FIELD =
      FieldSpec.builder(PROVIDER, "ABSENT_OPTIONAL_PROVIDER", PRIVATE, STATIC, FINAL)
          .addAnnotation(SUPPRESS_WARNINGS_RAWTYPES)
          .initializer("$T.create($T.absent())", InstanceFactory.class, Optional.class)
          .addJavadoc(
              "A {@link $T} that returns {@code $T.absent()}.", Provider.class, Optional.class)
          .build();

  /**
   * A method specification for a {@link Provider<Optional<T>>} that always returns {@code
   * Optional.absent()}.
   */
  private static final MethodSpec ABSENT_OPTIONAL_PROVIDER_METHOD =
      methodBuilder("absentOptionalProvider")
          .addModifiers(PRIVATE, STATIC)
          .addTypeVariable(TypeVariableName.get("T"))
          .returns(providerOf(optionalOf(TypeVariableName.get("T"))))
          .addJavadoc(
              "Returns a {@link $T} that returns {@code $T.absent()}.",
              Provider.class,
              Optional.class)
          .addCode("$L // safe covariant cast\n", SUPPRESS_WARNINGS_UNCHECKED)
          .addCode(
              "$1T provider = ($1T) $2N;",
              providerOf(optionalOf(TypeVariableName.get("T"))),
              ABSENT_OPTIONAL_PROVIDER_FIELD)
          .addCode("return provider;")
          .build();

  /**
   * The factory classes that implement present optional bindings for a given kind of dependency
   * request within the component.
   */
  private final Map<DependencyRequest.Kind, TypeSpec> presentFactoryClasses =
      new EnumMap<>(DependencyRequest.Kind.class);

  /**
   * If the component contains any absent optional bindings, this will be the member select for a
   * static method that returns a Provider<Optional<T>> that always returns {@link
   * Optional#absent()}.
   */
  private Optional<CodeBlock> absentOptionalProviderMethod = Optional.absent();

  /**
   * Returns an expression that calls a static method that returns a {@code Provider<Optional<T>>}
   * for absent optional bindings.
   */
  CodeBlock absentOptionalProvider() {
    if (!absentOptionalProviderMethod.isPresent()) {
      absentOptionalProviderMethod =
          Optional.of(CodeBlock.of("$N()", ABSENT_OPTIONAL_PROVIDER_METHOD));
    }
    return absentOptionalProviderMethod.get();
  }

  /**
   * Returns an expression for an instance of a nested class that implements {@code
   * Provider<Optional<T>>} for a present optional binding, where {@code T} represents dependency
   * requests of that kind.
   *
   * <ul>
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#INSTANCE}, the class
   *     implements {@code Provider<Optional<T>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#PROVIDER}, the class
   *     implements {@code Provider<Optional<Provider<T>>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#LAZY}, the class implements
   *     {@code Provider<Optional<Lazy<T>>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#PROVIDER_OF_LAZY}, the
   *     class implements {@code Provider<Optional<Provider<Lazy<T>>>>}.
   * </ul>
   *
   * <p>Production requests are not yet supported.
   *
   * @param delegateProvider an expression for a {@link Provider} of the underlying type
   */
  CodeBlock presentOptionalProvider(DependencyRequest.Kind valueKind, CodeBlock delegateProvider) {
    if (!presentFactoryClasses.containsKey(valueKind)) {
      presentFactoryClasses.put(valueKind, createPresentFactoryClass(valueKind));
    }
    return CodeBlock.of("$N.of($L)", presentFactoryClasses.get(valueKind), delegateProvider);
  }
  
  /**
   * Adds classes and methods required by previous calls to {@link #absentOptionalProvider()} and
   * {@link #presentOptionalProvider(DependencyRequest.Kind, CodeBlock)} to the top-level {@code
   * component}.
   */
  void addMembers(TypeSpec.Builder component) {
    if (absentOptionalProviderMethod.isPresent()) {
      component.addField(ABSENT_OPTIONAL_PROVIDER_FIELD).addMethod(ABSENT_OPTIONAL_PROVIDER_METHOD);
    }
    for (TypeSpec presentFactoryClass : presentFactoryClasses.values()) {
      component.addType(presentFactoryClass);
    }
  }

  private TypeSpec createPresentFactoryClass(DependencyRequest.Kind valueKind) {
    String factoryClassName =
        String.format(
            "PresentOptional%sFactory", UPPER_UNDERSCORE.to(UPPER_CAMEL, valueKind.toString()));

    TypeVariableName typeVariable = TypeVariableName.get("T");

    FieldSpec providerField =
        FieldSpec.builder(providerOf(typeVariable), "provider", PRIVATE, FINAL).build();

    ParameterSpec providerParameter =
        ParameterSpec.builder(providerOf(typeVariable), "provider").build();

    ParameterizedTypeName optionalType = optionalType(valueKind, typeVariable);
    return classBuilder(factoryClassName)
        .addTypeVariable(typeVariable)
        .addModifiers(PRIVATE, STATIC, FINAL)
        .addSuperinterface(providerOf(optionalType))
        .addJavadoc(
            "A {@link $T} that returns an {@code $T} using a {@code Provider<T>}.",
            Provider.class,
            optionalType)
        .addField(providerField)
        .addMethod(
            constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(providerParameter)
                .addCode(
                    "this.$N = $T.checkNotNull($N);",
                    FieldSpec.builder(providerOf(typeVariable), "provider", PRIVATE, FINAL).build(),
                    Preconditions.class,
                    providerParameter)
                .build())
        .addMethod(
            methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(optionalType)
                .addCode(
                    "return $T.of($L);",
                    Optional.class,
                    FrameworkType.PROVIDER.to(valueKind, CodeBlock.of("$N", providerField)))
                .build())
        .addMethod(
            methodBuilder("of")
                .addModifiers(PRIVATE, STATIC)
                .addTypeVariable(typeVariable)
                .returns(providerOf(optionalType))
                .addParameter(providerParameter)
                .addCode("return new $L<T>($N);", factoryClassName, providerParameter)
                .build())
        .build();
  }

  private ParameterizedTypeName optionalType(
      DependencyRequest.Kind optionalValueKind, TypeName valueType) {
    switch (optionalValueKind) {
      case INSTANCE:
        return optionalOf(valueType);

      case LAZY:
        return optionalOf(lazyOf(valueType));

      case PROVIDER:
        return optionalOf(providerOf(valueType));

      case PROVIDER_OF_LAZY:
        return optionalOf(providerOf(lazyOf(valueType)));

      default:
        throw new AssertionError(optionalValueKind);
    }
  }
}
