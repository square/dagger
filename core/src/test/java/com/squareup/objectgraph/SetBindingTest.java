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

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of injection of Set-based bindings using {@code @Element} with {@code @Provider}.
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

  @Test public void multiValueBindings_WithSingletonAndDefaultValues() {
    class TestEntryPoint {
      @Inject Set<Object> objects1;
      @Inject Set<Object> objects2;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Element @Singleton Object provideSingleObject() { return new Object(); }
      @Provides @Element Object provideObjects() { return new Object(); }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    Set<Object> set1 = new HashSet<Object>();
    set1.addAll(ep.objects1);
    assertEquals(2, set1.size());
    Set<Object> set2 = new HashSet<Object>();
    set2.addAll(ep.objects2);
    assertEquals(2, set2.size());
    assertThat(set1).isNotEqualTo(set2);
    assertTrue(set1.retainAll(set2));
    assertEquals(1, set1.size());
 }

  @Test public void multiValueBindings_WithQualifiers() {
    class TestEntryPoint {
      @Inject Set<String> strings;
      @Inject @Named("foo") Set<String> fooStrings;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides @Element String provideString1() { return "string1"; }
      @Provides @Element String provideString2() { return "string2"; }
      @Provides @Element @Named("foo") String provideString3() { return "string3"; }
      @Provides @Element @Named("foo") String provideString4() { return "string4"; }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(2, ep.strings.size());
    assertEquals(2, ep.fooStrings.size());
    assertTrue(ep.strings.contains("string1"));
    assertTrue(ep.strings.contains("string2"));
    assertTrue(ep.fooStrings.contains("string3"));
    assertTrue(ep.fooStrings.contains("string4"));
 }

  private <T> T injectWithModule(T ep, Object ... modules) {
    // TODO(cgruber): Make og.inject(foo) return foo properly.
    ObjectGraph og = ObjectGraph.get(modules);
    og.inject(ep);
    return ep;
  }

}
