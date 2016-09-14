/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.Visibility.PRIVATE;
import static com.google.auto.common.Visibility.PUBLIC;
import static com.google.auto.common.Visibility.effectiveVisibilityOfElement;
import static com.google.common.collect.Iterables.any;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleIncludes;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleSubcomponents;
import static dagger.internal.codegen.ConfigurationAnnotations.getSubcomponentBuilder;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;
import static dagger.internal.codegen.ErrorMessages.INCOMPATIBLE_MODULE_METHODS;
import static dagger.internal.codegen.ErrorMessages.METHOD_OVERRIDES_PROVIDES_METHOD;
import static dagger.internal.codegen.ErrorMessages.MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.ModuleMessages.moduleSubcomponentsDoesntHaveBuilder;
import static dagger.internal.codegen.ErrorMessages.ModuleMessages.moduleSubcomponentsIncludesBuilder;
import static dagger.internal.codegen.ErrorMessages.ModuleMessages.moduleSubcomponentsIncludesNonSubcomponent;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_OVERRIDES_ANOTHER;
import static dagger.internal.codegen.ErrorMessages.REFERENCED_MODULE_MUST_NOT_HAVE_TYPE_PARAMS;
import static dagger.internal.codegen.ErrorMessages.REFERENCED_MODULE_NOT_ANNOTATED;
import static dagger.internal.codegen.Util.isAnyAnnotationPresent;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.Visibility;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.internal.codegen.ModuleDescriptor.Kind;
import dagger.multibindings.Multibinds;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * A {@linkplain ValidationReport validator} for {@link Module}s or {@link ProducerModule}s.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ModuleValidator {
  private static final ImmutableSet<Class<? extends Annotation>> SUBCOMPONENT_TYPES =
      ImmutableSet.of(Subcomponent.class, ProductionSubcomponent.class);
  private static final ImmutableSet<Class<? extends Annotation>> SUBCOMPONENT_BUILDER_TYPES =
      ImmutableSet.of(Subcomponent.Builder.class, ProductionSubcomponent.Builder.class);

  private final Types types;
  private final Elements elements;
  private final MethodSignatureFormatter methodSignatureFormatter;

  ModuleValidator(
      Types types, Elements elements, MethodSignatureFormatter methodSignatureFormatter) {
    this.types = types;
    this.elements = elements;
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  ValidationReport<TypeElement> validate(final TypeElement subject) {
    final ValidationReport.Builder<TypeElement> builder = ValidationReport.about(subject);
    ModuleDescriptor.Kind moduleKind = ModuleDescriptor.Kind.forAnnotatedElement(subject).get();

    List<ExecutableElement> moduleMethods = ElementFilter.methodsIn(subject.getEnclosedElements());
    ListMultimap<String, ExecutableElement> allMethodsByName = ArrayListMultimap.create();
    ListMultimap<String, ExecutableElement> bindingMethodsByName = ArrayListMultimap.create();

    Set<ModuleMethodKind> methodKinds = EnumSet.noneOf(ModuleMethodKind.class);
    for (ExecutableElement moduleMethod : moduleMethods) {
      if (isAnyAnnotationPresent(
          moduleMethod,
          ImmutableSet.of(moduleKind.methodAnnotation(), Binds.class, Multibinds.class))) {
        bindingMethodsByName.put(moduleMethod.getSimpleName().toString(), moduleMethod);
        methodKinds.add(ModuleMethodKind.ofMethod(moduleMethod));
      }
      allMethodsByName.put(moduleMethod.getSimpleName().toString(), moduleMethod);
    }

    if (methodKinds.containsAll(
        EnumSet.of(ModuleMethodKind.ABSTRACT_DECLARATION, ModuleMethodKind.INSTANCE_BINDING))) {
      builder.addError(
          String.format(
              INCOMPATIBLE_MODULE_METHODS,
              moduleKind.moduleAnnotation().getSimpleName(),
              moduleKind.methodAnnotation().getSimpleName()));
    }

    validateModuleVisibility(subject, moduleKind, builder);
    validateMethodsWithSameName(moduleKind, builder, bindingMethodsByName);
    if (subject.getKind() != ElementKind.INTERFACE) {
      validateProvidesOverrides(
          subject, moduleKind, builder, allMethodsByName, bindingMethodsByName);
    }
    validateModifiers(subject, builder);
    validateReferencedModules(subject, moduleKind, builder);
    validateReferencedSubcomponents(subject, moduleKind, builder);

    // TODO(gak): port the dagger 1 module validation?
    return builder.build();
  }

  private void validateReferencedSubcomponents(
      final TypeElement subject,
      ModuleDescriptor.Kind moduleKind,
      final ValidationReport.Builder<TypeElement> builder) {
    final AnnotationMirror moduleAnnotation = moduleKind.getModuleAnnotationMirror(subject).get();
    // TODO(ronshapiro): use validateTypesAreDeclared when it is checked in
    for (TypeMirror subcomponentAttribute : getModuleSubcomponents(moduleAnnotation)) {
      subcomponentAttribute.accept(
          new SimpleTypeVisitor6<Void, Void>(){
            @Override
            protected Void defaultAction(TypeMirror e, Void aVoid) {
              builder.addError(e + " is not a valid subcomponent type", subject, moduleAnnotation);
              return null;
            }

            @Override
            public Void visitDeclared(DeclaredType declaredType, Void aVoid) {
              TypeElement attributeType = MoreTypes.asTypeElement(declaredType);
              if (isAnyAnnotationPresent(attributeType, SUBCOMPONENT_TYPES)) {
                validateSubcomponentHasBuilder(attributeType, moduleAnnotation, builder);
              } else {
                builder.addError(
                    isAnyAnnotationPresent(attributeType, SUBCOMPONENT_BUILDER_TYPES)
                        ? moduleSubcomponentsIncludesBuilder(attributeType)
                        : moduleSubcomponentsIncludesNonSubcomponent(attributeType),
                    attributeType,
                    moduleAnnotation);
              }

              return null;
            }
          }, null);
    }
  }

  private void validateSubcomponentHasBuilder(
      TypeElement subcomponentAttribute,
      AnnotationMirror moduleAnnotation,
      ValidationReport.Builder<TypeElement> builder) {
    if (getSubcomponentBuilder(subcomponentAttribute).isPresent()) {
      return;
    }
    builder.addError(
        moduleSubcomponentsDoesntHaveBuilder(subcomponentAttribute, moduleAnnotation),
        subcomponentAttribute,
        moduleAnnotation);
  }

  enum ModuleMethodKind {
    ABSTRACT_DECLARATION,
    INSTANCE_BINDING,
    STATIC_BINDING,
    ;

    static ModuleMethodKind ofMethod(ExecutableElement moduleMethod) {
      if (moduleMethod.getModifiers().contains(STATIC)) {
        return STATIC_BINDING;
      } else if (moduleMethod.getModifiers().contains(ABSTRACT)) {
        return ABSTRACT_DECLARATION;
      } else {
        return INSTANCE_BINDING;
      }
    }
  }

  private void validateModifiers(
      TypeElement subject, ValidationReport.Builder<TypeElement> builder) {
    // This coupled with the check for abstract modules in ComponentValidator guarantees that
    // only modules without type parameters are referenced from @Component(modules={...}).
    if (!subject.getTypeParameters().isEmpty() && !subject.getModifiers().contains(ABSTRACT)) {
      builder.addError(MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT, subject);
    }
  }

  private void validateMethodsWithSameName(
      ModuleDescriptor.Kind moduleKind,
      ValidationReport.Builder<TypeElement> builder,
      ListMultimap<String, ExecutableElement> bindingMethodsByName) {
    for (Entry<String, Collection<ExecutableElement>> entry :
        bindingMethodsByName.asMap().entrySet()) {
      if (entry.getValue().size() > 1) {
        for (ExecutableElement offendingMethod : entry.getValue()) {
          builder.addError(
              String.format(
                  BINDING_METHOD_WITH_SAME_NAME, moduleKind.methodAnnotation().getSimpleName()),
              offendingMethod);
        }
      }
    }
  }

  private void validateReferencedModules(
      TypeElement subject,
      ModuleDescriptor.Kind moduleKind,
      ValidationReport.Builder<TypeElement> builder) {
    // Validate that all the modules we include are valid for inclusion.
    AnnotationMirror mirror = moduleKind.getModuleAnnotationMirror(subject).get();
    ImmutableList<TypeMirror> includes = getModuleIncludes(mirror);
    validateReferencedModules(subject, builder, includes, ImmutableSet.of(moduleKind));
  }

  private static ImmutableSet<? extends Class<? extends Annotation>> includedModuleClasses(
      ImmutableSet<ModuleDescriptor.Kind> validModuleKinds) {
    return FluentIterable.from(validModuleKinds).transformAndConcat(Kind::includesTypes).toSet();
  }

  /**
   * Used by {@link ModuleValidator} & {@link ComponentValidator} to validate referenced modules.
   */
  void validateReferencedModules(
      final TypeElement subject,
      final ValidationReport.Builder<TypeElement> builder,
      ImmutableList<TypeMirror> includes,
      ImmutableSet<ModuleDescriptor.Kind> validModuleKinds) {
    final ImmutableSet<? extends Class<? extends Annotation>> includedModuleClasses =
        includedModuleClasses(validModuleKinds);

    for (TypeMirror includesType : includes) {
      includesType.accept(
          new SimpleTypeVisitor6<Void, Void>() {
            @Override
            protected Void defaultAction(TypeMirror mirror, Void p) {
              builder.addError(mirror + " is not a valid module type.", subject);
              return null;
            }

            @Override
            public Void visitDeclared(DeclaredType t, Void p) {
              final TypeElement element = MoreElements.asType(t.asElement());
              if (!t.getTypeArguments().isEmpty()) {
                builder.addError(
                    String.format(
                        REFERENCED_MODULE_MUST_NOT_HAVE_TYPE_PARAMS, element.getQualifiedName()),
                    subject);
              }
              boolean isIncludedModule =
                  any(
                      includedModuleClasses,
                      otherClass -> MoreElements.isAnnotationPresent(element, otherClass));
              if (!isIncludedModule) {
                builder.addError(
                    String.format(
                        REFERENCED_MODULE_NOT_ANNOTATED,
                        element.getQualifiedName(),
                        (includedModuleClasses.size() > 1 ? "one of " : "")
                            + includedModuleClasses.stream()
                                .map(otherClass -> "@" + otherClass.getSimpleName())
                                .collect(joining(", "))),
                    subject);
              }
              return null;
            }
          },
          null);
    }
  }

  private void validateProvidesOverrides(
      TypeElement subject,
      ModuleDescriptor.Kind moduleKind,
      ValidationReport.Builder<TypeElement> builder,
      ListMultimap<String, ExecutableElement> allMethodsByName,
      ListMultimap<String, ExecutableElement> bindingMethodsByName) {
    // For every @Provides method, confirm it overrides nothing *and* nothing overrides it.
    // Consider the following hierarchy:
    // class Parent {
    //    @Provides Foo a() {}
    //    @Provides Foo b() {}
    //    Foo c() {}
    // }
    // class Child extends Parent {
    //    @Provides Foo a() {}
    //    Foo b() {}
    //    @Provides Foo c() {}
    // }
    // In each of those cases, we want to fail.  "a" is clear, "b" because Child is overriding
    // a method marked @Provides in Parent, and "c" because Child is defining an @Provides
    // method that overrides Parent.
    TypeElement currentClass = subject;
    TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    // We keep track of methods that failed so we don't spam with multiple failures.
    Set<ExecutableElement> failedMethods = Sets.newHashSet();
    while (!types.isSameType(currentClass.getSuperclass(), objectType)) {
      currentClass = MoreElements.asType(types.asElement(currentClass.getSuperclass()));
      List<ExecutableElement> superclassMethods =
          ElementFilter.methodsIn(currentClass.getEnclosedElements());
      for (ExecutableElement superclassMethod : superclassMethods) {
        String name = superclassMethod.getSimpleName().toString();
        // For each method in the superclass, confirm our @Provides methods don't override it
        for (ExecutableElement providesMethod : bindingMethodsByName.get(name)) {
          if (!failedMethods.contains(providesMethod)
              && elements.overrides(providesMethod, superclassMethod, subject)) {
            failedMethods.add(providesMethod);
            builder.addError(
                String.format(
                    PROVIDES_METHOD_OVERRIDES_ANOTHER,
                    moduleKind.methodAnnotation().getSimpleName(),
                    methodSignatureFormatter.format(superclassMethod)),
                providesMethod);
          }
        }
        // For each @Provides method in superclass, confirm our methods don't override it.
        if (isAnnotationPresent(superclassMethod, moduleKind.methodAnnotation())) {
          for (ExecutableElement method : allMethodsByName.get(name)) {
            if (!failedMethods.contains(method)
                && elements.overrides(method, superclassMethod, subject)) {
              failedMethods.add(method);
              builder.addError(
                  String.format(
                      METHOD_OVERRIDES_PROVIDES_METHOD,
                      moduleKind.methodAnnotation().getSimpleName(),
                      methodSignatureFormatter.format(superclassMethod)),
                  method);
            }
          }
        }
        allMethodsByName.put(superclassMethod.getSimpleName().toString(), superclassMethod);
      }
    }
  }

  private void validateModuleVisibility(
      final TypeElement moduleElement,
      ModuleDescriptor.Kind moduleKind,
      final ValidationReport.Builder<?> reportBuilder) {
    Visibility moduleVisibility = Visibility.ofElement(moduleElement);
    if (moduleVisibility.equals(PRIVATE)) {
      reportBuilder.addError("Modules cannot be private.", moduleElement);
    } else if (effectiveVisibilityOfElement(moduleElement).equals(PRIVATE)) {
      reportBuilder.addError("Modules cannot be enclosed in private types.", moduleElement);
    }

    switch (moduleElement.getNestingKind()) {
      case ANONYMOUS:
        throw new IllegalStateException("Can't apply @Module to an anonymous class");
      case LOCAL:
        throw new IllegalStateException("Local classes shouldn't show up in the processor");
      case MEMBER:
      case TOP_LEVEL:
        if (moduleVisibility.equals(PUBLIC)) {
          ImmutableSet<Element> nonPublicModules =
              FluentIterable.from(
                      getModuleIncludes(
                          getAnnotationMirror(moduleElement, moduleKind.moduleAnnotation()).get()))
                  .transform(types::asElement)
                  .filter(element -> effectiveVisibilityOfElement(element).compareTo(PUBLIC) < 0)
                  .toSet();
          if (!nonPublicModules.isEmpty()) {
            reportBuilder.addError(
                String.format(
                    "This module is public, but it includes non-public "
                        + "(or effectively non-public) modules. "
                        + "Either reduce the visibility of this module or make %s public.",
                    formatListForErrorMessage(nonPublicModules.asList())),
                moduleElement);
          }
        }
        break;
      default:
        throw new AssertionError();
    }
  }

  private static String formatListForErrorMessage(List<?> things) {
    switch (things.size()) {
      case 0:
        return "";
      case 1:
        return things.get(0).toString();
      default:
        StringBuilder output = new StringBuilder();
        Joiner.on(", ").appendTo(output, things.subList(0, things.size() - 1));
        output.append(" and ").append(things.get(things.size() - 1));
        return output.toString();
    }
  }
}
