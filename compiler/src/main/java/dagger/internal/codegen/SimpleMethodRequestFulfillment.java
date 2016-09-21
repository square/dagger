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

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import javax.lang.model.element.ExecutableElement;

/**
 * A request fulfillment implementation that invokes methods or constructors directly to fulfill
 * requests whenever possible. In cases where direct invocation is not possible, this implementation
 * delegates to one that uses a {@link javax.inject.Provider}.
 */
final class SimpleMethodRequestFulfillment extends RequestFulfillment {
  private final ProvisionBinding provisionBinding;
  private final RequestFulfillment providerDelegate;

  SimpleMethodRequestFulfillment(
      BindingKey bindingKey,
      ProvisionBinding provisionBinding,
      RequestFulfillment providerDelegate) {
    super(bindingKey);
    checkArgument(provisionBinding.implicitDependencies().isEmpty());
    checkArgument(!provisionBinding.scope().isPresent());
    checkArgument(!provisionBinding.requiresModuleInstance());
    checkArgument(provisionBinding.bindingElement().isPresent());
    this.provisionBinding = provisionBinding;
    this.providerDelegate = providerDelegate;
  }

  @Override
  CodeBlock getSnippetForDependencyRequest(DependencyRequest request, ClassName requestingClass) {
    switch (request.kind()) {
      case INSTANCE:
        return invokeMethod();
      case FUTURE:
        return CodeBlock.of("$T.immediateFuture($L)", Futures.class, invokeMethod());
      default:
        return providerDelegate.getSnippetForDependencyRequest(request, requestingClass);
    }
  }

  private CodeBlock invokeMethod() {
    // we use the type from the key to ensure we get the right generics
    // TODO(gak): use <>?
    ExecutableElement method = asExecutable(provisionBinding.bindingElement().get());
    switch (method.getKind()) {
      case CONSTRUCTOR:
        return CodeBlock.of("new $T()", provisionBinding.key().type());
      case METHOD:
        checkState(method.getModifiers().contains(STATIC));
        return CodeBlock.of(
            "$T.$L()", provisionBinding.bindingTypeElement().get(), method.getSimpleName());
      default:
        throw new IllegalStateException();
    }
  }
}
