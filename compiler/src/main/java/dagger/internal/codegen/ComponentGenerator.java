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

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Factory;
import dagger.MembersInjector;
import dagger.internal.InstanceFactory;
import dagger.internal.ScopedProvider;
import dagger.internal.SetFactory;
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
import dagger.internal.codegen.writer.VoidName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.internal.codegen.writer.VoidName;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static dagger.internal.codegen.DependencyRequest.Kind.MEMBERS_INJECTOR;
import static dagger.internal.codegen.ProvisionBinding.Kind.COMPONENT;
import static dagger.internal.codegen.ProvisionBinding.Kind.COMPONENT_PROVISION;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generateMembersInjectorNamesForBindings;
import static dagger.internal.codegen.SourceFiles.generateProviderNamesForBindings;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForMembersInjectionBinding;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<ComponentDescriptor> {
  private final Elements elements;
  private final Key.Factory keyFactory;

  ComponentGenerator(Filer filer, Elements elements, Key.Factory keyFactory) {
    super(filer);
    this.elements = elements;
    this.keyFactory = keyFactory;
  }

  @Override
  ClassName nameGeneratedType(ComponentDescriptor input) {
    ClassName componentDefinitionClassName =
        ClassName.fromTypeElement(input.componentDefinitionType());
    return componentDefinitionClassName.topLevelClassName().peerNamed(
        "Dagger_" + componentDefinitionClassName.classFileName());
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(ComponentDescriptor input) {
    return ImmutableSet.of(input.componentDefinitionType());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ComponentDescriptor input) {
    return Optional.of(input.componentDefinitionType());
  }

  @Override
  JavaWriter write(ClassName componentName, ComponentDescriptor input)  {
    ClassName componentDefinitionTypeName =
        ClassName.fromTypeElement(input.componentDefinitionType());

    JavaWriter writer = JavaWriter.inPackage(componentName.packageName());
    ClassWriter componentWriter = writer.addClass(componentName.simpleName());
    if (elements.getTypeElement(Generated.class.getCanonicalName()) != null) {
      componentWriter.annotate(Generated.class)
          .setValue(ComponentProcessor.class.getCanonicalName());
    }
    componentWriter.addModifiers(PUBLIC, FINAL);
    componentWriter.addImplementedType(componentDefinitionTypeName);

    ClassWriter builderWriter = componentWriter.addNestedClass("Builder");
    builderWriter.addModifiers(PUBLIC, STATIC, FINAL);

    builderWriter.addConstructor().addModifiers(PRIVATE);

    MethodWriter builderFactoryMethod = componentWriter.addMethod(builderWriter, "builder");
    builderFactoryMethod.addModifiers(PUBLIC, STATIC);
    builderFactoryMethod.body().addSnippet("return new %s();", builderWriter.name());

    ImmutableSetMultimap<Key, ProvisionBinding> resolvedProvisionBindings =
        input.resolvedProvisionBindings();
    ImmutableMap<Key, MembersInjectionBinding> resolvedMembersInjectionBindings =
        input.resolvedMembersInjectionBindings();

    ImmutableBiMap<Key, String> providerNames =
        generateProviderNamesForBindings(resolvedProvisionBindings);
    ImmutableBiMap<Key, String> membersInjectorNames =
        generateMembersInjectorNamesForBindings(resolvedMembersInjectionBindings);

    // the full set of types that calling code uses to construct a component instance
    ImmutableBiMap<TypeElement, String> componentContributionNames =
        ImmutableBiMap.copyOf(Maps.asMap(
            Sets.union(input.moduleDependencies(), input.dependencies()),
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

    for (Entry<TypeElement, String> entry : componentContributionNames.entrySet()) {
      TypeElement moduleElement = entry.getKey();
      String moduleName = entry.getValue();
      componentWriter.addField(moduleElement, moduleName)
          .addModifiers(PRIVATE, FINAL);
      builderWriter.addField(moduleElement, moduleName)
          .addModifiers(PRIVATE);
      constructorWriter.body()
          .addSnippet("this.%1$s = builder.%1$s;", moduleName);
      MethodWriter builderMethod = builderWriter.addMethod(builderWriter, moduleName);
      builderMethod.addModifiers(PUBLIC);
      builderMethod.addParameter(moduleElement, moduleName);
      builderMethod.body()
          .addSnippet("if (%s == null) {", moduleName)
          .addSnippet("  throw new NullPointerException(%s);", StringLiteral.forValue(moduleName))
          .addSnippet("}")
          .addSnippet("this.%1$s = %1$s;", moduleName)
          .addSnippet("return this;");
      if (Util.getNoArgsConstructor(moduleElement) == null) {
        requiresBuilder = true;
        buildMethod.body()
            .addSnippet("if (%s == null) {", moduleName)
            .addSnippet("  throw new IllegalStateException(\"%s must be set\");", moduleName)
            .addSnippet("}");
      } else {
        buildMethod.body()
            .addSnippet("if (%s == null) {", moduleName)
            .addSnippet("  this.%s = new %s();",
                moduleName, ClassName.fromTypeElement(moduleElement))
            .addSnippet("}");
      }
    }

    buildMethod.body().addSnippet("return new %s(this);", componentWriter.name());

    // this will eventually need to be modules & components
    if (!requiresBuilder) {
      MethodWriter factoryMethod = componentWriter.addMethod(componentDefinitionTypeName, "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod.body().addSnippet("return builder().build();");
    }

    for (Entry<Key, String> providerEntry : providerNames.entrySet()) {
      Key key = providerEntry.getKey();
      // TODO(gak): provide more elaborate information about which requests relate
      TypeName providerTypeReferece = ParameterizedTypeName.create(
          ClassName.fromClass(Provider.class),
          TypeNames.forTypeMirror(key.type()));
      FieldWriter providerField =
          componentWriter.addField(providerTypeReferece, providerEntry.getValue());
      providerField.addModifiers(PRIVATE, FINAL);
    }
    for (Entry<Key, String> providerEntry : membersInjectorNames.entrySet()) {
      Key key = providerEntry.getKey();
      // TODO(gak): provide more elaborate information about which requests relate
      TypeName membersInjectorTypeReferece = ParameterizedTypeName.create(
          ClassName.fromClass(MembersInjector.class),
          TypeNames.forTypeMirror(key.type()));
      FieldWriter membersInjectorField =
          componentWriter.addField(membersInjectorTypeReferece, providerEntry.getValue());
      membersInjectorField.addModifiers(PRIVATE, FINAL);
    }

    for (FrameworkKey frameworkKey : input.initializationOrdering()) {
      Key key = frameworkKey.key();
      if (frameworkKey.frameworkClass().equals(Provider.class)) {
        Set<ProvisionBinding> bindings = resolvedProvisionBindings.get(key);
        if (ProvisionBinding.isSetBindingCollection(bindings)) {
          ImmutableList.Builder<Snippet> setFactoryParameters = ImmutableList.builder();
          for (ProvisionBinding binding : bindings) {
            setFactoryParameters.add(initializeFactoryForBinding(
                binding, componentContributionNames, providerNames,membersInjectorNames));
          }
          constructorWriter.body().addSnippet("this.%s = %s.create(%n%s);",
              providerNames.get(key),
              ClassName.fromClass(SetFactory.class),
              Snippet.makeParametersSnippet(setFactoryParameters.build()));
        } else {
          ProvisionBinding binding = Iterables.getOnlyElement(bindings);
          constructorWriter.body().addSnippet("this.%s = %s;",
              providerNames.get(key),
              initializeFactoryForBinding(
                  binding, componentContributionNames, providerNames, membersInjectorNames));
        }
      } else if (frameworkKey.frameworkClass().equals(MembersInjector.class)) {
        constructorWriter.body().addSnippet("this.%s = %s;",
            membersInjectorNames.get(key),
            initializeMembersInjectorForBinding(resolvedMembersInjectionBindings.get(key),
                providerNames, membersInjectorNames));
      } else {
        throw new IllegalStateException(
            "unknown framework class: " + frameworkKey.frameworkClass());
      }
    }

    for (DependencyRequest interfaceRequest : input.interfaceRequests()) {
      ExecutableElement requestElement = (ExecutableElement) interfaceRequest.requestElement();
      MethodWriter interfaceMethod = requestElement.getReturnType().getKind().equals(VOID)
          ? componentWriter.addMethod(VoidName.VOID, requestElement.getSimpleName().toString())
          : componentWriter.addMethod(requestElement.getReturnType(),
              requestElement.getSimpleName().toString());
      interfaceMethod.annotate(Override.class);
      interfaceMethod.addModifiers(PUBLIC);
      if (interfaceRequest.kind().equals(MEMBERS_INJECTOR)) {
        String membersInjectorName = membersInjectorNames.get(interfaceRequest.key());
        VariableElement parameter = Iterables.getOnlyElement(requestElement.getParameters());
        Name parameterName = parameter.getSimpleName();
        interfaceMethod.addParameter(
            TypeNames.forTypeMirror(parameter.asType()), parameterName.toString());
        interfaceMethod.body()
            .addSnippet("%s.injectMembers(%s);", membersInjectorName, parameterName);
        if (!requestElement.getReturnType().getKind().equals(VOID)) {
          interfaceMethod.body().addSnippet("return %s;", parameterName);
        }
      } else {
        // provision requests
        String providerName = providerNames.get(interfaceRequest.key());

        // look up the provider in the Key->name map and invoke.  Done.
        interfaceMethod.body().addSnippet("return %s;",
            frameworkTypeUsageStatement(providerName, interfaceRequest.kind()));
      }
    }

    return writer;
  }

  private Snippet initializeFactoryForBinding(ProvisionBinding binding,
      ImmutableBiMap<TypeElement, String> moduleNames,
      ImmutableBiMap<Key, String> providerNames,
      ImmutableBiMap<Key, String> membersInjectorNames) {
    if (binding.bindingKind().equals(COMPONENT)) {
      return Snippet.format("%s.<%s>create(this)",
          ClassName.fromClass(InstanceFactory.class),
          TypeNames.forTypeMirror(binding.providedKey().type()));
    } else if (binding.bindingKind().equals(COMPONENT_PROVISION)) {
      return Snippet.format(Joiner.on('\n').join(
          "new %s<%2$s>() {",
          "  @Override public %2$s get() {",
          "    return %3$s.%4$s();",
          "  }",
          "}"),
          ClassName.fromClass(Factory.class),
          TypeNames.forTypeMirror(binding.providedKey().type()),
          moduleNames.get(binding.bindingTypeElement()),
          binding.bindingElement().getSimpleName().toString());
    } else {
      List<String> parameters = Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
      if (binding.bindingKind().equals(PROVISION)) {
        parameters.add(moduleNames.get(binding.bindingTypeElement()));
      }
      if (binding.requiresMemberInjection()) {
        String membersInjectorName =
            membersInjectorNames.get(keyFactory.forType(binding.providedKey().type()));
        if (membersInjectorName != null) {
          parameters.add(membersInjectorName);
        } else {
          throw new UnsupportedOperationException("Non-generated MembersInjector");
        }
      }
      parameters.addAll(
          getDependencyParameters(binding.dependencies(), providerNames, membersInjectorNames));
      return binding.scope().isPresent()
          ? Snippet.format("%s.create(new %s(%s))",
              ClassName.fromClass(ScopedProvider.class),
              factoryNameForProvisionBinding(binding).toString(),
              Joiner.on(", ").join(parameters))
          : Snippet.format("new %s(%s)",
              factoryNameForProvisionBinding(binding).toString(),
              Joiner.on(", ").join(parameters));
    }
  }

  private static Snippet initializeMembersInjectorForBinding(
      MembersInjectionBinding binding,
      ImmutableBiMap<Key, String> providerNames,
      ImmutableBiMap<Key, String> membersInjectorNames) {
    List<String> parameters = getDependencyParameters(binding.dependencySet(),
        providerNames, membersInjectorNames);
    return Snippet.format("new %s(%s)",
       membersInjectorNameForMembersInjectionBinding(binding).toString(),
        Joiner.on(", ").join(parameters));
  }

  private static List<String> getDependencyParameters(Iterable<DependencyRequest> dependencies,
      ImmutableBiMap<Key, String> providerNames,
      ImmutableBiMap<Key, String> membersInjectorNames) {
    ImmutableList.Builder<String> parameters = ImmutableList.builder();
    for (DependencyRequest dependency : dependencies) {
        parameters.add(dependency.kind().equals(MEMBERS_INJECTOR)
            ? membersInjectorNames.get(dependency.key())
            : providerNames.get(dependency.key()));
    }
    return parameters.build();
  }
}
