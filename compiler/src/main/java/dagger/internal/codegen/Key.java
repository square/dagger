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
import com.google.common.base.Equivalence;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import dagger.MapKey;
import dagger.Provides;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ConfigurationAnnotations.getMapKeys;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.Util.unwrapOptionalEquivalence;
import static dagger.internal.codegen.Util.wrapOptionalInEquivalence;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.type.TypeKind.DECLARED;

/**
 * Represents a unique combination of {@linkplain TypeMirror type} and
 * {@linkplain Qualifier qualifier} to which binding can occur.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class Key {
  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   *
   * Despite documentation in {@link AnnotationMirror}, equals and hashCode aren't implemented
   * to represent logical equality, so {@link AnnotationMirrors#equivalence()}
   * provides this facility.
   */
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier();

  /**
   * The type represented by this key.
   *
   * As documented in {@link TypeMirror}, equals and hashCode aren't implemented to represent
   * logical equality, so {@link MoreTypes#equivalence()} wraps this type.
   */
  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

  Optional<AnnotationMirror> qualifier() {
    return unwrapOptionalEquivalence(wrappedQualifier());
  }

  TypeMirror type() {
    return wrappedType().get();
  }

  boolean isValidMembersInjectionKey() {
    return !qualifier().isPresent()
        && type().accept(new SimpleTypeVisitor6<Boolean, Void>(false) {
          @Override
          public Boolean visitDeclared(DeclaredType t, Void p) {
            return t.getTypeArguments().isEmpty();
          }
        }, null);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(Key.class)
        .omitNullValues()
        .add("qualifier", qualifier().orNull())
        .add("type", type())
        .toString();
  }

  static final class Factory {
    private final Types types;
    private final Elements elements;

    Factory(Types types, Elements elements) {
      this.types = checkNotNull(types);
      this.elements = checkNotNull(elements);
    }

    private TypeMirror normalize(TypeMirror type) {
      TypeKind kind = type.getKind();
      return kind.isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
    }

    private TypeElement getSetElement() {
      return elements.getTypeElement(Set.class.getCanonicalName());
    }

    private TypeElement getMapElement() {
      return elements.getTypeElement(Map.class.getCanonicalName());
    }

    private TypeElement getProviderElement() {
      return elements.getTypeElement(Provider.class.getCanonicalName());
    }

    Key forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      TypeMirror returnType = normalize(componentMethod.getReturnType());
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(componentMethod)),
          MoreTypes.equivalence().wrap(returnType));
    }

    Key forProvidesMethod(ExecutableElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(METHOD));
      Provides providesAnnotation = e.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      TypeMirror returnType = normalize(e.getReturnType());
      switch (providesAnnotation.type()) {
        case UNIQUE:
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(returnType));
        case SET:
          TypeMirror setType = types.getDeclaredType(getSetElement(), returnType);
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(setType));
        case MAP:
          AnnotationMirror mapKeyAnnotation = Iterables.getOnlyElement(getMapKeys(e));
          MapKey mapKey =
              mapKeyAnnotation.getAnnotationType().asElement().getAnnotation(MapKey.class);
          TypeElement keyTypeElement =
              mapKey.unwrapValue() ? Util.getKeyTypeElement(mapKeyAnnotation, elements)
                  : (TypeElement) mapKeyAnnotation.getAnnotationType().asElement();
          TypeMirror valueType = types.getDeclaredType(getProviderElement(), returnType);
          TypeMirror mapType =
              types.getDeclaredType(getMapElement(), keyTypeElement.asType(), valueType);
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(mapType));
        case SET_VALUES:
          // TODO(gak): do we want to allow people to use "covariant return" here?
          checkArgument(returnType.getKind().equals(DECLARED));
          checkArgument(((DeclaredType) returnType).asElement().equals(getSetElement()));
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(returnType));
        default:
          throw new AssertionError();
      }
    }

    Key forInjectConstructor(ExecutableElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(CONSTRUCTOR));
      checkArgument(!getQualifier(e).isPresent());
      // Must use the enclosing element.  The return type is void for constructors(?!)
      TypeMirror type = e.getEnclosingElement().asType();
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(type));
    }

    Key forComponent(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(normalize(type)));
    }

    Key forMembersInjectedType(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(normalize(type)));
    }

    Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), qualifier),
          MoreTypes.equivalence().wrap(normalize(type)));
    }

    /**
     * Optionally extract a {@link Key} for the underlying provision binding(s) if such a
     * valid key can be inferred from the given key.  Specifically, if the key represents a
     * {@link Map}{@code <K, V>}, a key of {@code Map<K, Provider<V>>} will be returned.
     */
    Optional<Key> implicitMapProviderKeyFrom(Key possibleMapKey) {
      if (MoreTypes.isTypeOf(Map.class, possibleMapKey.type())) {
        DeclaredType declaredMapType = MoreTypes.asDeclared(possibleMapKey.type());
        TypeMirror mapValueType = Util.getValueTypeOfMap(declaredMapType);
        if (!MoreTypes.isTypeOf(Provider.class, mapValueType)) {
          DeclaredType keyType = Util.getKeyTypeOfMap(declaredMapType);
          DeclaredType providerType = types.getDeclaredType(getProviderElement(), mapValueType);
          TypeMirror mapType = types.getDeclaredType(getMapElement(), keyType, providerType);
          return Optional.<Key>of(new AutoValue_Key(
              possibleMapKey.wrappedQualifier(),
              MoreTypes.equivalence().wrap(mapType)));
        }
      }
      return Optional.absent();
    }
  }
}
