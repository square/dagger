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
package com.squareup.objectgraph.androidmanifest;

import com.squareup.objectgraph.Module;
import com.squareup.objectgraph.internal.codegen.JavaWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

// TODO: support inner classes
// TODO: support relative class name references like ".FooActivity"

/**
 * Generates an object graph module source file that has entry points for all
 * classes referenced in an {@code AndroidManifest.xml} file.
 */
public final class ModuleGenerator {
  private static final String NAMESPACE = "http://schemas.android.com/apk/res/android";

  /**
   * Returns the path of the generated ManifestModule.java for {@code manifest}.
   *
   * @param baseDir the directory where generated files are to be created.
   */
  public File path(Document manifest, File baseDir) {
    String packageName = packageName(manifest);
    return new File(baseDir, packageName.replace('.', '/') + "/ManifestModule.java");
  }

  String packageName(Document manifest) {
    Element root = manifest.getDocumentElement();
    if (!root.getTagName().equals("manifest")) {
      throw new IllegalArgumentException("Expected <manifest> but was <" + root.getTagName() + ">");
    }
    Attr packageAttr = root.getAttributeNode("package");
    if (packageAttr == null) {
      throw new IllegalArgumentException("Expected a package attribute");
    }
    return packageAttr.getValue();
  }

  public void generate(Document manifest, JavaWriter out) throws IOException {
    String packageName = packageName(manifest);
    List<String> nameReferences = getNameReferences(manifest);
    generate(packageName, nameReferences, out);
  }

  void generate(String packageName, List<String> nameReferences, JavaWriter out)
      throws IOException {
    String className = packageName + ".ManifestModule";
    out.addPackage(packageName);
    out.addImport(Module.class);

    List<String> classLiterals = namesToClassLiterals(nameReferences);
    Collections.sort(classLiterals);
    Map<String, Object> attributes = new HashMap<String, Object>();
    attributes.put("entryPoints", classLiterals.toArray());

    out.annotation(Module.class, attributes);
    out.beginType(className, "class", Modifier.PUBLIC | Modifier.FINAL);
    out.endType();
  }

  /**
   * Returns class name references for the given class names. This appends the
   * {@code .class} suffix.
   */
  private List<String> namesToClassLiterals(List<String> classNameReferences) {
    List<String> result = new ArrayList<String>();
    for (String name : classNameReferences) {
      result.add(name + ".class");
    }
    return result;
  }

  /**
   * Returns the names of classes referenced by {@code activity}, {@code
   * provider}, {@code receiver} and {@code service} tags within {@code
   * manifest}.
   */
  List<String> getNameReferences(Document manifest) {
    List<String> result = new ArrayList<String>();
    Element root = manifest.getDocumentElement();
    if (!root.getTagName().equals("manifest")) {
      throw new IllegalArgumentException("Expected <manifest> but was <" + root.getTagName() + ">");
    }
    for (Element e : childElements(root)) {
      if (!e.getTagName().equals("application")) {
        continue;
      }
      for (Element ee : childElements(e)) {
        String tagName = ee.getTagName();
        if (tagName.equals("activity")
            || tagName.equals("provider")
            || tagName.equals("receiver")
            || tagName.equals("service")) {
          Attr nameAttr = ee.getAttributeNodeNS(NAMESPACE, "name");
          if (nameAttr == null) {
            throw new IllegalArgumentException("Expected a name attribute on " + ee);
          }
          result.add(nameAttr.getValue());
        }
      }
    }
    return result;
  }

  private List<Element> childElements(Element element) {
    NodeList childNodes = element.getChildNodes();
    ArrayList<Element> result = new ArrayList<Element>();
    for (int i = 0; i < childNodes.getLength(); i++) {
      if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
        result.add((Element) childNodes.item(i));
      }
    }
    return result;
  }

  public Document manifestToDocument(InputSource androidManifestIn)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
    return documentBuilder.parse(androidManifestIn);
  }
}
