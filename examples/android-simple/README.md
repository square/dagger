Example: Android Simple
=======================

This example demonstrates how to structure an Android application with Dagger.

A custom `Application` class is used to manage a global object graph of objects. Modules are
assembled with a `getModules` method on the application that can be overridden to add additional
modules in development versions of your applications or in tests.

Injection of activities is done automatically in a base activity.

_Note: The app does not actually do anything when it is run. It is only to show how you can
 structure Dagger within an Android app_
