/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.BindingKey.Kind.CONTRIBUTION;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.MembersInjector;
import dagger.producers.Producer;
import javax.inject.Provider;

/** Fulfills requests for {@link ProductionBinding} instances. */
final class ProducerFieldRequestFulfillment extends RequestFulfillment {
  private final MemberSelect producerFieldSelect;

  ProducerFieldRequestFulfillment(BindingKey bindingKey, MemberSelect producerFieldSelect) {
    super(bindingKey);
    checkArgument(bindingKey.kind().equals(CONTRIBUTION));
    this.producerFieldSelect = producerFieldSelect;
  }

  @Override
  public CodeBlock getSnippetForDependencyRequest(
      DependencyRequest request, ClassName requestingClass) {
    switch (request.kind()) {
      case FUTURE:
        return CodeBlock.of("$L.get()", producerFieldSelect.getExpressionFor(requestingClass));
      case PRODUCER:
        return CodeBlock.of("$L", producerFieldSelect.getExpressionFor(requestingClass));
      case INSTANCE:
      case LAZY:
      case PRODUCED:
      case PROVIDER_OF_LAZY:
        throw new IllegalArgumentException(
            String.format(
                "The framework should never request a %s from a producer: %s",
                request.kind(), request));
      case MEMBERS_INJECTOR:
        throw new IllegalArgumentException(
            String.format(
                "Cannot request a %s from a %s",
                MembersInjector.class.getSimpleName(), Producer.class.getSimpleName()));
      case PROVIDER:
        throw new IllegalArgumentException(
            String.format(
                "Cannot request a %s from a %s",
                Provider.class.getSimpleName(), Producer.class.getSimpleName()));
      default:
        throw new AssertionError(request.kind().toString());
    }
  }
}
