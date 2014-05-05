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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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
  private InjectConstructorValidator constructorValidator;
  private InjectFieldValidator fieldValidator;
  private InjectMethodValidator methodValidator;
  private ProvisionBinding.Factory provisionBindingFactory;
  private InjectConstructorFactoryGenerator factoryWriter;
  private MembersInjectionBinding.Factory membersInjectionBindingFactory;
  private MembersInjectorGenerator membersInjectorWriter;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
    this.constructorValidator = new InjectConstructorValidator();
    this.fieldValidator = new InjectFieldValidator();
    this.methodValidator = new InjectMethodValidator();
    Filer filer = processingEnv.getFiler();
    Elements elements = processingEnv.getElementUtils();
    Types types = processingEnv.getTypeUtils();
    DependencyRequest.Factory dependencyRequestFactory =
        new DependencyRequest.Factory(elements, types);
    ProviderTypeRepository providerTypeRepository = new ProviderTypeRepository(elements, types);
    this.provisionBindingFactory = new ProvisionBinding.Factory(dependencyRequestFactory);
    this.factoryWriter = new InjectConstructorFactoryGenerator(filer, providerTypeRepository);
    this.membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(dependencyRequestFactory);
    this.membersInjectorWriter = new MembersInjectorGenerator(filer, providerTypeRepository);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(Inject.class.getName());
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
        factoryWriter.generate(binding);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }

    return false;
  }
}
