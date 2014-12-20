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

import com.google.auto.value.AutoValue;
import dagger.MembersInjector;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.producers.Producer;
import javax.inject.Provider;

/**
 * A value object that represents a field for a binding in a generated source file.
 *
 *  @author Jesse Beder
 *  @since 2.0
 */
@AutoValue
abstract class BindingField {
  static BindingField create(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    String suffix = frameworkClass.getSimpleName();
    return new AutoValue_BindingField(frameworkClass, bindingKey,
        name.endsWith(suffix) ? name : name + suffix);
  }

  ParameterizedTypeName frameworkType() {
    return ParameterizedTypeName.create(
        ClassName.fromClass(frameworkClass()),
        TypeNames.forTypeMirror(bindingKey().key().type()));
  }

  abstract Class<?> frameworkClass();
  abstract BindingKey bindingKey();
  abstract String name();
}
