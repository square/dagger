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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.Util.elementsWithAnnotation;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep implements ProcessingStep {

  /**
   * A {@link ProcessingStep} for {@code @Module} classes that generates factories for {@code
   * @Provides} methods.
   */
  static ModuleProcessingStep moduleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      final ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      ProvidesMethodValidator providesMethodValidator,
      BindsMethodValidator bindsMethodValidator,
      MultibindsMethodValidator multibindsMethodValidator) {
    return new ModuleProcessingStep(
        messager,
        Module.class,
        moduleValidator,
        ImmutableSet.<ModuleMethodFactoryGenerator>of(
            new ProvisionModuleMethodFactoryGenerator(provisionBindingFactory, factoryGenerator)),
        ImmutableSet.of(providesMethodValidator, bindsMethodValidator, multibindsMethodValidator));
  }

  /**
   * A {@link ProcessingStep} for {@code @ProducerModule} classes that generates factories for
   * {@code @Provides} and {@code @Produces} methods.
   */
  static ModuleProcessingStep producerModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      ProvidesMethodValidator providesMethodValidator,
      ProductionBinding.Factory productionBindingFactory,
      ProducerFactoryGenerator producerFactoryGenerator,
      ProducesMethodValidator producesMethodValidator,
      BindsMethodValidator bindsMethodValidator,
      MultibindsMethodValidator multibindsMethodValidator) {
    return new ModuleProcessingStep(
        messager,
        ProducerModule.class,
        moduleValidator,
        ImmutableSet.of(
            new ProvisionModuleMethodFactoryGenerator(provisionBindingFactory, factoryGenerator),
            new ProductionModuleMethodFactoryGenerator(
                productionBindingFactory, producerFactoryGenerator)),
        ImmutableSet.of(
            providesMethodValidator,
            producesMethodValidator,
            bindsMethodValidator,
            multibindsMethodValidator));
  }

  private final Messager messager;
  private final Class<? extends Annotation> moduleAnnotation;
  private final ModuleValidator moduleValidator;
  private final ImmutableSet<ModuleMethodFactoryGenerator> moduleMethodFactoryGenerators;
  private final ImmutableSet<? extends BindingMethodValidator> methodValidators;
  private final Set<TypeElement> processedModuleElements = Sets.newLinkedHashSet();

  /**
   * Creates a new processing step.
   *
   * @param moduleAnnotation the annotation on the module class
   * @param methodValidators validators for binding methods
   */
  ModuleProcessingStep(
      Messager messager,
      Class<? extends Annotation> moduleAnnotation,
      ModuleValidator moduleValidator,
      ImmutableSet<ModuleMethodFactoryGenerator> moduleMethodFactoryGenerators,
      Iterable<? extends BindingMethodValidator> methodValidators) {
    this.messager = messager;
    this.moduleAnnotation = moduleAnnotation;
    this.moduleValidator = moduleValidator;
    this.moduleMethodFactoryGenerators = moduleMethodFactoryGenerators;
    this.methodValidators = ImmutableSet.copyOf(methodValidators);
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    ImmutableSet.Builder<Class<? extends Annotation>> annotations = ImmutableSet.builder();
    annotations.add(moduleAnnotation);
    for (BindingMethodValidator validator : methodValidators) {
      annotations.add(validator.methodAnnotation());
    }
    return annotations.build();
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet<ExecutableElement> validMethods = validMethods(elementsByAnnotation);

    // process each module
    for (TypeElement moduleElement :
        Sets.difference(
            typesIn(elementsByAnnotation.get(moduleAnnotation)), processedModuleElements)) {
      ValidationReport<TypeElement> report = moduleValidator.validate(moduleElement);
      report.printMessagesTo(messager);

      if (report.isClean()) {
        List<ExecutableElement> moduleMethods = methodsIn(moduleElement.getEnclosedElements());
        if (moduleMethodsAreValid(validMethods, moduleMethods)) {
          for (ModuleMethodFactoryGenerator generator : moduleMethodFactoryGenerators) {
            for (ExecutableElement method :
                elementsWithAnnotation(moduleMethods, generator.factoryMethodAnnotation())) {
              generator.generate(method, moduleElement, messager);
            }
          }
        }
      }
      processedModuleElements.add(moduleElement);
    }
    return ImmutableSet.of();
  }

  /** The binding methods that are valid according to their validator. */
  private ImmutableSet<ExecutableElement> validMethods(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<ExecutableElement> validMethods = ImmutableSet.builder();
    for (BindingMethodValidator validator : methodValidators) {
      validMethods.addAll(
          validator.validate(
              messager, methodsIn(elementsByAnnotation.get(validator.methodAnnotation()))));
    }
    return validMethods.build();
  }

  /**
   * {@code true} if all {@code moduleMethods} that are annotated with a binding method annotation
   * are in {@code validMethods}.
   */
  private boolean moduleMethodsAreValid(
      ImmutableSet<ExecutableElement> validMethods, Iterable<ExecutableElement> moduleMethods) {
    for (ExecutableElement methodElement : moduleMethods) {
      if (!validMethods.contains(methodElement)) {
        for (BindingMethodValidator validator : methodValidators) {
          if (isAnnotationPresent(methodElement, validator.methodAnnotation())) {
            return false;
          }
        }
      }
    }
    return true;
  }

  interface ModuleMethodFactoryGenerator {
    /** Binding method annotation for which factories should be generated. */
    Class<? extends Annotation> factoryMethodAnnotation();

    /** Generates the factory source file for the given method and module. */
    void generate(ExecutableElement method, TypeElement moduleElement, Messager messager);
  }

  private static final class ProvisionModuleMethodFactoryGenerator
      implements ModuleMethodFactoryGenerator {

    private final ProvisionBinding.Factory provisionBindingFactory;
    private final FactoryGenerator factoryGenerator;

    ProvisionModuleMethodFactoryGenerator(
        ProvisionBinding.Factory provisionBindingFactory, FactoryGenerator factoryGenerator) {
      this.provisionBindingFactory = provisionBindingFactory;
      this.factoryGenerator = factoryGenerator;
    }

    @Override
    public Class<? extends Annotation> factoryMethodAnnotation() {
      return Provides.class;
    }

    @Override
    public void generate(ExecutableElement method, TypeElement moduleElement, Messager messager) {
      factoryGenerator.generate(
          provisionBindingFactory.forProvidesMethod(method, moduleElement), messager);
    }
  }

  private static final class ProductionModuleMethodFactoryGenerator
      implements ModuleMethodFactoryGenerator {

    private final ProductionBinding.Factory productionBindingFactory;
    private final ProducerFactoryGenerator producerFactoryGenerator;

    ProductionModuleMethodFactoryGenerator(
        ProductionBinding.Factory productionBindingFactory,
        ProducerFactoryGenerator productionFactoryGenerator) {
      this.productionBindingFactory = productionBindingFactory;
      this.producerFactoryGenerator = productionFactoryGenerator;
    }

    @Override
    public Class<? extends Annotation> factoryMethodAnnotation() {
      return Produces.class;
    }

    @Override
    public void generate(ExecutableElement method, TypeElement moduleElement, Messager messager) {
      producerFactoryGenerator.generate(
          productionBindingFactory.forProducesMethod(method, moduleElement), messager);
    }
  }
}
