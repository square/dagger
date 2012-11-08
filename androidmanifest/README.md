AndroidManifest Plugin
======================

This plugin parses an `AndroidManifest.xml` file for entry points. It has
designed to be used both in a standalone manner (such as with Ant) and as a
Maven plugin.

The module generated from this plugin is not automatically added to your
object graph. You will need to explicitly include it during construction:

```java
ObjectGraph og = ObjectGraph.get(
  new MyModule(),       // Your declared module.
  new ManifestModule()  // Module generated from this plugin.
);
```


Maven Usage
-----------

```xml
<plugin>
  <groupId>com.squareup</groupId>
  <artifactId>dagger-androidmanifest-plugin</artifactId>
  <version>(latest version)</version>
  <executions>
    <execution>
      <phase>generate-sources</phase>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Optional configuration:

 * `androidManifest` - Path to the `AndroidManifest.xml` file.
 * `moduleName` - Class name. Defaults to `ManifestModule` in the package declared in your manifest.
 * `outputDirectory` - Generated source directory, automatically added to build path.



Ant Usage
---------

```xml
<macrodef name="generate-dagger-module">
  <attribute name="dir"/>
  <attribute name="name"/>

  <sequential>
    <mkdir dir="@{dir}/src"/>
    <java classname="dagger.androidmanifest.ModuleGenerator"
          classpath="${com.squareup:dagger:jar}:${com.squareup:dagger-androidmanifest-plugin:jar}">
      <arg value="@{dir}/AndroidManifest.xml"/>
      <arg value="@{name}"/>
      <arg value="@{dir}/gen"/>
    </java>
  </sequential>
</macrodef>
```
