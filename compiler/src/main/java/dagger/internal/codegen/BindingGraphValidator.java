package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import dagger.Component;
import dagger.internal.codegen.BindingGraph.ResolvedBindings;
import dagger.internal.codegen.BindingGraph.ResolvedBindings.State;
import dagger.internal.codegen.ContributionBinding.BindingType;
import dagger.internal.codegen.ValidationReport.Builder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.Set;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ErrorMessages.INDENT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;

public class BindingGraphValidator implements Validator<BindingGraph> {

  private final Types types;
  private final InjectBindingRegistry injectBindingRegistry;
  private final ScopeCycleValidation disableInterComponentScopeCycles;
  private final DependencyRequestMapper dependencyRequestMapper;

  BindingGraphValidator(
      Types types,
      InjectBindingRegistry injectBindingRegistry,
      ScopeCycleValidation disableInterComponentScopeCycles,
      DependencyRequestMapper dependencyRequestMapper) {
    this.types = types;
    this.injectBindingRegistry = injectBindingRegistry;
    this.disableInterComponentScopeCycles = disableInterComponentScopeCycles;
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  public ValidationReport<BindingGraph> validate(final BindingGraph subject) {
    final ValidationReport.Builder<BindingGraph> reportBuilder =
        ValidationReport.Builder.about(subject);
    ImmutableMap<FrameworkKey, ResolvedBindings> resolvedBindings = subject.resolvedBindings();

    validateComponentScope(subject, reportBuilder, resolvedBindings);
    validateDependencyScopes(subject, reportBuilder);

    for (DependencyRequest entryPoint : subject.entryPoints()) {
      ResolvedBindings resolvedBinding = resolvedBindings.get(
          dependencyRequestMapper.getFrameworkKey(entryPoint));
      if (!resolvedBinding.state().equals(State.COMPLETE)) {
        LinkedList<DependencyRequest> requestPath = Lists.newLinkedList();
        requestPath.push(entryPoint);
        traversalHelper(subject, requestPath, new Traverser() {
          @Override
          boolean visitResolvedBinding(
              Deque<DependencyRequest> requestPath, ResolvedBindings binding) {
            switch (binding.state()) {
              case COMPLETE:
              case INCOMPLETE:
                return true;
              case MISSING:
                reportMissingBinding(requestPath, reportBuilder);
                return false;
              case DUPLICATE_BINDINGS:
                reportDuplicateBindings(requestPath, binding, reportBuilder);
                return false;
              case MULTIPLE_BINDING_TYPES:
                reportMultipleBindingTypes(requestPath, binding, reportBuilder);
                return false;
              case CYCLE:
                reportCycle(requestPath, subject, reportBuilder);
                return false;
              case MALFORMED:
                return false;
              default:
                throw new AssertionError();
            }
          }
        });
      }
    }

    return reportBuilder.build();
  }

  /**
   * Validates that among the dependencies are at most one scoped dependency,
   * that there are no cycles within the scoping chain, and that singleton
   * components have no scoped dependencies.
   */
  private void validateDependencyScopes(BindingGraph subject,
      Builder<BindingGraph> reportBuilder) {
    ComponentDescriptor descriptor = subject.componentDescriptor();
    Optional<AnnotationMirror> scope = subject.componentDescriptor().scope();
    ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(descriptor.dependencies());
    if (scope.isPresent()) {
      // Dagger 1.x scope compatibility requires this be suppress-able.
      if (disableInterComponentScopeCycles.diagnosticKind().isPresent()
          && isTypeOf(Singleton.class, scope.get().getAnnotationType())) {
        // Singleton is a special-case representing the longest lifetime, and therefore
        // @Singleton components may not depend on scoped components
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message = new StringBuilder(
              "This @Singleton component cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportBuilder.addItem(message.toString(),
              disableInterComponentScopeCycles.diagnosticKind().get(),
              descriptor.componentDefinitionType(),
              descriptor.componentAnnotation());
        }
      } else if (scopedDependencies.size() > 1) {
        // Scoped components may depend on at most one scoped component.
        StringBuilder message = new StringBuilder(ErrorMessages.format(scope.get()))
            .append(' ')
            .append(descriptor.componentDefinitionType().getQualifiedName())
            .append(" depends on more than one scoped component:\n");
        appendIndentedComponentsList(message, scopedDependencies);
        reportBuilder.addItem(message.toString(),
            descriptor.componentDefinitionType(),
            descriptor.componentAnnotation());
      } else {
        // Dagger 1.x scope compatibility requires this be suppress-able.
        if (!disableInterComponentScopeCycles.equals(ScopeCycleValidation.NONE)) {
          validateScopeHierarchy(descriptor.componentDefinitionType(),
              descriptor.componentDefinitionType(),
              reportBuilder,
              new ArrayDeque<Equivalence.Wrapper<AnnotationMirror>>(),
              new ArrayDeque<TypeElement>());
        }
      }
    } else {
      // Scopeless components may not depend on scoped components.
      if (!scopedDependencies.isEmpty()) {
        StringBuilder message =
            new StringBuilder(descriptor.componentDefinitionType().getQualifiedName())
                .append(" (unscoped) cannot depend on scoped components:\n");
        appendIndentedComponentsList(message, scopedDependencies);
        reportBuilder.addItem(message.toString(),
            descriptor.componentDefinitionType(),
            descriptor.componentAnnotation());
      }
    }
  }

  /**
   * Append and format a list of indented component types (with their scope annotations)
   */
  private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
    for (TypeElement scopedComponent : types) {
      message.append(INDENT);
      Optional<AnnotationMirror> scope = getScopeAnnotation(scopedComponent);
      if (scope.isPresent()) {
        message.append(ErrorMessages.format(scope.get())).append(' ');
      }
      message.append(stripCommonTypePrefixes(scopedComponent.getQualifiedName().toString()))
          .append('\n');
    }
  }

  /**
   * Returns a set of type elements containing only those found in the input set that have
   * a scoping annotation.
   */
  private ImmutableSet<TypeElement> scopedTypesIn(Set<TypeElement> types) {
    return FluentIterable.from(types).filter(new Predicate<TypeElement>() {
      @Override public boolean apply(TypeElement input) {
        return getScopeAnnotation(input).isPresent();
      }
    }).toSet();
  }

  /**
   * Validates that scopes do not participate in a scoping cycle - that is to say, scoped
   * components are in a hierarchical relationship terminating with Singleton.
   *
   * <p>As a side-effect, this means scoped components cannot have a dependency cycle between
   * themselves, since a component's presence within its own dependency path implies a cyclical
   * relationship between scopes.
   */
  private void validateScopeHierarchy(TypeElement rootComponent,
      TypeElement componentType,
      Builder<BindingGraph> reportBuilder,
      Deque<Equivalence.Wrapper<AnnotationMirror>> scopeStack,
      Deque<TypeElement> scopedDependencyStack) {
    Optional<AnnotationMirror> scope = getScopeAnnotation(componentType);
    if (scope.isPresent()) {
      Equivalence.Wrapper<AnnotationMirror> wrappedScope =
          AnnotationMirrors.equivalence().wrap(scope.get());
      if (scopeStack.contains(wrappedScope)) {
        scopedDependencyStack.push(componentType);
        // Current scope has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(rootComponent.getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (disableInterComponentScopeCycles.diagnosticKind().isPresent()) {
          reportBuilder.addItem(message.toString(),
              disableInterComponentScopeCycles.diagnosticKind().get(),
              rootComponent, getAnnotationMirror(rootComponent, Component.class).get());
        }
        scopedDependencyStack.pop();
      } else {
        Optional<AnnotationMirror> componentAnnotation =
            getAnnotationMirror(componentType, Component.class);
        if (componentAnnotation.isPresent()) {
          ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(
              MoreTypes.asTypeElements(types, getComponentDependencies(componentAnnotation.get())));
          if (scopedDependencies.size() == 1) {
            // empty can be ignored (base-case), and > 1 is a different error reported separately.
            scopeStack.push(wrappedScope);
            scopedDependencyStack.push(componentType);
            validateScopeHierarchy(rootComponent, getOnlyElement(scopedDependencies),
                reportBuilder, scopeStack, scopedDependencyStack);
            scopedDependencyStack.pop();
            scopeStack.pop();
          }
        } // else: we skip component dependencies which are not components
      }
    }
  }

  /**
   * Validates that the scope (if any) of this component are compatible with the scopes of the
   * bindings available in this component
   */
  void validateComponentScope(final BindingGraph subject,
      final ValidationReport.Builder<BindingGraph> reportBuilder,
      ImmutableMap<FrameworkKey, ResolvedBindings> resolvedBindings) {
    Optional<Equivalence.Wrapper<AnnotationMirror>> componentScope =
        subject.componentDescriptor().wrappedScope();
    ImmutableSet.Builder<String> incompatiblyScopedMethodsBuilder = ImmutableSet.builder();
    for (ResolvedBindings bindings : resolvedBindings.values()) {
      if (bindings.kind().equals(FrameworkKey.Kind.PROVIDER)) {
        for (ProvisionBinding provisionBinding : bindings.provisionBindings()) {
          if (provisionBinding.scope().isPresent()
              && !componentScope.equals(provisionBinding.wrappedScope())) {
            // Scoped components cannot reference bindings to @Provides methods or @Inject
            // types decorated by a different scope annotation. Unscoped components cannot
            // reference to scoped @Provides methods or @Inject types decorated by any
            // scope annotation.
            switch (provisionBinding.bindingKind()) {
              case PROVISION:
                ExecutableElement provisionMethod =
                    MoreElements.asExecutable(provisionBinding.bindingElement());
                incompatiblyScopedMethodsBuilder.add(
                    MethodSignatureFormatter.instance().format(provisionMethod));
                break;
              case INJECTION:
                incompatiblyScopedMethodsBuilder.add(
                    stripCommonTypePrefixes(provisionBinding.scope().get().toString()) + " class "
                        + provisionBinding.bindingTypeElement().getQualifiedName());
                break;
              default:
                throw new IllegalStateException();
            }
          }
        }
      }
    }
    ImmutableSet<String> incompatiblyScopedMethods = incompatiblyScopedMethodsBuilder.build();
    if (!incompatiblyScopedMethods.isEmpty()) {
      TypeElement componentType = subject.componentDescriptor().componentDefinitionType();
      StringBuilder message = new StringBuilder(componentType.getQualifiedName());
      if (componentScope.isPresent()) {
        message.append(" scoped with ");
        message.append(stripCommonTypePrefixes(ErrorMessages.format(componentScope.get().get())));
        message.append(" may not reference bindings with different scopes:\n");
      } else {
        message.append(" (unscoped) may not reference scoped bindings:\n");
      }
      for (String method : incompatiblyScopedMethods) {
        message.append(ErrorMessages.INDENT).append(method).append("\n");
      }
      reportBuilder.addItem(message.toString(), componentType,
          subject.componentDescriptor().componentAnnotation());
    }
  }

  private void reportMissingBinding(
      Deque<DependencyRequest> requestPath, ValidationReport.Builder<BindingGraph> reportBuilder) {
    Key key = requestPath.peek().key();
    TypeMirror type = key.type();
    Name typeName = MoreElements.asType(types.asElement(type)).getQualifiedName();
    boolean requiresProvidesMethod = type.accept(new SimpleTypeVisitor6<Boolean, Void>() {
      @Override protected Boolean defaultAction(TypeMirror e, Void p) {
        return true;
      }

      @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
        // Note - this logic is also in InjectConstructorValidator but is woven into errors.
        TypeElement typeElement = MoreElements.asType(type.asElement());
        if (typeElement.getTypeParameters().isEmpty()
            && typeElement.getKind().equals(ElementKind.CLASS)
            && !typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
          return false;
        }
        return true;
      }
    }, null);
    StringBuilder errorMessage = new StringBuilder();
    if(requiresProvidesMethod) {
      errorMessage.append(String.format(REQUIRES_PROVIDER_FORMAT, typeName));
    } else {
      errorMessage.append(
          String.format(REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT, typeName));
    }
    if (key.isValidMembersInjectionKey()
        && !injectBindingRegistry.getOrFindMembersInjectionBinding(key).injectionSites()
            .isEmpty()) {
      errorMessage.append(" ").append(ErrorMessages.MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION);
    }
    ImmutableList<String> printableDependencyPath =
        FluentIterable.from(requestPath)
            .transform(DependencyRequestFormatter.instance())
            .toList()
            .reverse();
    for(String dependency :
        printableDependencyPath.subList(1, printableDependencyPath.size())) {
      errorMessage.append("\n").append(dependency);
    }
    reportBuilder.addItem(errorMessage.toString(), requestPath.getLast().requestElement());
  }

  private static final int DUPLICATE_SIZE_LIMIT = 10;

  @SuppressWarnings("resource") // Appendable is a StringBuilder.
  private void reportDuplicateBindings(Deque<DependencyRequest> requestPath,
      ResolvedBindings resolvedBinding, ValidationReport.Builder<BindingGraph> reportBuilder) {
    StringBuilder builder = new StringBuilder();
    new Formatter(builder).format(ErrorMessages.DUPLICATE_BINDINGS_FOR_KEY_FORMAT,
        KeyFormatter.instance().format(requestPath.peek().key()));
    for (Binding binding : Iterables.limit(resolvedBinding.bindings(), DUPLICATE_SIZE_LIMIT)) {
      builder.append('\n').append(INDENT);
      builder.append(ProvisionBindingFormatter.instance().format((ProvisionBinding) binding));
    }
    int numberOfOtherBindings = resolvedBinding.bindings().size() - DUPLICATE_SIZE_LIMIT;
    if (numberOfOtherBindings > 0) {
      builder.append('\n').append(INDENT)
          .append("and ").append(numberOfOtherBindings).append(" other");
    }
    if (numberOfOtherBindings > 1) {
      builder.append('s');
    }
    reportBuilder.addItem(builder.toString(), requestPath.getLast().requestElement());
  }

  @SuppressWarnings("resource") // Appendable is a StringBuilder.
  private void reportMultipleBindingTypes(Deque<DependencyRequest> requestPath,
      ResolvedBindings resolvedBinding, ValidationReport.Builder<BindingGraph> reportBuilder) {
    StringBuilder builder = new StringBuilder();
    new Formatter(builder).format(ErrorMessages.MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT,
        KeyFormatter.instance().format(requestPath.peek().key()));
    @SuppressWarnings("unchecked")
    ImmutableListMultimap<BindingType, ProvisionBinding> bindingsByType =
        ProvisionBinding.bindingTypesFor((Iterable<ProvisionBinding>) resolvedBinding.bindings());
    for (BindingType type :
        Ordering.natural().immutableSortedCopy(bindingsByType.keySet())) {
      builder.append(INDENT);
      builder.append(formatBindingType(type));
      builder.append(" bindings:\n");
      for (ProvisionBinding binding : bindingsByType.get(type)) {
        builder.append(INDENT).append(INDENT);
        builder.append(ProvisionBindingFormatter.instance().format(binding));
        builder.append('\n');
      }
    }
    reportBuilder.addItem(builder.toString(), requestPath.getLast().requestElement());
  }

  private String formatBindingType(BindingType type) {
    switch(type) {
      case MAP:
        return "Map";
      case SET:
        return "Set";
      case UNIQUE:
        return "Unique";
      default:
        throw new IllegalStateException("Unknown binding type: " + type);
    }
  }

  private void reportCycle(Deque<DependencyRequest> requestPath,
      BindingGraph graph, final ValidationReport.Builder<BindingGraph> reportBuilder) {
    final DependencyRequest startingRequest = requestPath.peek();
    final Key cycleKey = startingRequest.key();
    traversalHelper(graph, requestPath, new Traverser() {
      @Override
      boolean visitResolvedBinding(Deque<DependencyRequest> requestPath, ResolvedBindings binding) {
        DependencyRequest request = requestPath.peek();
        boolean endOfCycle = !startingRequest.equals(request) && cycleKey.equals(request.key());
        if (endOfCycle) {
          ImmutableList<String> printableDependencyPath = FluentIterable.from(requestPath)
              .transform(DependencyRequestFormatter.instance()).toList().reverse();
          DependencyRequest rootRequest = requestPath.getLast();
          TypeElement componentType =
              MoreElements.asType(rootRequest.requestElement().getEnclosingElement());
          // TODO(user): Restructure to provide a hint for the start and end of the cycle.
          reportBuilder.addItem(
              String.format(ErrorMessages.CONTAINS_DEPENDENCY_CYCLE_FORMAT,
                  componentType.getQualifiedName(),
                  rootRequest.requestElement().getSimpleName(),
                  Joiner.on("\n")
                      .join(printableDependencyPath.subList(1, printableDependencyPath.size()))),
                  rootRequest.requestElement());
        }
        return !endOfCycle;
      }
    });

  }

  private void traversalHelper(BindingGraph graph, Deque<DependencyRequest> requestPath,
      Traverser traverser) {
    ResolvedBindings resolvedBinding = graph.resolvedBindings().get(
        dependencyRequestMapper.getFrameworkKey(requestPath.peek()));
    ImmutableSet<DependencyRequest> allDeps =
        FluentIterable.from(resolvedBinding.bindings())
            .transformAndConcat(
                new Function<Binding, Set<DependencyRequest>>() {
                  @Override
                  public Set<DependencyRequest> apply(Binding input) {
                    return input.implicitDependencies();
                  }
                })
            .toSet();
    boolean descend = traverser.visitResolvedBinding(requestPath, resolvedBinding);
    if (descend) {
      for (DependencyRequest dependency : allDeps) {
        requestPath.push(dependency);
        traversalHelper(graph, requestPath, traverser);
        requestPath.pop();
      }
    }
  }

  static abstract class Traverser {
    abstract boolean visitResolvedBinding(
        Deque<DependencyRequest> requestPath, ResolvedBindings binding);
  }

  /**
   * {@code -Adagger.disableInterComponentScopeValidation=none} will suppress validation of
   * scoping relationships between dagger {@code @Component} interfaces. This is a migration
   * tool to permit easier migration from Dagger 1.x which used {@code @Singleton} for scoped
   * graphs in any lifetime.
   *
   * <p>The value can be (case-insensitively) set to any of {@code ERROR}, {@code WARNING},
   * or {@code NONE} and defaults to {@code ERROR}.
   */
  enum ScopeCycleValidation {
    ERROR,
    WARNING,
    NONE;

    Optional<Diagnostic.Kind> diagnosticKind() {
      switch (this) {
        case ERROR:
          return Optional.of(Diagnostic.Kind.ERROR);
        case WARNING:
          return Optional.of(Diagnostic.Kind.WARNING);
        default:
          return Optional.absent();
      }
    }
  }
}
