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
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.Preconditions;
import javax.inject.Provider;

/** Factory class specifications for optional bindings. */
// TODO(dpb): Name classes correctly if a component uses both Guava and JDK Optional.
final class OptionalFactoryClasses {

  /**
   * The class specification for a {@link Provider<Optional<T>>} that always returns {@code
   * Optional.absent()}.
   */
  static final TypeSpec ABSENT_FACTORY_CLASS = absentFactoryClass();

  private static TypeSpec absentFactoryClass() {
    String className = "AbsentOptionalFactory";
    TypeVariableName typeVariable = TypeVariableName.get("T");
    
    return classBuilder(className)
        .addTypeVariable(typeVariable)
        .addModifiers(PRIVATE, STATIC, FINAL)
        .addSuperinterface(providerOf(optionalOf(typeVariable)))
        .addJavadoc("A {@link $T} that returns {$T.absent()}.", Provider.class, Optional.class)
        .addField(FieldSpec.builder(Provider.class, "INSTANCE", PRIVATE, STATIC, FINAL)
            .addAnnotation(SUPPRESS_WARNINGS_RAWTYPES)
            .initializer("new $L()", className)
            .build())
        .addMethod(
            methodBuilder("instance")
                .addModifiers(PRIVATE, STATIC)
                .addTypeVariable(typeVariable)
                .returns(providerOf(optionalOf(typeVariable)))
                .addCode("$L // safe covariant cast\n", SUPPRESS_WARNINGS_UNCHECKED)
                .addCode("$1T provider = ($1T) INSTANCE;", providerOf(optionalOf(typeVariable)))
                .addCode("return provider;")
                .build())
        .addMethod(
            methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(optionalOf(typeVariable))
                .addCode("return $T.absent();", Optional.class)
                .build())
        .build();
  }

  /**
   * Returns the class specification for a {@link Provider} that returns a present value. The class
   * is generic in {@code T}.
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
   */
  static TypeSpec presentFactoryClass(DependencyRequest.Kind optionalValueKind) {
    String factoryClassName =
        String.format(
            "PresentOptional%sFactory",
            UPPER_UNDERSCORE.to(UPPER_CAMEL, optionalValueKind.toString()));
    
    TypeVariableName typeVariable = TypeVariableName.get("T");
    
    FieldSpec providerField =
        FieldSpec.builder(providerOf(typeVariable), "provider", PRIVATE, FINAL).build();
    
    ParameterSpec providerParameter =
        ParameterSpec.builder(providerOf(typeVariable), "provider").build();
    
    ParameterizedTypeName optionalType = optionalType(optionalValueKind, typeVariable);
    
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
                    FrameworkType.PROVIDER.to(optionalValueKind, CodeBlock.of("$N", providerField)))
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

  private static ParameterizedTypeName optionalType(
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
