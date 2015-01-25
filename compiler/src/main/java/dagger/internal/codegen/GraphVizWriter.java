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
package dagger.internal.codegen;

import com.squareup.javapoet.CodeBlock;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits dot graphs.
 */
public final class GraphVizWriter implements Closeable {
  private static final String INDENT = "  ";
  private final Writer out;
  private int indent = 0;
  private int nextName = 1;
  private final Map<String, String> generatedNames = new LinkedHashMap<String, String>();

  /**
   * @param out the stream to which dot data will be written. This should be a
   *     buffered stream.
   */
  public GraphVizWriter(Writer out) {
    this.out = out;
  }

  public void beginGraph(String... attributes) throws IOException {
    indent();
    String type = indent == 0 ? "digraph " : "subgraph ";
    String name = nextName(indent == 0 ? "G" : "cluster");
    out.write(type);
    out.write(name);
    out.write(" {\n");
    indent++;
    attributes(attributes);
  }

  public void endGraph() throws IOException {
    indent--;
    indent();
    out.write("}\n");
  }

  public void node(String name, String... attributes) throws IOException {
    name = nodeName(name);
    indent();
    out.write(name);
    inlineAttributes(attributes);
    out.write(";\n");
  }

  public void edge(String source, String target, String... attributes) throws IOException {
    source = nodeName(source);
    target = nodeName(target);
    indent();
    out.write(source);
    out.write(" -> ");
    out.write(target);
    inlineAttributes(attributes);
    out.write(";\n");
  }

  public void nodeDefaults(String... attributes) throws IOException {
    if (attributes.length == 0) return;
    indent();
    out.write("node");
    inlineAttributes(attributes);
    out.write(";\n");
  }

  public void edgeDefaults(String... attributes) throws IOException {
    if (attributes.length == 0) return;
    indent();
    out.write("edge");
    inlineAttributes(attributes);
    out.write(";\n");
  }

  private void attributes(String[] attributes) throws IOException {
    if (attributes.length == 0) return;
    if (attributes.length % 2 != 0) throw new IllegalArgumentException();
    for (int i = 0; i < attributes.length; i += 2) {
      indent();
      out.write(attributes[i]);
      out.write(" = ");
      out.write(literal(attributes[i + 1]));
      out.write(";\n");
    }
  }

  private void inlineAttributes(String[] attributes) throws IOException {
    if (attributes.length == 0) return;
    if (attributes.length % 2 != 0) throw new IllegalArgumentException();
    out.write(" [");
    for (int i = 0; i < attributes.length; i += 2) {
      if (i != 0) out.write(";");
      out.write(attributes[i]);
      out.write("=");
      out.write(literal(attributes[i + 1]));
    }
    out.write("]");
  }

  private String nodeName(String name) throws IOException {
    if (name.matches("\\w+")) return name;
    String generatedName = generatedNames.get(name);
    if (generatedName != null) return generatedName;
    generatedName = nextName("n");
    generatedNames.put(name, generatedName);
    node(generatedName, "label", name);
    return generatedName;
  }

  private String literal(String raw) {
    if (raw.matches("\\w+")) return raw;
    return CodeBlock.builder()
        .add("$S", raw)
        .build()
        .toString();
  }

  private void indent() throws IOException {
    for (int i = 0; i < indent; i++) {
      out.write(INDENT);
    }
  }

  private String nextName(String prefix) {
    return prefix + (nextName++);
  }

  @Override public void close() throws IOException {
    out.close();
  }
}
