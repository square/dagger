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
import static dagger.internal.codegen.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * A request fulfillment implementation that invokes methods or constructors directly to fulfill
 * requests whenever possible. In cases where direct invocation is not possible, this implementation
 * delegates to one that uses a {@link javax.inject.Provider}.
 */
final class SimpleMethodRequestFulfillment extends RequestFulfillment {

  private final ProvisionBinding provisionBinding;
  private final RequestFulfillment providerDelegate;
  private final RequestFulfillmentRegistry registry;

  SimpleMethodRequestFulfillment(
      BindingKey bindingKey,
      ProvisionBinding provisionBinding,
      RequestFulfillment providerDelegate,
      RequestFulfillmentRegistry registry) {
    super(bindingKey);
    checkArgument(
        provisionBinding.implicitDependencies().isEmpty(),
        "framework deps are not currently supported");
    checkArgument(!provisionBinding.scope().isPresent());
    checkArgument(!provisionBinding.requiresModuleInstance());
    checkArgument(provisionBinding.bindingElement().isPresent());
    this.provisionBinding = provisionBinding;
    this.providerDelegate = providerDelegate;
    this.registry = registry;
  }

  @Override
  CodeBlock getSnippetForDependencyRequest(DependencyRequest request, ClassName requestingClass) {
    String requestingPackage = requestingClass.packageName();
    /* This is where we do some checking to make sure we honor and/or dodge accessibility
     * restrictions:
     *
     * 1. Check to make sure that the method/constructor that we're trying to invoke is accessible.
     * 2. Check that the *raw type* of each parameter is accessible.  If something is only
     *    inaccessible due to a type variable, we do a raw type cast just like we do for framework
     *    types.
     */
    // TODO(gak): the accessibility limitation here needs to be addressed
    if (!isElementAccessibleFrom(provisionBinding.bindingElement().get(), requestingPackage)
        || provisionBinding
            .dependencies()
            .stream()
            .anyMatch(
                dependencyRequest ->
                    !isRawTypeAccessible(dependencyRequest.key().type(), requestingPackage))) {
      return providerDelegate.getSnippetForDependencyRequest(request, requestingClass);
    }
    switch (request.kind()) {
      case INSTANCE:
        return invokeMethod(requestingClass);
      case FUTURE:
        return CodeBlock.of("$T.immediateFuture($L)", Futures.class, invokeMethod(requestingClass));
      default:
        return providerDelegate.getSnippetForDependencyRequest(request, requestingClass);
    }
  }

  public static final SimpleTypeVisitor8<Boolean, String> RAW_TYPE_ACCESSIBILITY_VISITOR =
      new SimpleTypeVisitor8<Boolean, String>() {
        @Override
        protected Boolean defaultAction(TypeMirror e, String requestingPackage) {
          return isTypeAccessibleFrom(e, requestingPackage);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, String requestingPackage) {
          return isElementAccessibleFrom(t.asElement(), requestingPackage);
        }
      };

  private static boolean isRawTypeAccessible(TypeMirror type, String requestingPackage) {
    return type.accept(RAW_TYPE_ACCESSIBILITY_VISITOR, requestingPackage);
  }

  private CodeBlock invokeMethod(ClassName requestingClass) {
    CodeBlock parametersCodeBlock =
        CodeBlocks.makeParametersCodeBlock(
            provisionBinding
                .explicitDependencies()
                .stream()
                .map(
                    request -> {
                      CodeBlock snippet =
                          registry
                              .getRequestFulfillment(request.bindingKey())
                              .getSnippetForDependencyRequest(request, requestingClass);
                      return isTypeAccessibleFrom(
                              request.key().type(), requestingClass.packageName())
                          ? snippet
                          : CodeBlock.of(
                              "($T) $L", rawTypeName(TypeName.get(request.key().type())), snippet);
                    })
                .collect(toList()));
    // we use the type from the key to ensure we get the right generics
    // TODO(gak): use <>?
    ExecutableElement method = asExecutable(provisionBinding.bindingElement().get());
    switch (method.getKind()) {
      case CONSTRUCTOR:
        return CodeBlock.of("new $T($L)", provisionBinding.key().type(), parametersCodeBlock);
      case METHOD:
        checkState(method.getModifiers().contains(STATIC));
        return CodeBlock.of(
            "$T.$L($L)",
            provisionBinding.bindingTypeElement().get(),
            method.getSimpleName(),
            parametersCodeBlock);
      default:
        throw new IllegalStateException();
    }
  }

  private static TypeName rawTypeName(TypeName typeName) {
    return (typeName instanceof ParameterizedTypeName)
        ? ((ParameterizedTypeName) typeName).rawType
        : typeName;
  }
}
