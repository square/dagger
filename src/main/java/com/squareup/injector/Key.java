/**
 * Copyright (C) 2012 Square, Inc.
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
package com.squareup.injector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.inject.Qualifier;

/**
 * Identifies the value to be injected.
 *
 * @author Jesse Wilson
 */
final class Key<T> {
  private static final LruCache<Class<? extends Annotation>, Boolean> IS_QUALIFIER_ANNOTATION
      = new LruCache<Class<? extends Annotation>, Boolean>(Integer.MAX_VALUE) {
    @Override protected Boolean create(Class<? extends Annotation> annotationType) {
      return annotationType.isAnnotationPresent(Qualifier.class);
    }
  };

  final Type type;
  final Annotation annotation;

  Key(Type type, Annotation annotation) {
    this.type = type;
    this.annotation = annotation;
  }

  static <T> Key<T> get(Type type, Annotation[] annotations, Object subject) {
    Annotation bindingAnnotation = null;
    for (Annotation a : annotations) {
      if (!IS_QUALIFIER_ANNOTATION.get(a.annotationType())) {
        continue;
      }
      if (bindingAnnotation != null) {
        throw new IllegalArgumentException("Too many binding annotations on " + subject);
      }
      bindingAnnotation = a;
    }
    return new Key<T>(type, bindingAnnotation);
  }

  private static boolean equal(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }

  @Override public boolean equals(Object o) {
    return o instanceof Key
        && ((Key) o).type.equals(type)
        && equal(annotation, ((Key) o).annotation);
  }

  @Override public int hashCode() {
    int result = type.hashCode();
    if (annotation != null) {
      result += (37 * annotation.hashCode());
    }
    return result;
  }

  @Override public String toString() {
    return "key[type=" + type + ",annotation=" + annotation + "]";
  }
}
