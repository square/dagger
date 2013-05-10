Dagger
======

A fast dependency injector for Android and Java.

For more information please see [the website][1].



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

You can also find downloadable .jars on Maven Central. You'll need both
[Dagger](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup%22%20dagger) and
[JavaWriter](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22javawriter%22).



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
