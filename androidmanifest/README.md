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
 * `moduleName` - Generated module class name. Defaults to `ManifestModule` in the package declared
   in your manifest. May be a fully-qualified class name.
 * `outputDirectory` - Generated source directory, automatically added to build path.



Ant Usage
---------

```xml
<taskdef name="dagger-manifest" classname="dagger.androidmanifest.ModuleGeneratorTask"/>

<target name="-pre-build">
  <dagger-manifest/>
</target>
```

Optional task arguments:

 * `manifest` - Path to the `AndroidManifest.xml` file.
 * `name` - Generated module class name. Defaults to `ManifestModule` in the package declared in
   your manifest. May be a fully-qualified class name.
 * `out` - Generated source directory. Defaults to `gen/`.

For example,

```xml
<dagger-manifest name="com.other.pkg.ActivitiesModule"/>
```