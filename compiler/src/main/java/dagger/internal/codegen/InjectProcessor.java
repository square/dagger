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

import static dagger.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_GENERIC_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_INTO_PRIVATE_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static javax.lang.model.SourceVersion.RELEASE_6;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementKindVisitor6;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@SupportedSourceVersion(RELEASE_6)
public final class InjectProcessor extends AbstractProcessor {
  private Messager messager;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(Inject.class.getName());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // TODO(gak): add some error handling for bad source files
    for (Element injectElement : roundEnv.getElementsAnnotatedWith(Inject.class)) {
      injectElement.accept(
          new ElementKindVisitor6<Void, Void>() {
            @Override
            public Void visitExecutableAsConstructor(ExecutableElement constructorElement, Void v) {
              boolean errorRaised = false;

              if (constructorElement.getModifiers().contains(PRIVATE)) {
                messager.printMessage(ERROR, INJECT_ON_PRIVATE_CONSTRUCTOR, constructorElement);
                errorRaised = true;
              }

              TypeElement enclosingElement =
                  ElementUtil.asTypeElement(constructorElement.getEnclosingElement());

              if (enclosingElement.getModifiers().contains(PRIVATE)) {
                messager.printMessage(ERROR, INJECT_INTO_PRIVATE_CLASS, constructorElement);
                errorRaised = true;
              }

              if (enclosingElement.getModifiers().contains(ABSTRACT)) {
                messager.printMessage(ERROR, INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS,
                    constructorElement);
                errorRaised = true;
              }

              if (!enclosingElement.getTypeParameters().isEmpty()) {
                messager.printMessage(ERROR, INJECT_CONSTRUCTOR_ON_GENERIC_CLASS,
                    constructorElement);
                errorRaised = true;
              }

              if (enclosingElement.getNestingKind().isNested()
                  && !enclosingElement.getModifiers().contains(STATIC)) {
                messager.printMessage(ERROR, INJECT_CONSTRUCTOR_ON_INNER_CLASS,
                    constructorElement);
                errorRaised = true;
              }

              // This is computationally expensive, but probably preferable to a giant index
              FluentIterable<ExecutableElement> injectConstructors = FluentIterable.from(
                  ElementFilter.constructorsIn(enclosingElement.getEnclosedElements()))
                      .filter(new Predicate<ExecutableElement>() {
                        @Override public boolean apply(ExecutableElement input) {
                          return input.getAnnotation(Inject.class) != null;
                        }
                      });

              if (injectConstructors.size() > 1) {
                messager.printMessage(ERROR, MULTIPLE_INJECT_CONSTRUCTORS, constructorElement);
                errorRaised = true;
              }

              if (!errorRaised) {
                // collect bindings for generating factories
              }
              return null;
            }

            @Override
            public Void visitVariableAsField(VariableElement fieldElement, Void p) {
              boolean errorRaised = false;

              Set<Modifier> modifiers = fieldElement.getModifiers();
              if (modifiers.contains(FINAL)) {
                messager.printMessage(ERROR, FINAL_INJECT_FIELD, fieldElement);
                errorRaised = true;
              }

              if (modifiers.contains(PRIVATE)) {
                messager.printMessage(ERROR, PRIVATE_INJECT_FIELD, fieldElement);
                errorRaised = true;
              }

              // TODO(gak): check for static

              if (!errorRaised) {
                // collect bindings for generating members injectors
              }

              return null;
            }

            @Override
            public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
              boolean errorRaised = false;

              Set<Modifier> modifiers = methodElement.getModifiers();
              if (modifiers.contains(ABSTRACT)) {
                messager.printMessage(ERROR, ABSTRACT_INJECT_METHOD, methodElement);
                errorRaised = true;
              }

              if (modifiers.contains(PRIVATE)) {
                messager.printMessage(ERROR, PRIVATE_INJECT_METHOD, methodElement);
                errorRaised = true;
              }

              if (!methodElement.getTypeParameters().isEmpty()) {
                messager.printMessage(ERROR, GENERIC_INJECT_METHOD, methodElement);
                errorRaised = true;
              }

              // TODO(gak): check for static

              if (!errorRaised) {
                // collect bindings for generating members injectors
              }

              return null;
            }
          }, null);
    }

    // TODO(gak): generate the factories and members injectors

    return false;
  }
}
