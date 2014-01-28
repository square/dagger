Dagger
======

A fast dependency injector for Android and Java.

For more information please see [the website][1].

About Google's Fork
-------------

This project represents Google's fork of [Square's Dagger][upstream].
Google's Dagger fork is intended to vary from Square's project only where features are
needed to deviate from Square's feature-set.  Google's Dagger should be a drop-in compatible
alternative to Square's Dagger, and many features in this fork will, if successful, be
merged into the upstream project.  

Efforts are made to also keep Google's fork as close in structure to Square's as possible,
to facilitate merging and compatibility.  Google/dagger will release shortly after Square's
releases.

Differences with square/dagger
------------------------------

  * Injectable types with inheritance hierarchies are scanned at compile time and adapters are generated for their parent types statically.  This differs from square/dagger which always falls back to reflection for the parent types
    * Pro: Reflection can be entirely avoided in an application.
    * Con: Changes to parent types to add or remove @Inject members require that this class' adapter be regenerated
    * Con: Extra code is generated for each concrete sub-type which generates its own copy of the parent adapter (since we don't rely on the parent type having had InjectAdapterProcessor run over it)


Download
--------

You will need to include the `dagger-${dagger.version}.jar` in your
application's runtime.  In order to activate code generation you will need to
include `dagger-compiler-${dagger.version}.jar` in your build at compile time.

In a Maven project, one would include the runtime in the dependencies section
of your `pom.xml` (replacing `${dagger.version}` with the appropriate current
release), and the `dagger-compiler` artifact as an "optional" or "provided"
dependency:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>${dagger.version}</version>
  </dependency>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>${dagger.version}</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

You can also find downloadable .jars on Maven Central. You'll need
[Dagger][dl-dagger], [JavaWriter][dl-javawriter], and [javax.inject][dl-inject].



License
-------

    Copyright 2012 Square, Inc.
    Copyright 2012 Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



 [1]: http://google.github.com/dagger/
 [upstream]: http://github.com/square/dagger/
 [dl-dagger]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.google.dagger%22%20a%3A%22dagger%22
 [dl-javawriter]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup%22%20a%3A%22javawriter%22
 [dl-inject]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22javax.inject%22%20a%3A%22javax.inject%22
