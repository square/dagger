Change Log
==========

Version 1.2.5 *(2016-05-09)*
----------------------------

 * Fix: Correctly emit generated code for binding parameterized types.


Version 1.2.4 *(2016-05-03)*
----------------------------

 * Fix: Restore static injection support to work correctly.


Version 1.2.3 *(2016-05-02)*
----------------------------

 * Fix: Correct detection of module base classes. This previously erroneously failed compilation
   on modules which extended from `Object` but were not detected as such.
 * Fix: Allow the use of dollar signs in processed class names.
 * Fix: Remove the need for `javac` to generate synthetic accessor methods for internal classes.
 * Fix: Error when duplicate classes are listed in `injects=` or `includes=` lists.


Version 1.2.2 *(2014-07-21)*
----------------------------

 * Update JavaWriter to 2.5.0. This fixes incorrectly compressing fully-qualified class names
   in child packages of `java.lang` (e.g., `java.lang.ref.WeakReference`).


Version 1.2.1 *(2014-02-16)*
----------------------------

 * Restore Java 5 compatibility.
 * New: Improve performance of `.plus()` with large volumes of set bindings.
 * Fix: Do not mask underlying exception message from binding problems when constructing a graph.


Version 1.2.0 *(2013-12-13)*
----------------------------

 * Numerous performance improvements in both the compiler and runtime.
   * Use more efficient `String` concatenation.
   * Module adapters are now stateless.
   * Use read/write locks over global locks.
   * Reflective constructor invocation is now cached with `Class.newInstance`.
   * Avoid re-linking all bindings when calling `.plus()`.
 * Set bindings are now unioned when calling `.plus()`.
 * Fix: Tolerate missing type information during compilation by deferring writing
   module adapters.


Version 1.1.0 *(2013-08-05)*
----------------------------

 * Module loading now requires code generation via the 'dagger-compiler' artifact.
 * Allow multiple contributions to Set binding via `Provides.Type.SET_VALUES`.
 * Request classloading from the classloader of the requesting object, not the current thread's
   context classloader.
 * Cache class loading at the root injector to reduce costs of loading adapters.
 * Fix: Primitive array types are no longer incorrectly changed to their boxed type.
 * Update JavaWriter to 2.1.1.


Version 1.0.1 *(2013-06-03)*
----------------------------

 * Explicitly forbid declaring `@Inject` on a class type (e.g., `@Inject class Foo {}`).
 * Update JavaWriter to 1.0.5.


Version 1.0.0 *(2013-05-07)*
----------------------------

Initial release.
