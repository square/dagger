/*
 * Copyright (c) 2013 Google, Inc.
 * Copyright (c) 2013 Square, Inc.
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
package dagger.tests.integration.dagger1;

import dagger.internal.codegen.dagger1.GraphAnalysisProcessor;
import dagger.internal.codegen.dagger1.InjectAdapterProcessor;
import dagger.internal.codegen.dagger1.ModuleAdapterProcessor;
import dagger.internal.codegen.dagger1.ValidationProcessor;
import java.util.Arrays;
import javax.annotation.processing.Processor;

/**
 * Internal test utilities.
 */
public class ProcessorTestUtils {
  public static Iterable<? extends Processor> daggerProcessors() {
    return Arrays.asList(
        new InjectAdapterProcessor(),
        new ModuleAdapterProcessor(),
        new GraphAnalysisProcessor(),
        new ValidationProcessor());
  }
}
