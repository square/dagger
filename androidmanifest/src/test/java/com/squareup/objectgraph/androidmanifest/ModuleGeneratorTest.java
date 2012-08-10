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

import com.squareup.objectgraph.internal.codegen.JavaWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class ModuleGeneratorTest {
  private static final String MANIFEST_XML = ""
      + "<manifest"
      + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
      + "    package=\"com.squareup.badhorse\">\n"
      + "  <uses-permission android:name=\"not.an.entry.point\"/>\n"
      + "  <permission android:name=\"not.an.entry.point\"/>\n"
      + "  <application android:name=\"not.an.entry.point\">\n"
      + "    <uses-library android:name=\"not.an.entry.point\"/>\n"
      + "    <activity android:name=\"result.a.Activity\">\n"
      + "      <intent-filter>\n"
      + "        <action android:name=\"not.an.entry.point\"/>\n"
      + "        <category android:name=\"not.an.entry.point\"/>\n"
      + "      </intent-filter>\n"
      + "    </activity>\n"
      + "    <provider android:name=\"result.b.Provider\"/>\n"
      + "    <receiver android:name=\"result.c.Receiver\">\n"
      + "      <intent-filter>\n"
      + "        <action android:name=\"not.an.entry.point\"/>\n"
      + "      </intent-filter>\n"
      + "    </receiver>\n"
      + "    <service android:name=\"result.d.Service\"/>\n"
      + "  </application>\n"
      + "</manifest>\n";
  private ModuleGenerator generator = new ModuleGenerator();
  private StringWriter stringWriter = new StringWriter();

  @Test public void packageName() throws Exception {
    Document document = document(""
        + "<manifest\n"
        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        + "    package=\"com.squareup.badhorse\"\n"
        + "    android:versionCode=\"42\"\n"
        + "    android:versionName=\"42.0\">\n"
        + "</manifest>");
    assertThat(generator.packageName(document)).isEqualTo("com.squareup.badhorse");
  }

  @Test public void packageNameWrongDocumentType() throws Exception {
    Document document = document("<html package=\"com.squareup.badhorse\"/>");
    try {
      generator.packageName(document);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void packageNameNoPackage() throws Exception {
    Document document = document("<manifest/>");
    try {
      generator.packageName(document);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void extractEntryPointNames() throws Exception {
    Document document = document(MANIFEST_XML);
    assertThat(generator.getNameReferences(document)).isEqualTo(Arrays.asList(
        "result.a.Activity", "result.b.Provider", "result.c.Receiver", "result.d.Service"));
  }

  @Test public void generate() throws IOException {
    String packageName = "com.squareup.badhorse";
    List<String> nameReferences = Arrays.asList(
        "com.squareup.badhorse.SinActivity", "com.squareup.badhorse.LeagueOfEvilActivity");
    generator.generate(packageName, nameReferences, "ManifestModule", new JavaWriter(stringWriter));
    assertCode(""
        + "package com.squareup.badhorse;\n"
        + "import com.squareup.objectgraph.Module;\n"
        + "@Module(\n"
        + "  entryPoints = {\n"
        + "    com.squareup.badhorse.LeagueOfEvilActivity.class,\n"
        + "    com.squareup.badhorse.SinActivity.class\n"
        + "  }\n"
        + ")\n"
        + "public final class ManifestModule {\n"
        + "}\n");
  }

  private Document document(String xml) throws Exception {
    InputSource xmlIn = new InputSource(new StringReader(xml));
    return generator.manifestToDocument(xmlIn);
  }

  private void assertCode(String expected) {
    assertThat(stringWriter.toString()).isEqualTo(expected);
  }
}
