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

import com.google.common.collect.ImmutableListMultimap;
import dagger.Module;
import dagger.Provides;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_WITH_SAME_NAME;

/**
 * A {@link Validator} for {@link Module}s.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ModuleValidator implements Validator<TypeElement> {
  @Override
  public ValidationReport<TypeElement> validate(TypeElement subject) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.Builder.about(subject);
    List<ExecutableElement> moduleMethods = ElementFilter.methodsIn(subject.getEnclosedElements());
    ImmutableListMultimap.Builder<String, ExecutableElement> providesMethodsByName =
        ImmutableListMultimap.builder();
    for (ExecutableElement moduleMethod : moduleMethods) {
      if (isAnnotationPresent(moduleMethod, Provides.class)) {
        providesMethodsByName.put(moduleMethod.getSimpleName().toString(), moduleMethod);
      }
    }
    for (Entry<String, Collection<ExecutableElement>> entry :
        providesMethodsByName.build().asMap().entrySet()) {
      if (entry.getValue().size() > 1) {
        for (ExecutableElement offendingMethod : entry.getValue()) {
          builder.addItem(PROVIDES_METHOD_WITH_SAME_NAME, offendingMethod);
        }
      }
    }
    // TODO(gak): port the dagger 1 module validation
    return builder.build();
  }
}
