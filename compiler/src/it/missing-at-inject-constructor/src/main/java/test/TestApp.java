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

import dagger.Module;

import dagger.ObjectGraph;
import javax.inject.Inject;

class TestApp implements Runnable {
  @Inject Dependency dep;

  @Override public void run() {
    dep.doit();
  }

  public static void main(String[] args) {
    ObjectGraph.create(new TestModule()).get(TestApp.class).run();
  }
  
  static class Dependency {
    // missing @Inject Dependency() {}
    public void doit() { throw AssertionError(); };
  }
  
  @Module(injects = TestApp.class)
  static class TestModule {
    /* missing */ // @Provides Dependency a() { return new Dependency(); }
  }
}
