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

import static javax.lang.model.SourceVersion.RELEASE_6;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;

import java.util.Collection;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@SupportedSourceVersion(RELEASE_6)
public final class InjectProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final InjectConstructorValidator constructorValidator;
  private final InjectFieldValidator fieldValidator;
  private final InjectMethodValidator methodValidator;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final FactoryGenerator factoryGenerator;
  private final MembersInjectionBinding.Factory membersInjectionBindingFactory;
  private final MembersInjectorGenerator membersInjectorWriter;
  private final InjectBindingRegistry factoryRegistrar;


  InjectProcessingStep(Messager messager,
      InjectConstructorValidator constructorValidator,
      InjectFieldValidator fieldValidator,
      InjectMethodValidator methodValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      MembersInjectionBinding.Factory membersInjectionBindingFactory,
      MembersInjectorGenerator membersInjectorWriter,
      InjectBindingRegistry factoryRegistrar) {
    this.messager = messager;
    this.constructorValidator = constructorValidator;
    this.fieldValidator = fieldValidator;
    this.methodValidator = methodValidator;
    this.provisionBindingFactory = provisionBindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.membersInjectionBindingFactory = membersInjectionBindingFactory;
    this.membersInjectorWriter = membersInjectorWriter;
    this.factoryRegistrar = factoryRegistrar;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // TODO(gak): add some error handling for bad source files
    final ImmutableSet.Builder<ProvisionBinding> provisions = ImmutableSet.builder();
    final ImmutableSet.Builder<MembersInjectionBinding> membersInjections = ImmutableSet.builder();

    for (Element injectElement : roundEnv.getElementsAnnotatedWith(Inject.class)) {
      injectElement.accept(
          new ElementKindVisitor6<Void, Void>() {
            @Override
            public Void visitExecutableAsConstructor(ExecutableElement constructorElement, Void v) {
              ValidationReport<ExecutableElement> report =
                  constructorValidator.validate(constructorElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                provisions.add(provisionBindingFactory.forInjectConstructor(constructorElement));
              }

              return null;
            }

            @Override
            public Void visitVariableAsField(VariableElement fieldElement, Void p) {
              ValidationReport<VariableElement> report = fieldValidator.validate(fieldElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjections.add(
                    membersInjectionBindingFactory.forInjectField(fieldElement));
              }

              return null;
            }

            @Override
            public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
              ValidationReport<ExecutableElement> report = methodValidator.validate(methodElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjections.add(
                    membersInjectionBindingFactory.forInjectMethod(methodElement));
              }

              return null;
            }
          }, null);
    }

    ImmutableListMultimap<TypeElement, MembersInjectionBinding> membersInjectionsByType =
        Multimaps.index(membersInjections.build(),
            new Function<MembersInjectionBinding, TypeElement>() {
              @Override public TypeElement apply(MembersInjectionBinding binding) {
                return binding.enclosingType();
              }
            });

    for (Collection<MembersInjectionBinding> bindings : membersInjectionsByType.asMap().values()) {
      try {
        membersInjectorWriter.generate(MembersInjectorDescriptor.create(bindings));
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }

    for (ProvisionBinding binding : provisions.build()) {
      try {
        factoryGenerator.generate(binding);
        factoryRegistrar.registerBinding(binding);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }

    return false;
  }
}
