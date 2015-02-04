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

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
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
import dagger.Component;
import dagger.Factory;
import dagger.MapKey;
import dagger.MembersInjector;
import dagger.internal.InstanceFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.MembersInjectors;
import dagger.internal.ScopedProvider;
import dagger.internal.SetFactory;
import dagger.internal.codegen.BindingGraph.ResolvedBindings;
import dagger.internal.codegen.ContributionBinding.BindingType;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.tools.Diagnostic;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static dagger.internal.codegen.Binding.bindingPackageFor;
import static dagger.internal.codegen.ConfigurationAnnotations.getMapKeys;
import static dagger.internal.codegen.DependencyRequest.Kind.MEMBERS_INJECTOR;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.ProductionBinding.Kind.COMPONENT_PRODUCTION;
import static dagger.internal.codegen.ProvisionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ProvisionBinding.Kind.COMPONENT;
import static dagger.internal.codegen.ProvisionBinding.Kind.COMPONENT_PROVISION;
import static dagger.internal.codegen.ProvisionBinding.Kind.INJECTION;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ProvisionBinding.Kind.SYNTHETIC_PROVISON;
import static dagger.internal.codegen.SourceFiles.factoryNameForProductionBinding;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForMembersInjectionBinding;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.NestingKind.MEMBER;
import static javax.lang.model.element.NestingKind.TOP_LEVEL;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final Diagnostic.Kind nullableValidationType;

  ComponentGenerator(Filer filer, Diagnostic.Kind nullableValidationType) {
    super(filer);
    this.nullableValidationType = nullableValidationType;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName =
        ClassName.fromTypeElement(input.componentDescriptor().componentDefinitionType());
    String componentName =
        "Dagger_" + componentDefinitionClassName.classFileName().replace('$', '_');
    return componentDefinitionClassName.topLevelClassName().peerNamed(componentName);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(BindingGraph input) {
    return ImmutableSet.of(input.componentDescriptor().componentDefinitionType());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentDescriptor().componentDefinitionType());
  }

  @AutoValue
  static abstract class ProxyClassAndField {
    abstract ClassWriter proxyWriter();
    abstract FieldWriter proxyFieldWriter();

    static ProxyClassAndField create(ClassWriter proxyWriter, FieldWriter proxyFieldWriter) {
      return new AutoValue_ComponentGenerator_ProxyClassAndField(proxyWriter, proxyFieldWriter);
    }
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName componentName, BindingGraph input) {
    ClassName componentDefinitionTypeName =
        ClassName.fromTypeElement(input.componentDescriptor().componentDefinitionType());

    JavaWriter writer = JavaWriter.inPackage(componentName.packageName());

    ClassWriter componentWriter = writer.addClass(componentName.simpleName());
    componentWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getCanonicalName());
    componentWriter.addModifiers(PUBLIC, FINAL);
    componentWriter.addImplementedType(componentDefinitionTypeName);

    Set<JavaWriter> javaWriters = Sets.newHashSet();
    javaWriters.add(writer);
    writeComponent(input, componentDefinitionTypeName, componentWriter, javaWriters);

    return ImmutableSet.copyOf(javaWriters);
  }

  private ImmutableMap<BindingKey, Snippet> writeComponent(
      BindingGraph input, ClassName componentDefinitionTypeName, ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters) {
    ClassWriter builderWriter = componentWriter.addNestedClass("Builder");
    builderWriter.addModifiers(PUBLIC, STATIC, FINAL);
    builderWriter.addConstructor().addModifiers(PRIVATE);

    MethodWriter builderFactoryMethod = componentWriter.addMethod(builderWriter, "builder");
    builderFactoryMethod.addModifiers(PUBLIC, STATIC);
    builderFactoryMethod.body().addSnippet("return new %s();", builderWriter.name());

    // the full set of types that calling code uses to construct a component instance
    ImmutableMap<TypeElement, String> componentContributionNames =
        ImmutableMap.copyOf(Maps.asMap(
            Sets.union(
                Sets.union(
                    input.transitiveModules().keySet(),
                    input.componentDescriptor().dependencies()),
                input.componentDescriptor().executorDependency().asSet()),
            Functions.compose(
                CaseFormat.UPPER_CAMEL.converterTo(LOWER_CAMEL),
                new Function<TypeElement, String>() {
                  @Override public String apply(TypeElement input) {
                    return input.getSimpleName().toString();
                  }
                })));

    ConstructorWriter constructorWriter = componentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);
    constructorWriter.addParameter(builderWriter, "builder");
    constructorWriter.body().addSnippet("assert builder != null;");

    MethodWriter buildMethod = builderWriter.addMethod(componentDefinitionTypeName, "build");
    buildMethod.addModifiers(PUBLIC);

    boolean requiresBuilder = false;

    Map<TypeElement, FieldWriter> componentContributionFields = Maps.newHashMap();

    for (Entry<TypeElement, String> entry : componentContributionNames.entrySet()) {
      TypeElement contributionElement = entry.getKey();
      String contributionName = entry.getValue();
      FieldWriter contributionField =
          componentWriter.addField(contributionElement, contributionName);
      contributionField.addModifiers(PRIVATE, FINAL);
      componentContributionFields.put(contributionElement, contributionField);
      FieldWriter builderField = builderWriter.addField(contributionElement, contributionName);
      builderField.addModifiers(PRIVATE);
      constructorWriter.body()
          .addSnippet("this.%1$s = builder.%1$s;", contributionField.name());
      MethodWriter builderMethod = builderWriter.addMethod(builderWriter, contributionName);
      builderMethod.addModifiers(PUBLIC);
      builderMethod.addParameter(contributionElement, contributionName);
      builderMethod.body()
          .addSnippet("if (%s == null) {", contributionName)
          .addSnippet("  throw new NullPointerException(%s);",
              StringLiteral.forValue(contributionName))
          .addSnippet("}")
          .addSnippet("this.%s = %s;", builderField.name(), contributionName)
          .addSnippet("return this;");
      if (hasNoArgsConstructor(contributionElement)) {
        buildMethod.body()
            .addSnippet("if (%s == null) {", builderField.name())
            .addSnippet("  this.%s = new %s();",
                builderField.name(), ClassName.fromTypeElement(contributionElement))
            .addSnippet("}");
      } else {
        requiresBuilder = true;
        buildMethod.body()
            .addSnippet("if (%s == null) {", builderField.name())
            .addSnippet("  throw new IllegalStateException(\"%s must be set\");",
                builderField.name())
            .addSnippet("}");
      }
    }

    if (!requiresBuilder) {
      MethodWriter factoryMethod = componentWriter.addMethod(componentDefinitionTypeName, "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod.body().addSnippet("return builder().build();");
    }

    Map<BindingKey, Snippet> memberSelectSnippetsBuilder = Maps.newHashMap();
    Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder = Maps.newHashMap();
    ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder = ImmutableSet.builder();

    Map<String, ProxyClassAndField> packageProxies = Maps.newHashMap();

    writeFields(input,
        componentWriter,
        proxyWriters,
        memberSelectSnippetsBuilder,
        ImmutableMap.<ContributionBinding, Snippet>of(),
        multibindingContributionSnippetsBuilder,
        enumBindingKeysBuilder,
        packageProxies);

    buildMethod.body().addSnippet("return new %s(this);", componentWriter.name());

    ImmutableMap<BindingKey, Snippet> memberSelectSnippets =
        ImmutableMap.copyOf(memberSelectSnippetsBuilder);
    ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets =
        ImmutableMap.copyOf(multibindingContributionSnippetsBuilder);
    ImmutableSet<BindingKey> enumBindingKeys = enumBindingKeysBuilder.build();

    initializeFrameworkTypes(input,
        componentWriter,
        constructorWriter,
        componentContributionFields,
        memberSelectSnippets,
        ImmutableMap.<ContributionBinding, Snippet>of(),
        multibindingContributionSnippets);

    writeInterfaceMethods(input, componentWriter, memberSelectSnippets, enumBindingKeys);

    writeSubcomponents(input,
        componentWriter,
        proxyWriters,
        componentContributionFields,
        memberSelectSnippets,
        multibindingContributionSnippets);

    return memberSelectSnippets;
  }

  private void writeSubcomponents(BindingGraph input,
      ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters,
      Map<TypeElement, FieldWriter> componentContributionFields,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets,
      ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets) {
    for (Entry<ExecutableElement, BindingGraph> subgraphEntry : input.subgraphs().entrySet()) {
      TypeName componentType =
          TypeNames.forTypeMirror(subgraphEntry.getKey().getReturnType());

      ClassWriter subcomponentWriter = componentWriter.addNestedClass(
          subgraphEntry.getValue().componentDescriptor().componentDefinitionType().getSimpleName()
              + "Impl");

      subcomponentWriter.addModifiers(PRIVATE, FINAL);
      subcomponentWriter.addImplementedType(componentType);

      writeSubcomponent(subgraphEntry.getValue(),
          subcomponentWriter,
          proxyWriters,
          ImmutableMap.copyOf(componentContributionFields),
          ImmutableMap.copyOf(multibindingContributionSnippets),
          memberSelectSnippets);

      MethodWriter componentMethod = componentWriter.addMethod(componentType,
          subgraphEntry.getKey().getSimpleName().toString());
      componentMethod.addModifiers(PUBLIC);
      componentMethod.annotate(Override.class);
      // TODO(gak): need to pipe through the method params
      componentMethod.body().addSnippet("return new %s();",
          subcomponentWriter.name());
    }
  }

  private ImmutableMap<BindingKey, Snippet> writeSubcomponent(
      BindingGraph input, ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters,
      ImmutableMap<TypeElement, FieldWriter> parentContributionFields,
      ImmutableMap<ContributionBinding, Snippet> parentMultibindingContributionSnippets,
      ImmutableMap<BindingKey, Snippet> parentMemberSelectSnippets) {
    // the full set of types that calling code uses to construct a component instance
    ImmutableMap<TypeElement, String> componentContributionNames =
        ImmutableMap.copyOf(Maps.asMap(
            Sets.union(
                input.transitiveModules().keySet(),
                input.componentDescriptor().dependencies()),
            new Function<TypeElement, String>() {
              @Override public String apply(TypeElement input) {
                return CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, input.getSimpleName().toString());
              }
            }));

    ConstructorWriter constructorWriter = componentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);

    Map<TypeElement, FieldWriter> componentContributionFields =
        Maps.newHashMap(parentContributionFields);

    for (Entry<TypeElement, String> entry : componentContributionNames.entrySet()) {
      TypeElement contributionElement = entry.getKey();
      String contributionName = entry.getValue();
      FieldWriter contributionField =
          componentWriter.addField(contributionElement, contributionName);
      if (hasNoArgsConstructor(entry.getKey())) {
        contributionField.setInitializer(Snippet.format("new %s()",
            ClassName.fromTypeElement(entry.getKey())));
      }
      contributionField.addModifiers(PRIVATE, FINAL);
      componentContributionFields.put(contributionElement, contributionField);
    }

    Map<BindingKey, Snippet> memberSelectSnippetsBuilder = Maps.newHashMap();

    Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder = Maps.newHashMap();
    ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder = ImmutableSet.builder();

    Map<String, ProxyClassAndField> packageProxies = Maps.newHashMap();

    writeFields(input,
        componentWriter,
        proxyWriters,
        memberSelectSnippetsBuilder,
        parentMultibindingContributionSnippets,
        multibindingContributionSnippetsBuilder,
        enumBindingKeysBuilder,
        packageProxies);

    for (Entry<BindingKey, Snippet> parentBindingEntry : parentMemberSelectSnippets.entrySet()) {
      if (!memberSelectSnippetsBuilder.containsKey(parentBindingEntry.getKey())) {
        memberSelectSnippetsBuilder.put(parentBindingEntry.getKey(), parentBindingEntry.getValue());
      }
    }

    ImmutableMap<BindingKey, Snippet> memberSelectSnippets =
        ImmutableMap.copyOf(memberSelectSnippetsBuilder);
    ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets =
        ImmutableMap.copyOf(multibindingContributionSnippetsBuilder);
    ImmutableSet<BindingKey> enumBindingKeys = enumBindingKeysBuilder.build();

    initializeFrameworkTypes(input,
        componentWriter,
        constructorWriter,
        componentContributionFields,
        memberSelectSnippets,
        parentMultibindingContributionSnippets,
        multibindingContributionSnippets);

    writeInterfaceMethods(input, componentWriter, memberSelectSnippets, enumBindingKeys);

    writeSubcomponents(input,
        componentWriter,
        proxyWriters,
        componentContributionFields,
        memberSelectSnippets,
        new ImmutableMap.Builder<ContributionBinding, Snippet>()
            .putAll(parentMultibindingContributionSnippets)
            .putAll(multibindingContributionSnippets)
            .build());

    return memberSelectSnippets;
  }

  private void writeFields(BindingGraph input,
      ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters,
      Map<BindingKey, Snippet> memberSelectSnippetsBuilder,
      Map<ContributionBinding, Snippet> parentMultibindingContributionSnippetsBuilder,
      Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder,
      ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder,
      Map<String, ProxyClassAndField> packageProxies) throws AssertionError {
    for (ResolvedBindings resolvedBindings : input.resolvedBindings().values()) {
      BindingKey bindingKey = resolvedBindings.bindingKey();

      if (resolvedBindings.bindings().size() == 1
          && bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)) {
        ContributionBinding contributionBinding =
            Iterables.getOnlyElement(resolvedBindings.contributionBindings());
        if (contributionBinding instanceof ProvisionBinding) {
          ProvisionBinding provisionBinding = (ProvisionBinding) contributionBinding;
          if (provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
              && !provisionBinding.scope().isPresent()) {
            enumBindingKeysBuilder.add(bindingKey);
            // skip keys whose factories are enum instances and aren't scoped
            memberSelectSnippetsBuilder.put(bindingKey, Snippet.format("%s.create()",
                    factoryNameForProvisionBinding(provisionBinding)));
            continue;
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
          proxyWriters.add(proxyJavaWriter);
          ClassWriter proxyWriter =
              proxyJavaWriter.addClass(componentWriter.name().simpleName() + "__PackageProxy");
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
          // otherwise, the nubmering wouldn't work
          int contributionNumber = 0;
          for (ContributionBinding contributionBinding : contributionBindings) {
            if (isSytheticProvisionBinding(contributionBinding)) {
              contributionNumber++;
              if (!parentMultibindingContributionSnippetsBuilder.containsKey(contributionBinding)) {
                FrameworkField contributionBindingField =
                    frameworkFieldForSyntheticProvisionBinding(
                          bindingKey, contributionNumber, contributionBinding);
                FieldWriter contributionField = classWithFields.addField(
                    contributionBindingField.frameworkType(), contributionBindingField.name());
                contributionField.addModifiers(fieldModifiers);

                ImmutableList<String> contirubtionSelectTokens = new ImmutableList.Builder<String>()
                    .addAll(proxySelector.asSet())
                    .add(contributionField.name())
                    .build();
                multibindingContributionSnippetsBuilder.put(contributionBinding,
                    Snippet.memberSelectSnippet(contirubtionSelectTokens));
              }
            }
          }
        }
      }

      FrameworkField bindingField = frameworkFieldForResolvedBindings(resolvedBindings);
      FieldWriter frameworkField =
          classWithFields.addField(bindingField.frameworkType(), bindingField.name());
      frameworkField.addModifiers(fieldModifiers);

      ImmutableList<String> memberSelectTokens = new ImmutableList.Builder<String>()
          .addAll(proxySelector.asSet())
          .add(frameworkField.name())
          .build();
      memberSelectSnippetsBuilder.put(bindingKey, Snippet.memberSelectSnippet(memberSelectTokens));
    }
  }

  private void writeInterfaceMethods(BindingGraph input, ClassWriter componentWriter,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets,
      ImmutableSet<BindingKey> enumBindingKeys) throws AssertionError {
    Set<MethodSignature> interfaceMethods = Sets.newHashSet();

    for (DependencyRequest interfaceRequest : input.entryPoints()) {
      ExecutableElement requestElement =
          MoreElements.asExecutable(interfaceRequest.requestElement());
      MethodSignature signature = MethodSignature.fromExecutableElement(requestElement);
      if (!interfaceMethods.contains(signature)) {
        interfaceMethods.add(signature);
        MethodWriter interfaceMethod = requestElement.getReturnType().getKind().equals(VOID)
            ? componentWriter.addMethod(VoidName.VOID, requestElement.getSimpleName().toString())
                : componentWriter.addMethod(requestElement.getReturnType(),
                    requestElement.getSimpleName().toString());
        interfaceMethod.annotate(Override.class);
        interfaceMethod.addModifiers(PUBLIC);
        BindingKey bindingKey = BindingKey.forDependencyRequest(interfaceRequest);
        switch(interfaceRequest.kind()) {
          case MEMBERS_INJECTOR:
            Snippet membersInjectorName = memberSelectSnippets.get(bindingKey);
            VariableElement parameter = Iterables.getOnlyElement(requestElement.getParameters());
            Name parameterName = parameter.getSimpleName();
            interfaceMethod.addParameter(
                TypeNames.forTypeMirror(parameter.asType()), parameterName.toString());
            interfaceMethod.body()
                .addSnippet("%s.injectMembers(%s);", membersInjectorName, parameterName);
            if (!requestElement.getReturnType().getKind().equals(VOID)) {
              interfaceMethod.body().addSnippet("return %s;", parameterName);
            }
            break;
          case INSTANCE:
            if (enumBindingKeys.contains(bindingKey)
                && !MoreTypes.asDeclared(bindingKey.key().type())
                        .getTypeArguments().isEmpty()) {
              // If using a parameterized enum type, then we need to store the factory
              // in a temporary variable, in order to help javac be able to infer
              // the generics of the Factory.create methods.
              TypeName factoryType = ParameterizedTypeName.create(Provider.class,
                  TypeNames.forTypeMirror(requestElement.getReturnType()));
              interfaceMethod.body().addSnippet("%s factory = %s;", factoryType,
                  memberSelectSnippets.get(bindingKey));
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
                frameworkTypeUsageStatement(memberSelectSnippets.get(bindingKey),
                    interfaceRequest.kind()));
            break;
          default:
            throw new AssertionError();
        }
      }
    }
  }

  private void initializeFrameworkTypes(BindingGraph input,
      ClassWriter componentWriter,
      ConstructorWriter constructorWriter,
      Map<TypeElement, FieldWriter> componentContributionFields,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets,
      ImmutableMap<ContributionBinding, Snippet> parentMultibindingContributionSnippets,
      ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets)
      throws AssertionError {
    List<List<BindingKey>> partitions = Lists.partition(
        input.resolvedBindings().keySet().asList(), 100);
    for (int i = 0; i < partitions.size(); i++) {
      MethodWriter initializeMethod =
          componentWriter.addMethod(VoidName.VOID, "initialize" + ((i == 0) ? "" : i));
      initializeMethod.body();
      initializeMethod.addModifiers(PRIVATE);
      constructorWriter.body().addSnippet("%s();", initializeMethod.name());

      for (BindingKey bindingKey : partitions.get(i)) {
        Snippet memberSelectSnippet = memberSelectSnippets.get(bindingKey);
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            ImmutableSet<? extends ContributionBinding> bindings =
                input.resolvedBindings().get(bindingKey).contributionBindings();

            switch (ContributionBinding.bindingTypeFor(bindings)) {
              case SET:
                if (Sets.filter(bindings, Predicates.instanceOf(ProductionBinding.class))
                    .isEmpty()) {
                  @SuppressWarnings("unchecked")  // checked by the instanceof filter above
                  ImmutableSet<ProvisionBinding> provisionBindings =
                      (ImmutableSet<ProvisionBinding>) bindings;
                  ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
                  for (ProvisionBinding provisionBinding : provisionBindings) {
                    if (multibindingContributionSnippets.containsKey(provisionBinding)) {
                      Snippet snippet = multibindingContributionSnippets.get(provisionBinding);
                      initializeMethod.body().addSnippet("this.%s = %s;",
                          snippet,
                          initializeFactoryForProvisionBinding(provisionBinding,
                              input.componentDescriptor().dependencyMethodIndex(),
                              componentContributionFields,
                              memberSelectSnippets));
                      parameterSnippets.add(snippet);
                    } else if (parentMultibindingContributionSnippets
                        .containsKey(provisionBinding)) {
                      parameterSnippets.add(
                          parentMultibindingContributionSnippets.get(provisionBinding));
                    } else {
                      throw new IllegalStateException();
                    }
                  }
                  Snippet initializeSetSnippet = Snippet.format("%s.create(%s)",
                      ClassName.fromClass(SetFactory.class),
                      Snippet.makeParametersSnippet(parameterSnippets.build()));
                  initializeMethod.body().addSnippet("this.%s = %s;",
                      memberSelectSnippet, initializeSetSnippet);
                } else {
                  // TODO(user): Implement producer set bindings.
                  throw new IllegalStateException("producer set bindings not implemented yet");
                }
                break;
              case MAP:
                if (Sets.filter(bindings, Predicates.instanceOf(ProductionBinding.class))
                    .isEmpty()) {
                  @SuppressWarnings("unchecked")  // checked by the instanceof filter above
                  ImmutableSet<ProvisionBinding> provisionBindings =
                      (ImmutableSet<ProvisionBinding>) bindings;
                  for (ProvisionBinding provisionBinding : provisionBindings) {
                    if (!isNonProviderMap(provisionBinding)) {
                      initializeMethod.body().addSnippet("this.%s = %s;",
                        multibindingContributionSnippets.get(provisionBinding),
                          initializeFactoryForProvisionBinding(provisionBinding,
                              input.componentDescriptor().dependencyMethodIndex(),
                              componentContributionFields,
                              memberSelectSnippets));
                    }
                  }
                  if (!provisionBindings.isEmpty()) {
                    Snippet initializeMapSnippet = initializeMapBinding(
                        memberSelectSnippets, multibindingContributionSnippets, provisionBindings);
                    initializeMethod.body().addSnippet("this.%s = %s;",
                        memberSelectSnippet, initializeMapSnippet);
                  }
                } else {
                  // TODO(user): Implement producer map bindings.
                  throw new IllegalStateException("producer map bindings not implemented yet");
                }
                break;
              case UNIQUE:
                ContributionBinding binding = Iterables.getOnlyElement(bindings);
                if (binding instanceof ProvisionBinding) {
                  ProvisionBinding provisionBinding = (ProvisionBinding) binding;
                  if (!provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
                      || provisionBinding.scope().isPresent()) {
                    initializeMethod.body().addSnippet("this.%s = %s;",
                        memberSelectSnippet,
                        initializeFactoryForProvisionBinding(provisionBinding,
                            input.componentDescriptor().dependencyMethodIndex(),
                            componentContributionFields, memberSelectSnippets));
                  }
                } else if (binding instanceof ProductionBinding) {
                  ProductionBinding productionBinding = (ProductionBinding) binding;
                  initializeMethod.body().addSnippet("this.%s = %s;",
                      memberSelectSnippet,
                      initializeFactoryForProductionBinding(productionBinding,
                          input,
                          input.componentDescriptor().dependencyMethodIndex(),
                          componentContributionFields, memberSelectSnippets));
                } else {
                  throw new IllegalStateException();
                }
                break;
              default:
                throw new IllegalStateException();
            }
            break;
          case MEMBERS_INJECTION:
            MembersInjectionBinding binding = Iterables.getOnlyElement(
                input.resolvedBindings().get(bindingKey).membersInjectionBindings());
            initializeMethod.body().addSnippet("this.%s = %s;",
                memberSelectSnippet,
                initializeMembersInjectorForBinding(binding, memberSelectSnippets));
            break;
          default:
            throw new AssertionError();
        }
      }
    }
  }

  private static FrameworkField frameworkFieldForSyntheticProvisionBinding(BindingKey bindingKey,
      int contributionNumber, ContributionBinding contributionBinding) throws AssertionError {
    FrameworkField contributionBindingField;
    switch (contributionBinding.bindingType()) {
      case MAP:
        contributionBindingField = FrameworkField.createForMapBindingContribution(
            Provider.class,
            BindingKey.create(bindingKey.kind(), contributionBinding.key()),
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
        break;
      case SET:
        contributionBindingField = FrameworkField.createWithTypeFromKey(
            Provider.class,
            bindingKey,
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
        break;
      case UNIQUE:
        contributionBindingField = FrameworkField.createWithTypeFromKey(
            Provider.class,
            bindingKey,
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
        break;
      default:
        throw new AssertionError();
    }
    return contributionBindingField;
  }

  private static boolean isSytheticProvisionBinding(ContributionBinding contributionBinding) {
    return !(contributionBinding instanceof ProvisionBinding
        && ((ProvisionBinding) contributionBinding)
            .bindingKind().equals(SYNTHETIC_PROVISON));
  }

  private static Class<?> frameworkClassForResolvedBindings(ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        for (ContributionBinding binding : resolvedBindings.contributionBindings()) {
          if (binding instanceof ProductionBinding) {
            return Producer.class;
          }
        }
        return Provider.class;
      case MEMBERS_INJECTION:
        return MembersInjector.class;
      default:
        throw new AssertionError();
    }
  }

  private FrameworkField frameworkFieldForResolvedBindings(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();
    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        ImmutableSet<? extends ContributionBinding> contributionBindings =
            resolvedBindings.contributionBindings();
        BindingType bindingsType = ProvisionBinding.bindingTypeFor(contributionBindings);
        switch (bindingsType) {
          case SET:
          case MAP:
            return FrameworkField.createWithTypeFromKey(
                frameworkClassForResolvedBindings(resolvedBindings),
                bindingKey,
                KeyVariableNamer.INSTANCE.apply(bindingKey.key()));
          case UNIQUE:
            ContributionBinding binding = Iterables.getOnlyElement(contributionBindings);
            return FrameworkField.createWithTypeFromKey(
                frameworkClassForResolvedBindings(resolvedBindings),
                bindingKey,
                binding.bindingElement().accept(new ElementKindVisitor6<String, Void>() {
                  @Override
                  public String visitExecutableAsConstructor(ExecutableElement e, Void p) {
                    return e.getEnclosingElement().accept(this, null);
                  }

                  @Override
                  public String visitExecutableAsMethod(ExecutableElement e, Void p) {
                    return e.getSimpleName().toString();
                  }

                  @Override
                  public String visitType(TypeElement e, Void p) {
                    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,
                        e.getSimpleName().toString());
                  }
                }, null));
          default:
            throw new AssertionError();
        }
      case MEMBERS_INJECTION:
        return FrameworkField.createWithTypeFromKey(
            MembersInjector.class,
            bindingKey,
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,
                Iterables.getOnlyElement(resolvedBindings.bindings())
                .bindingElement().getSimpleName().toString()));
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeFactoryForProvisionBinding(ProvisionBinding binding,
      ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex,
      Map<TypeElement, FieldWriter> contributionFields,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets) {
    switch(binding.bindingKind()) {
      case COMPONENT:
        return Snippet.format("%s.<%s>create(this)",
            ClassName.fromClass(InstanceFactory.class),
            TypeNames.forTypeMirror(binding.key().type()));
      case COMPONENT_PROVISION:
        if (binding.nullableType().isPresent()
            || nullableValidationType.equals(Diagnostic.Kind.WARNING)) {
          Snippet nullableSnippet = binding.nullableType().isPresent()
              ? Snippet.format("@%s ", TypeNames.forTypeMirror(binding.nullableType().get()))
              : Snippet.format("");
          return Snippet.format(Joiner.on('\n').join(
            "new %s<%2$s>() {",
            "  %5$s@Override public %2$s get() {",
            "    return %3$s.%4$s();",
            "  }",
            "}"),
            ClassName.fromClass(Factory.class),
            TypeNames.forTypeMirror(binding.key().type()),
            contributionFields.get(dependencyMethodIndex.get(binding.bindingElement())).name(),
            binding.bindingElement().getSimpleName().toString(),
            nullableSnippet);
        } else {
          // TODO(sameb): This throws a very vague NPE right now.  The stack trace doesn't
          // help to figure out what the method or return type is.  If we include a string
          // of the return type or method name in the error message, that can defeat obfuscation.
          // We can easily include the raw type (no generics) + annotation type (no values),
          // using .class & String.format -- but that wouldn't be the whole story.
          // What should we do?
          StringLiteral failMsg =
              StringLiteral.forValue(CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          return Snippet.format(Joiner.on('\n').join(
            "new %s<%2$s>() {",
            "  @Override public %2$s get() {",
            "    %2$s provided = %3$s.%4$s();",
            "    if (provided == null) {",
            "      throw new NullPointerException(%5$s);",
            "    }",
            "    return provided;",
            "  }",
            "}"),
            ClassName.fromClass(Factory.class),
            TypeNames.forTypeMirror(binding.key().type()),
            contributionFields.get(dependencyMethodIndex.get(binding.bindingElement())).name(),
            binding.bindingElement().getSimpleName().toString(),
            failMsg);
        }
      case INJECTION:
      case PROVISION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
        if (binding.bindingKind().equals(PROVISION)) {
          parameters.add(
              Snippet.format(contributionFields.get(binding.contributedBy().get()).name()));
        }
        if (binding.memberInjectionRequest().isPresent()) {
          parameters.add(memberSelectSnippets.get(
              BindingKey.forDependencyRequest(binding.memberInjectionRequest().get())));
        }
        parameters.addAll(getDependencyParameters(binding.dependencies(), memberSelectSnippets));

        return binding.scope().isPresent()
            ? Snippet.format("%s.create(%s.create(%s))",
                ClassName.fromClass(ScopedProvider.class),
                factoryNameForProvisionBinding(binding),
                Snippet.makeParametersSnippet(parameters))
            : Snippet.format("%s.create(%s)",
                factoryNameForProvisionBinding(binding),
                Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeFactoryForProductionBinding(ProductionBinding binding,
      BindingGraph bindingGraph,
      ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex,
      Map<TypeElement, FieldWriter> contributionFields,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets) {
    switch (binding.bindingKind()) {
      case COMPONENT_PRODUCTION:
        return Snippet.format(Joiner.on('\n').join(
            "new %s<%2$s>() {",
            "  @Override public %3$s<%2$s> get() {",
            "    return %4$s.%5$s();",
            "  }",
            "}"),
            ClassName.fromClass(Producer.class),
            TypeNames.forTypeMirror(binding.key().type()),
            ClassName.fromClass(ListenableFuture.class),
            contributionFields.get(dependencyMethodIndex.get(binding.bindingElement())).name(),
            binding.bindingElement().getSimpleName().toString());
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 2);
        parameters.add(Snippet.format(contributionFields.get(binding.bindingTypeElement()).name()));
        parameters.add(Snippet.format(contributionFields.get(
            bindingGraph.componentDescriptor().executorDependency().get()).name()));
        parameters.addAll(getProducerDependencyParameters(
            bindingGraph, binding.dependencies(), memberSelectSnippets));

        return Snippet.format("new %s(%s)",
            factoryNameForProductionBinding(binding),
            Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private static Snippet initializeMembersInjectorForBinding(
      MembersInjectionBinding binding,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets) {
    if (binding.injectionSites().isEmpty()) {
      if (binding.parentInjectorRequest().isPresent()) {
        DependencyRequest parentInjectorRequest = binding.parentInjectorRequest().get();
        return Snippet.format("%s.delegatingTo(%s)",
            ClassName.fromClass(MembersInjectors.class),
            memberSelectSnippets.get(BindingKey.forDependencyRequest(parentInjectorRequest)));
      } else {
        return Snippet.format("%s.noOp()",
            ClassName.fromClass(MembersInjectors.class));
      }
    } else {
      List<Snippet> parameters = getDependencyParameters(
          Sets.union(binding.parentInjectorRequest().asSet(), binding.dependencies()),
          memberSelectSnippets);
      return Snippet.format("%s.create(%s)",
          membersInjectorNameForMembersInjectionBinding(binding),
          Snippet.makeParametersSnippet(parameters));
    }
  }

  private static List<Snippet> getDependencyParameters(
      Iterable<DependencyRequest> dependencies,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
         SourceFiles.indexDependenciesByUnresolvedKey(dependencies).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(new Function<DependencyRequest, BindingKey>() {
            @Override public BindingKey apply(DependencyRequest request) {
              return BindingKey.forDependencyRequest(request);
            }
          })
          .toSet());
      parameters.add(memberSelectSnippets.get(key));
    }
    return parameters.build();
  }

  private static List<Snippet> getProducerDependencyParameters(
      BindingGraph bindingGraph,
      Iterable<DependencyRequest> dependencies,
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
         SourceFiles.indexDependenciesByUnresolvedKey(dependencies).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(new Function<DependencyRequest, BindingKey>() {
            @Override public BindingKey apply(DependencyRequest request) {
              return BindingKey.forDependencyRequest(request);
            }
          }));
      ResolvedBindings resolvedBindings = bindingGraph.resolvedBindings().get(key);
      Class<?> frameworkClass =
          DependencyRequestMapper.FOR_PRODUCER.getFrameworkClass(requestsForKey);
      if (frameworkClassForResolvedBindings(resolvedBindings).equals(Provider.class)
          && frameworkClass.equals(Producer.class)) {
        parameters.add(Snippet.format(
            "%s.producerFromProvider(%s)",
            ClassName.fromClass(Producers.class),
            memberSelectSnippets.get(key)));
      } else {
        parameters.add(memberSelectSnippets.get(key));
      }
    }
    return parameters.build();
  }

  private Snippet initializeMapBinding(
      ImmutableMap<BindingKey, Snippet> memberSelectSnippets,
      ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets,
      Set<ProvisionBinding> bindings) {
    Iterator<ProvisionBinding> iterator = bindings.iterator();
    // get type information from first binding in iterator
    ProvisionBinding firstBinding = iterator.next();
    if (isNonProviderMap(firstBinding)) {
      return Snippet.format("%s.create(%s)",
          ClassName.fromClass(MapFactory.class),
          memberSelectSnippets.get(BindingKey.forDependencyRequest(
              Iterables.getOnlyElement(firstBinding.dependencies()))));
    } else {
      DeclaredType mapType = asDeclared(firstBinding.key().type());
      TypeMirror mapKeyType = Util.getKeyTypeOfMap(mapType);
      TypeMirror mapValueType = Util.getProvidedValueTypeOfMap(mapType); // V of Map<K, Provider<V>>
      StringBuilder snippetFormatBuilder = new StringBuilder("%s.<%s, %s>builder(%d)");
      for (int i = 0; i < bindings.size(); i++) {
        snippetFormatBuilder.append("\n    .put(%s, %s)");
      }
      snippetFormatBuilder.append("\n    .build()");

      List<Object> argsBuilder = Lists.newArrayList();
      argsBuilder.add(ClassName.fromClass(MapProviderFactory.class));
      argsBuilder.add(TypeNames.forTypeMirror(mapKeyType));
      argsBuilder.add(TypeNames.forTypeMirror(mapValueType));
      argsBuilder.add(bindings.size());

      writeEntry(argsBuilder, firstBinding, multibindingContributionSnippets.get(firstBinding));
      while (iterator.hasNext()) {
        ProvisionBinding binding = iterator.next();
        writeEntry(argsBuilder, binding, multibindingContributionSnippets.get(binding));
      }

      return Snippet.format(snippetFormatBuilder.toString(),
          argsBuilder.toArray(new Object[0]));
    }
  }

  // add one map entry for map Provider in Constructor
  private void writeEntry(List<Object> argsBuilder, Binding binding,
      Snippet factory) {
    AnnotationMirror mapKeyAnnotationMirror =
        Iterables.getOnlyElement(getMapKeys(binding.bindingElement()));
    Map<? extends ExecutableElement, ? extends AnnotationValue> map =
        mapKeyAnnotationMirror.getElementValues();
    MapKey mapKey =
        mapKeyAnnotationMirror.getAnnotationType().asElement().getAnnotation(MapKey.class);
    if (!mapKey.unwrapValue()) {// wrapped key case
      FluentIterable<AnnotationValue> originIterable = FluentIterable.from(
          AnnotationMirrors.getAnnotationValuesWithDefaults(mapKeyAnnotationMirror).values());
      FluentIterable<Snippet> annotationValueNames =
          originIterable.transform(new Function<AnnotationValue, Snippet>() {
            @Override
            public Snippet apply(AnnotationValue value) {
              return getValueSnippet(value);
            }
          });
      ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
      for (Snippet snippet : annotationValueNames) {
        snippets.add(snippet);
      }
      argsBuilder.add(Snippet.format("%sCreator.create(%s)",
          TypeNames.forTypeMirror(mapKeyAnnotationMirror.getAnnotationType()),
          Snippet.makeParametersSnippet(snippets.build())));
      argsBuilder.add(factory);
    } else { // unwrapped key case
      argsBuilder.add(Iterables.getOnlyElement(map.entrySet()).getValue());
      argsBuilder.add(factory);
    }
  }

  // Get the Snippet representation of a Annotation Value
  // TODO(user) write corresponding test to verify the AnnotationValueVisitor is right
  private Snippet getValueSnippet(AnnotationValue value) {
    AnnotationValueVisitor<Snippet, Void> mapKeyVisitor =
        new SimpleAnnotationValueVisitor6<Snippet, Void>() {
          @Override
          public Snippet visitEnumConstant(VariableElement c, Void p) {
            return Snippet.format("%s.%s",
                TypeNames.forTypeMirror(c.getEnclosingElement().asType()), c.getSimpleName());
          }

          @Override
          public Snippet visitAnnotation(AnnotationMirror a, Void p) {
            if (a.getElementValues().isEmpty()) {
              return Snippet.format("@%s", TypeNames.forTypeMirror(a.getAnnotationType()));
            } else {
              Map<ExecutableElement, AnnotationValue> map =
                  AnnotationMirrors.getAnnotationValuesWithDefaults(a);
              // build "@Annotation(a = , b = , c = ))
              ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
              for (Entry<ExecutableElement, AnnotationValue> entry : map.entrySet()) {
                snippets.add(Snippet.format("%s = %s",
                    TypeNames.forTypeMirror(entry.getKey().asType()),
                    getValueSnippet(entry.getValue())));

              }
              return Snippet.format("@%s(%s)", TypeNames.forTypeMirror(a.getAnnotationType()),
                  Snippet.makeParametersSnippet(snippets.build()));
            }
          }

          @Override
          public Snippet visitType(TypeMirror t, Void p) {
            return Snippet.format("%s", TypeNames.forTypeMirror(t));
          }

          @Override
          public Snippet visitString(String s, Void p) {
            return Snippet.format("\"%s\"", s);
          }

          @Override
          protected Snippet defaultAction(Object o, Void v) {
            return Snippet.format("%s", o);
          }

          @Override
          public Snippet visitArray(List<? extends AnnotationValue> values, Void v) {
            ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
            for (int i = 0; i < values.size(); i++) {
              snippets.add(values.get(i).accept(this, null));
            }
            return Snippet.format("[%s]", Snippet.makeParametersSnippet(snippets.build()));
          }
        };
    return value.accept(mapKeyVisitor, null);
  }

  private boolean isNonProviderMap(Binding binding) {
    TypeMirror bindingType = binding.key().type();
    return MoreTypes.isTypeOf(Map.class, bindingType) // Implicitly guarantees a declared type.
        && !MoreTypes.isTypeOf(Provider.class, asDeclared(bindingType).getTypeArguments().get(1));
  }

  private boolean hasNoArgsConstructor(TypeElement type) {
    if (type.getNestingKind().equals(TOP_LEVEL)
        || type.getNestingKind().equals(MEMBER) && type.getModifiers().contains(STATIC)) {
      for (Element enclosed : type.getEnclosedElements()) {
        if (enclosed.getKind().equals(CONSTRUCTOR)) {
          if (((ExecutableElement) enclosed).getParameters().isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
