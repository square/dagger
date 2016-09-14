/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.squareup.javapoet.ClassName.OBJECT;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.TypeNames.ASYNC_FUNCTION;
import static dagger.internal.codegen.TypeNames.FUTURES;
import static dagger.internal.codegen.TypeNames.PRODUCERS;
import static dagger.internal.codegen.TypeNames.PRODUCER_TOKEN;
import static dagger.internal.codegen.TypeNames.VOID_CLASS;
import static dagger.internal.codegen.TypeNames.abstractProducerOf;
import static dagger.internal.codegen.TypeNames.listOf;
import static dagger.internal.codegen.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.TypeNames.producedOf;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.producers.Producer;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Generates {@link Producer} implementations from {@link ProductionBinding} instances.
 *
 * @author Jesse Beder
 * @since 2.0
 */
final class ProducerFactoryGenerator extends SourceFileGenerator<ProductionBinding> {
  private final CompilerOptions compilerOptions;

  ProducerFactoryGenerator(Filer filer, Elements elements, CompilerOptions compilerOptions) {
    super(filer, elements);
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(ProductionBinding binding) {
    return generatedClassNameForBinding(binding);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProductionBinding binding) {
    return binding.bindingElement();
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ProductionBinding binding) {
    checkArgument(binding.bindingElement().isPresent());

    TypeName providedTypeName = TypeName.get(binding.factoryType());
    TypeName futureTypeName = listenableFutureOf(providedTypeName);

    TypeSpec.Builder factoryBuilder =
        classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .superclass(abstractProducerOf(providedTypeName));

    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    ImmutableMap.Builder<BindingKey, FieldSpec> fieldsBuilder = ImmutableMap.builder();

    MethodSpec.Builder constructorBuilder = constructorBuilder().addModifiers(PUBLIC);

    Optional<FieldSpec> moduleField =
        binding.requiresModuleInstance()
            ? Optional.of(
                addFieldAndConstructorParameter(
                    factoryBuilder,
                    constructorBuilder,
                    uniqueFieldNames.getUniqueName("module"),
                    TypeName.get(binding.bindingTypeElement().get().asType())))
            : Optional.<FieldSpec>absent();

    for (Map.Entry<BindingKey, FrameworkField> entry :
        generateBindingFieldsForDependencies(binding).entrySet()) {
      BindingKey bindingKey = entry.getKey();
      FrameworkField bindingField = entry.getValue();
      FieldSpec field =
          addFieldAndConstructorParameter(
              factoryBuilder,
              constructorBuilder,
              uniqueFieldNames.getUniqueName(bindingField.name()),
              bindingField.type());
      fieldsBuilder.put(bindingKey, field);
    }
    ImmutableMap<BindingKey, FieldSpec> fields = fieldsBuilder.build();

    constructorBuilder.addStatement(
        "super($N, $L)",
        fields.get(binding.monitorRequest().get().bindingKey()),
        producerTokenConstruction(generatedTypeName, binding));

    if (binding.requiresModuleInstance()) {
      assignField(constructorBuilder, moduleField.get());
    }
    
    for (FieldSpec field : fields.values()) {
      assignField(constructorBuilder, field);
    }

    MethodSpec.Builder computeMethodBuilder =
        methodBuilder("compute")
            .returns(futureTypeName)
            .addAnnotation(Override.class)
            .addModifiers(PROTECTED);

    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    for (DependencyRequest dependency : asyncDependencies) {
      TypeName futureType = listenableFutureOf(asyncDependencyType(dependency));
      CodeBlock futureAccess = CodeBlock.of("$N.get()", fields.get(dependency.bindingKey()));
      computeMethodBuilder.addStatement(
          "$T $L = $L",
          futureType,
          dependencyFutureName(dependency),
          dependency.kind().equals(DependencyRequest.Kind.PRODUCED)
              ? CodeBlock.of("$T.createFutureProduced($L)", PRODUCERS, futureAccess)
              : futureAccess);
    }
    FutureTransform futureTransform = FutureTransform.create(fields, binding, asyncDependencies);

    computeMethodBuilder.addStatement(
        "return $T.transformAsync($L, this, executorProvider.get())",
        FUTURES,
        futureTransform.futureCodeBlock());

    factoryBuilder.addSuperinterface(
        ParameterizedTypeName.get(
            ASYNC_FUNCTION, futureTransform.applyArgType(), providedTypeName));

    MethodSpec.Builder applyMethodBuilder =
        methodBuilder("apply")
            .returns(futureTypeName)
            .addJavadoc("@deprecated this may only be called from the internal {@link #compute()}")
            .addAnnotation(Deprecated.class)
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(futureTransform.applyArgType(), futureTransform.applyArgName())
            .addExceptions(getThrownTypeNames(binding.thrownTypes()))
            .addStatement(
                "assert monitor != null : $S",
                "apply() may only be called internally from compute(); "
                    + "if it's called explicitly, the monitor might be null")
            .addCode(
                getInvocationCodeBlock(
                    generatedTypeName,
                    binding,
                    providedTypeName,
                    futureTransform.parameterCodeBlocks()));
    if (futureTransform.hasUncheckedCast()) {
      applyMethodBuilder.addAnnotation(SUPPRESS_WARNINGS_UNCHECKED);
    }

    factoryBuilder.addMethod(constructorBuilder.build());
    factoryBuilder.addMethod(computeMethodBuilder.build());
    factoryBuilder.addMethod(applyMethodBuilder.build());

    // TODO(gak): write a sensible toString
    return Optional.of(factoryBuilder);
  }

  // TODO(ronshapiro): consolidate versions of these
  private static FieldSpec addFieldAndConstructorParameter(
      TypeSpec.Builder typeBuilder,
      MethodSpec.Builder constructorBuilder,
      String variableName,
      TypeName variableType) {
    FieldSpec field = FieldSpec.builder(variableType, variableName, PRIVATE, FINAL).build();
    typeBuilder.addField(field);
    constructorBuilder.addParameter(field.type, field.name);
    return field;
  }

  private static void assignField(MethodSpec.Builder constructorBuilder, FieldSpec field) {
    constructorBuilder
        .addStatement("assert $N != null", field)
        .addStatement("this.$1N = $1N", field);
  }

  /** Returns a list of dependencies that are generated asynchronously. */
  private static ImmutableList<DependencyRequest> asyncDependencies(Binding binding) {
    final ImmutableMap<DependencyRequest, FrameworkDependency> frameworkDependencies =
        FrameworkDependency.indexByDependencyRequest(
            FrameworkDependency.frameworkDependenciesForBinding(binding));
    return FluentIterable.from(binding.implicitDependencies())
        .filter(
            dependency ->
                isAsyncDependency(dependency)
                    && frameworkDependencies
                        .get(dependency)
                        .frameworkClass()
                        .equals(Producer.class))
        .toList();
  }

  private CodeBlock producerTokenConstruction(
      ClassName generatedTypeName, ProductionBinding binding) {
    CodeBlock producerTokenArgs =
        compilerOptions.writeProducerNameInToken()
            ? CodeBlock.of(
                "$S",
                String.format(
                    "%s#%s",
                    ClassName.get(binding.bindingTypeElement().get()),
                    binding.bindingElement().get().getSimpleName()))
            : CodeBlock.of("$T.class", generatedTypeName);
    return CodeBlock.of("$T.create($L)", PRODUCER_TOKEN, producerTokenArgs);
  }

  /** Returns a name of the variable representing this dependency's future. */
  private static String dependencyFutureName(DependencyRequest dependency) {
    return dependency.requestElement().get().getSimpleName() + "Future";
  }

  /** Represents the transformation of an input future by a producer method. */
  abstract static class FutureTransform {
    protected final ImmutableMap<BindingKey, FieldSpec> fields;
    protected final ProductionBinding binding;

    FutureTransform(ImmutableMap<BindingKey, FieldSpec> fields, ProductionBinding binding) {
      this.fields = fields;
      this.binding = binding;
    }

    /** The code block representing the future that should be transformed. */
    abstract CodeBlock futureCodeBlock();

    /** The type of the argument to the apply method. */
    abstract TypeName applyArgType();

    /** The name of the argument to the apply method */
    abstract String applyArgName();

    /** The code blocks to be passed to the produces method itself. */
    abstract ImmutableList<CodeBlock> parameterCodeBlocks();

    /** Whether the transform method has an unchecked cast. */
    boolean hasUncheckedCast() {
      return false;
    }

    static FutureTransform create(
        ImmutableMap<BindingKey, FieldSpec> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      if (asyncDependencies.isEmpty()) {
        return new NoArgFutureTransform(fields, binding);
      } else if (asyncDependencies.size() == 1) {
        return new SingleArgFutureTransform(
            fields, binding, Iterables.getOnlyElement(asyncDependencies));
      } else {
        return new MultiArgFutureTransform(fields, binding, asyncDependencies);
      }
    }
  }

  static final class NoArgFutureTransform extends FutureTransform {
    NoArgFutureTransform(ImmutableMap<BindingKey, FieldSpec> fields, ProductionBinding binding) {
      super(fields, binding);
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlock.of("$T.<$T>immediateFuture(null)", FUTURES, VOID_CLASS);
    }

    @Override
    TypeName applyArgType() {
      return VOID_CLASS;
    }

    @Override
    String applyArgName() {
      return "ignoredVoidArg";
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      ImmutableList.Builder<CodeBlock> parameterCodeBlocks = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        parameterCodeBlocks.add(
            frameworkTypeUsageStatement(
                CodeBlock.of("$N", fields.get(dependency.bindingKey())), dependency.kind()));
      }
      return parameterCodeBlocks.build();
    }
  }

  static final class SingleArgFutureTransform extends FutureTransform {
    private final DependencyRequest asyncDependency;

    SingleArgFutureTransform(
        ImmutableMap<BindingKey, FieldSpec> fields,
        ProductionBinding binding,
        DependencyRequest asyncDependency) {
      super(fields, binding);
      this.asyncDependency = asyncDependency;
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlock.of("$L", dependencyFutureName(asyncDependency));
    }

    @Override
    TypeName applyArgType() {
      return asyncDependencyType(asyncDependency);
    }

    @Override
    String applyArgName() {
      return asyncDependency.requestElement().get().getSimpleName().toString();
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      ImmutableList.Builder<CodeBlock> parameterCodeBlocks = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        // We really want to compare instances here, because asyncDependency is an element in the
        // set binding.dependencies().
        if (dependency == asyncDependency) {
          parameterCodeBlocks.add(CodeBlock.of("$L", applyArgName()));
        } else {
          parameterCodeBlocks.add(
              // TODO(ronshapiro) extract this into a method shared by FutureTransform subclasses
              frameworkTypeUsageStatement(
                  CodeBlock.of("$N", fields.get(dependency.bindingKey())), dependency.kind()));
        }
      }
      return parameterCodeBlocks.build();
    }
  }

  static final class MultiArgFutureTransform extends FutureTransform {
    private final ImmutableList<DependencyRequest> asyncDependencies;

    MultiArgFutureTransform(
        ImmutableMap<BindingKey, FieldSpec> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      super(fields, binding);
      this.asyncDependencies = asyncDependencies;
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlock.of(
          "$T.<$T>allAsList($L)",
          FUTURES,
          OBJECT,
          asyncDependencies
              .stream()
              .map(ProducerFactoryGenerator::dependencyFutureName)
              .collect(joining(", ")));
    }

    @Override
    TypeName applyArgType() {
      return listOf(OBJECT);
    }

    @Override
    String applyArgName() {
      return "args";
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      return getParameterCodeBlocks(binding, fields, applyArgName());
    }

    @Override
    boolean hasUncheckedCast() {
      return true;
    }
  }

  private static boolean isAsyncDependency(DependencyRequest dependency) {
    switch (dependency.kind()) {
      case INSTANCE:
      case PRODUCED:
        return true;
      default:
        return false;
    }
  }

  private static TypeName asyncDependencyType(DependencyRequest dependency) {
    TypeName keyName = TypeName.get(dependency.key().type());
    switch (dependency.kind()) {
      case INSTANCE:
        return keyName;
      case PRODUCED:
        return producedOf(keyName);
      default:
        throw new AssertionError();
    }
  }

  private static ImmutableList<CodeBlock> getParameterCodeBlocks(
      ProductionBinding binding, ImmutableMap<BindingKey, FieldSpec> fields, String listArgName) {
    int argIndex = 0;
    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    for (DependencyRequest dependency : binding.dependencies()) {
      if (isAsyncDependency(dependency)) {
        codeBlocks.add(
            CodeBlock.of(
                "($T) $L.get($L)", asyncDependencyType(dependency), listArgName, argIndex));
        argIndex++;
      } else {
        codeBlocks.add(
            frameworkTypeUsageStatement(
                CodeBlock.of("$N", fields.get(dependency.bindingKey())), dependency.kind()));
      }
    }
    return codeBlocks.build();
  }

  /**
   * Creates a code block for the invocation of the producer method from the module, which should be
   * used entirely within a method body.
   *
   * @param binding The binding to generate the invocation code block for.
   * @param providedTypeName The type name that should be provided by this producer.
   * @param parameterCodeBlocks The code blocks for all the parameters to the producer method.
   */
  private CodeBlock getInvocationCodeBlock(
      ClassName generatedTypeName,
      ProductionBinding binding,
      TypeName providedTypeName,
      ImmutableList<CodeBlock> parameterCodeBlocks) {
    CodeBlock moduleCodeBlock =
        CodeBlock.of(
            "$L.$L($L)",
            binding.requiresModuleInstance()
                ? CodeBlock.of("$T.this.module", generatedTypeName)
                : CodeBlock.of("$T", ClassName.get(binding.bindingTypeElement().get())),
            binding.bindingElement().get().getSimpleName(),
            makeParametersCodeBlock(parameterCodeBlocks));

    // NOTE(beder): We don't worry about catching exceptions from the monitor methods themselves
    // because we'll wrap all monitoring in non-throwing monitors before we pass them to the
    // factories.
    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    codeBlocks.add(CodeBlock.of("monitor.methodStarting();"));

    CodeBlock returnCodeBlock =
        binding.bindingKind().equals(ContributionBinding.Kind.FUTURE_PRODUCTION)
            ? moduleCodeBlock
            : CodeBlock.of(
                "$T.<$T>immediateFuture($L)", FUTURES, providedTypeName, moduleCodeBlock);
    return CodeBlock.of(
        Joiner.on('\n')
            .join(
                "monitor.methodStarting();",
                "try {",
                "  return $L;",
                "} finally {",
                "  monitor.methodFinished();",
                "}"),
        returnCodeBlock);
  }

  /**
   * Converts the list of thrown types into type names.
   *
   * @param thrownTypes the list of thrown types.
   */
  private FluentIterable<? extends TypeName> getThrownTypeNames(
      Iterable<? extends TypeMirror> thrownTypes) {
    return FluentIterable.from(thrownTypes).transform(TypeName::get);
  }
}
