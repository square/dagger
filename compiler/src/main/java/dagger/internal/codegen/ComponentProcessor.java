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

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.MapKey;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.BindingGraphValidator.ScopeCycleValidation;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * TODO(gak): give this some better documentation
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoService(Processor.class)
public final class ComponentProcessor extends AbstractProcessor {
  private ImmutableList<ProcessingStep> processingSteps;
  private InjectBindingRegistry injectBindingRegistry;

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        Component.class.getName(),
        Inject.class.getName(),
        Module.class.getName(),
        Provides.class.getName(),
        MapKey.class.getName(),
        ProducerModule.class.getName(),
        Produces.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return ImmutableSet.of(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY);
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    Messager messager = processingEnv.getMessager();
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    Filer filer = processingEnv.getFiler();

    InjectConstructorValidator injectConstructorValidator = new InjectConstructorValidator();
    InjectFieldValidator injectFieldValidator = new InjectFieldValidator();
    InjectMethodValidator injectMethodValidator = new InjectMethodValidator();
    ModuleValidator moduleValidator = new ModuleValidator(types, Module.class, Provides.class);
    ProvidesMethodValidator providesMethodValidator = new ProvidesMethodValidator(elements);
    ComponentValidator componentValidator = new ComponentValidator();
    MapKeyValidator mapKeyValidator = new MapKeyValidator();
    ModuleValidator producerModuleValidator = new ModuleValidator(
        types, ProducerModule.class, Produces.class);
    ProducesMethodValidator producesMethodValidator = new ProducesMethodValidator(elements);

    Key.Factory keyFactory = new Key.Factory(types, elements);

    FactoryGenerator factoryGenerator = new FactoryGenerator(filer);
    MembersInjectorGenerator membersInjectorGenerator =
        new MembersInjectorGenerator(filer, elements, types);
    ComponentGenerator componentGenerator = new ComponentGenerator(filer);
    ProducerFactoryGenerator producerFactoryGenerator = new ProducerFactoryGenerator(filer);

    DependencyRequest.Factory dependencyRequestFactory =
        new DependencyRequest.Factory(types, keyFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    this.injectBindingRegistry = new InjectBindingRegistry(
        elements, types, messager, provisionBindingFactory, factoryGenerator,
        membersInjectionBindingFactory, membersInjectorGenerator);

    ComponentDescriptor.Factory componentDescriptorFactory =
        new ComponentDescriptor.Factory(elements, types, provisionBindingFactory);

    BindingGraph.Factory bindingGraphFactory = new BindingGraph.Factory(
        elements, types, injectBindingRegistry, keyFactory, dependencyRequestFactory,
        provisionBindingFactory);

    MapKeyGenerator mapKeyGenerator = new MapKeyGenerator(filer);
    BindingGraphValidator bindingGraphValidator = new BindingGraphValidator(types,
        injectBindingRegistry, disableInterComponentScopeValidation(processingEnv));

    this.processingSteps = ImmutableList.<ProcessingStep>of(
        new MapKeyProcessingStep(
            messager,
            mapKeyValidator,
            mapKeyGenerator),
        new InjectProcessingStep(
            messager,
            injectConstructorValidator,
            injectFieldValidator,
            injectMethodValidator,
            provisionBindingFactory,
            membersInjectionBindingFactory,
            injectBindingRegistry),
        new ModuleProcessingStep(
            messager,
            moduleValidator,
            providesMethodValidator,
            provisionBindingFactory,
            factoryGenerator),
        new ComponentProcessingStep(
            messager,
            componentValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGenerator),
        new ProducerModuleProcessingStep(
            messager,
            producerModuleValidator,
            producesMethodValidator,
            productionBindingFactory,
            producerFactoryGenerator));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (ProcessingStep processingStep : processingSteps) {
      processingStep.process(annotations, roundEnv);
    }
    try {
      injectBindingRegistry.generateSourcesForRequiredBindings();
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(processingEnv.getMessager());
    }
    return false;
  }

  private static final String DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY =
      "dagger.disableInterComponentScopeValidation";

  private static ScopeCycleValidation disableInterComponentScopeValidation(
      ProcessingEnvironment processingEnv) {
    Map<String, String> options = processingEnv.getOptions();
    if(options.containsKey(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY)) {
      try {
        return ScopeCycleValidation.valueOf(
            options.get(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY).toUpperCase());
      } catch (IllegalArgumentException e) {
        processingEnv.getMessager().printMessage(ERROR, "Processor option -A"
            + DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY
            + " may only have the values ERROR, WARNING, or NONE (case insensitive) "
            + " found: " + options.get(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY));
      }
    }
    return ScopeCycleValidation.ERROR;
  }
}
