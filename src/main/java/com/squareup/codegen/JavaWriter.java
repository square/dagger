/**
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

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emits Java source files.
 *
 * @author Jesse Wilson
 */
public final class JavaWriter {
  private static final Pattern TYPE_PATTERN = Pattern.compile("[\\w.$]+\\.([A-Z][\\w.$]+)");
  private static final String INDENT = "  ";

  /** Map fully qualified type names to their short names. */
  private final Map<String, String> importedTypes = new HashMap<String, String>();

  private String packagePrefix;
  private final List<Scope> scopes = new ArrayList<Scope>();
  private final Writer out;

  /**
   * @param out the stream to which Java source will be written. This should be
   *     a buffered stream.
   */
  public JavaWriter(Writer out) {
    this.out = out;
  }

  /**
   * Emit a package declaration.
   */
  public void addPackage(String packageName) throws IOException {
    if (this.packagePrefix != null) {
      throw new IllegalStateException();
    }
    out.write("package ");
    out.write(packageName);
    out.write(";\n");
    this.packagePrefix = packageName + ".";
  }

  /**
   * Emit an import for the named class. For the duration of the file, all
   * references to this class will be automatically shortened.
   */
  public void addImport(String type) throws IOException {
    Matcher matcher = TYPE_PATTERN.matcher(type);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(type);
    }
    if (importedTypes.put(type, matcher.group(1)) != null) {
      throw new IllegalArgumentException(type);
    }
    out.write("import ");
    out.write(type);
    out.write(";\n");
  }

  /**
   * Emits a type name, shorting it from an import if possible.
   */
  private void type(String type) throws IOException {
    if (this.packagePrefix == null) {
      throw new IllegalStateException();
    }
    String imported;
    if ((imported = importedTypes.get(type)) != null) {
      out.write(imported);
    } else if (type.startsWith(packagePrefix)) {
      out.write(type.substring(packagePrefix.length()));
    } else if (type.startsWith("java.lang.")) {
      out.write(type.substring("java.lang.".length()));
    } else {
      out.write(type);
    }
  }

  /**
   * Emits a type declaration.
   *
   * @param kind such as "class", "interface" or "enum".
   */
  public void beginType(String type, String kind, int modifiers) throws IOException {
    indent();
    modifiers(modifiers);
    out.write(kind);
    out.write(" ");
    type(type);
    out.write(" {\n");
    pushScope(Scope.TYPE_DECLARATION);
  }

  /**
   * Completes the current type declaration.
   */
  public void endType() throws IOException {
    if (popScope() != Scope.TYPE_DECLARATION) {
      throw new IllegalStateException();
    }
    indent();
    out.write("}\n");
  }

  /**
   * Emits a field declaration.
   */
  public void field(String type, String name, int modifiers, String... initialValue)
      throws IOException {
    if (initialValue.length > 1) {
      throw new IllegalArgumentException("expected at most one declaration");
    }

    indent();
    modifiers(modifiers);
    type(type);
    out.write(" ");
    out.write(name);

    if (initialValue.length == 1) {
      out.write(" = ");
      out.write(initialValue[0]);
    }
    out.write(";\n");
  }

  /**
   * Emit a method declaration.
   *
   * @param returnType the method's return type, or null for constructors.
   * @param parameters alternating parameter types and names.
   * @param name the method name, or the class name for constructors.
   */
  public void beginMethod(String returnType, String name, int modifiers, String... parameters)
      throws IOException {
    indent();
    modifiers(modifiers);
    if (returnType != null) {
      type(returnType);
      out.write(" ");
    }
    out.write(name);
    out.write("(");
    for (int p = 0; p < parameters.length; ) {
      if (p != 0) {
        out.write(", ");
      }
      type(parameters[p++]);
      out.write(" ");
      type(parameters[p++]);
    }
    out.write(")");
    if ((modifiers & Modifier.ABSTRACT) != 0) {
      out.write(";\n");
      pushScope(Scope.ABSTRACT_METHOD);
    } else {
      out.write(" {\n");
      pushScope(Scope.NON_ABSTRACT_METHOD);
    }
  }

  /**
   * @param s a code statement like "int i = 5". Shouldn't contain a trailing
   * semicolon or newline character.
   */
  public void statement(String s) throws IOException {
    checkInMethod();
    indent();
    out.write(s);
    out.write(";\n");
  }

  /**
   * @param controlFlow the control flow construct and its code, such as
   *     "if (foo == 5)". Shouldn't contain braces or newline characters.
   */
  public void beginControlFlow(String controlFlow) throws IOException {
    checkInMethod();
    indent();
    out.write(controlFlow);
    out.write(" {\n");
    pushScope(Scope.CONTROL_FLOW);
  }

  /**
   * @param controlFlow the control flow construct and its code, such as
   *     "else if (foo == 10)". Shouldn't contain braces or newline characters.
   */
  public void nextControlFlow(String controlFlow) throws IOException {
    if (popScope() != Scope.CONTROL_FLOW) {
      throw new IllegalArgumentException();
    }

    indent();
    pushScope(Scope.CONTROL_FLOW);
    out.write("} ");
    out.write(controlFlow);
    out.write(" {\n");
  }

  /**
   * @param controlFlow the optional control flow construct and its code, such
   *     as "while(foo == 20)". Only used for "do/while" control flows.
   */
  public void endControlFlow(String... controlFlow) throws IOException {
    if (controlFlow.length > 1) {
      throw new IllegalArgumentException("expected 'while' part of do loop");
    }
    if (popScope() != Scope.CONTROL_FLOW) {
      throw new IllegalArgumentException();
    }

    indent();
    if (controlFlow.length == 1) {
      out.write("} ");
      out.write(controlFlow[0]);
      out.write(";\n");
    } else {
      out.write("}\n");
    }
  }

  /**
   * Completes the current method declaration.
   */
  public void endMethod() throws IOException {
    Scope popped = popScope();
    if (popped == Scope.NON_ABSTRACT_METHOD) {
      indent();
      out.write("}\n");
    } else if (popped != Scope.ABSTRACT_METHOD) {
      throw new IllegalStateException();
    }
  }

  public void close() throws IOException {
    out.close();
  }

  /**
   * Emit modifier names.
   */
  private void modifiers(int modifiers) throws IOException {
    if ((modifiers & Modifier.PUBLIC) != 0) {
      out.write("public ");
    }
    if ((modifiers & Modifier.PRIVATE) != 0) {
      out.write("private ");
    }
    if ((modifiers & Modifier.PROTECTED) != 0) {
      out.write("protected ");
    }
    if ((modifiers & Modifier.STATIC) != 0) {
      out.write("static ");
    }
    if ((modifiers & Modifier.FINAL) != 0) {
      out.write("final ");
    }
    if ((modifiers & Modifier.ABSTRACT) != 0) {
      out.write("abstract ");
    }
    if ((modifiers & Modifier.SYNCHRONIZED) != 0) {
      out.write("synchronized ");
    }
    if ((modifiers & Modifier.TRANSIENT) != 0) {
      out.write("transient ");
    }
    if ((modifiers & Modifier.VOLATILE) != 0) {
      out.write("volatile ");
    }
  }

  private void indent() throws IOException {
    for (int i = 0; i < scopes.size(); i++) {
      out.write(INDENT);
    }
  }

  private void checkInMethod() {
    Scope scope = peekScope();
    if (scope != Scope.NON_ABSTRACT_METHOD && scope != Scope.CONTROL_FLOW) {
      throw new IllegalArgumentException();
    }
  }

  private void pushScope(Scope pushed) {
    scopes.add(pushed);
  }

  private Scope peekScope() {
    return scopes.get(scopes.size() - 1);
  }

  private Scope popScope() {
    return scopes.remove(scopes.size() - 1);
  }

  private enum Scope {
    TYPE_DECLARATION,
    ABSTRACT_METHOD,
    NON_ABSTRACT_METHOD,
    CONTROL_FLOW,
  }
}
