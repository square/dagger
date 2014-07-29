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
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * The annotation processor responsible for validating the mapKey annotation
 *
 *
 * TODO(user): auto-generate implementation of annotations marked with &#064MapKey where necessary.
 *
 * @author Chenying Hou
 * @since 2.0
 */
public class MapKeyProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final MapKeyValidator mapKeyValidator;

  MapKeyProcessingStep(Messager messager, MapKeyValidator mapKeyValidator) {
    this.messager = messager;
    this.mapKeyValidator = mapKeyValidator;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // check the map key annotation field is valid
    if (!roundEnv.getElementsAnnotatedWith(MapKey.class).isEmpty()) {
      Set<? extends Element> mapKeyAnnotateds = roundEnv.getElementsAnnotatedWith(MapKey.class);
      for (Element element : mapKeyAnnotateds) {
        ValidationReport<Element> mapKeyReport = mapKeyValidator.validate(element);
        mapKeyReport.printMessagesTo(messager);
      }
    }
    return false;
  }
}
