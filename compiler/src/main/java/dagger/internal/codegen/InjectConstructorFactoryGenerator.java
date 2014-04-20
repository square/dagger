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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.squareup.javawriter.JavaWriter;
import dagger.Factory;
import dagger.MembersInjector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static com.squareup.javawriter.JavaWriter.stringLiteral;
import static com.squareup.javawriter.JavaWriter.type;
import static dagger.internal.codegen.SourceFiles.collectImportsFromDependencies;
import static dagger.internal.codegen.SourceFiles.flattenVariableMap;
import static dagger.internal.codegen.SourceFiles.generateProviderNames;
import static dagger.internal.codegen.SourceFiles.providerUsageStatement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for
 * {@link Inject} constructors.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectConstructorFactoryGenerator extends SourceFileGenerator<ProvisionBinding> {
  private final ProviderTypeRepository providerTypeRepository;

  InjectConstructorFactoryGenerator(Filer filer, ProviderTypeRepository providerTypeRepository) {
    super(filer);
    this.providerTypeRepository = providerTypeRepository;
  }

  @Override
  ClassName nameGeneratedType(ProvisionBinding binding) {
    TypeElement providedElement = binding.enclosingType();
    ClassName providedClassName = ClassName.fromTypeElement(providedElement);
    return providedClassName.peerNamed(providedClassName.simpleName() + "$$Factory");
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(ProvisionBinding binding) {
    return ImmutableSet.of(binding.bindingElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProvisionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  void write(ClassName factoryClassName, JavaWriter writer, ProvisionBinding binding)
      throws IOException {
    ClassName providedClassName = ClassName.fromTypeElement(binding.enclosingType());

    writer.emitPackage(factoryClassName.packageName());

    List<ClassName> importsBuilder = new ArrayList<ClassName>();
    importsBuilder.addAll(collectImportsFromDependencies(factoryClassName, binding.dependencies()));
    importsBuilder.add(ClassName.fromClass(Factory.class));
    importsBuilder.add(ClassName.fromClass(Generated.class));
    if (binding.requiresMemberInjection()) {
      importsBuilder.add(ClassName.fromClass(MembersInjector.class));
    }
    ImmutableSortedSet<String> imports = FluentIterable.from(importsBuilder)
        .transform(Functions.toStringFunction())
        .toSortedSet(Ordering.natural());
    writer.emitImports(imports).emitEmptyLine();

    writer.emitAnnotation(Generated.class, stringLiteral(InjectProcessor.class.getName()))
        .beginType(factoryClassName.simpleName(), "class", EnumSet.of(PUBLIC, FINAL), null,
            type(Factory.class, Util.typeToString(binding.providedKey().type())));

    final ImmutableBiMap<Key, String> providerNames =
        generateProviderNames(ImmutableList.of(binding));

    ImmutableMap.Builder<String, String> variableMapBuilder =
        new ImmutableMap.Builder<String, String>();
    if (binding.requiresMemberInjection()) {
      variableMapBuilder.put("membersInjector",
          type(MembersInjector.class, providedClassName.simpleName()));
    }
    ImmutableMap<String, String> variableMap = variableMapBuilder
        .putAll(providersAsVariableMap(providerNames))
        .build();

    if (binding.requiresMemberInjection()) {
      writeMembersInjectorField(writer, providedClassName);
    }
    writeProviderFields(writer, providerNames);

    writeConstructor(writer, variableMap);

    writer.emitAnnotation(Override.class)
        .beginMethod(providedClassName.simpleName(), "get", EnumSet.of(PUBLIC));
    String parameterString =
        Joiner.on(", ").join(FluentIterable.from(binding.dependencies())
            .transform(new Function<DependencyRequest, String>() {
              @Override public String apply(DependencyRequest input) {
                return providerUsageStatement(providerNames.get(input.key()), input.kind());
              }
            }));
    if (binding.requiresMemberInjection()) {
      writer.emitStatement("%1$s instance = new %1$s(%2$s)",
          providedClassName.simpleName(), parameterString);
      writer.emitStatement("membersInjector.injectMembers(instance)");
      writer.emitStatement("return instance");
    } else {
      writer.emitStatement("return new %s(%s)", providedClassName.simpleName(), parameterString);
    }
    writer.endMethod().emitEmptyLine();

    writeToString(writer, providedClassName);

    writer.endType();
  }

  private void writeMembersInjectorField(JavaWriter writer, ClassName providedClassName)
      throws IOException {
    writer.emitField(type(MembersInjector.class, providedClassName.fullyQualifiedName()),
        "membersInjector", EnumSet.of(PRIVATE, FINAL));
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

  private void writeConstructor(JavaWriter writer, Map<String, String> variableMap)
      throws IOException {
    writer.beginConstructor(EnumSet.of(PUBLIC),
        flattenVariableMap(variableMap),
        ImmutableList.<String>of());
    for (String variableName : variableMap.keySet()) {
      writer.emitStatement("assert %s != null", variableName);
      writer.emitStatement("this.%1$s = %1$s", variableName);
    }
    writer.endConstructor().emitEmptyLine();
  }

  private void writeToString(JavaWriter writer, ClassName providedClassName) throws IOException {
    writer.emitAnnotation(Override.class)
        .beginMethod("String", "toString", EnumSet.of(PUBLIC))
        .emitStatement("return \"%s<%s>\"",
            Factory.class.getSimpleName(), providedClassName.simpleName())
        .endMethod();
  }

  private Map<String, String> providersAsVariableMap(ImmutableBiMap<Key, String> providerNames) {
    return Maps.transformValues(providerNames.inverse(), new Function<Key, String>() {
      @Override public String apply(Key key) {
        return providerTypeString(key);
      }
    });
  }

  private String providerTypeString(Key key) {
    return Util.typeToString(providerTypeRepository.getProviderType(key));
  }
}
