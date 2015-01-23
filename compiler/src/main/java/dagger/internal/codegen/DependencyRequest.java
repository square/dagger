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

import com.google.auto.common.MoreTypes;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Represents a request for a key at an injection point. Parameters to {@link Inject} constructors
 * or {@link Provides} methods are examples of key requests.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): Set bindings and the permutations thereof need to be addressed
@AutoValue
abstract class DependencyRequest {
  enum Kind {
    /** A default request for an instance.  E.g.: {@code Blah} */
    INSTANCE,
    /** A request for a {@link Provider}.  E.g.: {@code Provider<Blah>} */
    PROVIDER,
    /** A request for a {@link Lazy}.  E.g.: {@code Lazy<Blah>} */
    LAZY,
    /** A request for a {@link MembersInjector}.  E.g.: {@code MembersInjector<Blah>} */
    MEMBERS_INJECTOR,
    /** A request for a {@link Producer}.  E.g.: {@code Producer<Blah>} */
    PRODUCER,
    /** A request for a {@link Produced}.  E.g.: {@code Produced<Blah>} */
    PRODUCED,
  }

  abstract Kind kind();
  abstract Key key();
  abstract Element requestElement();
  
  /**
   * Returns the possibly resolved type that contained the requesting element. For members injection
   * requests, this is the type itself.
   */
  abstract DeclaredType enclosingType();

  static final class Factory {
    private final Key.Factory keyFactory;

    Factory(Key.Factory keyFactory) {
      this.keyFactory = keyFactory;
    }

    ImmutableSet<DependencyRequest> forRequiredResolvedVariables(DeclaredType container,
        List<? extends VariableElement> variables, List<? extends TypeMirror> resolvedTypes) {
      checkState(resolvedTypes.size() == variables.size());
      ImmutableSet.Builder<DependencyRequest> builder = ImmutableSet.builder();
      for (int i = 0; i < variables.size(); i++) {
        builder.add(forRequiredResolvedVariable(container, variables.get(i), resolvedTypes.get(i)));
      }
      return builder.build();
    }

    ImmutableSet<DependencyRequest> forRequiredVariables(
        List<? extends VariableElement> variables) {
      return FluentIterable.from(variables)
          .transform(new Function<VariableElement, DependencyRequest>() {
            @Override public DependencyRequest apply(VariableElement input) {
              return forRequiredVariable(input);
            }
          })
          .toSet();
    }

    /**
     * Creates a DependencyRequest for implictMapBinding, this request's key will be
     * {@code Map<K, Provider<V>>}, this DependencyRequest is depended by the DependencyRequest
     * whose key is {@code Map<K, V>}
     */
    DependencyRequest forImplicitMapBinding(DependencyRequest delegatingRequest, Key delegateKey) {
      checkNotNull(delegatingRequest);
      return new AutoValue_DependencyRequest(Kind.PROVIDER, delegateKey,
          delegatingRequest.requestElement(), 
          MoreTypes.asDeclared(delegatingRequest.requestElement().getEnclosingElement().asType()));
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement) {
      checkNotNull(variableElement);
      TypeMirror type = variableElement.asType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, type, qualifier, MoreTypes.asDeclared(
          variableElement.getEnclosingElement().getEnclosingElement().asType()));
    }

    DependencyRequest forRequiredResolvedVariable(DeclaredType container,
        VariableElement variableElement,
        TypeMirror resolvedType) {
      checkNotNull(variableElement);
      checkNotNull(resolvedType);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, resolvedType, qualifier, container);
    }

    DependencyRequest forComponentProvisionMethod(ExecutableElement provisionMethod) {
      checkNotNull(provisionMethod);
      checkArgument(provisionMethod.getParameters().isEmpty(),
          "Component provision methods must be empty: " + provisionMethod);
      TypeMirror type = provisionMethod.getReturnType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
      return newDependencyRequest(provisionMethod, type, qualifier,
          MoreTypes.asDeclared(provisionMethod.getEnclosingElement().asType()));
    }

    DependencyRequest forComponentMembersInjectionMethod(ExecutableElement membersInjectionMethod) {
      checkNotNull(membersInjectionMethod);
      Optional<AnnotationMirror> qualifier =
          InjectionAnnotations.getQualifier(membersInjectionMethod);
      checkArgument(!qualifier.isPresent());
      return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
          keyFactory.forMembersInjectedType(
              Iterables.getOnlyElement(membersInjectionMethod.getParameters()).asType()),
          membersInjectionMethod,
          MoreTypes.asDeclared(membersInjectionMethod.getEnclosingElement().asType()));
    }

    DependencyRequest forMembersInjectedType(DeclaredType type) {
      return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
          keyFactory.forMembersInjectedType(type),
          type.asElement(),
          type);
    }

    private DependencyRequest newDependencyRequest(Element requestElement,
        TypeMirror type, Optional<AnnotationMirror> qualifier, DeclaredType container) {
      KindAndType kindAndType = extractKindAndType(type);
      if (kindAndType.kind() == Kind.MEMBERS_INJECTOR) {
        checkArgument(!qualifier.isPresent());
      }
      return new AutoValue_DependencyRequest(kindAndType.kind(),
            keyFactory.forQualifiedType(qualifier, kindAndType.type()),
            requestElement,
            container);
    }
    
    @AutoValue
    static abstract class KindAndType {
      abstract Kind kind();
      abstract TypeMirror type();
    }
    
    /**
     * Extracts the correct requesting type & kind out a request type. For example, if a user
     * requests Provider<Foo>, this will return Kind.PROVIDER with "Foo".
     */
    static KindAndType extractKindAndType(TypeMirror type) {
      // We must check TYPEVAR explicitly before the below checks because calling
      // isTypeOf(..) on a TYPEVAR throws an exception (because it can't be
      // represented as a Class).
      if (type.getKind().equals(TypeKind.TYPEVAR)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.INSTANCE, type);
      } else if (isTypeOf(Provider.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.PROVIDER,
            Iterables.getOnlyElement(((DeclaredType)type).getTypeArguments()));
      } else if (isTypeOf(Lazy.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.LAZY,
            Iterables.getOnlyElement(((DeclaredType)type).getTypeArguments()));
      } else if (isTypeOf(MembersInjector.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.MEMBERS_INJECTOR,
            Iterables.getOnlyElement(((DeclaredType)type).getTypeArguments()));
      } else if (isTypeOf(Producer.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.PRODUCER,
            Iterables.getOnlyElement(((DeclaredType)type).getTypeArguments()));
      } else if (isTypeOf(Produced.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.PRODUCED,
            Iterables.getOnlyElement(((DeclaredType)type).getTypeArguments()));
      } else {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.INSTANCE, type);
      }
    }
  }
}
