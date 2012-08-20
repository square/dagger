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
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

public final class ModuleGeneratorTest {
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
    assertThat(generator.packageName(document, "ActivitiesModule"))
        .isEqualTo("com.squareup.badhorse");
  }

  @Test public void packageNameWrongDocumentType() throws Exception {
    Document document = document("<html package=\"com.squareup.badhorse\"/>");
    try {
      generator.packageName(document, "ActivitiesModule");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void packageNameNoPackage() throws Exception {
    Document document = document("<manifest/>");
    try {
      generator.packageName(document, "ActivitiesModule");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void packageNameUserSpecified() throws Exception {
    Document document = document("<manifest package=\"com.squareup.badhorse\"/>");
    assertThat(generator.packageName(document, "com.squareup.captainhammer.Module"))
        .isEqualTo("com.squareup.captainhammer");
  }

  @Test public void extractEntryPointNames() throws Exception {
    String manifestXml = ""
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
    Document document = document(manifestXml);
    assertThat(generator.getNameReferences(document)).isEqualTo(Arrays.asList(
        "result.a.Activity", "result.b.Provider", "result.c.Receiver", "result.d.Service"));
  }

  @Test public void excludedEntryPointNames() throws Exception {
    String manifestXml = ""
        + "<manifest"
        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        + "    xmlns:objectgraph=\"http://github.com/square/objectgraph\"\n"
        + "    package=\"com.squareup.badhorse\">\n"
        + "  <application>\n"
        + "    <activity android:name=\"false.Activity\" objectgraph:entryPoint=\"false\"/>\n"
        + "    <activity android:name=\"true.Activity\" objectgraph:entryPoint=\"true\"/>\n"
        + "    <activity android:name=\"default.Activity\"/>\n"
        + "  </application>\n"
        + "</manifest>\n";
    Document document = document(manifestXml);
    assertThat(generator.getNameReferences(document))
        .isEqualTo(Arrays.asList("true.Activity", "default.Activity"));
  }

  @Test public void generate() throws IOException {
    String packageName = "com.squareup.badhorse";
    List<String> nameReferences = Arrays.asList(
        "com.squareup.badhorse.SinActivity", "com.squareup.badhorse.LeagueOfEvilActivity");
    generator.generate(packageName, nameReferences, "ActivitiesModule",
        new JavaWriter(stringWriter));
    assertCode(""
        + "package com.squareup.badhorse;\n"
        + "import com.squareup.objectgraph.Module;\n"
        + "@Module(\n"
        + "  entryPoints = {\n"
        + "    com.squareup.badhorse.LeagueOfEvilActivity.class,\n"
        + "    com.squareup.badhorse.SinActivity.class\n"
        + "  },\n"
        + "  complete = false\n"
        + ")\n"
        + "public final class ActivitiesModule {\n"
        + "}\n");
  }

  @Test public void generateWithUserSpecifiedPackageName() throws IOException {
    String packageName = "com.squareup.badhorse";
    List<String> nameReferences = Collections.emptyList();
    generator.generate(packageName, nameReferences, packageName + ".ActivitiesModule",
        new JavaWriter(stringWriter));
    assertCode(""
        + "package com.squareup.badhorse;\n"
        + "import com.squareup.objectgraph.Module;\n"
        + "@Module(\n"
        + "  entryPoints = {\n"
        + "  },\n"
        + "  complete = false\n"
        + ")\n"
        + "public final class ActivitiesModule {\n"
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
