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
import com.google.auto.common.SuperficialValidation;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import java.util.Collection;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
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
final class InjectProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final InjectConstructorValidator constructorValidator;
  private final InjectFieldValidator fieldValidator;
  private final InjectMethodValidator methodValidator;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final FactoryGenerator factoryGenerator;
  private final InjectionSite.Factory injectionSiteFactory;
  private final MembersInjectorGenerator membersInjectorWriter;
  private final InjectBindingRegistry injectBindingRegistry;


  InjectProcessingStep(Messager messager,
      InjectConstructorValidator constructorValidator,
      InjectFieldValidator fieldValidator,
      InjectMethodValidator methodValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      InjectionSite.Factory injectionSiteFactory,
      MembersInjectorGenerator membersInjectorWriter,
      InjectBindingRegistry factoryRegistrar) {
    this.messager = messager;
    this.constructorValidator = constructorValidator;
    this.fieldValidator = fieldValidator;
    this.methodValidator = methodValidator;
    this.provisionBindingFactory = provisionBindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.injectionSiteFactory = injectionSiteFactory;
    this.membersInjectorWriter = membersInjectorWriter;
    this.injectBindingRegistry = factoryRegistrar;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // TODO(gak): add some error handling for bad source files
    final ImmutableSet.Builder<ProvisionBinding> provisions = ImmutableSet.builder();
    final ImmutableSet.Builder<InjectionSite> memberInjectionSites = ImmutableSet.builder();

    for (Element injectElement : roundEnv.getElementsAnnotatedWith(Inject.class)) {
      if (SuperficialValidation.validateElement(injectElement)) {
        injectElement.accept(
            new ElementKindVisitor6<Void, Void>() {
              @Override
              public Void visitExecutableAsConstructor(
                  ExecutableElement constructorElement, Void v) {
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
                  memberInjectionSites.add(injectionSiteFactory.forInjectField(fieldElement));
                }

                return null;
              }

              @Override
              public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
                ValidationReport<ExecutableElement> report =
                    methodValidator.validate(methodElement);

                report.printMessagesTo(messager);

                if (report.isClean()) {
                  memberInjectionSites.add(injectionSiteFactory.forInjectMethod(methodElement));
                }

                return null;
              }
            }, null);
      }
    }

    ImmutableListMultimap<TypeElement, InjectionSite> membersInjectionsByType =
        Multimaps.index(memberInjectionSites.build(),
            new Function<InjectionSite, TypeElement>() {
              @Override public TypeElement apply(InjectionSite injectionSite) {
                return MoreElements.asType(injectionSite.element().getEnclosingElement());
              }
            });

    for (Collection<InjectionSite> injectionSites : membersInjectionsByType.asMap().values()) {
      try {
        MembersInjectionBinding binding = MembersInjectionBinding.create(injectionSites);
        membersInjectorWriter.generate(binding);
        injectBindingRegistry.registerMembersInjectionBinding(binding);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }

    for (ProvisionBinding binding : provisions.build()) {
      try {
        factoryGenerator.generate(binding);
        injectBindingRegistry.registerProvisionBinding(binding);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }

    return false;
  }
}
