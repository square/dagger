/*
 * Copyright (C) 2015 Google, Inc.
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
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.MembersInjector;
import dagger.internal.DelegateFactory;
import dagger.internal.Factory;
import dagger.internal.InstanceFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.MembersInjectors;
import dagger.internal.ScopedProvider;
import dagger.internal.SetFactory;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ComponentGenerator.MemberSelect;
import dagger.internal.codegen.ComponentGenerator.ProxyClassAndField;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.StringLiteral;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.internal.codegen.writer.TypeWriter;
import dagger.internal.codegen.writer.VoidName;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import dagger.producers.internal.SetProducer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Generated;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.DELEGATED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.INITIALIZED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.Binding.bindingPackageFor;
import static dagger.internal.codegen.ComponentGenerator.MemberSelect.staticMethodInvocationWithCast;
import static dagger.internal.codegen.ComponentGenerator.MemberSelect.staticSelect;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.MapKeys.getMapKeySnippet;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static dagger.internal.codegen.ProvisionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProductionBinding;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.indexDependenciesByUnresolvedKey;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.getKeyTypeOfMap;
import static dagger.internal.codegen.Util.getProvidedValueTypeOfMap;
import static dagger.internal.codegen.Util.isMapWithNonProvidedValues;
import static dagger.internal.codegen.writer.Snippet.memberSelectSnippet;
import static dagger.internal.codegen.writer.Snippet.nullCheck;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Creates the implementation class for a component or subcomponent.
 */
abstract class AbstractComponentWriter {
  // TODO(dpb): Make all these fields private after refactoring is complete.
  protected final Elements elements;
  protected final Types types;
  protected final Key.Factory keyFactory;
  protected final Kind nullableValidationType;
  protected final Set<JavaWriter> javaWriters = new LinkedHashSet<>();
  protected final ClassName name;
  protected final BindingGraph graph;
  private final Map<String, ProxyClassAndField> packageProxies = new HashMap<>();
  private final Map<BindingKey, InitializationState> initializationStates = new HashMap<>();
  protected ClassWriter componentWriter;
  private ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets;
  private ImmutableMap<ContributionBinding, MemberSelect> multibindingContributionSnippets;
  private ImmutableSet<BindingKey> enumBindingKeys;
  protected ConstructorWriter constructorWriter;
  protected Optional<ClassName> builderName = Optional.absent();

  /**
   * For each component requirement, the builder field. This map is empty for subcomponents that do
   * not use a builder.
   */
  private ImmutableMap<TypeElement, FieldWriter> builderFields = ImmutableMap.of();

  /**
   * For each component requirement, the snippet for the component field that holds it.
   *
   * <p>Fields are written for all requirements for subcomponents that do not use a builder, and for
   * any requirement that is reused from a subcomponent of this component.
   */
  protected final Map<TypeElement, MemberSelect> componentContributionFields = Maps.newHashMap();

  AbstractComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      Diagnostic.Kind nullableValidationType,
      ClassName name,
      BindingGraph graph) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.nullableValidationType = nullableValidationType;
    this.name = name;
    this.graph = graph;
  }

  protected final TypeElement componentDefinitionType() {
    return graph.componentDescriptor().componentDefinitionType();
  }

  protected final ClassName componentDefinitionTypeName() {
    return ClassName.fromTypeElement(componentDefinitionType());
  }

  /**
   * Returns an expression snippet that evaluates to an instance of the contribution, looking for
   * either a builder field or a component field.
   */
  private Snippet getComponentContributionSnippet(TypeElement contributionType) {
    if (builderFields.containsKey(contributionType)) {
      return Snippet.format("builder.%s", builderFields.get(contributionType).name());
    } else {
      Optional<Snippet> snippet = getOrCreateComponentContributionFieldSnippet(contributionType);
      checkState(snippet.isPresent(), "no builder or component field for %s", contributionType);
      return snippet.get();
    }
  }

  /**
   * Returns a snippet for a component contribution field. Adds a field the first time one is
   * requested for a contribution type if this component's builder has a field for it.
   */
  protected Optional<Snippet> getOrCreateComponentContributionFieldSnippet(
      TypeElement contributionType) {
    MemberSelect fieldSelect = componentContributionFields.get(contributionType);
    if (fieldSelect == null) {
      if (!builderFields.containsKey(contributionType)) {
        return Optional.absent();
      }
      FieldWriter componentField =
          componentWriter.addField(contributionType, simpleVariableName(contributionType));
      componentField.addModifiers(PRIVATE, FINAL);
      constructorWriter
          .body()
          .addSnippet(
              "this.%s = builder.%s;",
              componentField.name(),
              builderFields.get(contributionType).name());
      fieldSelect = MemberSelect.instanceSelect(name, Snippet.format("%s", componentField.name()));
      componentContributionFields.put(contributionType, fieldSelect);
    }
    return Optional.of(fieldSelect.getSnippetFor(name));
  }

  private Snippet getMemberSelectSnippet(BindingKey key) {
    return getMemberSelect(key).getSnippetFor(name);
  }

  protected MemberSelect getMemberSelect(BindingKey key) {
    return memberSelectSnippets.get(key);
  }

  protected Optional<MemberSelect> getMultibindingContributionSnippet(ContributionBinding binding) {
    return Optional.fromNullable(multibindingContributionSnippets.get(binding));
  }

  /**
   * Returns the initialization state of the factory field for a binding key in this component.
   */
  protected InitializationState getInitializationState(BindingKey bindingKey) {
    return initializationStates.containsKey(bindingKey)
        ? initializationStates.get(bindingKey)
        : UNINITIALIZED;
  }

  private void setInitializationState(BindingKey bindingKey, InitializationState state) {
    initializationStates.put(bindingKey, state);
  }

  ImmutableSet<JavaWriter> write() {
    if (javaWriters.isEmpty()) {
      writeComponent();
    }
    return ImmutableSet.copyOf(javaWriters);
  }

  private void writeComponent() {
    componentWriter = createComponentClass();
    addConstructor();
    addBuilder();
    addFactoryMethods();
    addFields();
    initializeFrameworkTypes();
    implementInterfaceMethods();
    addSubcomponents();
  }

  /**
   * Creates the component implementation class.
   */
  protected abstract ClassWriter createComponentClass();

  private void addConstructor() {
    constructorWriter = componentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);
  }

  /**
   * Adds a builder type.
   */
  protected void addBuilder() {
    ClassWriter builderWriter = createBuilder();
    builderWriter.addModifiers(FINAL);
    builderWriter.addConstructor().addModifiers(PRIVATE);
    builderName = Optional.of(builderWriter.name());

    Optional<BuilderSpec> builderSpec = graph.componentDescriptor().builderSpec();
    if (builderSpec.isPresent()) {
      builderWriter.addModifiers(PRIVATE);
      builderWriter.setSupertype(builderSpec.get().builderDefinitionType());
    } else {
      builderWriter.addModifiers(PUBLIC);
    }

    builderFields = addBuilderFields(builderWriter);
    addBuildMethod(builderWriter, builderSpec);
    addBuilderMethods(builderWriter, builderSpec);

    constructorWriter.addParameter(builderWriter, "builder");
    constructorWriter.body().addSnippet("assert builder != null;");
  }

  /**
   * Adds fields for each of the {@linkplain BindingGraph#componentRequirements component
   * requirements}. Regardless of builder spec, there is always one field per requirement.
   */
  private ImmutableMap<TypeElement, FieldWriter> addBuilderFields(ClassWriter builderWriter) {
    ImmutableMap.Builder<TypeElement, FieldWriter> builderFieldsBuilder = ImmutableMap.builder();
    for (TypeElement contributionElement : graph.componentRequirements()) {
      String contributionName = simpleVariableName(contributionElement);
      FieldWriter builderField = builderWriter.addField(contributionElement, contributionName);
      builderField.addModifiers(PRIVATE);
      builderFieldsBuilder.put(contributionElement, builderField);
    }
    return builderFieldsBuilder.build();
  }

  /** Adds the build method to the builder. */
  private void addBuildMethod(ClassWriter builderWriter, Optional<BuilderSpec> builderSpec) {
    MethodWriter buildMethod;
    if (builderSpec.isPresent()) {
      ExecutableElement specBuildMethod = builderSpec.get().buildMethod();
      // Note: we don't use the specBuildMethod.getReturnType() as the return type
      // because it might be a type variable.  We make use of covariant returns to allow
      // us to return the component type, which will always be valid.
      buildMethod =
          builderWriter.addMethod(
              componentDefinitionTypeName(), specBuildMethod.getSimpleName().toString());
      buildMethod.annotate(Override.class);
    } else {
      buildMethod = builderWriter.addMethod(componentDefinitionTypeName(), "build");
    }
    buildMethod.addModifiers(PUBLIC);

    for (Map.Entry<TypeElement, FieldWriter> builderFieldEntry : builderFields.entrySet()) {
      FieldWriter builderField = builderFieldEntry.getValue();
      if (componentCanMakeNewInstances(builderFieldEntry.getKey())) {
        buildMethod.body()
            .addSnippet("if (%1$s == null) { this.%1$s = new %2$s(); }",
                builderField.name(),
                builderField.type());
      } else {
        buildMethod.body()
            .addSnippet(
                "if (%s == null) { throw new %s(%s.class.getCanonicalName() + \" must be set\"); }",
                builderField.name(),
                ClassName.fromClass(IllegalStateException.class),
                builderField.type());
      }
    }

    buildMethod.body().addSnippet("return new %s(this);", name);
  }

  /**
   * Adds the methods that set each of parameters on the builder. If the {@link BuilderSpec} is
   * present, it will tailor the methods to match the spec.
   */
  private void addBuilderMethods(
      ClassWriter builderWriter,
      Optional<BuilderSpec> builderSpec) {
    if (builderSpec.isPresent()) {
      for (Map.Entry<TypeElement, ExecutableElement> builderMethodEntry :
          builderSpec.get().methodMap().entrySet()) {
        TypeElement builderMethodType = builderMethodEntry.getKey();
        ExecutableElement specMethod = builderMethodEntry.getValue();
        MethodWriter builderMethod = addBuilderMethodFromSpec(builderWriter, specMethod);
        String parameterName =
            Iterables.getOnlyElement(specMethod.getParameters()).getSimpleName().toString();
        builderMethod.addParameter(builderMethodType, parameterName);
        builderMethod.body().addSnippet(nullCheck(parameterName));
        if (graph.componentRequirements().contains(builderMethodType)) {
          // required type
          builderMethod.body().addSnippet("this.%s = %s;",
              builderFields.get(builderMethodType).name(),
              parameterName);
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else if (graph.ownedModuleTypes().contains(builderMethodType)) {
          // owned, but not required
          builderMethod.body()
              .addSnippet("// This module is declared, but not used in the component. "
                  + "This method is a no-op");
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else {
          // neither owned nor required, so it must be an inherited module
          builderMethod
              .body()
              .addSnippet(
                  "throw new %s(%s.format(%s, %s.class.getCanonicalName()));",
                  ClassName.fromClass(UnsupportedOperationException.class),
                  ClassName.fromClass(String.class),
                  StringLiteral.forValue(
                      "%s cannot be set because it is inherited from the enclosing component"),
                  ClassName.fromTypeElement(builderMethodType));
        }
      }
    } else {
      for (TypeElement componentRequirement : graph.availableDependencies()) {
        String componentRequirementName = simpleVariableName(componentRequirement);
        MethodWriter builderMethod = builderWriter.addMethod(
            builderWriter.name(),
            componentRequirementName);
        builderMethod.addModifiers(PUBLIC);
        builderMethod.addParameter(componentRequirement, componentRequirementName);
        builderMethod.body().addSnippet(nullCheck(componentRequirementName));
        if (graph.componentRequirements().contains(componentRequirement)) {
          builderMethod.body()
              .addSnippet("this.%s = %s;",
                  builderFields.get(componentRequirement).name(),
                  componentRequirementName);
        } else {
          builderMethod.annotate(Deprecated.class);
        }
        builderMethod.body().addSnippet("return this;");
      }
    }
  }

  private void addBuilderMethodReturnStatementForSpec(
      ExecutableElement specMethod, MethodWriter builderMethod) {
    if (!specMethod.getReturnType().getKind().equals(VOID)) {
      builderMethod.body().addSnippet("return this;");
    }
  }

  private MethodWriter addBuilderMethodFromSpec(
      ClassWriter builderWriter, ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    TypeMirror returnType = method.getReturnType();
    // If the return type is void, we add a method with the void return type.
    // Otherwise we use the builderWriter and take advantage of covariant returns
    // (so that we don't have to worry about setter methods that return type variables).
    MethodWriter builderMethod =
        returnType.getKind().equals(TypeKind.VOID)
            ? builderWriter.addMethod(returnType, methodName)
            : builderWriter.addMethod(builderWriter, methodName);
    builderMethod.annotate(Override.class);
    builderMethod.addModifiers(Sets.difference(method.getModifiers(), ImmutableSet.of(ABSTRACT)));
    return builderMethod;
  }

  /**
   * Creates the builder class.
   */
  protected abstract ClassWriter createBuilder();

  /**
   * Adds component factory methods.
   */
  protected abstract void addFactoryMethods();

  private void addFields() {
    Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder = Maps.newHashMap();
    Map<ContributionBinding, MemberSelect> multibindingContributionSnippetsBuilder =
        Maps.newHashMap();
    ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder = ImmutableSet.builder();

    for (ResolvedBindings resolvedBindings : graph.resolvedBindings().values()) {
      addField(
          memberSelectSnippetsBuilder,
          multibindingContributionSnippetsBuilder,
          enumBindingKeysBuilder,
          resolvedBindings);
    }

    memberSelectSnippets = ImmutableMap.copyOf(memberSelectSnippetsBuilder);
    multibindingContributionSnippets = ImmutableMap.copyOf(multibindingContributionSnippetsBuilder);
    enumBindingKeys = enumBindingKeysBuilder.build();
  }

  private void addField(
      Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder,
      Map<ContributionBinding, MemberSelect> multibindingContributionSnippetsBuilder,
      ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder,
      ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();

    // No field needed for unique contributions inherited from the parent.
    if (resolvedBindings.isUniqueContribution() && resolvedBindings.ownedBindings().isEmpty()) {
      return;
    }

    // No field needed for bindings with no dependencies or state.
    Optional<MemberSelect> staticMemberSelect = staticMemberSelect(resolvedBindings);
    if (staticMemberSelect.isPresent()) {
      // TODO(gak): refactor to use enumBindingKeys throughout the generator
      enumBindingKeysBuilder.add(bindingKey);
      memberSelectSnippetsBuilder.put(bindingKey, staticMemberSelect.get());
      return;
    }

    String bindingPackage = bindingPackageFor(resolvedBindings.bindings()).or(name.packageName());

    final Optional<String> proxySelector;
    final TypeWriter classWithFields;
    final Set<Modifier> fieldModifiers;

    if (bindingPackage.equals(name.packageName())) {
      // no proxy
      proxySelector = Optional.absent();
      // component gets the fields
      classWithFields = componentWriter;
      // private fields
      fieldModifiers = EnumSet.of(PRIVATE);
    } else {
      // get or create the proxy
      ProxyClassAndField proxyClassAndField = packageProxies.get(bindingPackage);
      if (proxyClassAndField == null) {
        JavaWriter proxyJavaWriter = JavaWriter.inPackage(bindingPackage);
        javaWriters.add(proxyJavaWriter);
        ClassWriter proxyWriter = proxyJavaWriter.addClass(name.simpleName() + "_PackageProxy");
        proxyWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getCanonicalName());
        proxyWriter.addModifiers(PUBLIC, FINAL);
        // create the field for the proxy in the component
        FieldWriter proxyFieldWriter =
            componentWriter.addField(
                proxyWriter.name(), bindingPackage.replace('.', '_') + "_Proxy");
        proxyFieldWriter.addModifiers(PRIVATE, FINAL);
        proxyFieldWriter.setInitializer("new %s()", proxyWriter.name());
        proxyClassAndField = ProxyClassAndField.create(proxyWriter, proxyFieldWriter);
        packageProxies.put(bindingPackage, proxyClassAndField);
      }
      // add the field for the member select
      proxySelector = Optional.of(proxyClassAndField.proxyFieldWriter().name());
      // proxy gets the fields
      classWithFields = proxyClassAndField.proxyWriter();
      // public fields in the proxy
      fieldModifiers = EnumSet.of(PUBLIC);
    }

    if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)) {
      ImmutableSet<? extends ContributionBinding> contributionBindings =
          resolvedBindings.contributionBindings();
      if (ContributionBinding.bindingTypeFor(contributionBindings).isMultibinding()) {
        // note that here we rely on the order of the resolved bindings being from parent to child
        // otherwise, the numbering wouldn't work
        int contributionNumber = 0;
        for (ContributionBinding contributionBinding : contributionBindings) {
          if (!contributionBinding.isSyntheticBinding()) {
            contributionNumber++;
            if (resolvedBindings.ownedBindings().contains(contributionBinding)) {
              FrameworkField contributionBindingField =
                  FrameworkField.createForSyntheticContributionBinding(
                      bindingKey, contributionNumber, contributionBinding);
              FieldWriter contributionField =
                  classWithFields.addField(
                      contributionBindingField.frameworkType(), contributionBindingField.name());
              contributionField.addModifiers(fieldModifiers);

              ImmutableList<String> contributionSelectTokens =
                  new ImmutableList.Builder<String>()
                      .addAll(proxySelector.asSet())
                      .add(contributionField.name())
                      .build();
              multibindingContributionSnippetsBuilder.put(
                  contributionBinding,
                  MemberSelect.instanceSelect(name, memberSelectSnippet(contributionSelectTokens)));
            }
          }
        }
      }
    }

    FrameworkField bindingField = FrameworkField.createForResolvedBindings(resolvedBindings);
    FieldWriter frameworkField =
        classWithFields.addField(bindingField.frameworkType(), bindingField.name());
    frameworkField.addModifiers(fieldModifiers);

    ImmutableList<String> memberSelectTokens =
        new ImmutableList.Builder<String>()
            .addAll(proxySelector.asSet())
            .add(frameworkField.name())
            .build();
    memberSelectSnippetsBuilder.put(
        bindingKey,
        MemberSelect.instanceSelect(name, Snippet.memberSelectSnippet(memberSelectTokens)));
  }

  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments or a
   * no-op members injection binding, then we do't need a field to hold its factory. In that case,
   * this method returns the static member select snippet that returns the factory or no-op members
   * injector.
   */
  private Optional<MemberSelect> staticMemberSelect(ResolvedBindings resolvedBindings) {
    if (resolvedBindings.bindings().size() != 1) {
      return Optional.absent();
    }
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        ContributionBinding contributionBinding =
            getOnlyElement(resolvedBindings.contributionBindings());
        if (contributionBinding.bindingType().isMultibinding()
            || !(contributionBinding instanceof ProvisionBinding)) {
          return Optional.absent();
        }
        ProvisionBinding provisionBinding = (ProvisionBinding) contributionBinding;
        if (provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
            && !provisionBinding.scope().isPresent()) {
          return Optional.of(
              staticSelect(
                  factoryNameForProvisionBinding(provisionBinding), Snippet.format("create()")));
        }
        break;

      case MEMBERS_INJECTION:
        if (getOnlyElement(resolvedBindings.membersInjectionBindings())
            .injectionStrategy()
            .equals(NO_OP)) {
          return Optional.of(
              staticMethodInvocationWithCast(
                  ClassName.fromClass(MembersInjectors.class),
                  Snippet.format("noOp()"),
                  ClassName.fromClass(MembersInjector.class)));
        }
        break;

      default:
        throw new AssertionError();
    }
    return Optional.absent();
  }

  private void implementInterfaceMethods() {
    Set<MethodSignature> interfaceMethods = Sets.newHashSet();
    for (ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        DependencyRequest interfaceRequest = componentMethod.dependencyRequest().get();
        ExecutableElement requestElement =
            MoreElements.asExecutable(interfaceRequest.requestElement());
        ExecutableType requestType = MoreTypes.asExecutable(types.asMemberOf(
            MoreTypes.asDeclared(componentDefinitionType().asType()), requestElement));
        MethodSignature signature = MethodSignature.fromExecutableType(
            requestElement.getSimpleName().toString(), requestType);
        if (!interfaceMethods.contains(signature)) {
          interfaceMethods.add(signature);
          MethodWriter interfaceMethod =
              requestType.getReturnType().getKind().equals(VOID)
                  ? componentWriter.addMethod(
                      VoidName.VOID, requestElement.getSimpleName().toString())
                  : componentWriter.addMethod(
                      requestType.getReturnType(), requestElement.getSimpleName().toString());
          interfaceMethod.annotate(Override.class);
          interfaceMethod.addModifiers(PUBLIC);
          BindingKey bindingKey = interfaceRequest.bindingKey();
          switch (interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              Snippet membersInjectorSelect = getMemberSelectSnippet(bindingKey);
              List<? extends VariableElement> parameters = requestElement.getParameters();
              if (parameters.isEmpty()) {
                // we're returning the framework type
                interfaceMethod.body().addSnippet("return %s;", membersInjectorSelect);
              } else {
                VariableElement parameter = Iterables.getOnlyElement(parameters);
                Name parameterName = parameter.getSimpleName();
                interfaceMethod.addParameter(
                    TypeNames.forTypeMirror(
                        Iterables.getOnlyElement(requestType.getParameterTypes())),
                    parameterName.toString());
                interfaceMethod
                    .body()
                    .addSnippet(
                        "%s.injectMembers(%s);",
                        // In this case we know we won't need the cast because we're never going to
                        // pass the reference to anything.
                        membersInjectorSelect,
                        parameterName);
                if (!requestType.getReturnType().getKind().equals(VOID)) {
                  interfaceMethod.body().addSnippet("return %s;", parameterName);
                }
              }
              break;
            case INSTANCE:
              if (enumBindingKeys.contains(bindingKey)
                  && bindingKey.key().type().getKind().equals(DECLARED)
                  && !((DeclaredType) bindingKey.key().type()).getTypeArguments().isEmpty()) {
                // If using a parameterized enum type, then we need to store the factory
                // in a temporary variable, in order to help javac be able to infer
                // the generics of the Factory.create methods.
                TypeName factoryType =
                    ParameterizedTypeName.create(
                        Provider.class, TypeNames.forTypeMirror(requestType.getReturnType()));
                interfaceMethod
                    .body()
                    .addSnippet(
                        "%s factory = %s;", factoryType, getMemberSelectSnippet(bindingKey));
                interfaceMethod.body().addSnippet("return factory.get();");
                break;
              }
              // fall through in the else case.
            case LAZY:
            case PRODUCED:
            case PRODUCER:
            case PROVIDER:
            case FUTURE:
              interfaceMethod
                  .body()
                  .addSnippet(
                      "return %s;",
                      frameworkTypeUsageStatement(
                          getMemberSelectSnippet(bindingKey), interfaceRequest.kind()));
              break;
            default:
              throw new AssertionError();
          }
        }
      }
    }
  }

  private void addSubcomponents() {
    for (Map.Entry<ExecutableElement, BindingGraph> subgraphEntry : graph.subgraphs().entrySet()) {
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, subgraphEntry.getKey(), subgraphEntry.getValue());
      javaWriters.addAll(subcomponent.write());
    }
  }

  private void initializeFrameworkTypes() {
    List<List<BindingKey>> partitions =
        Lists.partition(graph.resolvedBindings().keySet().asList(), 100);
    for (int i = 0; i < partitions.size(); i++) {
      MethodWriter initializeMethod =
          componentWriter.addMethod(VoidName.VOID, "initialize" + ((i == 0) ? "" : i));
      initializeMethod.body();
      initializeMethod.addModifiers(PRIVATE);
      if (builderName.isPresent()) {
        initializeMethod.addParameter(builderName.get(), "builder").addModifiers(FINAL);
        constructorWriter.body().addSnippet("%s(builder);", initializeMethod.name());
      } else {
        constructorWriter.body().addSnippet("%s();", initializeMethod.name());
      }
      for (BindingKey bindingKey : partitions.get(i)) {
        ResolvedBindings resolvedBindings = graph.resolvedBindings().get(bindingKey);
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            ImmutableSet<? extends ContributionBinding> bindings =
                resolvedBindings.contributionBindings();

            switch (ContributionBinding.bindingTypeFor(bindings)) {
              case SET:
                boolean hasOnlyProvisions =
                    Iterables.all(bindings, Predicates.instanceOf(ProvisionBinding.class));
                ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
                for (ContributionBinding binding : bindings) {
                  Optional<MemberSelect> multibindingContributionSnippet =
                      getMultibindingContributionSnippet(binding);
                  checkState(
                      multibindingContributionSnippet.isPresent(), "%s was not found", binding);
                  Snippet snippet = multibindingContributionSnippet.get().getSnippetFor(name);
                  if (multibindingContributionSnippet.get().owningClass().equals(name)) {
                    Snippet initializeSnippet = initializeFactoryForContributionBinding(binding);
                    initializeMethod.body().addSnippet("this.%s = %s;", snippet, initializeSnippet);
                  }
                  parameterSnippets.add(snippet);
                }
                Snippet initializeSetSnippet =
                    Snippet.format(
                        "%s.create(%s)",
                        hasOnlyProvisions
                            ? ClassName.fromClass(SetFactory.class)
                            : ClassName.fromClass(SetProducer.class),
                        Snippet.makeParametersSnippet(parameterSnippets.build()));
                initializeMember(initializeMethod, bindingKey, initializeSetSnippet);
                break;
              case MAP:
                if (Sets.filter(bindings, Predicates.instanceOf(ProductionBinding.class))
                    .isEmpty()) {
                  @SuppressWarnings("unchecked") // checked by the instanceof filter above
                  ImmutableSet<ProvisionBinding> provisionBindings =
                      (ImmutableSet<ProvisionBinding>) bindings;
                  for (ProvisionBinding provisionBinding : provisionBindings) {
                    Optional<MemberSelect> multibindingContributionSnippet =
                        getMultibindingContributionSnippet(provisionBinding);
                    if (!isMapWithNonProvidedValues(provisionBinding.key().type())
                        && multibindingContributionSnippet.isPresent()
                        && multibindingContributionSnippet.get().owningClass().equals(name)) {
                      initializeMethod
                          .body()
                          .addSnippet(
                              "this.%s = %s;",
                              multibindingContributionSnippet.get().getSnippetFor(name),
                              initializeFactoryForProvisionBinding(provisionBinding));
                    }
                  }
                  initializeMember(
                      initializeMethod, bindingKey, initializeMapBinding(provisionBindings));
                } else {
                  // TODO(beder): Implement producer map bindings.
                  throw new IllegalStateException("producer map bindings not implemented yet");
                }
                break;
              case UNIQUE:
                if (!resolvedBindings.ownedContributionBindings().isEmpty()) {
                  ContributionBinding binding = Iterables.getOnlyElement(bindings);
                  if (binding instanceof ProvisionBinding) {
                    ProvisionBinding provisionBinding = (ProvisionBinding) binding;
                    if (!provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
                        || provisionBinding.scope().isPresent()) {
                      initializeDelegateFactories(binding, initializeMethod);
                      initializeMember(
                          initializeMethod,
                          bindingKey,
                          initializeFactoryForProvisionBinding(provisionBinding));
                    }
                  } else if (binding instanceof ProductionBinding) {
                    ProductionBinding productionBinding = (ProductionBinding) binding;
                    initializeMember(
                        initializeMethod,
                        bindingKey,
                        initializeFactoryForProductionBinding(productionBinding));
                  } else {
                    throw new AssertionError();
                  }
                }
                break;
              default:
                throw new IllegalStateException();
            }
            break;
          case MEMBERS_INJECTION:
            MembersInjectionBinding binding =
                Iterables.getOnlyElement(resolvedBindings.membersInjectionBindings());
            if (!binding.injectionStrategy().equals(MembersInjectionBinding.Strategy.NO_OP)) {
              initializeDelegateFactories(binding, initializeMethod);
              initializeMember(
                  initializeMethod, bindingKey, initializeMembersInjectorForBinding(binding));
            }
            break;
          default:
            throw new AssertionError();
        }
      }
    }
  }

  private void initializeDelegateFactories(Binding binding, MethodWriter initializeMethod) {
    for (Collection<DependencyRequest> requestsForKey :
        indexDependenciesByUnresolvedKey(types, binding.dependencies()).asMap().values()) {
      BindingKey dependencyKey =
          Iterables.getOnlyElement(
              FluentIterable.from(requestsForKey)
                  .transform(DependencyRequest.BINDING_KEY_FUNCTION)
                  .toSet());
      if (!getMemberSelect(dependencyKey).staticMember()
          && getInitializationState(dependencyKey).equals(UNINITIALIZED)) {
        initializeMethod
            .body()
            .addSnippet(
                "this.%s = new %s();",
                getMemberSelectSnippet(dependencyKey),
                ClassName.fromClass(DelegateFactory.class));
        setInitializationState(dependencyKey, DELEGATED);
      }
    }
  }

  private void initializeMember(
      MethodWriter initializeMethod, BindingKey bindingKey, Snippet initializationSnippet) {
    Snippet memberSelect = getMemberSelectSnippet(bindingKey);
    Snippet delegateFactoryVariable = delegateFactoryVariableSnippet(bindingKey);
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializeMethod
          .body()
          .addSnippet(
              "%1$s %2$s = (%1$s) %3$s;",
              ClassName.fromClass(DelegateFactory.class),
              delegateFactoryVariable,
              memberSelect);
    }
    initializeMethod.body().addSnippet("this.%s = %s;", memberSelect, initializationSnippet);
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializeMethod
          .body()
          .addSnippet("%s.setDelegatedProvider(%s);", delegateFactoryVariable, memberSelect);
    }
    setInitializationState(bindingKey, INITIALIZED);
  }

  private Snippet delegateFactoryVariableSnippet(BindingKey key) {
    return Snippet.format("%sDelegate", getMemberSelectSnippet(key).toString().replace('.', '_'));
  }

  private Snippet initializeFactoryForContributionBinding(ContributionBinding binding) {
    if (binding instanceof ProvisionBinding) {
      return initializeFactoryForProvisionBinding((ProvisionBinding) binding);
    } else if (binding instanceof ProductionBinding) {
      return initializeFactoryForProductionBinding((ProductionBinding) binding);
    } else {
      throw new AssertionError();
    }
  }

  private Snippet initializeFactoryForProvisionBinding(ProvisionBinding binding) {
    TypeName bindingKeyTypeName = TypeNames.forTypeMirror(binding.key().type());
    switch (binding.bindingKind()) {
      case COMPONENT:
        return Snippet.format(
            "%s.<%s>create(%s)",
            ClassName.fromClass(InstanceFactory.class),
            bindingKeyTypeName,
            bindingKeyTypeName.equals(componentDefinitionTypeName())
                ? "this"
                : getComponentContributionSnippet(MoreTypes.asTypeElement(binding.key().type())));
      case COMPONENT_PROVISION:
        TypeElement bindingTypeElement =
            graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
        if (binding.nullableType().isPresent()
            || nullableValidationType.equals(Diagnostic.Kind.WARNING)) {
          Snippet nullableSnippet =
              binding.nullableType().isPresent()
                  ? Snippet.format("@%s ", TypeNames.forTypeMirror(binding.nullableType().get()))
                  : Snippet.format("");
          return Snippet.format(
              Joiner.on('\n')
                  .join(
                      "new %1$s<%2$s>() {",
                      "  private final %6$s %7$s = %3$s;",
                      "  %5$s@Override public %2$s get() {",
                      "    return %7$s.%4$s();",
                      "  }",
                      "}"),
              /* 1 */ ClassName.fromClass(Factory.class),
              /* 2 */ bindingKeyTypeName,
              /* 3 */ getComponentContributionSnippet(bindingTypeElement),
              /* 4 */ binding.bindingElement().getSimpleName().toString(),
              /* 5 */ nullableSnippet,
              /* 6 */ TypeNames.forTypeMirror(bindingTypeElement.asType()),
              /* 7 */ simpleVariableName(bindingTypeElement));
        } else {
          // TODO(sameb): This throws a very vague NPE right now.  The stack trace doesn't
          // help to figure out what the method or return type is.  If we include a string
          // of the return type or method name in the error message, that can defeat obfuscation.
          // We can easily include the raw type (no generics) + annotation type (no values),
          // using .class & String.format -- but that wouldn't be the whole story.
          // What should we do?
          StringLiteral failMsg =
              StringLiteral.forValue(CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          return Snippet.format(
              Joiner.on('\n')
                  .join(
                      "new %1$s<%2$s>() {",
                      "  private final %6$s %7$s = %3$s;",
                      "  @Override public %2$s get() {",
                      "    %2$s provided = %7$s.%4$s();",
                      "    if (provided == null) {",
                      "      throw new NullPointerException(%5$s);",
                      "    }",
                      "    return provided;",
                      "  }",
                      "}"),
              /* 1 */ ClassName.fromClass(Factory.class),
              /* 2 */ bindingKeyTypeName,
              /* 3 */ getComponentContributionSnippet(bindingTypeElement),
              /* 4 */ binding.bindingElement().getSimpleName().toString(),
              /* 5 */ failMsg,
              /* 6 */ TypeNames.forTypeMirror(bindingTypeElement.asType()),
              /* 7 */ simpleVariableName(bindingTypeElement));
        }
      case INJECTION:
      case PROVISION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
        if (binding.bindingKind().equals(PROVISION)
            && !binding.bindingElement().getModifiers().contains(STATIC)) {
          parameters.add(getComponentContributionSnippet(binding.contributedBy().get()));
        }
        parameters.addAll(getDependencyParameters(binding));

        Snippet factorySnippet =
            Snippet.format(
                "%s.create(%s)",
                factoryNameForProvisionBinding(binding),
                Snippet.makeParametersSnippet(parameters));
        return binding.scope().isPresent()
            ? Snippet.format(
                "%s.create(%s)", ClassName.fromClass(ScopedProvider.class), factorySnippet)
            : factorySnippet;
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeFactoryForProductionBinding(ProductionBinding binding) {
    switch (binding.bindingKind()) {
      case COMPONENT_PRODUCTION:
        TypeElement bindingTypeElement =
            graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
        return Snippet.format(
            Joiner.on('\n')
                .join(
                    "new %1$s<%2$s>() {",
                    "  private final %6$s %7$s = %4$s;",
                    "  @Override public %3$s<%2$s> get() {",
                    "    return %7$s.%5$s();",
                    "  }",
                    "}"),
            /* 1 */ ClassName.fromClass(Producer.class),
            /* 2 */ TypeNames.forTypeMirror(binding.key().type()),
            /* 3 */ ClassName.fromClass(ListenableFuture.class),
            /* 4 */ getComponentContributionSnippet(bindingTypeElement),
            /* 5 */ binding.bindingElement().getSimpleName().toString(),
            /* 6 */ TypeNames.forTypeMirror(bindingTypeElement.asType()),
            /* 7 */ simpleVariableName(bindingTypeElement));
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 3);
        // TODO(beder): Pass the actual ProductionComponentMonitor.
        parameters.add(Snippet.format("null"));
        if (!binding.bindingElement().getModifiers().contains(STATIC)) {
          parameters.add(getComponentContributionSnippet(binding.bindingTypeElement()));
        }
        parameters.add(
            getComponentContributionSnippet(
                graph.componentDescriptor().executorDependency().get()));
        parameters.addAll(getProducerDependencyParameters(binding));

        return Snippet.format(
            "new %s(%s)",
            factoryNameForProductionBinding(binding),
            Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeMembersInjectorForBinding(MembersInjectionBinding binding) {
    switch (binding.injectionStrategy()) {
      case NO_OP:
        return Snippet.format("%s.noOp()", ClassName.fromClass(MembersInjectors.class));
      case INJECT_MEMBERS:
        List<Snippet> parameters = getDependencyParameters(binding);
        return Snippet.format(
            "%s.create(%s)",
            membersInjectorNameForType(binding.bindingElement()),
            Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private List<Snippet> getDependencyParameters(Binding binding) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    Set<Key> keysSeen = new HashSet<>();
    for (Collection<DependencyRequest> requestsForKey :
        indexDependenciesByUnresolvedKey(types, binding.implicitDependencies()).asMap().values()) {
      Set<BindingKey> requestedBindingKeys = new HashSet<>();
      for (DependencyRequest dependencyRequest : requestsForKey) {
        Element requestElement = dependencyRequest.requestElement();
        TypeMirror typeMirror = typeMirrorAsMemberOf(binding.bindingTypeElement(), requestElement);
        Key key = keyFactory.forQualifiedType(dependencyRequest.key().qualifier(), typeMirror);
        if (keysSeen.add(key)) {
          requestedBindingKeys.add(dependencyRequest.bindingKey());
        }
      }
      if (!requestedBindingKeys.isEmpty()) {
        BindingKey key = Iterables.getOnlyElement(requestedBindingKeys);
        parameters.add(getMemberSelect(key).getSnippetWithRawTypeCastFor(name));
      }
    }
    return parameters.build();
  }

  // TODO(dpb): Investigate use of asMemberOf here. Why aren't the dependency requests already
  // resolved?
  private TypeMirror typeMirrorAsMemberOf(TypeElement bindingTypeElement, Element requestElement) {
    TypeMirror requestType = requestElement.asType();
    if (requestType.getKind() == TypeKind.TYPEVAR) {
      return types.asMemberOf(
          MoreTypes.asDeclared(bindingTypeElement.asType()),
          (requestElement.getKind() == ElementKind.PARAMETER)
              ? MoreTypes.asElement(requestType)
              : requestElement);
    } else {
      return requestType;
    }
  }

  private List<Snippet> getProducerDependencyParameters(Binding binding) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
        SourceFiles.indexDependenciesByUnresolvedKey(
            types, binding.dependencies()).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(DependencyRequest.BINDING_KEY_FUNCTION));
      ResolvedBindings resolvedBindings = graph.resolvedBindings().get(key);
      Class<?> frameworkClass =
          DependencyRequestMapper.FOR_PRODUCER.getFrameworkClass(requestsForKey);
      if (FrameworkField.frameworkClassForResolvedBindings(resolvedBindings).equals(Provider.class)
          && frameworkClass.equals(Producer.class)) {
        parameters.add(
            Snippet.format(
                "%s.producerFromProvider(%s)",
                ClassName.fromClass(Producers.class),
                getMemberSelectSnippet(key)));
      } else {
        parameters.add(getMemberSelectSnippet(key));
      }
    }
    return parameters.build();
  }

  private Snippet initializeMapBinding(Set<ProvisionBinding> bindings) {
    // Get type information from the first binding.
    ProvisionBinding firstBinding = bindings.iterator().next();
    DeclaredType mapType = asDeclared(firstBinding.key().type());

    if (isMapWithNonProvidedValues(mapType)) {
      return Snippet.format(
          "%s.create(%s)",
          ClassName.fromClass(MapFactory.class),
          getMemberSelectSnippet(getOnlyElement(firstBinding.dependencies()).bindingKey()));
    }

    ImmutableList.Builder<dagger.internal.codegen.writer.Snippet> snippets =
        ImmutableList.builder();
    snippets.add(Snippet.format("%s.<%s, %s>builder(%d)",
        ClassName.fromClass(MapProviderFactory.class),
        TypeNames.forTypeMirror(getKeyTypeOfMap(mapType)),
        TypeNames.forTypeMirror(getProvidedValueTypeOfMap(mapType)), // V of Map<K, Provider<V>>
        bindings.size()));

    for (ProvisionBinding binding : bindings) {
      snippets.add(
          Snippet.format(
              "    .put(%s, %s)",
              getMapKeySnippet(binding.bindingElement()),
              getMultibindingContributionSnippet(binding).get().getSnippetFor(name)));
    }

    snippets.add(Snippet.format("    .build()"));

    return Snippet.join(Joiner.on('\n'), snippets.build());
  }

  private static String simpleVariableName(TypeElement typeElement) {
    return UPPER_CAMEL.to(LOWER_CAMEL, typeElement.getSimpleName().toString());
  }

  /**
   * Initialization state for a factory field.
   */
  enum InitializationState {
    /** The field is {@code null}. */
    UNINITIALIZED,

    /** The field is set to a {@link DelegateFactory}. */
    DELEGATED,

    /** The field is set to an undelegated factory. */
    INITIALIZED;
  }
}
