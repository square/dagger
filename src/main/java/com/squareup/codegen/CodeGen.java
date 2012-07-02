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
package com.squareup.codegen;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Support for annotation processors.
 *
 * @author Jesse Wilson
 */
final class CodeGen {
  private CodeGen() {
  }

  public static PackageElement getPackage(Element type) {
    while (type.getKind() != ElementKind.PACKAGE) {
      type = type.getEnclosingElement();
    }
    return (PackageElement) type;
  }

  /**
   * Returns a fully qualified class name to complement {@code type}. The
   * returned class is in the same package as {@code type}. This supports nested
   * classes by using a '$' instead of '.' for nesting:  "java.util.Map.Entry"
   * becomes "java.util.Map$Entry".
   */
  public static String adapterName(TypeElement typeName, String suffix) {
    String packageName = CodeGen.getPackage(typeName).getQualifiedName().toString();
    String qualifiedName = typeName.getQualifiedName().toString();
    return packageName + '.'
        + qualifiedName.substring(packageName.length() + 1).replace('.', '$')
        + suffix;
  }

  /**
   * Returns a string like {@code java.util.List<java.lang.String>}.
   */
  public static String parameterizedType(Class<?> raw, String... parameters) {
    StringBuilder result = new StringBuilder();
    result.append(raw.getName());
    result.append("<");
    for (int i = 0; i < parameters.length; i++) {
      if (i != 0) {
        result.append(", ");
      }
      result.append(parameters[i]);
    }
    result.append(">");
    return result.toString();
  }
}
