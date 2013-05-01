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

import dagger.ObjectGraph;
import dagger.Module;
import javax.inject.Inject;
import javax.inject.Singleton;

class TestApp implements Runnable {
  @Inject A a;

  @Override public void run() {
    a.doit();
  }

  public static void main(String[] args) {
    ObjectGraph.create(new TestModule()).get(TestApp.class).run();
  }
  
  @Module(injects = { TestApp.class })
  static class TestModule {}

  @Singleton
  static class A {
    @Inject A() {}
    public void doit() {};
  }
}
