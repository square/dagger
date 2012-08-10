ObjectGraph
===========

A fast dependency injector for Android and Java.

### Introduction

The best classes in any application are the ones that do stuff: the `BarcodeDecoder`, the `KoopaPhysicsEngine`, and the `AudioStreamer`. These classes have dependencies; perhaps a `BarcodeCameraFinder`, `DefaultPhysicsEngine`, and an `HttpStreamer`.

To contrast, the worst classes in any application are the ones that take up space without doing much at all: the `BarcodeDecoderFactory`, the `CameraServiceLoader`, and the `MutableContextWrapper`. These classes are the clumsy duct tape that wires the interesting stuff together.

ObjectGraph is a replacement for these `FactoryFactory` classes. It allows you to focus on the interesting classes. Declare dependencies, specify how to satisfy them, and ship your app.

By building on standard [javax.inject][1] annotations (JSR-330), each class is **easy to test**. You don't need a bunch of boilerplate just to swap the `RpcCreditCardService` out for a `FakeCreditCardService`.

Dependency injection isn't just for testing. It also makes it easy to create **reusable, interchangeable modules**. You can share the same `AuthenticationModule`  across all of your apps. And you can run `DevLoggingModule` during development and `ProdLoggingModule` in production to get the right behavior in each situation.

### Declaring Dependencies

ObjectGraph constructs instances of your application classes and satisfies their dependencies. It uses the `javax.inject.Inject` annotation to identify which constructors and fields it is interested in.

Use `@Inject` to annotate the constructor that ObjectGraph should use to create instances of a class. When a new instance is requested, ObjectGraph will obtain the required parameters values and invoke this constructor.

```java
class Thermosiphon implements Pump {
  private final Heater heater;

  @Inject
  Thermosiphon(Heater heater) {
    this.heater = heater;
  }

  ...
}
```

ObjectGraph can inject fields directly. In this example it obtains a `Heater` instance for the `heater` field and a `Pump` instance for the `pump` field.

```java
class CoffeeMaker {
  @Inject Heater heater;
  @Inject Pump pump;

  ...
}
```

If your class has `@Inject`-annotated fields but no `@Inject`-annotated constructor, ObjectGraph will use a no-argument constructor if it exists. Classes that lack `@Inject` annotations cannot be constructed by ObjectGraph.

ObjectGraph does not support method injection.

### Satisfying Dependencies

By default, ObjectGraph satisfies each dependency by constructing an instance of the requested type as described above. When you request a `CoffeeMaker`, it'll obtain one by calling `new CoffeeMaker()` and setting its injectable fields.

But `@Inject` doesn't work everywhere:

* Interfaces can't be constructed.
* Third-party classes can't be annotated.
* Configurable objects must be configured!

For these cases where `@Inject` is insufficient or awkward, use an `@Provides`-annotated method to satisfy a dependency. The method's return type defines which dependency it satisfies.

For example, `provideHeater()` is invoked whenever a `Heater` is required:

```java
@Provides Heater provideHeater() {
  return new ElectricHeater();
}
```

It's possible for `@Provides` methods to have dependencies of their own. This one returns a `Thermosiphon` whenever a `Pump` is required:

```java
@Provides Pump providePump(Thermosiphon pump) {
  return pump;
}
```

All `@Provides` methods must belong to a module. These are just classes that have an `@Module` annotation.

```java
@Module
class DripCoffeeModule {
  @Provides Heater provideHeater() {
    return new ElectricHeater();
  }

  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
```

By convention, `@Provides` methods are named with a `provide` prefix and module classes are named with a `Module` suffix.

### Building the Graph

The `@Inject` and `@Provides`-annotated classes form a graph of objects, linked by their dependencies. Obtain this graph by calling `ObjectGraph.get()`, which accepts one or more modules:

```java
ObjectGraph objectGraph = ObjectGraph.get(new DripCoffeeModule());
```

In order to put the graph to use we need to create an **entry point**. This is usually the main class that starts the application. In this example, the `CoffeeApp` class serves as the entry point. We construct an instance of this type and then ask the object graph to inject its fields.

```java
class CoffeeApp implements Runnable {
  @Inject CoffeeMaker coffeeMaker;

  @Override public void run() {
    coffeeMaker.brew();
  }

  public static void main(String[] args) {
    ObjectGraph objectGraph = ObjectGraph.get(new DripCoffeeModule());
    CoffeeApp coffeeApp = new CoffeeApp();
    objectGraph.inject(coffeeApp);
    ...
  }
}
```

The only thing that's missing is that the entry point class `CoffeeApp` isn't included in the graph. We need to explicitly register it as an entry point in the `@Module` annotation.

```java
@Module(
    entryPoints = CoffeeApp.class
)
class DripCoffeeModule {
  ...
}
```

Entry points enable the complete graph to be validated **at compile time**. Detecting problems early speeds up development and takes some of the danger out of refactoring.

Now that the graph is constructed and the entry point is injected, we run our coffee maker app. Fun.

```
$ java -cp ... coffee.CoffeeApp
~ ~ ~ heating ~ ~ ~
=> => pumping => =>
 [_]P coffee! [_]P
```

### Singletons

Annotate an `@Provides` method or injectable class with `@Singleton`. The graph will use a single instance of the value for all of its clients.

```java
@Provides @Singleton Heater provideHeater() {
  return new ElectricHeater();
}
```

The `@Singleton` annotation on an injectable class also serves as documentation. It reminds potential maintainers that this class may be shared by multiple threads.

```java
@Singleton
class CoffeeMaker {
  ...
}
```

### Providers

Not to be confused with `@Provides`, a `Provider` is a special dependency that can be used to retrieve any number of instances. Use a `Provider` to make a dependency lazy:

```java
class GridingCoffeeMaker {
  @Inject Provider<Grinder> grinderProvider;

  public void brew() {
    if (needsGrinding()) {
      Grinder grinder = grinderProvider.get();
      ...
    }
  }
}
```

Or when multiple values are required:

```java
class BigCoffeeMaker {
  @Inject Provider<Filter> filterProvider;

  public void brew(int numberOfPots) {
    for (int p = 0; p < numberOfPots; p++) {
      Filter coffeeFilter = filterProvider.get();
      ...
    }
  }
}
```

### Qualifiers

Sometimes the type alone is insufficient to identify a dependency. For example, a sophisticated coffee maker app may want separate heaters for the water and the hot plate.

In this case, we add a **qualifier annotation**. This is any annotation that itself has a `@Qualifier` annotation. Here's the declaration of `@Named`, a qualifier annotation included in `javax.inject`:

```java
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface Named {
  String value() default "";
}
```

Create your own qualifier annotations just use `@Named`. Apply qualifiers by annotating the field or parameter of interest. The type and qualifier annotation will both be used to identify the dependency.

```java
class ExpensiveCoffeeMaker {
  @Inject @Named("water") Heater waterHeater;
  @Inject @Named("hot plate") Heater hotPlateHeater;
  ...
}
```

Supply qualified values by annotating the corresponding `@Provides` method.

```java
@Provides @Named("hot plate") Heater provideHotPlateHeater() {
  return new ElectricHeater(70);
}

@Provides @Named("water") Heater provideWaterHeater() {
  return new ElectricHeater(93);
}
```

Dependencies may not have multiple qualifier annotations.


Upgrading from Guice
====================

Some notable Guice features that ObjectGraph doesn't support:

* Injecting `final` fields and `private` members. For best performance `ObjectGraph` generates code. Work around this by using constructor injection.
* Eager singletons. Work around this by creating an `EagerSingletons` class that declares static fields for each eager singleton.
* Method injection.
* Classes that lack `@Inject` annotations cannot be constructed by ObjectGraph, even if they have a no-argument constructor.


Contributing
============

If you would like to contribute code to ObjectGraph you can do so through
GitHub by forking the repository and sending a pull request.

When submitting code, please make every effort to follow existing conventions
and style in order to keep the code as readable as possible. Please also make
sure your code compiles by running `mvn clean verify`. Checkstyle failures
during compilation indicate errors in your style and can be viewed in the
`checkstyle-result.xml` file.

Before your code can be accepted into the project you must also sign the
[Individual Contributor License Agreement (CLA)][2].


License
=======

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

 [1]: http://atinject.googlecode.com/svn/trunk/javadoc/javax/inject/package-summary.html
 [2]: https://spreadsheets.google.com/spreadsheet/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1
