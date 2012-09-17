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

import dagger.internal.Binding;
import dagger.internal.Linker;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * A build time binding that injects the constructor and fields of a class.
 */
final class AtInjectBinding extends Binding<Object> {
  private final TypeElement type;
  private final List<String> keys;

  private AtInjectBinding(String provideKey, String membersKey,
      TypeElement type, List<String> keys) {
    super(provideKey, membersKey, type.getAnnotation(Singleton.class) != null,
        type.getQualifiedName().toString());
    this.type = type;
    this.keys = keys;
  }

  /**
   * @param forMembersInjection true if the binding is being created to inject
   *     members only. Such injections do not require {@code @Inject}
   *     annotations.
   */
  static AtInjectBinding create(TypeElement type, boolean forMembersInjection) {
    List<String> requiredKeys = new ArrayList<String>();
    boolean hasInjectAnnotatedConstructor = false;
    boolean isConstructable = false;

    for (Element enclosed : type.getEnclosedElements()) {
      switch (enclosed.getKind()) {
      case FIELD:
        if (hasAtInject(enclosed) && !enclosed.getModifiers().contains(Modifier.STATIC)) {
          // Attach the non-static fields of 'type'.
          requiredKeys.add(GeneratorKeys.get((VariableElement) enclosed));
        }
        break;

      case CONSTRUCTOR:
        ExecutableElement constructor = (ExecutableElement) enclosed;
        List<? extends VariableElement> parameters = constructor.getParameters();
        if (hasAtInject(enclosed)) {
          if (hasInjectAnnotatedConstructor) {
            throw new IllegalArgumentException("Too many injectable constructors on "
                + type.getQualifiedName().toString());
          }
          hasInjectAnnotatedConstructor = true;
          isConstructable = true;
          for (VariableElement parameter : parameters) {
            requiredKeys.add(GeneratorKeys.get(parameter));
          }
        } else if (parameters.isEmpty()) {
          isConstructable = true;
        }
        break;

      default:
        if (hasAtInject(enclosed)) {
          throw new IllegalArgumentException("Unexpected @Inject annotation on " + enclosed);
        }
      }
    }

    if (!hasInjectAnnotatedConstructor && requiredKeys.isEmpty() && !forMembersInjection) {
      throw new IllegalArgumentException("No injectable members on "
          + type.getQualifiedName().toString() + ". Do you want to add an injectable constructor?");
    }

    // Attach the supertype.
    TypeMirror supertype = CodeGen.getApplicationSupertype(type);
    if (supertype != null) {
      requiredKeys.add(GeneratorKeys.rawMembersKey(supertype));
    }

    String provideKey = isConstructable ? GeneratorKeys.get(type.asType()) : null;
    String membersKey = GeneratorKeys.rawMembersKey(type.asType());
    return new AtInjectBinding(provideKey, membersKey, type, requiredKeys);
  }

  private static boolean hasAtInject(Element enclosed) {
    return enclosed.getAnnotation(Inject.class) != null;
  }

  @Override public void attach(Linker linker) {
    String requiredBy = type.getQualifiedName().toString();
    for (String key : keys) {
      linker.requestBinding(key, requiredBy);
    }
  }
}
