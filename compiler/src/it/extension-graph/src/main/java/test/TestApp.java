/*
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2012 Square, Inc.
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

import dagger.ObjectGraph;
import dagger.Module;
import javax.inject.Inject;
import javax.inject.Singleton;

class TestApp implements Runnable {
  @Inject C c;

  @Override public void run() {
    c.doit();
  }

  public static void main(String[] args) {
    ObjectGraph root = ObjectGraph.create(new RootModule());
    ObjectGraph extension = root.plus(new ExtensionModule());
    extension.get(TestApp.class).run();
  }
  
  @Module(injects = { A.class, B.class })
  static class RootModule { }

  @Module(addsTo=RootModule.class, injects = { C.class, TestApp.class })
  static class ExtensionModule { }

  @Singleton
  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject A a;
    @Inject B() {}
  }

  static class C {
    @Inject A a;
    @Inject B b;
    @Inject C() {}
    public void doit() {};
  }
}
