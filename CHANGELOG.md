Change Log
==========

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
