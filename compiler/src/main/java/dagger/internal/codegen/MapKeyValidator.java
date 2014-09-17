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

import dagger.MapKey;
import javax.lang.model.element.Element;

import static dagger.internal.codegen.ErrorMessages.UNWRAP_MAPKEY;

/**
 * A {@link Validator} for {@link MapKey} Annotation.
 *
 * @author Chenying Hou
 * @since 2.0
 */
final class MapKeyValidator implements Validator<Element>  {
  @Override
  public ValidationReport<Element> validate(Element element) {
    ValidationReport.Builder<Element> builder =
        ValidationReport.Builder.about(element);
    MapKey mapkey = element.getAnnotation(MapKey.class);
    if (mapkey.unwrapValue()) {
      builder.addItem(UNWRAP_MAPKEY, element);
    }
    return builder.build();
  }

}
