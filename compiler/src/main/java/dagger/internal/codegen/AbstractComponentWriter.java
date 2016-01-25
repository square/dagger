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
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
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
import dagger.internal.codegen.writer.VoidName;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.MapOfProducedProducer;
import dagger.producers.internal.MapOfProducerProducer;
import dagger.producers.internal.MapProducer;
import dagger.producers.internal.Producers;
import dagger.producers.internal.SetOfProducedProducer;
import dagger.producers.internal.SetProducer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.DELEGATED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.INITIALIZED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.FrameworkDependency.frameworkDependenciesForBinding;
import static dagger.internal.codegen.MapKeys.getMapKeySnippet;
import static dagger.internal.codegen.MemberSelect.emptyFrameworkMapFactory;
import static dagger.internal.codegen.MemberSelect.emptySetProvider;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.MemberSelect.noOpMembersInjector;
import static dagger.internal.codegen.MemberSelect.staticMethod;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
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
  protected final ImmutableMap<ComponentDescriptor, String> subcomponentImplNames;
  private final Map<BindingKey, InitializationState> initializationStates = new HashMap<>();
  protected ClassWriter componentWriter;
  private final Map<BindingKey, MemberSelect> memberSelectSnippets = new HashMap<>();
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
      BindingGraph graph,
      ImmutableMap<ComponentDescriptor, String> subcomponentImplNames) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.nullableValidationType = nullableValidationType;
    this.name = name;
    this.graph = graph;
    this.subcomponentImplNames = subcomponentImplNames;
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
      fieldSelect = localField(name, componentField.name());
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
    for (ResolvedBindings resolvedBindings : graph.resolvedBindings().values()) {
      addField(resolvedBindings);
    }
  }

  private void addField(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();

    // If the binding can be satisfied with a static method call without dependencies or state,
    // no field is necessary.
    Optional<MemberSelect> staticMemberSelect = staticMemberSelect(resolvedBindings);
    if (staticMemberSelect.isPresent()) {
      memberSelectSnippets.put(bindingKey, staticMemberSelect.get());
      return;
    }

    // No field needed if there are no owned bindings.
    if (resolvedBindings.ownedBindings().isEmpty()) {
      return;
    }

    FieldWriter frameworkField = addFrameworkField(resolvedBindings);

    memberSelectSnippets.put(
        bindingKey,
        localField(name, frameworkField.name()));
  }

  private FieldWriter addFrameworkField(ResolvedBindings resolvedBindings) {
    boolean useRawType = useRawType(resolvedBindings);

    FrameworkField contributionBindingField =
        FrameworkField.createForResolvedBindings(resolvedBindings);
    FieldWriter contributionField =
        componentWriter.addField(
            useRawType
                ? contributionBindingField.frameworkType().type()
                : contributionBindingField.frameworkType(),
            contributionBindingField.name());
    contributionField.addModifiers(PRIVATE);
    if (useRawType) {
      contributionField.annotate(SuppressWarnings.class).setValue("rawtypes");
    }
    return contributionField;
  }

  private boolean useRawType(ResolvedBindings resolvedBindings) {
    Optional<String> bindingPackage = resolvedBindings.bindingPackage();
    return bindingPackage.isPresent() && !bindingPackage.get().equals(name.packageName());
  }

  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments or a
   * no-op members injection binding, then we don't need a field to hold its factory. In that case,
   * this method returns the static member select snippet that returns the factory or no-op members
   * injector.
   */
  private Optional<MemberSelect> staticMemberSelect(ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        ContributionBinding contributionBinding = resolvedBindings.contributionBinding();
        if (contributionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
            && !contributionBinding.scope().isPresent()) {
          switch (contributionBinding.bindingKind()) {
            case SYNTHETIC_MULTIBOUND_MAP:
              BindingType bindingType = contributionBinding.bindingType();
              MapType mapType = MapType.from(contributionBinding.key().type());
              return Optional.of(
                  emptyFrameworkMapFactory(
                      frameworkMapFactoryClassName(bindingType),
                      mapType.keyType(),
                      mapType.unwrappedValueType(bindingType.frameworkClass())));

            case SYNTHETIC_MULTIBOUND_SET:
              return Optional.of(
                  emptySetFactoryStaticMemberSelect(
                      contributionBinding.bindingType(), contributionBinding.key()));

            default:
              return Optional.of(
                  staticMethod(
                      generatedClassNameForBinding(contributionBinding),
                      Snippet.format("create()")));
          }
        }
        break;

      case MEMBERS_INJECTION:
        Optional<MembersInjectionBinding> membersInjectionBinding =
            resolvedBindings.membersInjectionBinding();
        if (membersInjectionBinding.isPresent()
            && membersInjectionBinding.get().injectionStrategy().equals(NO_OP)) {
          return Optional.of(noOpMembersInjector(membersInjectionBinding.get().key().type()));
        }
        break;

      default:
        throw new AssertionError();
    }
    return Optional.absent();
  }

  /**
   * A static member select for an empty set factory. Calls
   * {@link SetFactory#create(javax.inject.Provider...)},
   * {@link SetProducer#create(dagger.producers.Producer...)}, or
   * {@link SetOfProducedProducer#create(dagger.producers.Producer...)}, depending on the set
   * bindings.
   */
  private static MemberSelect emptySetFactoryStaticMemberSelect(BindingType bindingType, Key key) {
    return emptySetProvider(setFactoryClassName(bindingType, key), SetType.from(key.type()));
  }

  /**
   * The {@link Set} factory class name appropriate for set bindings.
   *
   * <ul>
   * <li>{@link SetFactory} for provision bindings.
   * <li>{@link SetProducer} for production bindings for {@code Set<T>}.
   * <li>{@link SetOfProducedProducer} for production bindings for {@code Set<Produced<T>>}.
   * </ul>
   */
  private static ClassName setFactoryClassName(BindingType bindingType, Key key) {
    if (bindingType.equals(BindingType.PROVISION)) {
      return ClassName.fromClass(SetFactory.class);
    } else {
      SetType setType = SetType.from(key.type());
      return ClassName.fromClass(
          setType.elementsAreTypeOf(Produced.class)
              ? SetOfProducedProducer.class
              : SetProducer.class);
    }
  }

  /**
   * The {@link Map}-of-value factory class name appropriate for map bindings.
   *
   * <ul>
   * <li>{@link MapFactory} for provision bindings.
   * <li>{@link MapProducer} for production bindings.
   * </ul>
   */
  private static ClassName mapFactoryClassName(ContributionBinding binding) {
    switch (binding.bindingType()) {
      case PRODUCTION:
        return ClassName.fromClass(
            MapType.from(binding.key().type()).valuesAreTypeOf(Produced.class)
                ? MapOfProducedProducer.class
                : MapProducer.class);

      case PROVISION:
      case MEMBERS_INJECTION:
        return ClassName.fromClass(MapFactory.class);

      default:
        throw new AssertionError(binding.toString());
    }
  }

  /**
   * The {@link Map}-of-framework factory class name appropriate for map bindings.
   *
   * <ul>
   * <li>{@link MapProviderFactory} for provision bindings.
   * <li>{@link MapOfProducerProducer} for production bindings.
   * </ul>
   */
  private static ClassName frameworkMapFactoryClassName(BindingType bindingType) {
    return ClassName.fromClass(
        bindingType.equals(BindingType.PRODUCTION)
            ? MapOfProducerProducer.class
            : MapProviderFactory.class);
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
          MemberSelect memberSelect = getMemberSelect(bindingKey);
          Snippet memberSelectSnippet = memberSelect.getSnippetFor(name);
          switch (interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              List<? extends VariableElement> parameters = requestElement.getParameters();
              if (parameters.isEmpty()) {
                // we're returning the framework type
                interfaceMethod.body().addSnippet("return %s;", memberSelectSnippet);
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
                        memberSelectSnippet,
                        parameterName);
                if (!requestType.getReturnType().getKind().equals(VOID)) {
                  interfaceMethod.body().addSnippet("return %s;", parameterName);
                }
              }
              break;
            case INSTANCE:
              if (memberSelect.staticMember()
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
                        "%s factory = %s;", factoryType, memberSelectSnippet);
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
                          memberSelectSnippet, interfaceRequest.kind()));
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

  private static final int SNIPPETS_PER_INITIALIZATION_METHOD = 100;

  private void initializeFrameworkTypes() {
    ImmutableList.Builder<Snippet> snippetsBuilder = ImmutableList.builder();
    for (BindingKey bindingKey : graph.resolvedBindings().keySet()) {
      snippetsBuilder.addAll(initializeFrameworkType(bindingKey).asSet());
    }
    ImmutableList<Snippet> snippets = snippetsBuilder.build();

    List<List<Snippet>> partitions = Lists.partition(snippets, SNIPPETS_PER_INITIALIZATION_METHOD);
    for (int i = 0; i < partitions.size(); i++) {
      MethodWriter initializeMethod =
          componentWriter.addMethod(VoidName.VOID, "initialize" + ((i == 0) ? "" : i));
      /* TODO(gak): Strictly speaking, we only need the suppression here if we are also initializing
       * a raw field in this method, but the structure of this code makes it awkward to pass that
       * bit through.  This will be cleaned up when we no longer separate fields and initilization
       * as we do now. */
      initializeMethod.annotate(SuppressWarnings.class).setValue("unchecked");
      for (Snippet snippet : partitions.get(i)) {
        initializeMethod.body().addSnippet(snippet);
      }
      initializeMethod.addModifiers(PRIVATE);
      if (builderName.isPresent()) {
        initializeMethod.addParameter(builderName.get(), "builder").addModifiers(FINAL);
        constructorWriter.body().addSnippet("%s(builder);", initializeMethod.name());
      } else {
        constructorWriter.body().addSnippet("%s();", initializeMethod.name());
      }
    }
  }

  /**
   * Returns a single snippet representing the initialization of the framework type.
   *
   * <p>Note that this must be a single snippet because initialization snippets can be invoked from
   * any place in any order.  By requiring a single snippet (often of concatenated snippets) we
   * ensure that things like local variables always behave as expected by the initialization logic.
   */
  private Optional<Snippet> initializeFrameworkType(BindingKey bindingKey) {
    // If the field is inherited or the member select is static, don't initialize.
    MemberSelect memberSelect = getMemberSelect(bindingKey);
    if (memberSelect.staticMember() || !memberSelect.owningClass().equals(name)) {
      return Optional.absent();
    }
    
    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        return initializeContributionBinding(bindingKey);

      case MEMBERS_INJECTION:
        return initializeMembersInjectionBinding(bindingKey);

      default:
        throw new AssertionError();
    }
  }

  private Optional<Snippet> initializeContributionBinding(BindingKey bindingKey) {
    ContributionBinding binding = graph.resolvedBindings().get(bindingKey).contributionBinding();
    if (binding.factoryCreationStrategy().equals(ENUM_INSTANCE) && !binding.scope().isPresent()) {
      return Optional.absent();
    }

    return Optional.of(
        Snippet.concat(
            ImmutableList.of(
                initializeDelegateFactoriesForUninitializedDependencies(binding),
                initializeMember(bindingKey, initializeFactoryForContributionBinding(binding)))));
  }

  private Optional<Snippet> initializeMembersInjectionBinding(BindingKey bindingKey) {
    MembersInjectionBinding binding =
        graph.resolvedBindings().get(bindingKey).membersInjectionBinding().get();

    if (binding.injectionStrategy().equals(MembersInjectionBinding.Strategy.NO_OP)) {
      return Optional.absent();
    }

    return Optional.of(
        Snippet.concat(
            ImmutableList.of(
                initializeDelegateFactoriesForUninitializedDependencies(binding),
                initializeMember(bindingKey, initializeMembersInjectorForBinding(binding)))));
  }

  /**
   * Initializes delegate factories for any dependencies of {@code binding} that are uninitialized
   * because of a dependency cycle.
   */
  private Snippet initializeDelegateFactoriesForUninitializedDependencies(Binding binding) {
    ImmutableList.Builder<Snippet> initializationSnippets = ImmutableList.builder();

    for (BindingKey dependencyKey :
        FluentIterable.from(binding.implicitDependencies())
            .transform(DependencyRequest.BINDING_KEY_FUNCTION)
            .toSet()) {
      if (!getMemberSelect(dependencyKey).staticMember()
          && getInitializationState(dependencyKey).equals(UNINITIALIZED)) {
        initializationSnippets.add(
            Snippet.format(
                "this.%s = new %s();",
                getMemberSelectSnippet(dependencyKey),
                ClassName.fromClass(DelegateFactory.class)));
        setInitializationState(dependencyKey, DELEGATED);
      }
    }

    return Snippet.concat(initializationSnippets.build());
  }

  private Snippet initializeMember(BindingKey bindingKey, Snippet initializationSnippet) {
    ImmutableList.Builder<Snippet> initializationSnippets = ImmutableList.builder();

    Snippet memberSelect = getMemberSelectSnippet(bindingKey);
    Snippet delegateFactoryVariable = delegateFactoryVariableSnippet(bindingKey);
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializationSnippets.add(
          Snippet.format(
              "%1$s %2$s = (%1$s) %3$s;",
              ClassName.fromClass(DelegateFactory.class),
              delegateFactoryVariable,
              memberSelect));
    }
    initializationSnippets.add(
        Snippet.format("this.%s = %s;", memberSelect, initializationSnippet));
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializationSnippets.add(
          Snippet.format("%s.setDelegatedProvider(%s);", delegateFactoryVariable, memberSelect));
    }
    setInitializationState(bindingKey, INITIALIZED);

    return Snippet.concat(initializationSnippets.build());
  }

  private Snippet delegateFactoryVariableSnippet(BindingKey key) {
    return Snippet.format("%sDelegate", getMemberSelectSnippet(key).toString().replace('.', '_'));
  }

  private Snippet initializeFactoryForContributionBinding(ContributionBinding binding) {
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
        {
          TypeElement bindingTypeElement =
              graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
          String localFactoryVariable = simpleVariableName(bindingTypeElement);
          Snippet callFactoryMethodSnippet =
              Snippet.format(
                  "%s.%s()",
                  localFactoryVariable,
                  binding.bindingElement().getSimpleName().toString());
          // TODO(sameb): This throws a very vague NPE right now.  The stack trace doesn't
          // help to figure out what the method or return type is.  If we include a string
          // of the return type or method name in the error message, that can defeat obfuscation.
          // We can easily include the raw type (no generics) + annotation type (no values),
          // using .class & String.format -- but that wouldn't be the whole story.
          // What should we do?
          StringLiteral failMsg =
              StringLiteral.forValue(CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          Snippet getMethodBody =
              binding.nullableType().isPresent()
                      || nullableValidationType.equals(Diagnostic.Kind.WARNING)
                  ? Snippet.format("return %s;", callFactoryMethodSnippet)
                  : Snippet.format(
                      Joiner.on('\n')
                          .join(
                              "%s provided = %s;",
                              "if (provided == null) {",
                              "  throw new NullPointerException(%s);",
                              "}",
                              "return provided;"),
                      bindingKeyTypeName,
                      callFactoryMethodSnippet,
                      failMsg);
          return Snippet.format(
              Joiner.on('\n')
                  .join(
                      "new %1$s<%2$s>() {",
                      "  private final %5$s %6$s = %3$s;",
                      "  %4$s@Override public %2$s get() {",
                      "    %7$s",
                      "  }",
                      "}"),
              /* 1 */ ClassName.fromClass(Factory.class),
              /* 2 */ bindingKeyTypeName,
              /* 3 */ getComponentContributionSnippet(bindingTypeElement),
              /* 4 */ nullableSnippet(binding.nullableType()),
              /* 5 */ TypeNames.forTypeMirror(bindingTypeElement.asType()),
              /* 6 */ localFactoryVariable,
              /* 7 */ getMethodBody);
        }

      case SUBCOMPONENT_BUILDER:
        return Snippet.format(
            Joiner.on('\n')
                .join(
                    "new %1$s<%2$s>() {",
                    "  @Override public %2$s get() {",
                    "    return %3$s();",
                    "  }",
                    "}"),
            /* 1 */ ClassName.fromClass(Factory.class),
            /* 2 */ bindingKeyTypeName,
            /* 3 */ binding.bindingElement().getSimpleName().toString());

      case INJECTION:
      case PROVISION:
        {
          List<Snippet> parameters =
              Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
          if (binding.bindingKind().equals(PROVISION)
              && !binding.bindingElement().getModifiers().contains(STATIC)) {
            parameters.add(getComponentContributionSnippet(binding.contributedBy().get()));
          }
          parameters.addAll(getDependencyParameterSnippets(binding));

          Snippet factorySnippet =
              Snippet.format(
                  "%s.create(%s)",
                  generatedClassNameForBinding(binding),
                  Snippet.makeParametersSnippet(parameters));
          return binding.scope().isPresent()
              ? Snippet.format(
                  "%s.create(%s)", ClassName.fromClass(ScopedProvider.class), factorySnippet)
              : factorySnippet;
        }

      case COMPONENT_PRODUCTION:
        {
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
        }

      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        {
          List<Snippet> parameters =
              Lists.newArrayListWithCapacity(binding.implicitDependencies().size() + 2);
          if (!binding.bindingElement().getModifiers().contains(STATIC)) {
            parameters.add(getComponentContributionSnippet(binding.bindingTypeElement()));
          }
          parameters.add(
              getComponentContributionSnippet(
                  graph.componentDescriptor().executorDependency().get()));
          parameters.addAll(getDependencyParameterSnippets(binding));

          return Snippet.format(
              "new %s(%s)",
              generatedClassNameForBinding(binding),
              Snippet.makeParametersSnippet(parameters));
        }

      case SYNTHETIC_MAP:
        return Snippet.format(
            "%s.create(%s)",
            mapFactoryClassName(binding),
            getMemberSelectSnippet(getOnlyElement(binding.dependencies()).bindingKey()));

      case SYNTHETIC_MULTIBOUND_SET:
        return initializeFactoryForSetMultibinding(binding);

      case SYNTHETIC_MULTIBOUND_MAP:
        return initializeFactoryForMapMultibinding(binding);

      default:
        throw new AssertionError(binding.toString());
    }
  }

  private Snippet nullableSnippet(Optional<DeclaredType> nullableType) {
    return nullableType.isPresent()
        ? Snippet.format("@%s ", TypeNames.forTypeMirror(nullableType.get()))
        : Snippet.format("");
  }

  private Snippet initializeMembersInjectorForBinding(MembersInjectionBinding binding) {
    switch (binding.injectionStrategy()) {
      case NO_OP:
        return Snippet.format("%s.noOp()", ClassName.fromClass(MembersInjectors.class));
      case INJECT_MEMBERS:
        return Snippet.format(
            "%s.create(%s)",
            membersInjectorNameForType(binding.bindingElement()),
            makeParametersSnippet(getDependencyParameterSnippets(binding)));
      default:
        throw new AssertionError();
    }
  }

  /**
   * The snippets that represent factory arguments for the dependencies of a binding.
   */
  private ImmutableList<Snippet> getDependencyParameterSnippets(Binding binding) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (FrameworkDependency frameworkDependency : frameworkDependenciesForBinding(binding)) {
      parameters.add(getDependencySnippet(frameworkDependency));
    }
    return parameters.build();
  }

  /**
   * The snippet for a dependency.
   */
  private Snippet getDependencySnippet(FrameworkDependency frameworkDependency) {
    BindingKey requestedKey = frameworkDependency.bindingKey();
    Snippet frameworkSnippet = getMemberSelectSnippet(requestedKey);
    ResolvedBindings resolvedBindings = graph.resolvedBindings().get(requestedKey);
    if (resolvedBindings.frameworkClass().equals(Provider.class)
        && frameworkDependency.frameworkClass().equals(Producer.class)) {
      return Snippet.format(
          "%s.producerFromProvider(%s)", ClassName.fromClass(Producers.class), frameworkSnippet);
    } else {
      return frameworkSnippet;
    }
  }

  private Snippet initializeFactoryForSetMultibinding(ContributionBinding binding) {
    return Snippet.format(
        "%s.create(%s)",
        setFactoryClassName(binding.bindingType(), binding.key()),
        makeParametersSnippet(getDependencyParameterSnippets(binding)));
  }

  private Snippet initializeFactoryForMapMultibinding(ContributionBinding binding) {
    ImmutableSet<FrameworkDependency> frameworkDependencies =
        FrameworkDependency.frameworkDependenciesForBinding(binding);

    ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
    MapType mapType = MapType.from(binding.key().type());
    snippets.add(
        Snippet.format(
            "%s.<%s, %s>builder(%d)",
            frameworkMapFactoryClassName(binding.bindingType()),
            TypeNames.forTypeMirror(mapType.keyType()),
            TypeNames.forTypeMirror(
                mapType.unwrappedValueType(binding.bindingType().frameworkClass())),
            frameworkDependencies.size()));

    for (FrameworkDependency frameworkDependency : frameworkDependencies) {
      BindingKey bindingKey = frameworkDependency.bindingKey();
      ContributionBinding contributionBinding =
          graph.resolvedBindings().get(bindingKey).contributionBinding();
      snippets.add(
          Snippet.format(
              ".put(%s, %s)",
              getMapKeySnippet(contributionBinding.bindingElement()),
              getDependencySnippet(frameworkDependency)));
    }

    snippets.add(Snippet.format(".build()"));

    return Snippet.concat(snippets.build());
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
