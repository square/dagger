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

import static com.google.auto.common.MoreTypes.isTypeOf;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;

    Factory(Types types, Key.Factory keyFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
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
          delegatingRequest.requestElement());
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement) {
      checkNotNull(variableElement);
      TypeMirror type = variableElement.asType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, type, qualifier);
    }

    DependencyRequest forComponentProvisionMethod(ExecutableElement provisionMethod) {
      checkNotNull(provisionMethod);
      checkArgument(provisionMethod.getParameters().isEmpty(),
          "Component provision methods must be empty: " + provisionMethod);
      TypeMirror type = provisionMethod.getReturnType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
      return newDependencyRequest(provisionMethod, type, qualifier);
    }

    DependencyRequest forComponentMembersInjectionMethod(ExecutableElement membersInjectionMethod) {
      checkNotNull(membersInjectionMethod);
      Optional<AnnotationMirror> qualifier =
          InjectionAnnotations.getQualifier(membersInjectionMethod);
      checkArgument(!qualifier.isPresent());
      return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
          keyFactory.forMembersInjectedType(
              Iterables.getOnlyElement(membersInjectionMethod.getParameters()).asType()),
          membersInjectionMethod);
    }

    DependencyRequest forMembersInjectedType(TypeElement type) {
      return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
          keyFactory.forMembersInjectedType(type.asType()),
          type);
    }

    private DependencyRequest newDependencyRequest(Element requestElement, TypeMirror type,
        Optional<AnnotationMirror> qualifier) {
      if (isTypeOf(Provider.class, type)) {
        return new AutoValue_DependencyRequest(Kind.PROVIDER,
            qualifiedTypeForParameter(qualifier, (DeclaredType) type),
            requestElement);
      } else if (isTypeOf(Lazy.class, type)) {
        return new AutoValue_DependencyRequest(Kind.LAZY,
            qualifiedTypeForParameter(qualifier, (DeclaredType) type),
            requestElement);
      } else if (isTypeOf(MembersInjector.class, type)) {
        checkArgument(!qualifier.isPresent());
        return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
            qualifiedTypeForParameter(qualifier, (DeclaredType) type),
            requestElement);
      } else if (isTypeOf(Producer.class, type)) {
        return new AutoValue_DependencyRequest(Kind.PRODUCER,
            qualifiedTypeForParameter(qualifier, (DeclaredType) type),
            requestElement);
      } else if (isTypeOf(Produced.class, type)) {
        return new AutoValue_DependencyRequest(Kind.PRODUCED,
            qualifiedTypeForParameter(qualifier, (DeclaredType) type),
            requestElement);
      } else {
        return new AutoValue_DependencyRequest(Kind.INSTANCE,
            keyFactory.forQualifiedType(qualifier, type),
            requestElement);
      }
    }

    private Key qualifiedTypeForParameter(
        Optional<AnnotationMirror> qualifier, DeclaredType type) {
      return keyFactory.forQualifiedType(qualifier,
          Iterables.getOnlyElement(type.getTypeArguments()));
    }
  }
}
