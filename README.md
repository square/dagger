Dagger 1
========

A fast dependency injector for Android and Java.


Deprecated – Please upgrade to Dagger 2
---------------------------------------

Square's Dagger 1.x is deprecated in favor of [Google's Dagger 2](https://github.com/google/dagger).
Please see [the migration guide](https://google.github.io/dagger/dagger-1-migration.html) for help
with the upgrade.


Download Dagger 1
-----------------

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
    <groupId>com.squareup.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>${dagger.version}</version>
  </dependency>
  <dependency>
    <groupId>com.squareup.dagger</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>${dagger.version}</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

You can also find downloadable .jars on Maven Central. You'll need
[Dagger][dl-dagger], [JavaPoet][dl-javapoet], and [javax.inject][dl-inject].

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].



License
-------

    Copyright 2012 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



 [1]: http://square.github.com/dagger/
 [dl-dagger]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup.dagger%22%20a%3A%22dagger%22
 [dl-javapoet]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup%22%20a%3A%22javapoet%22
 [dl-inject]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22javax.inject%22%20a%3A%22javax.inject%22
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
