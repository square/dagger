/*
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
package dagger.internal.codegen;

import dagger.internal.Linker;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * A {@code Linker.ErrorHandler} which gathers errors and reports them via a processing
 * environment.
 */
final class GraphAnalysisErrorHandler implements Linker.ErrorHandler {
  private final ProcessingEnvironment processingEnv;
  private final String moduleName;

  GraphAnalysisErrorHandler(ProcessingEnvironment processingEnv, String moduleName) {
    this.processingEnv = processingEnv;
    this.moduleName = moduleName;
  }

  @Override public void handleErrors(List<String> errors) {
    TypeElement module = processingEnv.getElementUtils().getTypeElement(moduleName);
    for (String error : errors) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error + " for " + moduleName,
          module);
    }
  }
}
