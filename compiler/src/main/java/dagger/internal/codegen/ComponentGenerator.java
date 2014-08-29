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
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
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
import dagger.internal.codegen.ProvisionBinding.BindingsType;
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
import javax.lang.model.util.SimpleAnnotationValueVisitor6;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static dagger.internal.codegen.ConfigurationAnnotations.getMapKeys;
import static dagger.internal.codegen.DependencyRequest.Kind.MEMBERS_INJECTOR;
import static dagger.internal.codegen.ProvisionBinding.Kind.COMPONENT;
import static dagger.internal.codegen.ProvisionBinding.Kind.COMPONENT_PROVISION;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generateMembersInjectorNamesForBindings;
import static dagger.internal.codegen.SourceFiles.generateProviderNamesForBindings;
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
final class ComponentGenerator extends SourceFileGenerator<ComponentDescriptor> {
  ComponentGenerator(Filer filer) {
    super(filer);
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
  ImmutableSet<JavaWriter> write(ClassName componentName, ComponentDescriptor input) {
    ClassName componentDefinitionTypeName =
        ClassName.fromTypeElement(input.componentDefinitionType());

    JavaWriter writer = JavaWriter.inPackage(componentName.packageName());
    ClassWriter componentWriter = writer.addClass(componentName.simpleName());
    componentWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getCanonicalName());
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

    ImmutableMap<Key, String> providerNames =
        generateProviderNamesForBindings(resolvedProvisionBindings);
    ImmutableMap<Key, String> membersInjectorNames =
        generateMembersInjectorNamesForBindings(resolvedMembersInjectionBindings);

    // the full set of types that calling code uses to construct a component instance
    ImmutableMap<TypeElement, String> componentContributionNames =
        ImmutableMap.copyOf(Maps.asMap(
            Sets.union(input.moduleDependencies(), input.dependencies()),
            Functions.compose(
                CaseFormat.UPPER_CAMEL.converterTo(LOWER_CAMEL),
                new Function<TypeElement, String>() {
                  @Override public String apply(TypeElement input) {
                    return input.getSimpleName().toString();
                  }
                })));

    ImmutableSet.Builder<JavaWriter> packageProxies = ImmutableSet.builder();

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

    ImmutableMap.Builder<FrameworkKey, Snippet> memberSelectSnippetsBuilder =
        ImmutableMap.builder();

    for (Entry<String, Set<FrameworkKey>> packageEntry :
        Multimaps.asMap(input.initializationByPackage()).entrySet()) {
      String packageName = packageEntry.getKey();

      final Optional<String> proxySelector;
      final ClassWriter classWithFields;
      final Set<Modifier> fieldModifiers;

      if (packageName.equals(componentName.packageName())) {
        // no proxy
        proxySelector = Optional.absent();
        // component gets the fields
        classWithFields = componentWriter;
        // private fields
        fieldModifiers = EnumSet.of(PRIVATE);
      } else {
        // create the proxy
        JavaWriter proxyWriter = JavaWriter.inPackage(packageName);
        packageProxies.add(proxyWriter);
        ClassWriter proxyClassWriter =
            proxyWriter.addClass(componentName.simpleName() + "__PackageProxy");
        proxyClassWriter.addModifiers(PUBLIC, FINAL);
        // create the field for the proxy in the component
        FieldWriter proxyField =
            componentWriter.addField(proxyClassWriter.name(), packageName.replace('.', '_')
                + "_Proxy");
        proxyField.addModifiers(PRIVATE, FINAL);
        proxyField.setInitializer("new %s()", proxyClassWriter.name());
        // add the field for the member select
        proxySelector = Optional.of(proxyField.name());
        // proxy gets the fields
        classWithFields = proxyClassWriter;
        // public fields in the proxy
        fieldModifiers = EnumSet.of(PUBLIC);
      }

      for (FrameworkKey frameworkKey : packageEntry.getValue()) {
        Key key = frameworkKey.key();
        TypeName frameworkTypeName = ParameterizedTypeName.create(
            ClassName.fromClass(frameworkKey.frameworkClass()),
            TypeNames.forTypeMirror(key.type()));

        final String fieldName;
        if (frameworkKey.frameworkClass().equals(Provider.class)) {
          fieldName = providerNames.get(key);
        } else if (frameworkKey.frameworkClass().equals(MembersInjector.class)) {
          fieldName = membersInjectorNames.get(key);
        } else {
          throw new IllegalStateException();
        }

        FieldWriter frameworkField = classWithFields.addField(frameworkTypeName, fieldName);
        frameworkField.addModifiers(fieldModifiers);

        ImmutableList<String> memberSelectTokens = new ImmutableList.Builder<String>()
            .addAll(proxySelector.asSet())
            .add(frameworkField.name())
            .build();
        memberSelectSnippetsBuilder.put(frameworkKey,
            Snippet.memberSelectSnippet(memberSelectTokens));
      }
    }

    buildMethod.body().addSnippet("return new %s(this);", componentWriter.name());

    if (!requiresBuilder) {
      MethodWriter factoryMethod = componentWriter.addMethod(componentDefinitionTypeName, "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod.body().addSnippet("return builder().build();");
    }

    ImmutableMap<FrameworkKey, Snippet> memberSelectSnippets = memberSelectSnippetsBuilder.build();

    List<List<FrameworkKey>> partitions = Lists.partition(input.initializationOrdering(), 100);
    for (int i = 0; i < partitions.size(); i++) {
      MethodWriter initializeMethod =
          componentWriter.addMethod(VoidName.VOID, "initialize" + ((i == 0) ? "" : i));
      initializeMethod.addModifiers(PRIVATE);
      constructorWriter.body().addSnippet("%s();", initializeMethod.name());

      for (FrameworkKey frameworkKey : partitions.get(i)) {
        Key key = frameworkKey.key();
        Snippet memberSelectSnippet = memberSelectSnippets.get(frameworkKey);

        if (frameworkKey.frameworkClass().equals(Provider.class)) {
          Set<ProvisionBinding> bindings = resolvedProvisionBindings.get(key);
          BindingsType bindingsType = ProvisionBinding.getBindingsType(bindings);
          switch (bindingsType) {
            case SET_BINDING:
              ImmutableList.Builder<Snippet> setFactoryParameters = ImmutableList.builder();
              for (ProvisionBinding binding : bindings) {
                setFactoryParameters.add(initializeFactoryForBinding(binding,
                    input.dependencyMethodIndex(), componentContributionFields,
                    memberSelectSnippets));
              }
              Snippet initializeSetSnippet = Snippet.format("%s.create(%s)",
                  ClassName.fromClass(SetFactory.class),
                  Snippet.makeParametersSnippet(setFactoryParameters.build()));
              initializeMethod.body().addSnippet("this.%s = %s;",
                  memberSelectSnippet, initializeSetSnippet);
              break;
            case MAP_BINDING:
              if (!bindings.isEmpty()) {
                Snippet initializeMapSnippet =
                    initializeMapBinding(componentContributionFields, input.dependencyMethodIndex(),
                        memberSelectSnippets, bindings);
                initializeMethod.body().addSnippet("this.%s = %s;",
                    memberSelectSnippet, initializeMapSnippet);
              }
              break;
            case SINGULAR_BINDING:
              ProvisionBinding binding = Iterables.getOnlyElement(bindings);
              initializeMethod.body().addSnippet("this.%s = %s;",
                  memberSelectSnippet,
                  initializeFactoryForBinding(binding, input.dependencyMethodIndex(),
                      componentContributionFields, memberSelectSnippets));
              break;
            default:
              throw new IllegalStateException();
          }
        } else if (frameworkKey.frameworkClass().equals(MembersInjector.class)) {
          MembersInjectionBinding binding = resolvedMembersInjectionBindings.get(key);
          initializeMethod.body().addSnippet("this.%s = %s;",
              memberSelectSnippet,
              initializeMembersInjectorForBinding(binding,
                  memberSelectSnippets));
        } else {
          throw new IllegalStateException(
              "unknown framework class: " + frameworkKey.frameworkClass());
        }
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
      FrameworkKey frameworkKey = interfaceRequest.frameworkKey();
      if (interfaceRequest.kind().equals(MEMBERS_INJECTOR)) {
        Snippet membersInjectorName = memberSelectSnippets.get(frameworkKey);
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
        interfaceMethod.body().addSnippet("return %s;",
            frameworkTypeUsageStatement(memberSelectSnippets.get(frameworkKey),
                interfaceRequest.kind()));
      }
    }

    return new ImmutableSet.Builder<JavaWriter>()
        .addAll(packageProxies.build())
        .add(writer)
        .build();
  }

  private Snippet initializeFactoryForBinding(ProvisionBinding binding,
      ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex,
      Map<TypeElement, FieldWriter> contributionFields,
      ImmutableMap<FrameworkKey, Snippet> memberSelectSnippets) {
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
          contributionFields.get(dependencyMethodIndex.get(binding.bindingElement())).name(),
          binding.bindingElement().getSimpleName().toString());
    } else {
      List<Snippet> parameters = Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
      if (binding.bindingKind().equals(PROVISION)) {
        parameters.add(Snippet.format(contributionFields.get(binding.bindingTypeElement()).name()));
      }
      if (binding.memberInjectionRequest().isPresent()) {
        Snippet snippet = memberSelectSnippets.get(
            binding.memberInjectionRequest().get().frameworkKey());
        if (snippet != null) {
          parameters.add(snippet);
        } else {
          throw new UnsupportedOperationException("Non-generated MembersInjector");
        }
      }
      parameters.addAll(getDependencyParameters(binding.dependencies(), memberSelectSnippets));
      return binding.scope().isPresent()
          ? Snippet.format("%s.create(new %s(%s))",
              ClassName.fromClass(ScopedProvider.class),
              factoryNameForProvisionBinding(binding),
              Joiner.on(", ").join(parameters))
          : Snippet.format("new %s(%s)",
              factoryNameForProvisionBinding(binding),
              Joiner.on(", ").join(parameters));
    }
  }

  private static Snippet initializeMembersInjectorForBinding(
      MembersInjectionBinding binding,
      ImmutableMap<FrameworkKey, Snippet> memberSelectSnippets) {
    if (binding.injectionSites().isEmpty()) {
      if (binding.parentInjectorRequest().isPresent()) {
        DependencyRequest parentInjectorRequest = binding.parentInjectorRequest().get();
        return Snippet.format("%s.delegatingTo(%s)",
            ClassName.fromClass(MembersInjectors.class),
            memberSelectSnippets.get(
                parentInjectorRequest.frameworkKey()));
      } else {
        return Snippet.format("%s.noOp()",
            ClassName.fromClass(MembersInjectors.class));
      }
    } else {
      List<Snippet> parameters = getDependencyParameters(
          Sets.union(binding.parentInjectorRequest().asSet(), binding.dependencies()),
          memberSelectSnippets);
      return Snippet.format("new %s(%s)",
          membersInjectorNameForMembersInjectionBinding(binding),
          Joiner.on(", ").join(parameters));
    }
  }

  private static List<Snippet> getDependencyParameters(Iterable<DependencyRequest> dependencies,
      ImmutableMap<FrameworkKey, Snippet> memberSelectSnippets) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (DependencyRequest dependency : dependencies) {
      parameters.add(memberSelectSnippets.get(dependency.frameworkKey()));
    }
    return parameters.build();
  }

  private Snippet initializeMapBinding(
      Map<TypeElement, FieldWriter> contributionFields,
      ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex,
      ImmutableMap<FrameworkKey, Snippet> memberSelectSnippets,
      Set<ProvisionBinding> bindings) {
    Iterator<ProvisionBinding> iterator = bindings.iterator();
    // get type information from first binding in iterator
    ProvisionBinding firstBinding = iterator.next();
    if (isNonProviderMap(firstBinding)) {
      return Snippet.format("%s.create(%s)",
          ClassName.fromClass(MapFactory.class),
          memberSelectSnippets.get(Iterables.getOnlyElement(firstBinding.dependencies())
              .frameworkKey()));
    } else {
      DeclaredType declaredMapType =
          Util.getDeclaredTypeOfMap(firstBinding.providedKey().type());
      TypeMirror mapKeyType = Util.getKeyTypeOfMap(declaredMapType);
      TypeMirror mapValueType = Util.getProvideValueTypeOfMap(declaredMapType);
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

      writeEntry(argsBuilder, firstBinding, initializeFactoryForBinding(
          firstBinding, dependencyMethodIndex, contributionFields, memberSelectSnippets));
      while (iterator.hasNext()) {
        ProvisionBinding binding = iterator.next();
        writeEntry(argsBuilder, binding, initializeFactoryForBinding(
            binding, dependencyMethodIndex, contributionFields, memberSelectSnippets));
      }

      return Snippet.format(snippetFormatBuilder.toString(),
          argsBuilder.toArray(new Object[0]));
    }
  }

  // add one map entry for map Provider in Constructor
  private void writeEntry(List<Object> argsBuilder, ProvisionBinding binding,
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

  private boolean isNonProviderMap(ProvisionBinding binding) {
    DeclaredType declaredMapType = Util.getDeclaredTypeOfMap(binding.providedKey().type());
    TypeMirror mapValueType = Util.getProvideValueTypeOfMap(declaredMapType);
    if (mapValueType == null) {
      return true;
    }
    return false;
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
