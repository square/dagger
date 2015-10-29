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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final Types types;
  private final Elements elements;
  private final Key.Factory keyFactory;
  private final Diagnostic.Kind nullableValidationType;

  ComponentGenerator(
      Filer filer,
      Elements elements,
      Types types,
      Key.Factory keyFactory,
      Diagnostic.Kind nullableValidationType) {
    super(filer);
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.nullableValidationType = nullableValidationType;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName =
        ClassName.fromTypeElement(input.componentDescriptor().componentDefinitionType());
    String componentName = "Dagger" + componentDefinitionClassName.classFileName('_');
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

  @AutoValue static abstract class MemberSelect {
    static MemberSelect instanceSelect(ClassName owningClass, Snippet snippet) {
      return new AutoValue_ComponentGenerator_MemberSelect(
          Optional.<TypeName> absent(), owningClass, false, snippet);
    }

    static MemberSelect staticSelect(ClassName owningClass, Snippet snippet) {
      return new AutoValue_ComponentGenerator_MemberSelect(
          Optional.<TypeName> absent(), owningClass, true, snippet);
    }

    static MemberSelect staticMethodInvocationWithCast(
        ClassName owningClass, Snippet snippet, TypeName castType) {
      return new AutoValue_ComponentGenerator_MemberSelect(
          Optional.of(castType), owningClass, true, snippet);
    }

    /**
     * This exists only to facilitate edge cases in which we need to select a member, but that
     * member uses a type parameter that can't be inferred.
     */
    abstract Optional<TypeName> selectedCast();
    abstract ClassName owningClass();
    abstract boolean staticMember();
    abstract Snippet snippet();

    private Snippet qualifiedSelectSnippet() {
      return Snippet.format(
          "%s" + (staticMember() ? "" : ".this") + ".%s",
          owningClass(), snippet());
    }

    Snippet getSnippetWithRawTypeCastFor(ClassName usingClass) {
      Snippet snippet = getSnippetFor(usingClass);
      return selectedCast().isPresent()
          ? Snippet.format("(%s) %s", selectedCast().get(), snippet)
          : snippet;
    }

    Snippet getSnippetFor(ClassName usingClass) {
      return owningClass().equals(usingClass) ? snippet() : qualifiedSelectSnippet();
    }
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName componentName, BindingGraph input) {
    return new ComponentWriter(
            types, elements, keyFactory, nullableValidationType, componentName, input)
        .write();
  }
}
