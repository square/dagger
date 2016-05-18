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
import dagger.Multibindings;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.METHOD_MUST_RETURN_MAP_OR_SET;
import static dagger.internal.codegen.FrameworkTypes.isFrameworkType;

/** A {@link Validator} for methods in {@link Multibindings @Multibindings} interfaces. */
final class MultibindingsMethodValidator extends BindingMethodValidator {

  MultibindingsMethodValidator(Elements elements, Types types) {
    super(
        elements,
        types,
        Multibindings.class,
        Multibindings.class,
        MUST_BE_ABSTRACT,
        ExceptionSuperclass.NONE);
  }

  @Override
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    // TODO(dpb): Why not do the rest of the checks?
    checkReturnType(builder);
    checkQualifiers(builder);
  }

  @Override
  protected void checkReturnType(ValidationReport.Builder<ExecutableElement> builder) {
    if (!isPlainMap(builder.getSubject().getReturnType())
        && !isPlainSet(builder.getSubject().getReturnType())) {
      builder.addError(METHOD_MUST_RETURN_MAP_OR_SET);
    }
  }

  private boolean isPlainMap(TypeMirror returnType) {
    if (!MapType.isMap(returnType)) {
      return false;
    }
    MapType mapType = MapType.from(returnType);
    return !mapType.isRawType()
        && MoreTypes.isType(mapType.valueType()) // No wildcards.
        && !isFrameworkType(mapType.valueType());
  }

  private boolean isPlainSet(TypeMirror returnType) {
    if (!SetType.isSet(returnType)) {
      return false;
    }
    SetType setType = SetType.from(returnType);
    return !setType.isRawType()
        && MoreTypes.isType(setType.elementType()) // No wildcards.
        && !isFrameworkType(setType.elementType());
  }
}
