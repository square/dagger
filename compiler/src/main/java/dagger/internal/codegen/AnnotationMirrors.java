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
import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A utility class for working with {@link AnnotationMirror} instances.
 *
 * @author Gregory Kick
 */
final class AnnotationMirrors {
  /**
   * Takes a {@link Map} like that returned from {@link Elements#getElementValuesWithDefaults} and
   * key it by the member name rather than the {@link ExecutableElement}.
   */
  static ImmutableMap<String, AnnotationValue> simplifyAnnotationValueMap(
      Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap) {
    ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
    for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
        : annotationValueMap.entrySet()) {
      builder.put(entry.getKey().getSimpleName().toString(), entry.getValue());
    }
    return builder.build();
  }

  static ImmutableList<TypeMirror> getAttributeAsListOfTypes(Elements elements,
      AnnotationMirror annotationMirror, String attributeName) {
    checkNotNull(annotationMirror);
    checkNotNull(attributeName);
    ImmutableMap<String, AnnotationValue> valueMap =
        simplifyAnnotationValueMap(elements.getElementValuesWithDefaults(annotationMirror));
    ImmutableList.Builder<TypeMirror> builder = ImmutableList.builder();

    @SuppressWarnings("unchecked") // that's the whole point of this method
    List<? extends AnnotationValue> typeValues =
        (List<? extends AnnotationValue>) valueMap.get(attributeName).getValue();
    for (AnnotationValue typeValue : typeValues) {
      builder.add((TypeMirror) typeValue.getValue());
    }
    return builder.build();
  }

  private static final Equivalence<AnnotationMirror> ANNOTATION_MIRROR_EQUIVALENCE =
    new Equivalence<AnnotationMirror>() {
      @Override protected boolean doEquivalent(AnnotationMirror left, AnnotationMirror right) {
        return MoreTypes.equivalence()
            .equivalent(left.getAnnotationType(), right.getAnnotationType())
            && AnnotationValues.equivalence().pairwise().equivalent(
                getAnnotationValuesWithDefaults(left),
                getAnnotationValuesWithDefaults(right));
      }

      @Override protected int doHash(AnnotationMirror annotation) {
        DeclaredType type = annotation.getAnnotationType();
        Iterable<AnnotationValue> annotationValues = getAnnotationValuesWithDefaults(annotation);
        return Objects.hashCode(type,
            AnnotationValues.equivalence().pairwise().hash(annotationValues));
      }
    };

  /**
   * Returns an {@link Equivalence} for {@link AnnotationMirror} as some implementations
   * delegate equality tests to {@link Object#equals} whereas the documentation explicitly
   * states that instance/reference equality is not the proper test.
   *
   * Note: The contract of this equivalence is not quite that described in the javadoc, as
   * hashcode values returned by {@link Equivalence#hash(T)} are not the same as would
   * be returned from {@link AnnotationMirror#hashCode()}, though the proper invariants
   * relating hashCode() and equals() hold for {@code hash(T)} and {@code equivalent(T, T)}.
   */
  static Equivalence<AnnotationMirror> equivalence() {
    return ANNOTATION_MIRROR_EQUIVALENCE;
  }

  /**
   * Returns the {@link AnnotationMirror}'s map of {@link AnnotationValue} indexed by
   * {@link ExecutableElement}, supplying default values from the annotation if the
   * annotation property has not been set.  This is equivalent to
   * {@link Elements#getElementValuesWithDefaults(AnnotationMirror)} but can be called
   * statically without an {@Elements} instance.
   */
  static Iterable<AnnotationValue> getAnnotationValuesWithDefaults(
      AnnotationMirror annotation) {
    Map<ExecutableElement, AnnotationValue> values = Maps.newHashMap();
    for (ExecutableElement method :
        ElementFilter.methodsIn(annotation.getAnnotationType().asElement().getEnclosedElements())) {
      values.put(method, method.getDefaultValue());
    }
    values.putAll(annotation.getElementValues());
    return values.values();
  }

  private AnnotationMirrors() {}
}
