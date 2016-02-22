/*
 * Copyright (C) 2016 Google, Inc.
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
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static com.google.common.base.Preconditions.checkArgument;

/** A representation of an annotation with no fields. */
final class SimpleAnnotationMirror implements AnnotationMirror {
  private final DeclaredType type;

  private SimpleAnnotationMirror(DeclaredType type) {
    this.type = type;
  }

  @Override
  public DeclaredType getAnnotationType() {
    return type;
  }

  @Override
  public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
    return ImmutableMap.of();
  }

  @Override
  public String toString() {
    return "@" + type;
  }

  static AnnotationMirror of(TypeElement element) {
    checkArgument(element.getKind().equals(ElementKind.ANNOTATION_TYPE));
    checkArgument(element.getEnclosedElements().isEmpty());
    return new SimpleAnnotationMirror(MoreTypes.asDeclared(element.asType()));
  }
}
