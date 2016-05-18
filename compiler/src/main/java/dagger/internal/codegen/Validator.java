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

import com.google.common.collect.ImmutableSet;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

/** An object that produces a {@link ValidationReport} for an element. */
abstract class Validator<T extends Element> {
  /** Returns a {@link ValidationReport} for {@code element}. */
  abstract ValidationReport<T> validate(T element);

  /** Prints validation reports to {@code messager}, and returns valid elements. */
  final ImmutableSet<T> validate(Messager messager, Iterable<? extends T> elements) {
    ImmutableSet.Builder<T> validElements = ImmutableSet.builder();
    for (T element : elements) {
      ValidationReport<T> elementReport = validate(element);
      elementReport.printMessagesTo(messager);
      if (elementReport.isClean()) {
        validElements.add(element);
      }
    }
    return validElements.build();
  }
}
