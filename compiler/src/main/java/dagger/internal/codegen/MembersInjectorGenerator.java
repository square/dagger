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

import com.google.auto.common.MoreElements;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import dagger.MembersInjector;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.Modifiable;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.internal.codegen.writer.TypeVariableName;
import dagger.internal.codegen.writer.VariableWriter;
import dagger.internal.codegen.writer.VoidName;
import dagger.producers.Producer;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor7;

import static com.google.auto.common.MoreElements.getPackage;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final DependencyRequestMapper dependencyRequestMapper;

  MembersInjectorGenerator(
      Filer filer,
      DependencyRequestMapper dependencyRequestMapper) {
    super(filer);
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  ClassName nameGeneratedType(MembersInjectionBinding binding) {
    return membersInjectorNameForType(binding.bindingElement());
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
    return Optional.of(binding.bindingElement());
  }
  
  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, MembersInjectionBinding binding) {
    // Empty members injection bindings are special and don't need source files.
    if (binding.injectionSites().isEmpty()) {
      return ImmutableSet.of();
    }
    Set<String> delegateMethods = new HashSet<>();

    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkState(!binding.hasNonDefaultTypeParameters()); 

    TypeName injectedTypeName = TypeNames.forTypeMirror(binding.key().type());
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter injectorWriter = writer.addClass(generatedTypeName.simpleName());
    List<TypeVariableName> typeParameters = Lists.newArrayList();
    for (TypeParameterElement typeParameter : binding.bindingTypeElement().getTypeParameters()) {
      typeParameters.add(TypeVariableName.fromTypeParameterElement(typeParameter));
    }
    injectorWriter.addTypeParameters(typeParameters);
    injectorWriter.annotate(Generated.class)
        .setValue(ComponentProcessor.class.getCanonicalName());
    injectorWriter.addModifiers(PUBLIC, FINAL);
    TypeName implementedType =
        ParameterizedTypeName.create(MembersInjector.class, injectedTypeName);
    injectorWriter.addImplementedType(implementedType);

    ConstructorWriter constructorWriter = injectorWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);
    MethodWriter injectMembersWriter = injectorWriter.addMethod(VoidName.VOID, "injectMembers");
    injectMembersWriter.addModifiers(PUBLIC);
    injectMembersWriter.annotate(Override.class);
    injectMembersWriter.addParameter(injectedTypeName, "instance");
    injectMembersWriter.body().addSnippet(Joiner.on('\n').join(
        "if (instance == null) {",
        "  throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "}"));

    ImmutableMap<BindingKey, FrameworkField> fields =
        SourceFiles.generateBindingFieldsForDependencies(
            dependencyRequestMapper, ImmutableSet.copyOf(binding.dependencies()));

    ImmutableMap.Builder<BindingKey, FieldWriter> dependencyFieldsBuilder =
        ImmutableMap.builder();
    
    // We use a static create method so that generated components can avoid having
    // to refer to the generic types of the factory.
    // (Otherwise they may have visibility problems referring to the types.)
    MethodWriter createMethodWriter = injectorWriter.addMethod(implementedType, "create");
    createMethodWriter.addTypeParameters(typeParameters);
    createMethodWriter.addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    boolean usesRawFrameworkTypes = false;
    for (Entry<BindingKey, FrameworkField> fieldEntry : fields.entrySet()) {
      BindingKey bindingKey = fieldEntry.getKey();
      FrameworkField bindingField = fieldEntry.getValue();

      // If the dependency type is not visible to this members injector, then use the raw framework
      // type for the field.
      boolean useRawFrameworkType =
          !VISIBLE_TO_MEMBERS_INJECTOR.visit(bindingKey.key().type(), binding);

      FieldWriter field =
          injectorWriter.addField(
              useRawFrameworkType
                  ? bindingField.frameworkType().type()
                  : bindingField.frameworkType(),
              bindingField.name());

      field.addModifiers(PRIVATE, FINAL);
      VariableWriter constructorParameter =
          constructorWriter.addParameter(field.type(), field.name());
      VariableWriter createMethodParameter =
          createMethodWriter.addParameter(constructorParameter.type(), constructorParameter.name());

      // If we're using the raw type for the field, then suppress the injectMembers method's
      // unchecked-type warning and the field's and the constructor and create-method's
      // parameters' raw-type warnings.
      if (useRawFrameworkType) {
        usesRawFrameworkTypes = true;
        suppressRawTypesWarning(field);
        suppressRawTypesWarning(constructorParameter);
        suppressRawTypesWarning(createMethodParameter);
      }

      constructorWriter.body().addSnippet("assert %s != null;", field.name());
      constructorWriter.body().addSnippet("this.%1$s = %1$s;", field.name());
      dependencyFieldsBuilder.put(bindingKey, field);
    }

    createMethodWriter
        .body()
        .addSnippet(
            "  return new %s(%s);",
            parameterizedGeneratedTypeNameForBinding(binding),
            Joiner.on(", ").join(constructorWriter.parameters().keySet()));

    ImmutableMap<BindingKey, FieldWriter> dependencyFields = dependencyFieldsBuilder.build();
    for (InjectionSite injectionSite : binding.injectionSites()) {
      injectMembersWriter
          .body()
          .addSnippet(
              visibleToMembersInjector(binding, injectionSite.element())
                  ? directInjectMemberSnippet(binding, dependencyFields, injectionSite)
                  : delegateInjectMemberSnippet(dependencyFields, injectionSite));
      if (!injectionSite.element().getModifiers().contains(PUBLIC)
          && injectionSite.element().getEnclosingElement().equals(binding.bindingElement())
          && delegateMethods.add(injectionSiteDelegateMethodName(injectionSite.element()))) {
        writeInjectorMethodForSubclasses(
            injectorWriter,
            dependencyFields,
            typeParameters,
            injectedTypeName,
            injectionSite.element(),
            injectionSite.dependencies());
      }
    }
    
    if (usesRawFrameworkTypes) {
      injectMembersWriter.annotate(SuppressWarnings.class).setValue("unchecked");
    }

    return ImmutableSet.of(writer);
  }

  /**
   * Returns {@code true} if {@code element} is visible to the members injector for {@code binding}.
   */
  // TODO(dpb,gak): Make sure that all cases are covered here. E.g., what if element is public but
  // enclosed in a package-private element?
  private static boolean visibleToMembersInjector(
      MembersInjectionBinding binding, Element element) {
    return getPackage(element).equals(getPackage(binding.bindingElement()))
        || element.getModifiers().contains(PUBLIC);
  }

  /**
   * Returns a snippet that directly injects the instance's field or method.
   */
  private Snippet directInjectMemberSnippet(
      MembersInjectionBinding binding,
      ImmutableMap<BindingKey, FieldWriter> dependencyFields,
      InjectionSite injectionSite) {
    return Snippet.format(
        injectionSite.element().getKind().isField() ? "%s.%s = %s;" : "%s.%s(%s);",
        getInstanceSnippetWithPotentialCast(
            injectionSite.element().getEnclosingElement(), binding.bindingElement()),
        injectionSite.element().getSimpleName(),
        makeParametersSnippet(
            parameterSnippets(dependencyFields, injectionSite.dependencies(), true)));
  }

  /**
   * Returns a snippet that injects the instance's field or method by calling a static method on the
   * parent members injector class.
   */
  private Snippet delegateInjectMemberSnippet(
      ImmutableMap<BindingKey, FieldWriter> dependencyFields, InjectionSite injectionSite) {
    return Snippet.format(
        "%s.%s(%s);",
        membersInjectorNameForType(
            MoreElements.asType(injectionSite.element().getEnclosingElement())),
        injectionSiteDelegateMethodName(injectionSite.element()),
        makeParametersSnippet(
            new ImmutableList.Builder<Snippet>()
                .add(Snippet.format("instance"))
                .addAll(parameterSnippets(dependencyFields, injectionSite.dependencies(), false))
                .build()));
  }

  /**
   * Returns the parameters for injecting a member.
   *
   * @param passValue if {@code true}, each parameter snippet will be the result of converting the
   *     field from the framework type ({@link Provider}, {@link Producer}, etc.) to the real value;
   *     if {@code false}, each parameter snippet will be just the field
   */
  private ImmutableList<Snippet> parameterSnippets(
      ImmutableMap<BindingKey, FieldWriter> dependencyFields,
      ImmutableSet<DependencyRequest> dependencies,
      boolean passValue) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (DependencyRequest dependency : dependencies) {
      Snippet fieldSnippet =
          Snippet.format("%s", dependencyFields.get(dependency.bindingKey()).name());
      parameters.add(
          passValue ? frameworkTypeUsageStatement(fieldSnippet, dependency.kind()) : fieldSnippet);
    }
    return parameters.build();
  }

  private Snippet getInstanceSnippetWithPotentialCast(
      Element injectionSiteElement, Element bindingElement) {
    return (injectionSiteElement.equals(bindingElement))
        ? Snippet.format("instance")
        : Snippet.format("((%s)instance)", injectionSiteElement);
  }

  private String injectionSiteDelegateMethodName(Element injectionSiteElement) {
    return "inject"
        + CaseFormat.LOWER_CAMEL.to(
            CaseFormat.UPPER_CAMEL, injectionSiteElement.getSimpleName().toString());
  }

  private void writeInjectorMethodForSubclasses(
      ClassWriter injectorWriter,
      ImmutableMap<BindingKey, FieldWriter> dependencyFields,
      List<TypeVariableName> typeParameters,
      TypeName injectedTypeName,
      Element injectionElement,
      ImmutableSet<DependencyRequest> dependencies) {
    MethodWriter methodWriter =
        injectorWriter.addMethod(VoidName.VOID, injectionSiteDelegateMethodName(injectionElement));
    methodWriter.addModifiers(PUBLIC, STATIC);
    methodWriter.addParameter(injectedTypeName, "instance");
    methodWriter.addTypeParameters(typeParameters);
    ImmutableList.Builder<Snippet> providedParameters = ImmutableList.builder();
    Set<String> parameterNames = new HashSet<>();
    for (DependencyRequest dependency : dependencies) {
      FieldWriter field = dependencyFields.get(dependency.bindingKey());
      VariableWriter parameter =
          methodWriter.addParameter(
              field.type(),
              staticInjectMethodDependencyParameterName(parameterNames, dependency, field));
      providedParameters.add(
          frameworkTypeUsageStatement(Snippet.format("%s", parameter.name()), dependency.kind()));
    }
    if (injectionElement.getKind().isField()) {
      methodWriter
          .body()
          .addSnippet(
              "instance.%s = %s;",
              injectionElement.getSimpleName(),
              getOnlyElement(providedParameters.build()));
    } else {
      methodWriter
          .body()
          .addSnippet(
              "instance.%s(%s);",
              injectionElement.getSimpleName(),
              makeParametersSnippet(providedParameters.build()));
    }
  }

  /**
   * Returns the static inject method parameter name for a dependency.
   *
   * @param parameterNames the parameter names used so far
   * @param dependency the dependency
   * @param field the field used to hold the framework type for the dependency
   */
  private String staticInjectMethodDependencyParameterName(
      Set<String> parameterNames, DependencyRequest dependency, FieldWriter field) {
    StringBuilder parameterName =
        new StringBuilder(dependency.requestElement().getSimpleName().toString());
    switch (dependency.kind()) {
      case LAZY:
      case INSTANCE:
      case FUTURE:
        String suffix = ((ParameterizedTypeName) field.type()).type().simpleName();
        if (parameterName.length() <= suffix.length()
            || !parameterName.substring(parameterName.length() - suffix.length()).equals(suffix)) {
          parameterName.append(suffix);
        }
        break;

      default:
        break;
    }
    int baseLength = parameterName.length();
    for (int i = 2; !parameterNames.add(parameterName.toString()); i++) {
      parameterName.replace(baseLength, parameterName.length(), String.valueOf(i));
    }
    return parameterName.toString();
  }

  private void suppressRawTypesWarning(Modifiable modifiable) {
    modifiable.annotate(SuppressWarnings.class).setValue("rawtypes");
  }

  private static final TypeVisitor<Boolean, MembersInjectionBinding> VISIBLE_TO_MEMBERS_INJECTOR =
      new SimpleTypeVisitor7<Boolean, MembersInjectionBinding>(true) {
        @Override
        public Boolean visitArray(ArrayType t, MembersInjectionBinding p) {
          return visit(t.getComponentType(), p);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, MembersInjectionBinding p) {
          return visibleToMembersInjector(p, t.asElement());
        }
      };
}
