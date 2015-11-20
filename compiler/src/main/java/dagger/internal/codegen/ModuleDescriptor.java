package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleIncludes;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.util.ElementFilter.methodsIn;

@AutoValue
abstract class ModuleDescriptor {
  static final Function<ModuleDescriptor, TypeElement> getModuleElement() {
    return new Function<ModuleDescriptor, TypeElement>() {
      @Override public TypeElement apply(ModuleDescriptor input) {
        return input.moduleElement();
      }
    };
  }

  abstract AnnotationMirror moduleAnnotation();

  abstract TypeElement moduleElement();

  abstract ImmutableSet<ModuleDescriptor> includedModules();

  abstract ImmutableSet<ContributionBinding> bindings();

  enum DefaultCreationStrategy {
    PASSED,
    CONSTRUCTED,
  }

  abstract DefaultCreationStrategy defaultCreationStrategy();

  enum Kind {
    MODULE(
        Module.class, Provides.class, ImmutableSet.of(Module.class)),
    PRODUCER_MODULE(
        ProducerModule.class,
        Produces.class,
        ImmutableSet.of(Module.class, ProducerModule.class));

    private final Class<? extends Annotation> moduleAnnotation;
    private final Class<? extends Annotation> methodAnnotation;
    private final ImmutableSet<? extends Class<? extends Annotation>> includesTypes;

    /**
     * Returns the kind of an annotated element if it is annotated with one of the
     * {@linkplain #moduleAnnotation() annotation types}.
     *
     * @throws IllegalArgumentException if the element is annotated with more than one of the
     *     annotation types
     */
    static Optional<Kind> forAnnotatedElement(TypeElement element) {
      Set<Kind> kinds = EnumSet.noneOf(Kind.class);
      for (Kind kind : values()) {
        if (MoreElements.isAnnotationPresent(element, kind.moduleAnnotation())) {
          kinds.add(kind);
        }
      }
      checkArgument(
          kinds.size() <= 1, "%s cannot be annotated with more than one of %s", element, kinds);
      return Optional.fromNullable(Iterables.getOnlyElement(kinds, null));
    }

    Kind(
        Class<? extends Annotation> moduleAnnotation,
        Class<? extends Annotation> methodAnnotation,
        ImmutableSet<? extends Class<? extends Annotation>> includesTypes) {
      this.moduleAnnotation = moduleAnnotation;
      this.methodAnnotation = methodAnnotation;
      this.includesTypes = includesTypes;
    }

    Class<? extends Annotation> moduleAnnotation() {
      return moduleAnnotation;
    }

    Class<? extends Annotation> methodAnnotation() {
      return methodAnnotation;
    }

    ImmutableSet<? extends Class<? extends Annotation>> includesTypes() {
      return includesTypes;
    }
  }

  static final class Factory {
    private final Elements elements;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final ProductionBinding.Factory productionBindingFactory;

    Factory(
        Elements elements,
        ProvisionBinding.Factory provisionBindingFactory,
        ProductionBinding.Factory productionBindingFactory) {
      this.elements = elements;
      this.provisionBindingFactory = provisionBindingFactory;
      this.productionBindingFactory = productionBindingFactory;
    }

    ModuleDescriptor create(TypeElement moduleElement) {
      AnnotationMirror moduleAnnotation = getModuleAnnotation(moduleElement).get();

      ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
      for (ExecutableElement moduleMethod : methodsIn(elements.getAllMembers(moduleElement))) {
        if (isAnnotationPresent(moduleMethod, Provides.class)) {
          bindings.add(
              provisionBindingFactory.forProvidesMethod(moduleMethod, moduleElement.asType()));
        }
        if (isAnnotationPresent(moduleMethod, Produces.class)) {
          bindings.add(
              productionBindingFactory.forProducesMethod(moduleMethod, moduleElement.asType()));
        }
      }

      DefaultCreationStrategy defaultCreationStrategy =
          (componentCanMakeNewInstances(moduleElement)
              && moduleElement.getTypeParameters().isEmpty())
                  ? ModuleDescriptor.DefaultCreationStrategy.CONSTRUCTED
                  : ModuleDescriptor.DefaultCreationStrategy.PASSED;

      return new AutoValue_ModuleDescriptor(
          moduleAnnotation,
          moduleElement,
          ImmutableSet.copyOf(
              collectIncludedModules(new LinkedHashSet<ModuleDescriptor>(), moduleElement)),
          bindings.build(),
          defaultCreationStrategy);
    }

    private static Optional<AnnotationMirror> getModuleAnnotation(TypeElement moduleElement) {
      return getAnnotationMirror(moduleElement, Module.class)
          .or(getAnnotationMirror(moduleElement, ProducerModule.class));
    }

    private Set<ModuleDescriptor> collectIncludedModules(
        Set<ModuleDescriptor> includedModules, TypeElement moduleElement) {
      TypeMirror superclass = moduleElement.getSuperclass();
      if (!superclass.getKind().equals(NONE)) {
        verify(superclass.getKind().equals(DECLARED));
        TypeElement superclassElement = MoreTypes.asTypeElement(superclass);
        if (!superclassElement.getQualifiedName().contentEquals(Object.class.getCanonicalName())) {
          collectIncludedModules(includedModules, superclassElement);
        }
      }
      Optional<AnnotationMirror> moduleAnnotation = getModuleAnnotation(moduleElement);
      if (moduleAnnotation.isPresent()) {
        for (TypeMirror moduleIncludesType : getModuleIncludes(moduleAnnotation.get())) {
          includedModules.add(create(MoreTypes.asTypeElement(moduleIncludesType)));
        }
      }
      return includedModules;
    }
  }
}
