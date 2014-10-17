Dagger 2
========

A fast dependency injector for Android and Java.

About Google's Fork
-------------

Dagger 2.0 is a completely compile-time evolution of the Dagger approach to dependency injection,
eliminating all reflection, and moving towards user-specified access to managed graphs (vs.
the traditional Context/Injector/ObjectGraph found in earlier dependency-injection frameworks.

This github project represents the Dagger 2.0 development stream.  The earlier 
[project page][square] (Square, Inc's repository) represents the earlier 1.0 development stream.  
Both versions have benefitted from strong involvement from Square, Google, and other contributors. 

Status
------

***Version:* 2.0-SNAPSHOT** 

Dagger is currently in active development, primarily internally at Google, with regular pushes
to the open-source community.  Snapshot releases are auto-deployed to sonatype's central maven
repository on a clean build with the version tag `2.0-SNAPSHOT`.

Dagger 2.0 is pre-alpha and should be used with caution, though it is usable.  Stable 
pre-releases will be made as stable points in 2.0's development occur.

Documentation
-------------

The Dagger project will undergo a documentation re-vamp in preparation for 2.0.  In the mean-time,
you can get an initial picture of Dagger 2.0's direction from [the original proposal][proposal],
[this talk by Greg Kick][gaktalk], and discussions on the dagger-discuss@googlegroups.com 
mailing list.

Also, Javadocs are updated on every merge and are [available here][latestapi]

Installation
--------

You will need to include the `dagger-2.0-SNAPSHOT.jar` in your
application's runtime.   In order to activate code generation you will need to
include `dagger-compiler-2.0-SNAPSHOT.jar` in your build at compile time.

In a Maven project, one would include the runtime in the dependencies section
of your `pom.xml` (replacing `${dagger.version}` with the appropriate current
release), and the `dagger-compiler` artifact as an "optional" or "provided"
dependency:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>2.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>2.0-SNAPSHOT</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

  - ***Release Version:* N/A**
  - ***Pre-Release Version:* N/A**
  - ***Snapshot Version:* 2.0-SNAPSHOT**

### Download 

  * 2.0 (google/dagger)
    * [Dagger 2.0 Javadocs][latestapi]
    * [Google's Dagger project site on GitHub][project]
    * <a href="https://plus.google.com/118328287768685565185" rel="publisher">Google+ Dagger Project Page</a>
    * [Google+ Dagger Users Community][community]
  * 1.0 (square/dagger)
    * [Square's original Dagger project site on GitHub][square]
    * [Square Open Source Community][squarecommunity]


Upon release, downloadable .jars will appear via search on Maven Central. You'll need
[Dagger][dl-dagger], [dagger-compiler][dl-dagger-compiler] and [javax.inject][dl-inject].

Pre-release directly downloaddable jars of the snapshots are available for [dagger](https://oss.sonatype.org/content/repositories/snapshots/com/google/dagger/dagger/2.0-SNAPSHOT/) and [dagger-compiler](https://oss.sonatype.org/content/repositories/snapshots/com/google/dagger/dagger-compiler/2.0-SNAPSHOT/) from sonatype's snapshot repository, and are built on a clean build at head.

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
 [dl-dagger]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.google.dagger%22%20a%3A%22dagger%22
 [dl-dagger-compiler]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.google.dagger%22%20a%3A%22dagger-compiler%22
 [dl-javawriter]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup%22%20a%3A%22javawriter%22
 [dl-inject]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22javax.inject%22%20a%3A%22javax.inject%22
 [latestapi]: http://google.github.io/dagger/api/latest/
 [gaktalk]: https://www.youtube.com/watch?v=oK_XtfXPkqw
 [proposal]: https://github.com/square/dagger/issues/366
 [project]: http://github.com/google/dagger/
 [community]: https://plus.google.com/communities/111933036769103367883
 [square]: http://github.com/square/dagger/
 [squarecommunity]: https://plus.google.com/communities/109244258569782858265

