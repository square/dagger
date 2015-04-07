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
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.getTransitiveModules;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Performs superficial validation of the contract of the {@link Component} annotation.
 *
 * @author Gregory Kick
 */
final class ComponentValidator implements Validator<TypeElement> {
  private final Elements elements;
  private final Types types;
  private final ModuleValidator moduleValidator;

  ComponentValidator(Elements elements, Types types, ModuleValidator moduleValidator) {
    this.elements = elements;
    this.types = types;
    this.moduleValidator = moduleValidator;
  }

  @Override public ValidationReport<TypeElement> validate(final TypeElement subject) {
    final ValidationReport.Builder<TypeElement> builder = ValidationReport.Builder.about(subject);

    if (!subject.getKind().equals(INTERFACE)
        && !(subject.getKind().equals(CLASS) && subject.getModifiers().contains(ABSTRACT))) {
      builder.addItem("@Component may only be applied to an interface or abstract class", subject);
    }

    List<? extends Element> members = elements.getAllMembers(subject);
    for (ExecutableElement method : ElementFilter.methodsIn(members)) {
      if (method.getModifiers().contains(ABSTRACT)) {
        List<? extends VariableElement> parameters = method.getParameters();
        TypeMirror returnType = method.getReturnType();

        // abstract methods are ones we have to implement, so they each need to be validated
        // first, check the return type.  if it's a subcomponent, validate that method as such.
        Optional<AnnotationMirror> subcomponentAnnotation  = returnType.accept(
            new SimpleTypeVisitor6<Optional<AnnotationMirror>, Void>() {
              @Override protected Optional<AnnotationMirror> defaultAction(TypeMirror e, Void p) {
                return Optional.absent();
              }

              @Override public Optional<AnnotationMirror> visitDeclared(DeclaredType t, Void p) {
                return MoreElements.getAnnotationMirror(t.asElement(), Subcomponent.class);
              }
            }, null);
        if (subcomponentAnnotation.isPresent()) {
          validateSubcomponentMethod(
              builder, method, parameters, returnType, subcomponentAnnotation);
        } else {
          // if it's not a subcomponent...
          switch (parameters.size()) {
            case 0:
              // no parameters means that it is a provision method
              // basically, there are no restrictions here.  \o/
              break;
            case 1:
              // one parameter means that it's a members injection method
              VariableElement onlyParameter = Iterables.getOnlyElement(parameters);
              if (!(returnType.getKind().equals(VOID)
                  || types.isSameType(returnType, onlyParameter.asType()))) {
                builder.addItem(
                    "Members injection methods may only return the injected type or void.",
                    method);
              }
              break;
            default:
              // this isn't any method that we know how to implement...
              builder.addItem(
                  "This method isn't a valid provision method, members injection method or "
                      + "subcomponent factory method. Dagger cannot implement this method", method);
              break;
          }
        }
      }
    }

    AnnotationMirror componentMirror = getAnnotationMirror(subject, Component.class).get();
    ImmutableList<TypeMirror> moduleTypes = getComponentModules(componentMirror);
    moduleValidator.validateReferencedModules(subject, builder, moduleTypes);
    return builder.build();
  }

  private void validateSubcomponentMethod(final ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method, List<? extends VariableElement> parameters, TypeMirror returnType,
      Optional<AnnotationMirror> subcomponentAnnotation) {
    ImmutableSet<TypeElement> moduleTypes =
        MoreTypes.asTypeElements(getComponentModules(subcomponentAnnotation.get()));

    ImmutableSet<TypeElement> transitiveModules =
        getTransitiveModules(types, elements, moduleTypes);

    ImmutableSet<TypeElement> requiredModules =
        FluentIterable.from(transitiveModules)
            .filter(new Predicate<TypeElement>() {
              @Override public boolean apply(TypeElement input) {
                return !componentCanMakeNewInstances(input);
              }
            })
            .toSet();

    Set<TypeElement> variableTypes = Sets.newHashSet();

    for (VariableElement parameter : parameters) {
      Optional<TypeElement> moduleType = parameter.asType().accept(
          new SimpleTypeVisitor6<Optional<TypeElement>, Void>() {
            @Override protected Optional<TypeElement> defaultAction(TypeMirror e, Void p) {
              return Optional.absent();
            }

            @Override public Optional<TypeElement> visitDeclared(DeclaredType t, Void p) {
              return MoreElements.isAnnotationPresent(t.asElement(), Module.class)
                  ? Optional.of(MoreTypes.asTypeElement(t))
                  : Optional.<TypeElement>absent();
            }
          }, null);
      if (moduleType.isPresent()) {
        if (variableTypes.contains(moduleType.get())) {
          builder.addItem(
              String.format(
                  "A module may only occur once an an argument in a Subcomponent factory "
                      + "method, but %s was already passed.",
                  moduleType.get().getQualifiedName()), parameter);
        }
        if (!transitiveModules.contains(moduleType.get())) {
          builder.addItem(
              String.format(
                  "%s is present as an argument to the %s factory method, but is not one of the"
                      + " modules used to implement the subcomponent.",
                  moduleType.get().getQualifiedName(),
                  MoreTypes.asTypeElement(returnType).getQualifiedName()),
              method);
        }
        variableTypes.add(moduleType.get());
      } else {
        builder.addItem(
            String.format(
                "Subcomponent factory methods may only accept modules, but %s is not.",
                parameter.asType()),
            parameter);
      }
    }

    SetView<TypeElement> missingModules =
        Sets.difference(requiredModules, ImmutableSet.copyOf(variableTypes));
    if (!missingModules.isEmpty()) {
      builder.addItem(
          String.format(
              "%s requires modules which have no visible default constructors. "
                  + "Add the following modules as parameters to this method: %s",
              MoreTypes.asTypeElement(returnType).getQualifiedName(),
              Joiner.on(", ").join(missingModules)),
          method);
    }
  }
}
