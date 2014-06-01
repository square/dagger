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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javawriter.JavaWriter.stringLiteral;
import static com.squareup.javawriter.JavaWriter.type;
import static dagger.internal.codegen.SourceFiles.collectImportsFromDependencies;
import static dagger.internal.codegen.SourceFiles.flattenVariableMap;
import static dagger.internal.codegen.SourceFiles.generateProviderNamesForDependencies;
import static dagger.internal.codegen.SourceFiles.providerUsageStatement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.squareup.javawriter.JavaWriter;

import dagger.MembersInjector;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final Elements elements;
  private final Types types;

  MembersInjectorGenerator(Filer filer, Elements elements, Types types) {
    super(filer);
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  @Override
  ClassName nameGeneratedType(MembersInjectionBinding binding) {
    ClassName injectedClassName = ClassName.fromTypeElement(binding.injectedType());
    return injectedClassName.peerNamed(injectedClassName.simpleName() + "$$MembersInjector");
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(
      MembersInjectionBinding binding) {
    return FluentIterable.from(binding.injectionSites())
        .transform(new Function<InjectionSite, Element>() {
          @Override public Element apply(InjectionSite injectionSite) {
            return injectionSite.element();
          }
        })
        .toSet();
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(MembersInjectionBinding binding) {
    return Optional.of(binding.injectedType());
  }

  @Override
  void write(ClassName injectorClassName, JavaWriter writer, MembersInjectionBinding binding)
      throws IOException {
    ClassName injectedClassName = ClassName.fromTypeElement(binding.injectedType());

    writer.emitPackage(injectedClassName.packageName());

    ImmutableSet<DependencyRequest> dependencies = binding.dependencySet();

    List<ClassName> importsBuilder = new ArrayList<ClassName>();
    importsBuilder.addAll(collectImportsFromDependencies(injectorClassName, dependencies));
    importsBuilder.add(ClassName.fromClass(MembersInjector.class));
    importsBuilder.add(ClassName.fromClass(Generated.class));
    ImmutableSortedSet<String> imports = FluentIterable.from(importsBuilder)
        .transform(Functions.toStringFunction())
        .toSortedSet(Ordering.natural());
    writer.emitImports(imports).emitEmptyLine();

    writer.emitJavadoc("A {@link MembersInjector} implementation for {@link %s}.",
        injectedClassName.simpleName());

    String membersInjectorType = type(MembersInjector.class, injectedClassName.simpleName());
    // @Generated("dagger.internal.codegen.InjectProcessor")
    // public final class Blah$$MembersInjector implements MembersInjector<Blah>
    writer.emitAnnotation(Generated.class, stringLiteral(ComponentProcessor.class.getName()))
        .beginType(injectorClassName.simpleName(), "class", EnumSet.of(FINAL), null,
            membersInjectorType);


    final ImmutableBiMap<Key, String> providerNames =
        generateProviderNamesForDependencies(dependencies);

    // Add the fields
    writeProviderFields(writer, providerNames);

    // Add the constructor
    writeConstructor(writer, providerNames);

    // @Override public void injectMembers(Blah instance)
    writer.emitAnnotation(Override.class)
        .beginMethod("void", "injectMembers", EnumSet.of(PUBLIC),
            injectedClassName.simpleName(), "instance");
    // TODO(gak): figure out what (if anything) to do about being passed a subtype of the class
    // specified as the type parameter for the MembersInjector.
    writer.beginControlFlow("if (instance == null)")
        .emitStatement(
            "throw new NullPointerException(\"Cannot inject members into a null reference\")")
        .endControlFlow();

    for (InjectionSite injectionSite : binding.injectionSites()) {
      switch (injectionSite.kind()) {
        case FIELD:
          Name fieldName = ((VariableElement) injectionSite.element()).getSimpleName();
          DependencyRequest singleDependency =
              Iterables.getOnlyElement(injectionSite.dependencies());
          String providerName = providerNames.get(singleDependency.key());
          writer.emitStatement("instance.%s = %s",
              fieldName, providerUsageStatement(providerName, singleDependency.kind()));
          break;
        case METHOD:
          Name methodName = ((ExecutableElement) injectionSite.element()).getSimpleName();
          String parameterString =
              Joiner.on(", ").join(FluentIterable.from(injectionSite.dependencies())
                  .transform(new Function<DependencyRequest, String>() {
                    @Override public String apply(DependencyRequest input) {
                      return providerUsageStatement(providerNames.get(input.key()), input.kind());
                    }
                  }));
          writer.emitStatement("instance.%s(%s)", methodName, parameterString);
          break;
        default:
          throw new AssertionError();
      }
    }
    writer.endMethod();

    writer.endType();
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

  private void writeConstructor(JavaWriter writer, ImmutableBiMap<Key, String> providerNames)
      throws IOException {
    writer.beginConstructor(EnumSet.noneOf(Modifier.class),
        flattenVariableMap(providersAsVariableMap(providerNames)),
        ImmutableList.<String>of());
    for (String providerName : providerNames.values()) {
      writer.emitStatement("assert %s != null", providerName);
      writer.emitStatement("this.%1$s = %1$s", providerName);
    }
    writer.endConstructor().emitEmptyLine();
  }

  private Map<String, String> providersAsVariableMap(ImmutableBiMap<Key, String> providerNames) {
    return Maps.transformValues(providerNames.inverse(), new Function<Key, String>() {
      @Override public String apply(Key key) {
        return providerTypeString(key);
      }
    });
  }

  private String providerTypeString(Key key) {
    return Util.typeToString(types.getDeclaredType(
        elements.getTypeElement(Provider.class.getCanonicalName()), key.type()));
  }
}
