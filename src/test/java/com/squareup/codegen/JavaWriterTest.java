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
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public final class JavaWriterTest {
  private final StringWriter stringWriter = new StringWriter();
  private final JavaWriter javaWriter = new JavaWriter(stringWriter);

  @Test public void typeDeclaration() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", Modifier.PUBLIC | Modifier.FINAL);
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "public final class Foo {\n"
        + "}\n");
  }

  @Test public void fieldDeclaration() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.field("java.lang.String", "string", Modifier.PRIVATE | Modifier.STATIC);
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  private static String string;\n"
        + "}\n");
  }

  @Test public void fieldDeclarationWithInitialValue() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.field("java.lang.String", "string", 0, "\"bar\" + \"baz\"");
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  String string = \"bar\" + \"baz\";\n"
        + "}\n");
  }

  @Test public void abstractMethodDeclaration() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod("java.lang.String", "foo", Modifier.ABSTRACT | Modifier.PUBLIC,
        "java.lang.Object", "object", "java.lang.String", "s");
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  public abstract String foo(Object object, String s);\n"
        + "}\n");
  }

  @Test public void nonAbstractMethodDeclaration() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod("int", "foo", 0, "java.lang.String", "s");
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  int foo(String s) {\n"
        + "  }\n"
        + "}\n");
  }

  @Test public void constructorDeclaration() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod(null, "Foo", Modifier.PUBLIC, "java.lang.String", "s");
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  public Foo(String s) {\n"
        + "  }\n"
        + "}\n");
  }

  @Test public void statement() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod("int", "foo", 0, "java.lang.String", "s");
    javaWriter.statement("int j = s.length() + %s", 13);
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  int foo(String s) {\n"
        + "    int j = s.length() + 13;\n"
        + "  }\n"
        + "}\n");
  }

  @Test public void addImport() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.addImport("java.util.ArrayList");
    javaWriter.beginType("com.squareup.Foo", "class", Modifier.PUBLIC | Modifier.FINAL);
    javaWriter.field("java.util.ArrayList", "list", 0, "new java.util.ArrayList()");
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "import java.util.ArrayList;\n"
        + "public final class Foo {\n"
        + "  ArrayList list = new java.util.ArrayList();\n"
        + "}\n");
  }

  @Test public void ifControlFlow() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod("int", "foo", 0, "java.lang.String", "s");
    javaWriter.beginControlFlow("if (s.isEmpty())");
    javaWriter.statement("int j = s.length() + %s", 13);
    javaWriter.endControlFlow();
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  int foo(String s) {\n"
        + "    if (s.isEmpty()) {\n"
        + "      int j = s.length() + 13;\n"
        + "    }\n"
        + "  }\n"
        + "}\n");
  }

  @Test public void doWhileControlFlow() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod("int", "foo", 0, "java.lang.String", "s");
    javaWriter.beginControlFlow("do");
    javaWriter.statement("int j = s.length() + %s", 13);
    javaWriter.endControlFlow("while (s.isEmpty())");
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  int foo(String s) {\n"
        + "    do {\n"
        + "      int j = s.length() + 13;\n"
        + "    } while (s.isEmpty());\n"
        + "  }\n"
        + "}\n");
  }

  @Test public void tryCatchFinallyControlFlow() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.beginMethod("int", "foo", 0, "java.lang.String", "s");
    javaWriter.beginControlFlow("try");
    javaWriter.statement("int j = s.length() + %s", 13);
    javaWriter.nextControlFlow("catch (RuntimeException e)");
    javaWriter.statement("%s.printStackTrace()", "e");
    javaWriter.nextControlFlow("finally");
    javaWriter.statement("int k = %s", 13);
    javaWriter.endControlFlow();
    javaWriter.endMethod();
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  int foo(String s) {\n"
        + "    try {\n"
        + "      int j = s.length() + 13;\n"
        + "    } catch (RuntimeException e) {\n"
        + "      e.printStackTrace();\n"
        + "    } finally {\n"
        + "      int k = 13;\n"
        + "    }\n"
        + "  }\n"
        + "}\n");
  }

  @Test public void annotatedType() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.addImport("javax.inject.Singleton");
    javaWriter.annotation("javax.inject.Singleton");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "import javax.inject.Singleton;\n"
        + "@Singleton\n"
        + "class Foo {\n"
        + "}\n");
  }

  @Test public void annotatedMember() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.annotation(Deprecated.class);
    javaWriter.field("java.lang.String", "s", 0);
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "class Foo {\n"
        + "  @Deprecated\n"
        + "  String s;\n"
        + "}\n");
  }

  @Test public void parameterizedType() throws IOException {
    javaWriter.addPackage("com.squareup");
    javaWriter.addImport("java.util.Map");
    javaWriter.addImport("java.util.Date");
    javaWriter.beginType("com.squareup.Foo", "class", 0);
    javaWriter.field("java.util.Map<java.lang.String, java.util.Date>", "map", 0);
    javaWriter.endType();
    assertCode(""
        + "package com.squareup;\n"
        + "import java.util.Map;\n"
        + "import java.util.Date;\n"
        + "class Foo {\n"
        + "  Map<String, Date> map;\n"
        + "}\n");
  }

  @Test public void testStringLiteral() {
    assertThat(JavaWriter.stringLiteral("")).isEqualTo("\"\"");
    assertThat(JavaWriter.stringLiteral("JavaWriter")).isEqualTo("\"JavaWriter\"");
    assertThat(JavaWriter.stringLiteral("\\")).isEqualTo("\"\\\\\"");
    assertThat(JavaWriter.stringLiteral("\"")).isEqualTo("\"\\\"\"");
    assertThat(JavaWriter.stringLiteral("\t")).isEqualTo("\"\\\t\"");
    assertThat(JavaWriter.stringLiteral("\n")).isEqualTo("\"\\\n\"");
  }

  private void assertCode(String expected) {
    assertThat(stringWriter.toString()).isEqualTo(expected);
  }
}
