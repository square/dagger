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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.squareup.javawriter.JavaWriter.stringLiteral;
import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.collectImportsFromDependencies;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.flattenVariableMap;
import static dagger.internal.codegen.SourceFiles.generateProviderNamesForBindings;
import static dagger.internal.codegen.SourceFiles.providerUsageStatement;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javawriter.JavaWriter;

import dagger.Component;
import dagger.internal.SetFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<ComponentDescriptor> {
  private final ProviderTypeRepository providerTypeRepository;

  ComponentGenerator(Filer filer, ProviderTypeRepository providerTypeRepository) {
    super(filer);
    this.providerTypeRepository = providerTypeRepository;
  }

  @Override
  ClassName nameGeneratedType(ComponentDescriptor input) {
    ClassName componentDefinitionClassName =
        ClassName.fromTypeElement(input.componentDefinitionType());
    return componentDefinitionClassName.peerNamed(
        "Dagger_" + componentDefinitionClassName.simpleName());
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
  void write(ClassName componentName, JavaWriter writer, ComponentDescriptor input)
      throws IOException {
    writer.emitPackage(componentName.packageName());

    writeImports(writer, componentName, input.provisionRequests(),
        input.resolvedBindings().values());

    writer.emitAnnotation(Generated.class, stringLiteral(ComponentProcessor.class.getName()));
    writer.beginType(componentName.simpleName(), "class", EnumSet.of(PUBLIC, FINAL), null,
        input.componentDefinitionType().getQualifiedName().toString());

    ImmutableSetMultimap<Key, ProvisionBinding> resolvedBindings = input.resolvedBindings();

    ImmutableBiMap<Key, String> providerNames = generateProviderNamesForBindings(resolvedBindings);

    ImmutableBiMap<TypeElement, String> moduleNames =
        ImmutableBiMap.copyOf(Maps.asMap(input.moduleDependencies(), Functions.compose(
            CaseFormat.UPPER_CAMEL.converterTo(LOWER_CAMEL),
            new Function<TypeElement, String>() {
              @Override public String apply(TypeElement input) {
                return input.getSimpleName().toString();
              }
            })));

    writeModuleFields(writer, moduleNames);
    writeProviderFields(writer, providerNames);

    writeConstructor(writer, resolvedBindings, providerNames, moduleNames);

    writeProvisionMethods(writer, input.provisionRequests(), providerNames);

    writer.endType();
  }

  private void writeImports(JavaWriter writer, ClassName factoryClassName,
      ImmutableSet<DependencyRequest> provisionRequests,
      ImmutableCollection<ProvisionBinding> bindings) throws IOException {
    ImmutableSortedSet.Builder<ClassName> importsBuilder =
        ImmutableSortedSet.<ClassName>naturalOrder()
            .addAll(collectImportsFromDependencies(factoryClassName, provisionRequests))
            .add(ClassName.fromClass(Generated.class))
            .add(ClassName.fromClass(Provider.class));
    for (ProvisionBinding binding : bindings) {
      if (binding.provisionType().equals(SET) || binding.provisionType().equals(SET_VALUES)) {
        importsBuilder.add(ClassName.fromClass(SetFactory.class));
      }
      for (TypeElement referencedType : MoreTypes.referencedTypes(binding.providedKey().type())) {
        ClassName className = ClassName.fromTypeElement(referencedType);
        if (!className.packageName().equals("java.lang")
            && !className.packageName().equals(factoryClassName.packageName()))
          importsBuilder.add(className);
      }
    }

    writer.emitImports(Collections2.transform(importsBuilder.build(), Functions.toStringFunction()))
        .emitEmptyLine();
  }

  private void writeModuleFields(JavaWriter writer,
      ImmutableBiMap<TypeElement, String> moduleDependencies) throws IOException {
    for (Entry<TypeElement, String> entry : moduleDependencies.entrySet()) {
      writer.emitField(entry.getKey().getQualifiedName().toString(), entry.getValue(),
          EnumSet.of(PRIVATE, FINAL));
    }
  }

  private void writeProviderFields(JavaWriter writer, ImmutableBiMap<Key, String> providerNames)
      throws IOException {
    for (Entry<Key, String> providerEntry : providerNames.entrySet()) {
      Key key = providerEntry.getKey();
      // TODO(gak): provide more elaborate information about which requests relate
      writer.emitJavadoc(key.toString())
          .emitField(providerTypeString(key), providerEntry.getValue(),
              EnumSet.of(PRIVATE, FINAL));
    }
    writer.emitEmptyLine();
  }

  private void writeConstructor(final JavaWriter writer,
      ImmutableSetMultimap<Key, ProvisionBinding> resolvedBindings,
      ImmutableBiMap<Key, String> providerNames,
      ImmutableBiMap<TypeElement, String> moduleNames)
          throws IOException {
    Map<String, String> variableMap =
        Maps.transformValues(moduleNames.inverse(), new Function<TypeElement, String>() {
      @Override
      public String apply(TypeElement input) {
        return writer.compressType(input.getQualifiedName().toString());
      }
    });

    writer.beginConstructor(EnumSet.of(PUBLIC), flattenVariableMap(variableMap),
        ImmutableList.<String>of());
    for (String variableName : variableMap.keySet()) {
      writer.beginControlFlow("if (%s == null)", variableName)
          .emitStatement("throw new NullPointerException(\"%s\")", variableName)
          .endControlFlow();
      writer.emitStatement("this.%1$s = %1$s", variableName);
    }

    for (Entry<String, Key> providerFieldEntry
        : Lists.reverse(providerNames.inverse().entrySet().asList())) {
      Set<ProvisionBinding> bindings = resolvedBindings.get(providerFieldEntry.getValue());
      if (ProvisionBinding.isSetBindingCollection(bindings)) {
        ImmutableList.Builder<String> setFactoryParameters = ImmutableList.builder();
        for (ProvisionBinding binding : bindings) {
          setFactoryParameters.add(
              initializeFactoryForBinding(writer, binding, moduleNames, providerNames));
        }
        writer.emitStatement("this.%s = SetFactory.create(%n%s)",
            providerFieldEntry.getKey(),
            Joiner.on(",\n").join(setFactoryParameters.build()));
      } else {
        ProvisionBinding binding = Iterables.getOnlyElement(bindings);
        writer.emitStatement("this.%s = %s",
            providerFieldEntry.getKey(),
            initializeFactoryForBinding(writer, binding, moduleNames, providerNames));
      }
    }

    writer.endConstructor().emitEmptyLine();
  }

  private static String initializeFactoryForBinding(JavaWriter writer, ProvisionBinding binding,
      ImmutableBiMap<TypeElement, String> moduleNames,
      ImmutableBiMap<Key, String> providerNames) {
    List<String> parameters = Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
    if (binding.bindingKind().equals(PROVISION)) {
      parameters.add(moduleNames.get(binding.bindingElement().getEnclosingElement()));
    }
    FluentIterable.from(binding.dependenciesByKey().keySet())
        .transform(Functions.forMap(providerNames))
        .copyInto(parameters);
    return String.format("new %s(%s)",
        writer.compressType(factoryNameForProvisionBinding(binding).toString()),
        Joiner.on(", ").join(parameters));
  }

  private void writeProvisionMethods(JavaWriter writer,
      ImmutableSet<DependencyRequest> provisionRequests,
      ImmutableBiMap<Key, String> providerNames) throws IOException {
    for (DependencyRequest provisionRequest : provisionRequests) {
      ExecutableElement requestElement = (ExecutableElement) provisionRequest.requestElement();
      writer.emitAnnotation(Override.class)
          .beginMethod(Util.typeToString(requestElement.getReturnType()),
              requestElement.getSimpleName().toString(),
              Sets.difference(requestElement.getModifiers(), EnumSet.of(ABSTRACT)));

      String providerName = providerNames.get(provisionRequest.key());

      // look up the provider in the Key->name map and invoke.  Done.
      writer.emitStatement("return "
          + providerUsageStatement(providerName, provisionRequest.kind()));
      writer.endMethod();
    }
  }

  private String providerTypeString(Key key) {
    return Util.typeToString(providerTypeRepository.getProviderType(key));
  }
}
