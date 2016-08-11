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
import static dagger.internal.codegen.TypeNames.PROVIDER_OF_LAZY;

import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.MembersInjector;
import dagger.internal.DoubleCheck;
import dagger.producers.Produced;
import dagger.producers.internal.Producers;
import javax.inject.Provider;

/** Fulfills requests for {@link ProvisionBinding} instances. */
final class ProviderFieldRequestFulfillment extends RequestFulfillment {
  private final MemberSelect providerFieldSelect;

  ProviderFieldRequestFulfillment(BindingKey bindingKey, MemberSelect frameworkFieldSelect) {
    super(bindingKey);
    checkArgument(bindingKey.kind().equals(CONTRIBUTION));
    this.providerFieldSelect = frameworkFieldSelect;
  }

  @Override
  public CodeBlock getSnippetForDependencyRequest(
      DependencyRequest request, ClassName requestingClass) {
    switch (request.kind()) {
      case FUTURE:
        return CodeBlock.of(
            "$T.immediateFuture($L.get())",
            Futures.class,
            providerFieldSelect.getExpressionFor(requestingClass));
      case INSTANCE:
        return CodeBlock.of("$L.get()", providerFieldSelect.getExpressionFor(requestingClass));
      case LAZY:
        return CodeBlock.of(
            "$T.lazy($L)",
            DoubleCheck.class,
            providerFieldSelect.getExpressionFor(requestingClass));
      case MEMBERS_INJECTOR:
        throw new IllegalArgumentException(
            String.format(
                "Cannot request a %s from a %s",
                MembersInjector.class.getSimpleName(), Provider.class.getSimpleName()));
      case PRODUCED:
        return CodeBlock.of(
            "$T.successful($L.get())",
            Produced.class,
            providerFieldSelect.getExpressionFor(requestingClass));
      case PRODUCER:
        return CodeBlock.of(
            "$T.producerFromProvider($L)",
            Producers.class,
            providerFieldSelect.getExpressionFor(requestingClass));
      case PROVIDER:
        return CodeBlock.of("$L", providerFieldSelect.getExpressionFor(requestingClass));
      case PROVIDER_OF_LAZY:
        return CodeBlock.of(
            "$T.create($L)",
            PROVIDER_OF_LAZY,
            providerFieldSelect.getExpressionFor(requestingClass));
      default:
        throw new AssertionError();
    }
  }
}
