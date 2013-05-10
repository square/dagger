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
import java.io.IOException;
import java.lang.Override;

class TestApp implements Runnable {

  @Inject String string;

  @Override public void run() {
    // Yay! \o/
  }

  public static void main(String[] args) {
    ObjectGraph.create(new TestModule()).get(TestApp.class).run();
  }

  @Module(injects = TestApp.class)
  static class TestModule {

    @Provides String string() throws IOException {
      return "string";
    }
  }
}
