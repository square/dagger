Dagger
======

A fast dependency injector for Android and Java.

### Introduction

The best classes in any application are the ones that do stuff: the `BarcodeDecoder`, the `KoopaPhysicsEngine`, and the `AudioStreamer`. These classes have dependencies; perhaps a `BarcodeCameraFinder`, `DefaultPhysicsEngine`, and an `HttpStreamer`.

To contrast, the worst classes in any application are the ones that take up space without doing much at all: the `BarcodeDecoderFactory`, the `CameraServiceLoader`, and the `MutableContextWrapper`. These classes are the clumsy duct tape that wires the interesting stuff together.

Dagger is a replacement for these `FactoryFactory` classes. It allows you to focus on the interesting classes. Declare dependencies, specify how to satisfy them, and ship your app.

By building on standard [javax.inject][1] annotations (JSR-330), each class is **easy to test**. You don't need a bunch of boilerplate just to swap the `RpcCreditCardService` out for a `FakeCreditCardService`.

Dependency injection isn't just for testing. It also makes it easy to create **reusable, interchangeable modules**. You can share the same `AuthenticationModule`  across all of your apps. And you can run `DevLoggingModule` during development and `ProdLoggingModule` in production to get the right behavior in each situation.

### Declaring Dependencies

Dagger constructs instances of your application classes and satisfies their dependencies. It uses the `javax.inject.Inject` annotation to identify which constructors and fields it is interested in.

Use `@Inject` to annotate the constructor that Dagger should use to create instances of a class. When a new instance is requested, Dagger will obtain the required parameters values and invoke this constructor.

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

Dagger can inject fields directly. In this example it obtains a `Heater` instance for the `heater` field and a `Pump` instance for the `pump` field.

```java
class CoffeeMaker {
  @Inject Heater heater;
  @Inject Pump pump;

  ...
}
```

If your class has `@Inject`-annotated fields but no `@Inject`-annotated constructor, Dagger will use a no-argument constructor if it exists. Classes that lack `@Inject` annotations cannot be constructed by Dagger.

Dagger does not support method injection.

### Satisfying Dependencies

By default, Dagger satisfies each dependency by constructing an instance of the requested type as described above. When you request a `CoffeeMaker`, it'll obtain one by calling `new CoffeeMaker()` and setting its injectable fields.

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

The `@Inject` and `@Provides`-annotated classes form a graph of objects, linked by their dependencies. Obtain this graph by calling `ObjectGraph.create()`, which accepts one or more modules:

```java
ObjectGraph objectGraph = ObjectGraph.create(new DripCoffeeModule());
```

In order to put the graph to use we need to create an **entry point**. This is usually the main class that starts the application. In this example, the `CoffeeApp` class serves as the entry point. We ask the graph to provide an injected instance of this type:

```java
class CoffeeApp implements Runnable {
  @Inject CoffeeMaker coffeeMaker;

  @Override public void run() {
    coffeeMaker.brew();
  }

  public static void main(String[] args) {
    ObjectGraph objectGraph = ObjectGraph.create(new DripCoffeeModule());
    CoffeeApp coffeeApp = objectGraph.get(CoffeeApp.class);
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

### Lazy injections

Sometimes you need to make a dependency lazily created.  For any binding `T`, you can create a `Lazy<T>` which defers instantiation until the first call to `Lazy<T>`'s `get()` method. If `T` is a singleton, then `Lazy<T>` will be the same instance for all injections within the ObjectGraph.  Otherwise, each injection site will get its own `Lazy<T>` instance.  Regardless, subsequent calls to any given instance of `Lazy<T>` will return the same underlying instance of `T`.

```java
class GridingCoffeeMaker {
  @Inject Lazy<Grinder> lazyGrinder;

  public void brew() {
    while (needsGrinding()) {
      // Grinder created once on first call to .get() and cached.
      lazyGrinder.get().grind();
    }
  }
}
```

### Provider injections

Sometimes you need multiple instances to be returned instead of just injecting a single value.  While you have several options (Factories, Builders, etc.)   one option is to inject a `Provider<T>` instead of just `T`.  A `Provider<T>` creates a new instance of `T` each time `.get()` is called.

```java

class BigCoffeeMaker {
  @Inject Provider<Filter> filterProvider;

  public void brew(int numberOfPots) {
	...
    for (int p = 0; p < numberOfPots; p++) {
      maker.addFilter(filterProvider.get()); //new filter every time.
      maker.addCoffee(...);
      maker.percolate();
      ...
    }
  }
}
```

***Note:*** *Injecting `Provider<T>` has the possibility of creating confusing code, and may be a design smell of mis-scoped or mis-structured objects in your graph.  Often you will want to use a `Factory<T>` or a `Lazy<T>` or re-organize the lifetimes and structure of your code to be able to just inject a `T`.  Injecting `Provider<T>` can, however, be a life saver in some cases.  A common use is when you must use a legacy architecture that doesn't line up with your object's natural lifetimes (e.g. servlets are singletons by design, but only are valid in the context of request-specfic data).*

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

### Static Injection

**Warning:** This feature should be used sparingly because static dependencies are difficult to test and reuse.

Dagger can inject static fields. Classes that declare static fields with `@Inject` annotations must be listed as `staticInjections` in a module annotation.

```java
@Module(
    staticInjections = LegacyCoffeeUtils.class
)
class LegacyModule {
}
```

Use `ObjectGraph.injectStatics()` to populate these static fields with their injected values:

```java
ObjectGraph objectGraph = ObjectGraph.create(new LegacyModule());
objectGraph.injectStatics();
```

### Compile-time Validation

Dagger includes an [annotation processor][2] that validates modules and injections. This processor is strict and will cause a compiler error if any bindings are invalid or incomplete. For example, this module is missing a binding for `Executor`:

```java
@Module
class DripCoffeeModule {
  @Provides Heater provideHeater(Executor executor) {
    return new CpuHeater(executor);
  }
}
```

When compiling it, `javac` rejects the missing binding:

```
[ERROR] COMPILATION ERROR :
[ERROR] error: No binding for java.util.concurrent.Executor
               required by provideHeater(java.util.concurrent.Executor)
```

Fix the problem either by adding the an `@Provides`-annotated method for `Executor`, or by marking the module as incomplete. Incomplete modules are permitted to have missing dependencies.

```java
@Module(complete = false)
class DripCoffeeModule {
  @Provides Heater provideHeater(Executor executor) {
    return new CpuHeater(executor);
  }
}
```

To get the most out of compile-time validation, create a module that includes all of your application's modules as children. The annotation processor will detect problems across the modules and report them.

```java
@Module(
    children = {
        DripCoffeeModule.class,
        ExecutorModule.class
    }
)
public class CoffeeAppModule {
}
```

The annotation processor is enabled automatically when you include Dagger's jar file on your compile classpath.

### Compile-time Code Generation

Dagger's annotation processor may also generate source files with names like `CoffeeMaker$InjectAdapter.java` or `DripCoffeeModule$ModuleAdapter`. These files are Dagger implementation details. You shouldn't need to use them directly, though they can be handy when step-debugging through an injection.

### Module overrides

Dagger will fail with an error if there are multiple competing `@Provides` methods for the same dependency. But sometimes it's necessary to replace production code with a substitute for development or testing. Using `overrides = true` in a module annotation lets you take precedence over the bindings of other modules.

This JUnit test overrides `DripCoffeeModule`'s binding for `Heater` with a mock object from [Mockito][3]. The mock gets injected into the `CoffeeMaker` and also into the test.

```java
public class CoffeeMakerTest {
  @Inject CoffeeMaker coffeeMaker;
  @Inject Heater heater;

  @Before public void setUp() {
    ObjectGraph.create(new TestModule()).inject(this);
  }

  @Module(
      children = DripCoffeeModule.class,
      entryPoints = CoffeeMakerTest.class,
      overrides = true
  )
  static class TestModule {
    @Provides @Singleton Heater provideHeater() {
      return Mockito.mock(Heater.class);
    }
  }

  @Test public void testHeaterIsTurnedOnAndThenOff() {
    Mockito.when(heater.isHot()).thenReturn(true);
    coffeeMaker.brew();
    Mockito.verify(heater, Mockito.times(1)).on();
    Mockito.verify(heater, Mockito.times(1)).off();
  }
}
```

Overrides are best suited for small variations on the application:

* Replacing the real implementation with a mock for unit tests.
* Replacing LDAP authentication with fake authentication for development.

For more substantial variations it's often simpler to use a different combination of modules.


Using Dagger in your build
==========================

You will need to include the dagger-${dagger.version}.jar in your application's runtime.  In order to activate code generation you will need to include dagger-compiler-${dagger.version}.jar in your build at compile time. 

In a Maven project, one would include the runtime in the dependencies section of your `pom.xml` (replacing ${dagger.version} with the appropriate current release), and the dagger-compiler artifact as a dependency of the compiler plugin: 

```java
  <dependencies>
    <dependency>
      <groupId>com.squareup</groupId>
      <artifactId>dagger</artifactId>
      <version>${dagger.version}</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <dependencies>
          <dependency>
            <groupId>com.squareup</groupId>
            <artifactId>dagger-compiler</artifactId>
            <version>${dagger.version}</version>
          </dependency>
        </dependencies>
      </plugin>
    </plugins>
  </build>
```



Upgrading from Guice
====================

Some notable Guice features that Dagger doesn't support:

* Injecting `final` fields and `private` members. For best performance Dagger generates code. Work around this by using constructor injection.
* Eager singletons. Work around this by creating an `EagerSingletons` class that declares static fields for each eager singleton.
* Method injection.
* Classes that lack `@Inject` annotations cannot be constructed by Dagger, even if they have a no-argument constructor.


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
 [2]: http://docs.oracle.com/javase/6/docs/api/javax/annotation/processing/package-summary.html
 [3]: http://mockito.googlecode.com/
