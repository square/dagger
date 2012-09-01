/*
 * Copyright (C) 2012 Google Inc.
 * Copyright (C) 2012 Square Inc.
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
package com.squareup.objectgraph;

import java.util.Set;
import javax.inject.Inject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of injection of Lazy<T> bindings.
 */
public final class SetBindingTest {
  @Test public void multiValueBindings_SingleModule() {
    class TestEntryPoint {
      @Inject Set<String> strings;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Element String provideFirstString() { return "string1"; }
      @Provides @Element String provideSecondString() { return "string2"; }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(2, ep.strings.size());
    assertTrue(ep.strings.contains("string1"));
    assertTrue(ep.strings.contains("string2"));
  }

  @Test public void multiValueBindings_MultiModule() {
    class TestEntryPoint {
      @Inject Set<String> strings;
    }

    @Module
    class TestChildModule {
      @Provides @Element String provideSecondString() { return "string2"; }
    }

    @Module(entryPoints = TestEntryPoint.class, children = TestChildModule.class)
    class TestModule {
      @Provides @Element String provideFirstString() { return "string1"; }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule(), new TestChildModule());
    assertEquals(2, ep.strings.size());
    assertTrue(ep.strings.contains("string1"));
    assertTrue(ep.strings.contains("string2"));
  }

  private <T> T injectWithModule(T ep, Object ... modules) {
    // TODO(cgruber): Make og.inject(foo) return foo properly.
    ObjectGraph og = ObjectGraph.get(modules);
    og.inject(ep);
    return ep;
  }

}
