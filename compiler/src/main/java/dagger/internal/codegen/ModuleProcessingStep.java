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
import com.google.auto.common.MoreElements;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.util.ElementFilter.methodsIn;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Module}
 * (and {@link Provides}) annotation.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ModuleProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final Validator<ExecutableElement> providesMethodValidator;
  private final Validator<ExecutableElement> bindsMethodValidator;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final FactoryGenerator factoryGenerator;
  private final Set<Element> processedModuleElements = Sets.newLinkedHashSet();

  ModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      Validator<ExecutableElement> providesMethodValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      Validator<ExecutableElement> bindsMethodValidator,
      FactoryGenerator factoryGenerator) {
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.providesMethodValidator = providesMethodValidator;
    this.bindsMethodValidator = bindsMethodValidator;
    this.provisionBindingFactory = provisionBindingFactory;
    this.factoryGenerator = factoryGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Module.class, Provides.class, Binds.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    // first, check and collect all provides methods
    ImmutableSet<ExecutableElement> validProvidesMethods =
        providesMethodValidator.validate(
            messager, methodsIn(elementsByAnnotation.get(Provides.class)));

    // second, check and collect all bind methods
    ImmutableSet<ExecutableElement> validBindsMethods =
        bindsMethodValidator.validate(messager, methodsIn(elementsByAnnotation.get(Binds.class)));

    // process each module
    for (Element moduleElement :
        Sets.difference(elementsByAnnotation.get(Module.class), processedModuleElements)) {
      ValidationReport<TypeElement> report =
          moduleValidator.validate(MoreElements.asType(moduleElement));
      report.printMessagesTo(messager);

      if (report.isClean()) {
        ImmutableSet.Builder<ExecutableElement> moduleProvidesMethodsBuilder =
            ImmutableSet.builder();
        ImmutableSet.Builder<ExecutableElement> moduleBindsMethodsBuilder =
            ImmutableSet.builder();
        List<ExecutableElement> moduleMethods =
            ElementFilter.methodsIn(moduleElement.getEnclosedElements());
        for (ExecutableElement methodElement : moduleMethods) {
          if (isAnnotationPresent(methodElement, Provides.class)) {
            moduleProvidesMethodsBuilder.add(methodElement);
          }
          if (isAnnotationPresent(methodElement, Binds.class)) {
            moduleBindsMethodsBuilder.add(methodElement);
          }
        }
        ImmutableSet<ExecutableElement> moduleProvidesMethods =
            moduleProvidesMethodsBuilder.build();
        ImmutableSet<ExecutableElement> moduleBindsMethods =
            moduleBindsMethodsBuilder.build();

        if (Sets.difference(moduleProvidesMethods, validProvidesMethods).isEmpty()
            && Sets.difference(moduleBindsMethods, validBindsMethods).isEmpty()) {
          // all of the provides and bind methods in this module are valid!
          // time to generate some factories!
          ImmutableSet<ProvisionBinding> bindings =
              FluentIterable.from(moduleProvidesMethods)
                  .transform(
                      new Function<ExecutableElement, ProvisionBinding>() {
                        @Override
                        public ProvisionBinding apply(ExecutableElement providesMethod) {
                          return provisionBindingFactory.forProvidesMethod(
                              providesMethod,
                              MoreElements.asType(providesMethod.getEnclosingElement()));
                        }
                      })
                  .toSet();

          try {
            for (ProvisionBinding binding : bindings) {
              factoryGenerator.generate(binding);
            }
          } catch (SourceFileGenerationException e) {
            e.printMessageTo(messager);
          }
        }
      }
      processedModuleElements.add(moduleElement);
    }
    return ImmutableSet.of();
  }
}
