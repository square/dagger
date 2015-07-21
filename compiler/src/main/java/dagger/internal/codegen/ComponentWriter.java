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
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Generated;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Binding.bindingPackageFor;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.MapKeys.getMapKeySnippet;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static dagger.internal.codegen.ProvisionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProductionBinding;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForMembersInjectionBinding;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.getKeyTypeOfMap;
import static dagger.internal.codegen.Util.getProvidedValueTypeOfMap;
import static dagger.internal.codegen.Util.isMapWithNonProvidedValues;
import static dagger.internal.codegen.writer.Snippet.memberSelectSnippet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Creates the implementation class for a component.
 */
class ComponentWriter {

  // TODO(dpb): Make all these fields private after refactoring is complete.

  protected final Types types;
  protected final Kind nullableValidationType;
  protected final Set<JavaWriter> javaWriters = new LinkedHashSet<>();
  protected final ClassName name;
  protected final BindingGraph graph;
  protected ClassWriter componentWriter;
  private final Map<String, ProxyClassAndField> packageProxies = Maps.newHashMap();
  protected ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets;
  protected ImmutableMap<ContributionBinding, MemberSelect> multibindingContributionSnippets;
  protected ImmutableSet<BindingKey> enumBindingKeys;
  protected ConstructorWriter constructorWriter;
  protected final Map<TypeElement, MemberSelect> componentContributionFields = Maps.newHashMap();

  ComponentWriter(
      Types types,
      Diagnostic.Kind nullableValidationType,
      ClassName name,
      BindingGraph graph) {
    this.types = types;
    this.nullableValidationType = nullableValidationType;
    this.name = name;
    this.graph = graph;
  }

  protected TypeElement componentDefinitionType() {
    return graph.componentDescriptor().componentDefinitionType();
  }

  private ClassName componentDefinitionTypeName() {
    return ClassName.fromTypeElement(componentDefinitionType());
  }

  protected MemberSelect getMemberSelectSnippet(BindingKey key) {
    return memberSelectSnippets.get(key);
  }

  protected Optional<MemberSelect> getMultibindingContributionSnippet(ContributionBinding binding) {
    return Optional.fromNullable(multibindingContributionSnippets.get(binding));
  }

  ImmutableSet<JavaWriter> write() {
    if (javaWriters.isEmpty()) {
      writeComponent();
    }
    return ImmutableSet.copyOf(javaWriters);
  }

  protected void writeComponent() {
    JavaWriter javaWriter = JavaWriter.inPackage(name.packageName());
    javaWriters.add(javaWriter);

    componentWriter = javaWriter.addClass(name.simpleName());
    componentWriter
        .annotate(Generated.class)
        .setValue(ComponentProcessor.class.getCanonicalName());
    componentWriter.addModifiers(PUBLIC, FINAL);
    checkState(componentDefinitionType().getModifiers().contains(ABSTRACT));
    componentWriter.setSupertype(componentDefinitionType());

    ClassWriter builderWriter = writeBuilder(componentWriter);
    if (!requiresUserSuppliedDependents()) {
      MethodWriter factoryMethod =
          componentWriter.addMethod(componentDefinitionTypeName(), "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod.body().addSnippet("return builder().%s();",
          graph.componentDescriptor().builderSpec().isPresent()
              ? graph.componentDescriptor().builderSpec().get().buildMethod().getSimpleName()
              : "build");
    }

    writeFields();

    constructorWriter = componentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);
    constructorWriter.addParameter(builderWriter, "builder");
    constructorWriter.body().addSnippet("assert builder != null;");

    initializeFrameworkTypes(Optional.of(builderWriter.name()));
    writeInterfaceMethods();

    for (Map.Entry<ExecutableElement, BindingGraph> subgraphEntry : graph.subgraphs().entrySet()) {
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, subgraphEntry.getKey(), subgraphEntry.getValue());
      javaWriters.addAll(subcomponent.write());
    }
  }

  /**
   * Writes a builder class.
   *
   * @param container the class we're adding this builder to
   */
  protected ClassWriter writeBuilder(ClassWriter container) {
    ClassWriter builderWriter;
    Optional<BuilderSpec> builderSpec = graph.componentDescriptor().builderSpec();
    // TODO(dpb): Move subcomponent-specific logic into SubcomponentImplementation.
    switch (graph.componentDescriptor().kind()) {
      case COMPONENT:
      case PRODUCTION_COMPONENT:
        builderWriter = container.addNestedClass("Builder");
        builderWriter.addModifiers(STATIC);

        // Only top-level components have the factory builder() method.
        // Mirror the user's builder API type if they had one.
        MethodWriter builderFactoryMethod = builderSpec.isPresent()
            ? container.addMethod(
                builderSpec.get().builderDefinitionType().asType(), "builder")
            : container.addMethod(builderWriter, "builder");
        builderFactoryMethod.addModifiers(PUBLIC, STATIC);
        builderFactoryMethod.body().addSnippet("return new %s();", builderWriter.name());
        break;
      case SUBCOMPONENT:
        verify(builderSpec.isPresent()); // only write subcomponent builders if there was a spec
        builderWriter = container.addNestedClass(
            componentDefinitionTypeName().simpleName() + "Builder");
        break;
      default:
        throw new IllegalStateException();
    }
    builderWriter.addModifiers(FINAL);
    builderWriter.addConstructor().addModifiers(PRIVATE);
    if (builderSpec.isPresent()) {
      builderWriter.addModifiers(PRIVATE);
      builderWriter.setSupertype(builderSpec.get().builderDefinitionType());
    } else {
      builderWriter.addModifiers(PUBLIC);
    }

    // the full set of types that calling code uses to construct a component instance
    ImmutableMap<TypeElement, String> componentContributionNames =
        ImmutableMap.copyOf(Maps.asMap(
            graph.componentRequirements(),
            Functions.compose(
                CaseFormat.UPPER_CAMEL.converterTo(LOWER_CAMEL),
                new Function<TypeElement, String>() {
                  @Override public String apply(TypeElement input) {
                    return input.getSimpleName().toString();
                  }
                })));

    MethodWriter buildMethod;
    if (builderSpec.isPresent()) {
      ExecutableElement specBuildMethod = builderSpec.get().buildMethod();
      // Note: we don't use the specBuildMethod.getReturnType() as the return type
      // because it might be a type variable.  We make use of covariant returns to allow
      // us to return the component type, which will always be valid.
      buildMethod = builderWriter.addMethod(componentDefinitionTypeName(),
          specBuildMethod.getSimpleName().toString());
      buildMethod.annotate(Override.class);
    } else {
      buildMethod = builderWriter.addMethod(componentDefinitionTypeName(), "build");
    }
    buildMethod.addModifiers(PUBLIC);

    for (Map.Entry<TypeElement, String> entry : componentContributionNames.entrySet()) {
      TypeElement contributionElement = entry.getKey();
      String contributionName = entry.getValue();
      FieldWriter builderField = builderWriter.addField(contributionElement, contributionName);
      builderField.addModifiers(PRIVATE);
      componentContributionFields.put(contributionElement, MemberSelect.instanceSelect(
          name, Snippet.format("builder.%s", builderField.name())));
      if (componentCanMakeNewInstances(contributionElement)) {
        buildMethod.body()
            .addSnippet("if (%s == null) {", builderField.name())
            .addSnippet("  this.%s = new %s();",
                builderField.name(), ClassName.fromTypeElement(contributionElement))
            .addSnippet("}");
      } else {
        buildMethod.body()
            .addSnippet("if (%s == null) {", builderField.name())
            .addSnippet("  throw new IllegalStateException(\"%s must be set\");",
                builderField.name())
            .addSnippet("}");
      }
      MethodWriter builderMethod;
      boolean returnsVoid = false;
      if (builderSpec.isPresent()) {
        ExecutableElement method = builderSpec.get().methodMap().get(contributionElement);
        if (method == null) { // no method in the API, nothing to write out.
          continue;
        }
        // If the return type is void, we add a method with the void return type.
        // Otherwise we use the builderWriter and take advantage of covariant returns
        // (so that we don't have to worry about setter methods that return type variables).
        if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
          returnsVoid = true;
          builderMethod =
              builderWriter.addMethod(method.getReturnType(), method.getSimpleName().toString());
        } else {
          builderMethod = builderWriter.addMethod(builderWriter, method.getSimpleName().toString());
        }
        builderMethod.annotate(Override.class);
      } else {
        builderMethod = builderWriter.addMethod(builderWriter, contributionName);
      }
      // TODO(gak): Mirror the API's visibility.
      // (Makes no difference to the user since this class is private,
      //  but makes generated code prettier.)
      builderMethod.addModifiers(PUBLIC);
      builderMethod.addParameter(contributionElement, contributionName);
      builderMethod.body()
          .addSnippet("if (%s == null) {", contributionName)
          .addSnippet("  throw new NullPointerException(%s);",
              StringLiteral.forValue(contributionName))
          .addSnippet("}")
          .addSnippet("this.%s = %s;", builderField.name(), contributionName);
      if (!returnsVoid) {
        builderMethod.body().addSnippet("return this;");
      }
    }
    buildMethod.body().addSnippet("return new %s(this);", name);
    return builderWriter;
  }

  /** Returns true if the graph has any dependents that can't be automatically constructed. */
  private boolean requiresUserSuppliedDependents() {
    Set<TypeElement> userRequiredDependents =
        Sets.filter(graph.componentRequirements(), new Predicate<TypeElement>() {
          @Override public boolean apply(TypeElement input) {
            return !Util.componentCanMakeNewInstances(input);
          }
        });
    return !userRequiredDependents.isEmpty();
  }

  protected void writeFields() {
    Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder = Maps.newHashMap();
    Map<ContributionBinding, MemberSelect> multibindingContributionSnippetsBuilder =
        Maps.newHashMap();
    ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder = ImmutableSet.builder();

    for (ResolvedBindings resolvedBindings : graph.resolvedBindings().values()) {
      writeField(
          memberSelectSnippetsBuilder,
          multibindingContributionSnippetsBuilder,
          enumBindingKeysBuilder,
          resolvedBindings);
    }

    memberSelectSnippets = ImmutableMap.copyOf(memberSelectSnippetsBuilder);
    multibindingContributionSnippets =
        ImmutableMap.copyOf(multibindingContributionSnippetsBuilder);
    enumBindingKeys = enumBindingKeysBuilder.build();
  }

  private void writeField(
      Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder,
      Map<ContributionBinding, MemberSelect> multibindingContributionSnippetsBuilder,
      ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder,
      ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();

    if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)
        && resolvedBindings.ownedContributionBindings().isEmpty()
        && !ContributionBinding.bindingTypeFor(resolvedBindings.contributionBindings())
            .isMultibinding()) {
      return;
    }

    if (resolvedBindings.bindings().size() == 1) {
      if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)) {
        ContributionBinding contributionBinding =
            Iterables.getOnlyElement(resolvedBindings.contributionBindings());
        if (!contributionBinding.bindingType().isMultibinding()
            && (contributionBinding instanceof ProvisionBinding)) {
          ProvisionBinding provisionBinding = (ProvisionBinding) contributionBinding;
          if (provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
              && !provisionBinding.scope().isPresent()) {
            enumBindingKeysBuilder.add(bindingKey);
            // skip keys whose factories are enum instances and aren't scoped
            memberSelectSnippetsBuilder.put(bindingKey,
                MemberSelect.staticSelect(
                    factoryNameForProvisionBinding(provisionBinding),
                    Snippet.format("create()")));
            return;
          }
        }
      } else if (bindingKey.kind().equals(BindingKey.Kind.MEMBERS_INJECTION)) {
        MembersInjectionBinding membersInjectionBinding =
            Iterables.getOnlyElement(resolvedBindings.membersInjectionBindings());
        if (membersInjectionBinding.injectionStrategy().equals(NO_OP)) {
          // TODO(gak): refactor to use enumBindingKeys throughout the generator
          enumBindingKeysBuilder.add(bindingKey);
          // TODO(gak): suppress the warnings in a reasonable place
          memberSelectSnippetsBuilder.put(bindingKey,
              MemberSelect.staticMethodInvocationWithCast(
                  ClassName.fromClass(MembersInjectors.class),
                  Snippet.format("noOp()"),
                  ClassName.fromClass(MembersInjector.class)));
          return;
        }
      }
    }

    String bindingPackage = bindingPackageFor(resolvedBindings.bindings())
        .or(componentWriter.name().packageName());

    final Optional<String> proxySelector;
    final TypeWriter classWithFields;
    final Set<Modifier> fieldModifiers;

    if (bindingPackage.equals(componentWriter.name().packageName())) {
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
        ClassWriter proxyWriter =
            proxyJavaWriter.addClass(componentWriter.name().simpleName() + "_PackageProxy");
        proxyWriter.annotate(Generated.class)
            .setValue(ComponentProcessor.class.getCanonicalName());
        proxyWriter.addModifiers(PUBLIC, FINAL);
        // create the field for the proxy in the component
        FieldWriter proxyFieldWriter =
            componentWriter.addField(proxyWriter.name(),
                bindingPackage.replace('.', '_') + "_Proxy");
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

    ImmutableList<String> memberSelectTokens = new ImmutableList.Builder<String>()
        .addAll(proxySelector.asSet())
        .add(frameworkField.name())
        .build();
    memberSelectSnippetsBuilder.put(bindingKey, MemberSelect.instanceSelect(
        componentWriter.name(),
        Snippet.memberSelectSnippet(memberSelectTokens)));
  }

  protected void writeInterfaceMethods() {
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
          MethodWriter interfaceMethod = requestType.getReturnType().getKind().equals(VOID)
              ? componentWriter.addMethod(VoidName.VOID, requestElement.getSimpleName().toString())
              : componentWriter.addMethod(requestType.getReturnType(),
                  requestElement.getSimpleName().toString());
          interfaceMethod.annotate(Override.class);
          interfaceMethod.addModifiers(PUBLIC);
          BindingKey bindingKey = interfaceRequest.bindingKey();
          switch (interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              MemberSelect membersInjectorSelect = getMemberSelectSnippet(bindingKey);
              List<? extends VariableElement> parameters = requestElement.getParameters();
              if (parameters.isEmpty()) {
                // we're returning the framework type
                interfaceMethod.body().addSnippet("return %s;",
                    membersInjectorSelect.getSnippetFor(componentWriter.name()));
              } else {
                VariableElement parameter = Iterables.getOnlyElement(parameters);
                Name parameterName = parameter.getSimpleName();
                interfaceMethod.addParameter(
                    TypeNames.forTypeMirror(
                        Iterables.getOnlyElement(requestType.getParameterTypes())),
                    parameterName.toString());
                interfaceMethod.body().addSnippet("%s.injectMembers(%s);",
                    // in this case we know we won't need the cast because we're never going to pass
                    // the reference to anything
                    membersInjectorSelect.getSnippetFor(componentWriter.name()),
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
                TypeName factoryType = ParameterizedTypeName.create(Provider.class,
                    TypeNames.forTypeMirror(requestType.getReturnType()));
                interfaceMethod.body().addSnippet("%s factory = %s;", factoryType,
                    getMemberSelectSnippet(bindingKey).getSnippetFor(componentWriter.name()));
                interfaceMethod.body().addSnippet("return factory.get();");
                break;
              }
              // fall through in the else case.
            case LAZY:
            case PRODUCED:
            case PRODUCER:
            case PROVIDER:
            case FUTURE:
              interfaceMethod.body().addSnippet("return %s;",
                  frameworkTypeUsageStatement(
                      getMemberSelectSnippet(bindingKey).getSnippetFor(componentWriter.name()),
                      interfaceRequest.kind()));
              break;
            default:
              throw new AssertionError();
          }
        }
      }
    }
  }

  protected void initializeFrameworkTypes(Optional<ClassName> builderName) {
    List<List<BindingKey>> partitions = Lists.partition(
        graph.resolvedBindings().keySet().asList(), 100);
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
        Snippet memberSelectSnippet =
            getMemberSelectSnippet(bindingKey).getSnippetFor(componentWriter.name());
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
                Snippet initializeSetSnippet = Snippet.format("%s.create(%s)",
                    hasOnlyProvisions
                        ? ClassName.fromClass(SetFactory.class)
                        : ClassName.fromClass(SetProducer.class),
                    Snippet.makeParametersSnippet(parameterSnippets.build()));
                initializeMethod.body().addSnippet("this.%s = %s;",
                    memberSelectSnippet, initializeSetSnippet);
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
                      initializeMethod.body().addSnippet("this.%s = %s;",
                          multibindingContributionSnippet.get().getSnippetFor(name),
                          initializeFactoryForProvisionBinding(provisionBinding));
                    }
                  }
                  if (!provisionBindings.isEmpty()) {
                    initializeMethod.body().addSnippet("this.%s = %s;",
                        memberSelectSnippet, initializeMapBinding(provisionBindings));
                  }
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
                      initializeMethod.body().addSnippet("this.%s = %s;",
                          memberSelectSnippet,
                          initializeFactoryForProvisionBinding(provisionBinding));
                    }
                  } else if (binding instanceof ProductionBinding) {
                    ProductionBinding productionBinding = (ProductionBinding) binding;
                    initializeMethod.body().addSnippet("this.%s = %s;",
                        memberSelectSnippet,
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
            MembersInjectionBinding binding = Iterables.getOnlyElement(
                resolvedBindings.membersInjectionBindings());
            if (!binding.injectionStrategy().equals(MembersInjectionBinding.Strategy.NO_OP)) {
              initializeMethod.body().addSnippet("this.%s = %s;",
                  memberSelectSnippet, initializeMembersInjectorForBinding(binding));
            }
            break;
          default:
            throw new AssertionError();
        }
      }
    }
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
    switch (binding.bindingKind()) {
      case COMPONENT:
        MemberSelect componentContributionSelect =
            componentContributionFields.get(MoreTypes.asTypeElement(binding.key().type()));
        return Snippet.format("%s.<%s>create(%s)",
            ClassName.fromClass(InstanceFactory.class),
            TypeNames.forTypeMirror(binding.key().type()),
            componentContributionSelect != null
                ? componentContributionSelect.getSnippetFor(name)
                : "this");
      case COMPONENT_PROVISION:
        TypeElement bindingTypeElement =
            graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
        String sourceFieldName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, bindingTypeElement.getSimpleName().toString());
        if (binding.nullableType().isPresent()
            || nullableValidationType.equals(Diagnostic.Kind.WARNING)) {
          Snippet nullableSnippet = binding.nullableType().isPresent()
              ? Snippet.format("@%s ", TypeNames.forTypeMirror(binding.nullableType().get()))
              : Snippet.format("");
          return Snippet.format(
              Joiner.on('\n').join(
                  "new %s<%2$s>() {",
                  "  private final %6$s %7$s = %3$s;",
                  "  %5$s@Override public %2$s get() {",
                  "    return %7$s.%4$s();",
                  "  }",
                  "}"),
              ClassName.fromClass(Factory.class),
              TypeNames.forTypeMirror(binding.key().type()),
              componentContributionFields.get(bindingTypeElement).getSnippetFor(name),
              binding.bindingElement().getSimpleName().toString(),
              nullableSnippet,
              TypeNames.forTypeMirror(bindingTypeElement.asType()),
              sourceFieldName);
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
                      "new %s<%2$s>() {",
                      "  private final %6$s %7$s = %3$s;",
                      "  @Override public %2$s get() {",
                      "    %2$s provided = %7$s.%4$s();",
                      "    if (provided == null) {",
                      "      throw new NullPointerException(%5$s);",
                      "    }",
                      "    return provided;",
                      "  }",
                      "}"),
              ClassName.fromClass(Factory.class),
              TypeNames.forTypeMirror(binding.key().type()),
              componentContributionFields.get(bindingTypeElement).getSnippetFor(name),
              binding.bindingElement().getSimpleName().toString(),
              failMsg,
              TypeNames.forTypeMirror(bindingTypeElement.asType()),
              sourceFieldName);
        }
      case INJECTION:
      case PROVISION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
        if (binding.bindingKind().equals(PROVISION)
            && !binding.bindingElement().getModifiers().contains(STATIC)) {
          parameters.add(componentContributionFields.get(binding.contributedBy().get())
              .getSnippetFor(name));
        }
        parameters.addAll(getDependencyParameters(binding.implicitDependencies()));

        Snippet factorySnippet = Snippet.format("%s.create(%s)",
            factoryNameForProvisionBinding(binding), Snippet.makeParametersSnippet(parameters));
        return binding.scope().isPresent()
            ? Snippet.format("%s.create(%s)",
                ClassName.fromClass(ScopedProvider.class),
                factorySnippet)
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
        String sourceFieldName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, bindingTypeElement.getSimpleName().toString());
        return Snippet.format(
            Joiner.on('\n')
                .join(
                    "new %s<%2$s>() {",
                    "  private final %6$s %7$s = %4$s;",
                    "  @Override public %3$s<%2$s> get() {",
                    "    return %7$s.%5$s();",
                    "  }",
                    "}"),
            ClassName.fromClass(Producer.class),
            TypeNames.forTypeMirror(binding.key().type()),
            ClassName.fromClass(ListenableFuture.class),
            componentContributionFields.get(bindingTypeElement).getSnippetFor(name),
            binding.bindingElement().getSimpleName().toString(),
            TypeNames.forTypeMirror(bindingTypeElement.asType()),
            sourceFieldName);
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 2);
        parameters.add(componentContributionFields.get(binding.bindingTypeElement())
            .getSnippetFor(name));
        parameters.add(componentContributionFields.get(
            graph.componentDescriptor().executorDependency().get())
                .getSnippetFor(name));
        parameters.addAll(getProducerDependencyParameters(binding.dependencies()));

        return Snippet.format("new %s(%s)",
            factoryNameForProductionBinding(binding), Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeMembersInjectorForBinding(MembersInjectionBinding binding) {
    switch (binding.injectionStrategy()) {
      case NO_OP:
        return Snippet.format("%s.noOp()", ClassName.fromClass(MembersInjectors.class));
      case DELEGATE:
        DependencyRequest parentInjectorRequest = binding.parentInjectorRequest().get();
        return Snippet.format("%s.delegatingTo(%s)",
            ClassName.fromClass(MembersInjectors.class),
            getMemberSelectSnippet(parentInjectorRequest.bindingKey()).getSnippetFor(name));
      case INJECT_MEMBERS:
        List<Snippet> parameters = getDependencyParameters(binding.implicitDependencies());
        return Snippet.format("%s.create(%s)",
            membersInjectorNameForMembersInjectionBinding(binding),
            Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private List<Snippet> getDependencyParameters(Iterable<DependencyRequest> dependencies) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
        SourceFiles.indexDependenciesByUnresolvedKey(types, dependencies).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(new Function<DependencyRequest, BindingKey>() {
            @Override public BindingKey apply(DependencyRequest request) {
              return request.bindingKey();
            }
          })
          .toSet());
      parameters.add(getMemberSelectSnippet(key).getSnippetWithRawTypeCastFor(name));
    }
    return parameters.build();
  }

  private List<Snippet> getProducerDependencyParameters(
      Iterable<DependencyRequest> dependencies) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
        SourceFiles.indexDependenciesByUnresolvedKey(types, dependencies).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(new Function<DependencyRequest, BindingKey>() {
            @Override public BindingKey apply(DependencyRequest request) {
              return request.bindingKey();
            }
          }));
      ResolvedBindings resolvedBindings = graph.resolvedBindings().get(key);
      Class<?> frameworkClass =
          DependencyRequestMapper.FOR_PRODUCER.getFrameworkClass(requestsForKey);
      if (FrameworkField.frameworkClassForResolvedBindings(resolvedBindings)
              .equals(Provider.class)
          && frameworkClass.equals(Producer.class)) {
        parameters.add(Snippet.format("%s.producerFromProvider(%s)",
            ClassName.fromClass(Producers.class),
            getMemberSelectSnippet(key).getSnippetFor(name)));
      } else {
        parameters.add(getMemberSelectSnippet(key).getSnippetFor(name));
      }
    }
    return parameters.build();
  }

  private Snippet initializeMapBinding(Set<ProvisionBinding> bindings) {
    // Get type information from the first binding.
    ProvisionBinding firstBinding = bindings.iterator().next();
    DeclaredType mapType = asDeclared(firstBinding.key().type());

    if (isMapWithNonProvidedValues(mapType)) {
      return Snippet.format("%s.create(%s)",
          ClassName.fromClass(MapFactory.class),
          getMemberSelectSnippet(getOnlyElement(firstBinding.dependencies()).bindingKey())
              .getSnippetFor(name));
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
}
