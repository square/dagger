/*
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
package com.squareup.injector;

import javax.inject.Inject;
import org.junit.Test;

import static org.junit.Assert.fail;

public final class ProblemDetectorTest {
  @Test public void circularDependenciesDetected() {
    class TestEntryPoint {
      @Inject Rock rock;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides Object unused() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.get(new TestModule());
    try {
      graph.detectProblems();
      fail();
    } catch (RuntimeException expected) {
    }
  }

  static class Rock {
    @Inject Scissors scissors;
  }

  static class Scissors {
    @Inject Paper paper;
  }

  static class Paper {
    @Inject Rock rock;
  }
}
