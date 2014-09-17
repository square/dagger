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
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Provides;
import java.util.Iterator;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.Provides.Type.MAP;
import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.PUBLIC;
import static dagger.internal.codegen.ErrorMessages.NON_SETBINDING;
import static dagger.internal.codegen.ErrorMessages.NON_MAPBINDING;
import static dagger.internal.codegen.ErrorMessages.INVALID_COLLECTIONBINDING;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends Binding {
  @Override
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return new ImmutableSet.Builder<DependencyRequest>()
        .addAll(dependencies())
        .addAll(memberInjectionRequest().asSet())
        .build();
  }

  enum Kind {
    /** Represents an {@link Inject} binding. */
    INJECTION,
    /** Represents a binding configured by {@link Provides}. */
    PROVISION,
    /** Represents the implicit binding to the component. */
    COMPONENT,
    /** Represents a binding from a provision method on a component dependency. */
    COMPONENT_PROVISION,
  }

  /**
   * The type of binding ({@link Inject} or {@link Provides}). For the particular type of provision,
   * use {@link #provisionType}.
   */
  abstract Kind bindingKind();

  /** Returns provision type that was used to bind the key. */
  abstract Provides.Type provisionType();

  /** The {@link Key} that is provided by this binding. */
  abstract Key providedKey();

  /** The scope in which the binding declares the {@link #providedKey()}. */
  abstract Optional<AnnotationMirror> scope();

  /** If this provision requires members injeciton, this will be the corresonding request. */
  abstract Optional<DependencyRequest> memberInjectionRequest();

  private static ImmutableSet<Provides.Type> SET_BINDING_TYPES = immutableEnumSet(SET, SET_VALUES);
  private static ImmutableSet<Provides.Type> MAP_BINDING_TYPES = immutableEnumSet(MAP);
  
  
  static enum BindingsType {
    /** Represents set bindings. */
    SET_BINDING, 
    /** Represents map bindings. */
    MAP_BINDING, 
    /** Represents a valid non-collection binding. */
    SINGULAR_BINDING,
  }
  
  /**
   * Returns {@code BindingsType} for bindings, which can be {@code SETBINDING} if the given
   * bindings are all contributors to a set binding. Returns {@code MAPBINDING} if the given
   * bindings are all contributors to a map binding. Returns {@code NONCOLLECTIONBINDING} if the
   * given bindings is not a collection.
   *
   * @throws IllegalArgumentException if some of the bindings are map bindings or set bindings and
   *         some are not.
   * @throws IllegalArgumentException if the bindings in the collection are not supported in Dagger
   *         (Not set bindings or map Bindings).
   */
  static BindingsType getBindingsType(Iterable<ProvisionBinding> bindings) {
    checkNotNull(bindings);
    switch (Iterables.size(bindings)) {
      case 0:
        throw new IllegalArgumentException("no bindings");
      case 1:
        if (SET_BINDING_TYPES.contains(Iterables.getOnlyElement(bindings).provisionType())) {
          return BindingsType.SET_BINDING;
        } else if (MAP_BINDING_TYPES.contains(Iterables.getOnlyElement(bindings).provisionType())) {
          return BindingsType.MAP_BINDING;
        }
        return BindingsType.SINGULAR_BINDING;
      default:
        Iterator<ProvisionBinding> iterator = bindings.iterator();
        boolean setBinding = SET_BINDING_TYPES.contains(iterator.next().provisionType());
        if (setBinding) {
          while (iterator.hasNext()) {
            checkArgument(setBinding, NON_SETBINDING);
            checkArgument(SET_BINDING_TYPES.contains(iterator.next().provisionType()),
                NON_SETBINDING);
          }
          return BindingsType.SET_BINDING;
        }

        iterator = bindings.iterator();
        boolean mapBinding = MAP_BINDING_TYPES.contains(iterator.next().provisionType());
        if (mapBinding) {
          while (iterator.hasNext()) {
            checkArgument(mapBinding, NON_MAPBINDING);
            checkArgument(MAP_BINDING_TYPES.contains(iterator.next().provisionType()),
                NON_MAPBINDING);
          }
          return BindingsType.MAP_BINDING;
        }
        throw new IllegalStateException(INVALID_COLLECTIONBINDING);
    }
  }
  
  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    private static Optional<String> findBindingPackage(Key providedKey) {
      Set<String> packages = nonPublicPackageUse(providedKey.type());
      switch (packages.size()) {
        case 0:
          return Optional.absent();
        case 1:
          return Optional.of(packages.iterator().next());
        default:
          throw new IllegalStateException();
      }
    }

    private static Set<String> nonPublicPackageUse(TypeMirror typeMirror) {
      ImmutableSet.Builder<String> packages = ImmutableSet.builder();
      typeMirror.accept(new SimpleTypeVisitor6<Void, ImmutableSet.Builder<String>>() {
        @Override
        public Void visitArray(ArrayType t, ImmutableSet.Builder<String> p) {
          return t.getComponentType().accept(this, p);
        }

        @Override
        public Void visitDeclared(DeclaredType t, ImmutableSet.Builder<String> p) {
          for (TypeMirror typeArgument : t.getTypeArguments()) {
            typeArgument.accept(this, p);
          }
          // TODO(gak): address public nested types in non-public types
          TypeElement typeElement = MoreElements.asType(t.asElement());
          if (!typeElement.getModifiers().contains(PUBLIC)) {
            PackageElement elementPackage = MoreElements.getPackage(typeElement);
            Name qualifiedName = elementPackage.getQualifiedName();
            p.add(qualifiedName.toString());
          }
          return null;
        }

        @Override
        public Void visitTypeVariable(TypeVariable t, ImmutableSet.Builder<String> p) {
          t.getLowerBound().accept(this, p);
          t.getUpperBound().accept(this, p);
          return null;
        }

        @Override
        public Void visitWildcard(WildcardType t, ImmutableSet.Builder<String> p) {
          if (t.getExtendsBound() != null) {
            t.getExtendsBound().accept(this, p);
          }
          if (t.getSuperBound() != null) {
            t.getSuperBound().accept(this, p);
          }
          return null;
        }
      }, packages);
      return packages.build();
    }

    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(isAnnotationPresent(constructorElement, Inject.class));
      Key key = keyFactory.forInjectConstructor(constructorElement);
      checkArgument(!key.qualifier().isPresent());
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredVariables(constructorElement.getParameters());
      Optional<DependencyRequest> membersInjectionRequest = membersInjectionRequest(
          MoreElements.asType(constructorElement.getEnclosingElement()));
      return new AutoValue_ProvisionBinding(
          constructorElement,
          dependencies,
          findBindingPackage(key),
          Kind.INJECTION,
          Provides.Type.UNIQUE,
          key,
          getScopeAnnotation(constructorElement.getEnclosingElement()),
          membersInjectionRequest);
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private Optional<DependencyRequest> membersInjectionRequest(TypeElement type) {
      if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
          type.getSuperclass())) {
        return Optional.of(dependencyRequestFactory.forMembersInjectedType(type.asType()));
      }
      for (Element enclosedElement : type.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (isAnnotationPresent(enclosedElement, Inject.class))) {
          return Optional.of(dependencyRequestFactory.forMembersInjectedType(type.asType()));
        }
      }
      return Optional.absent();
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(METHOD));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      Key key = keyFactory.forProvidesMethod(providesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredVariables(providesMethod.getParameters());
      return new AutoValue_ProvisionBinding(
          providesMethod,
          dependencies,
          findBindingPackage(key),
          Kind.PROVISION,
          providesAnnotation.type(),
          key,
          getScopeAnnotation(providesMethod),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      Component componentAnnotation = componentDefinitionType.getAnnotation(Component.class);
      checkArgument(componentAnnotation != null);
      return new AutoValue_ProvisionBinding(
          componentDefinitionType,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          Kind.COMPONENT,
          Provides.Type.UNIQUE,
          keyFactory.forType(componentDefinitionType.asType()),
          Optional.<AnnotationMirror>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      return new AutoValue_ProvisionBinding(
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          Kind.COMPONENT_PROVISION,
          Provides.Type.UNIQUE,
          keyFactory.forComponentMethod(componentMethod),
          getScopeAnnotation(componentMethod),
          Optional.<DependencyRequest>absent());
    }
  }
}
