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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.JavaWriter;
import java.io.IOException;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A template class that provides a framework for properly handling IO while generating source files
 * from an annotation processor.  Particularly, it makes a best effort to ensure that files that
 * fail to write successfully are deleted.
 *
 * @param <T> The input type from which source is to be generated.
 * @author Gregory Kick
 * @since 2.0
 */
abstract class SourceFileGenerator<T> {
  private final Filer filer;

  SourceFileGenerator(Filer filer) {
    this.filer = checkNotNull(filer);
  }

  final ClassName generate(T input) throws SourceFileGenerationException {
    ClassName generatedTypeName = nameGeneratedType(input);
    ImmutableSet<Element> originatingElements = ImmutableSet.copyOf(getOriginatingElements(input));
    try {
      JavaWriter javaWriter = write(generatedTypeName, input);
      try {
        javaWriter.file(filer, originatingElements);
      } catch (IOException e) {
        throw new SourceFileGenerationException(generatedTypeName, e,
            getElementForErrorReporting(input));
      }
      return generatedTypeName;
    } catch (Exception e) {
      // if the code above threw a SFGE, use that
      Throwables.propagateIfPossible(e, SourceFileGenerationException.class);
      // otherwise, throw a new one
      throw new SourceFileGenerationException(generatedTypeName, e,
          getElementForErrorReporting(input));
    }
  }

  /**
   * Implementations should return the {@link ClassName} for the top-level type to be generated.
   */
  abstract ClassName nameGeneratedType(T input);

  /**
   * Implementations should return {@link Element} instances from which the source is to be
   * generated.
   */
  abstract Iterable<? extends Element> getOriginatingElements(T input);

  /**
   * Returns an optional element to be used for reporting errors. This returns a single element
   * rather than a collection to reduce output noise.
   */
  abstract Optional<? extends Element> getElementForErrorReporting(T input);

  /**
   */
  abstract JavaWriter write(ClassName generatedTypeName, T input);
}
