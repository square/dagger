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

import com.google.auto.common.MoreTypes;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentGenerator.MemberSelect;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Creates the nested implementation class for a subcomponent.
 */
class SubcomponentWriter extends ComponentWriter {

  private ComponentWriter parent;
  private ExecutableElement subcomponentFactoryMethod;

  public SubcomponentWriter(
      ComponentWriter parent,
      ExecutableElement subcomponentFactoryMethod,
      BindingGraph subgraph) {
    super(
        parent.types,
        parent.nullableValidationType,
        parent.name.nestedClassNamed(subcomponentSimpleName(subgraph)),
        subgraph);
    this.parent = parent;
    this.subcomponentFactoryMethod = subcomponentFactoryMethod;
  }

  private static String subcomponentSimpleName(BindingGraph subgraph) {
    return subgraph.componentDescriptor().componentDefinitionType().getSimpleName() + "Impl";
  }

  @Override
  protected Optional<Snippet> getOrCreateComponentContributionFieldSnippet(
      TypeElement contributionType) {
    return super.getOrCreateComponentContributionFieldSnippet(contributionType)
        .or(parent.getOrCreateComponentContributionFieldSnippet(contributionType));
  }

  @Override
  protected MemberSelect getMemberSelect(BindingKey key) {
    MemberSelect memberSelect = super.getMemberSelect(key);
    return memberSelect == null ? parent.getMemberSelect(key) : memberSelect;
  }

  @Override
  protected Optional<MemberSelect> getMultibindingContributionSnippet(ContributionBinding binding) {
    return super.getMultibindingContributionSnippet(binding)
        .or(parent.getMultibindingContributionSnippet(binding));
  }

  @Override
  protected void writeComponent() {
    componentWriter = parent.componentWriter.addNestedClass(name.simpleName());
    componentWriter.addModifiers(PRIVATE, FINAL);

    constructorWriter = componentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);
    constructorWriter.body();

    TypeMirror subcomponentType;
    MethodWriter componentMethod;
    Optional<ClassName> builderName;
    if (graph.componentDescriptor().builderSpec().isPresent()) {
      BuilderSpec spec = graph.componentDescriptor().builderSpec().get();
      subcomponentType = spec.componentType();
      componentMethod = parent.componentWriter.addMethod(
          ClassName.fromTypeElement(spec.builderDefinitionType()),
          subcomponentFactoryMethod.getSimpleName().toString());
      ClassWriter builderWriter = writeBuilder(parent.componentWriter);
      builderName = Optional.of(builderWriter.name());
      componentMethod.body().addSnippet("return new %s();", builderWriter.name());
    } else {
      builderName = Optional.absent();
      ExecutableType resolvedMethod = MoreTypes.asExecutable(types.asMemberOf(
          MoreTypes.asDeclared(parent.componentDefinitionType().asType()),
          subcomponentFactoryMethod));
      subcomponentType = resolvedMethod.getReturnType();
      componentMethod = parent.componentWriter.addMethod(subcomponentType,
          subcomponentFactoryMethod.getSimpleName().toString());
      writeSubcomponentWithoutBuilder(componentMethod, resolvedMethod);
    }
    componentMethod.addModifiers(PUBLIC);
    componentMethod.annotate(Override.class);

    TypeElement subcomponentElement = MoreTypes.asTypeElement(subcomponentType);
    checkState(subcomponentElement.getModifiers().contains(ABSTRACT));
    componentWriter.setSupertype(subcomponentElement);

    writeFields();
    initializeFrameworkTypes(builderName);
    writeInterfaceMethods();

    for (Map.Entry<ExecutableElement, BindingGraph> subgraphEntry :
        graph.subgraphs().entrySet()) {
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, subgraphEntry.getKey(), subgraphEntry.getValue());
      javaWriters.addAll(subcomponent.write());
    }
  }

  private void writeSubcomponentWithoutBuilder(
      MethodWriter componentMethod, ExecutableType resolvedMethod) {
    ImmutableList.Builder<Snippet> subcomponentConstructorParameters = ImmutableList.builder();
    List<? extends VariableElement> params = subcomponentFactoryMethod.getParameters();
    List<? extends TypeMirror> paramTypes = resolvedMethod.getParameterTypes();
    for (int i = 0; i < params.size(); i++) {
      VariableElement moduleVariable = params.get(i);
      TypeElement moduleTypeElement = MoreTypes.asTypeElement(paramTypes.get(i));
      TypeName moduleType = TypeNames.forTypeMirror(paramTypes.get(i));
      componentMethod.addParameter(moduleType, moduleVariable.getSimpleName().toString());
      if (!componentContributionFields.containsKey(moduleTypeElement)) {
        String preferredModuleName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleTypeElement.getSimpleName().toString());
        FieldWriter contributionField =
            componentWriter.addField(moduleTypeElement, preferredModuleName);
        contributionField.addModifiers(PRIVATE, FINAL);
        String actualModuleName = contributionField.name();
        constructorWriter.addParameter(moduleType, actualModuleName);
        constructorWriter.body()
            .addSnippet("if (%s == null) {", actualModuleName)
            .addSnippet("  throw new NullPointerException();")
            .addSnippet("}");
        constructorWriter.body().addSnippet("this.%1$s = %1$s;", actualModuleName);
        MemberSelect moduleSelect =
            MemberSelect.instanceSelect(name, Snippet.format(actualModuleName));
        componentContributionFields.put(moduleTypeElement, moduleSelect);
        subcomponentConstructorParameters.add(Snippet.format("%s", moduleVariable.getSimpleName()));
      }
    }

    ImmutableSet<TypeElement> uninitializedModules =
        FluentIterable.from(graph.componentDescriptor().transitiveModules())
            .transform(ModuleDescriptor.getModuleElement())
            .filter(Predicates.not(Predicates.in(componentContributionFields.keySet())))
            .toSet();

    for (TypeElement moduleType : uninitializedModules) {
      String preferredModuleName =
          CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleType.getSimpleName().toString());
      FieldWriter contributionField = componentWriter.addField(moduleType, preferredModuleName);
      contributionField.addModifiers(PRIVATE, FINAL);
      String actualModuleName = contributionField.name();
      constructorWriter.body().addSnippet("this.%s = new %s();",
          actualModuleName, ClassName.fromTypeElement(moduleType));
      MemberSelect moduleSelect =
          MemberSelect.instanceSelect(name, Snippet.format(actualModuleName));
      componentContributionFields.put(moduleType, moduleSelect);
    }

    componentMethod.body().addSnippet("return new %s(%s);",
        name, Snippet.makeParametersSnippet(subcomponentConstructorParameters.build()));
  }
}
