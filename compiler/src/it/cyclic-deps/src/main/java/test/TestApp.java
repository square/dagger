/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import javax.inject.Inject;

class TestApp implements Runnable {
  
  static class Foo {
    @Inject Foo(@SuppressWarnings("unused") Bar b) { }
  }
  
  static class Bar {
    @Inject Bar(@SuppressWarnings("unused") Blah b) { }
  }
  
  static class Blah {
    @Inject Blah(@SuppressWarnings("unused") Foo f) { }
  }
  
  static class EntryPoint {
    @Inject Foo f;
  }

  @Module(entryPoints = EntryPoint.class)
  static class TestModule {
    
  }
  
  static class A { }
  static class B { }
  static class C { }
  static class D { }
  @Module(entryPoints = D.class)
  static class CyclicModule {
    @Provides A a(@SuppressWarnings("unused") D d) { }
    @Provides B b(@SuppressWarnings("unused") A a) { }
    @Provides C c(@SuppressWarnings("unused") B b) { }
    @Provides D d(@SuppressWarnings("unused") C c) { }
  }
}
