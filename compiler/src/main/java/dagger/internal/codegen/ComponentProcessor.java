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

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

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
public final class ComponentProcessor extends BasicAnnotationProcessor {
  private InjectBindingRegistry injectBindingRegistry;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return ImmutableSet.of(DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY, NULLABLE_VALIDATION_KEY);
  }

  @Override
  protected Iterable<ProcessingStep> initSteps() {
    Messager messager = processingEnv.getMessager();
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    Filer filer = processingEnv.getFiler();

    Diagnostic.Kind nullableDiagnosticType =
        nullableValidationType(processingEnv).diagnosticKind().get();

    MethodSignatureFormatter methodSignatureFormatter = new MethodSignatureFormatter(types);
    ProvisionBindingFormatter provisionBindingFormatter =
        new ProvisionBindingFormatter(methodSignatureFormatter);
    ProductionBindingFormatter productionBindingFormatter =
        new ProductionBindingFormatter(methodSignatureFormatter);
    DependencyRequestFormatter dependencyRequestFormatter = new DependencyRequestFormatter(types);
    KeyFormatter keyFormatter = new KeyFormatter();

    InjectConstructorValidator injectConstructorValidator = new InjectConstructorValidator();
    InjectFieldValidator injectFieldValidator = new InjectFieldValidator();
    InjectMethodValidator injectMethodValidator = new InjectMethodValidator();
    ModuleValidator moduleValidator = new ModuleValidator(types, elements, methodSignatureFormatter,
        Module.class, Provides.class);
    ProvidesMethodValidator providesMethodValidator = new ProvidesMethodValidator(elements);
    ComponentValidator componentValidator =
        new ComponentValidator(elements, types, moduleValidator);
    MapKeyValidator mapKeyValidator = new MapKeyValidator();
    ModuleValidator producerModuleValidator = new ModuleValidator(types, elements,
        methodSignatureFormatter, ProducerModule.class, Produces.class);
    ProducesMethodValidator producesMethodValidator = new ProducesMethodValidator(elements);
    ProductionComponentValidator productionComponentValidator = new ProductionComponentValidator();

    Key.Factory keyFactory = new Key.Factory(types, elements);

    FactoryGenerator factoryGenerator =
        new FactoryGenerator(filer, DependencyRequestMapper.FOR_PROVIDER, nullableDiagnosticType);
    MembersInjectorGenerator membersInjectorGenerator =
        new MembersInjectorGenerator(filer, elements, types, DependencyRequestMapper.FOR_PROVIDER);
    ComponentGenerator componentGenerator =
        new ComponentGenerator(filer, types, nullableDiagnosticType);
    ProducerFactoryGenerator producerFactoryGenerator =
        new ProducerFactoryGenerator(filer, DependencyRequestMapper.FOR_PRODUCER);

    DependencyRequest.Factory dependencyRequestFactory = new DependencyRequest.Factory(keyFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    this.injectBindingRegistry = new InjectBindingRegistry(
        elements, types, messager, provisionBindingFactory, factoryGenerator,
        membersInjectionBindingFactory, membersInjectorGenerator);

    ComponentDescriptor.Factory componentDescriptorFactory =
        new ComponentDescriptor.Factory(elements, types, dependencyRequestFactory);

    BindingGraph.Factory bindingGraphFactory = new BindingGraph.Factory(
        elements, types, injectBindingRegistry, keyFactory,
        dependencyRequestFactory, provisionBindingFactory, productionBindingFactory);

    MapKeyGenerator mapKeyGenerator = new MapKeyGenerator(filer);
    BindingGraphValidator bindingGraphValidator = new BindingGraphValidator(
        types,
        injectBindingRegistry,
        scopeValidationType(processingEnv),
        nullableDiagnosticType,
        provisionBindingFormatter,
        productionBindingFormatter,
        methodSignatureFormatter,
        dependencyRequestFormatter,
        keyFormatter);

    return ImmutableList.<ProcessingStep>of(
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
            producerFactoryGenerator),
        new ProductionComponentProcessingStep(
            messager,
            productionComponentValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGenerator));
  }

  @Override
  protected void postProcess() {
    try {
      injectBindingRegistry.generateSourcesForRequiredBindings();
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(processingEnv.getMessager());
    }
  }

  private static final String DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY =
      "dagger.disableInterComponentScopeValidation";

  private static final String NULLABLE_VALIDATION_KEY =
      "dagger.nullableValidation";

  private static ValidationType scopeValidationType(ProcessingEnvironment processingEnv) {
    return validationTypeFor(processingEnv,
        DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.allOf(ValidationType.class));
  }

  private static ValidationType nullableValidationType(ProcessingEnvironment processingEnv) {
    return validationTypeFor(processingEnv,
        NULLABLE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType validationTypeFor(ProcessingEnvironment processingEnv, String key,
      ValidationType defaultValue, Set<ValidationType> validValues) {
    Map<String, String> options = processingEnv.getOptions();
    if (options.containsKey(key)) {
      try {
        ValidationType type = ValidationType.valueOf(options.get(key).toUpperCase());
        if (!validValues.contains(type)) {
          throw new IllegalArgumentException(); // let handler below print out good msg.
        }
        return type;
      } catch (IllegalArgumentException e) {
        processingEnv.getMessager().printMessage(ERROR, "Processor option -A"
            + key + " may only have the values " + validValues
            + " (case insensitive), found: " + options.get(key));
      }
    }
    return defaultValue;
  }
}
