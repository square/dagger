web-dagger-example
==================

This example shows how you can use Dagger in the context of a simple web app.


Build
-----
```
cd examples/web
mvn clean package
```
The above will create a shaded jar, `target/web-dagger-example.jar`, which
contains everything needed to run the example.

Usage
-----
The example uses an embedded Jetty web server so there's no need for an external
servlet container.

To run:
```
java -jar target/web-dagger-example.jar
```
