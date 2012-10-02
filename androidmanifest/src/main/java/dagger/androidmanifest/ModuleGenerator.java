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
package dagger.androidmanifest;

import dagger.Module;
import dagger.internal.codegen.JavaWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

// TODO: support relative class name references like ".FooActivity"

/**
 * Generates an object graph module source file that has entry points for all
 * classes referenced in an {@code AndroidManifest.xml} file.
 */
public final class ModuleGenerator {
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String DAGGER_NS = "http://github.com/square/dagger";

  /**
   * Returns the path of the generated ManifestModule.java for {@code manifest}.
   *
   * @param baseDir the directory where generated files are to be created.
   */
  public File path(Document manifest, String moduleName, File baseDir) {
    String packageName = packageName(manifest, moduleName);
    int dot = moduleName.lastIndexOf('.');
    if (dot != -1) {
      moduleName = moduleName.substring(dot + 1);
    }
    return new File(baseDir, packageName.replace('.', '/') + "/" + moduleName + ".java");
  }

  String packageName(Document manifest, String moduleName) {
    int dot = moduleName.lastIndexOf('.');
    if (dot != -1) {
      return moduleName.substring(0, dot);
    }
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

  public void generate(Document manifest, String moduleName, JavaWriter out) throws IOException {
    String packageName = packageName(manifest, moduleName);
    List<String> nameReferences = getNameReferences(manifest);
    generate(packageName, nameReferences, moduleName, out);
  }

  void generate(String packageName, List<String> nameReferences, String moduleName, JavaWriter out)
      throws IOException {
    String className = moduleName.contains(".")
        ? moduleName
        : packageName + "." + moduleName;
    out.addPackage(packageName);
    out.addImport(Module.class);

    List<String> classLiterals = namesToClassLiterals(nameReferences);
    Collections.sort(classLiterals);
    Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    attributes.put("entryPoints", classLiterals.toArray());
    attributes.put("complete", "false");

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
      result.add(name.replace('$', '.') + ".class");
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
          Attr nameAttr = ee.getAttributeNodeNS(ANDROID_NS, "name");
          if (nameAttr == null) {
            throw new IllegalArgumentException("Expected a name attribute on " + ee);
          }
          Attr entryPointAttr = ee.getAttributeNodeNS(DAGGER_NS, "entryPoint");
          if (entryPointAttr != null && !Boolean.valueOf(entryPointAttr.getValue())) {
            continue;
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

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      printUsage();
      return;
    }

    File manifestXml = new File(args[0]);
    String moduleName = args[1];
    File baseDir = new File(args[2]);

    if (!manifestXml.exists()) {
      System.out.println("No such file: " + manifestXml);
      printUsage();
      return;
    }

    if (!baseDir.isDirectory()) {
      System.out.println("No such directory: " + baseDir);
      printUsage();
      return;
    }

    generate(manifestXml, moduleName, baseDir);
  }

  /**
   * Generate a module from the specified <code>AndroidManifest.xml</code>.
   *
   * @param manifestXml Path to the <code>AndroidManifest.xml</code>.
   * @param moduleName Name of the target module.
   * @param baseDir Destination directory for the generated module.
   * @throws IOException
   * @throws SAXException
   * @throws ParserConfigurationException
   */
  public static void generate(File manifestXml, String moduleName, File baseDir)
      throws IOException, SAXException, ParserConfigurationException {
    ModuleGenerator moduleGenerator = new ModuleGenerator();
    InputSource in = new InputSource(new FileInputStream(manifestXml));
    Document document = moduleGenerator.manifestToDocument(in);
    File file = moduleGenerator.path(document, moduleName, baseDir);
    file.getParentFile().mkdirs();
    JavaWriter out = new JavaWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    moduleGenerator.generate(document, moduleName, out);
    out.close();
  }

  private static void printUsage() {
    System.out.println("Usage: ModuleGenerator manifest module out");
    System.out.println("  manifest: path to AndroidManifest.xml");
    System.out.println("    module: name of the generated class, like 'ActivitiesModule'.");
    System.out.println("            May be fully-qualified like 'com.squareup.ActivitiesModule'.");
    System.out.println("       out: base directory for generated .java source files");
  }
}
