/*
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

import javax.inject.Inject;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

@SuppressWarnings("unused")
public final class ModuleIncludesTest {
  static class TestEntryPoint {
    @Inject String s;
  }

  @Module(entryPoints = TestEntryPoint.class)
  static class ModuleWithEntryPoint {
  }

  @Test public void childModuleWithEntryPoint() {
    @Module(includes = ModuleWithEntryPoint.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    ObjectGraph objectGraph = ObjectGraph.get(new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  static class TestStaticInjection {
    @Inject static String s;
  }

  @Module(staticInjections = TestStaticInjection.class)
  static class ModuleWithStaticInjection {
  }

  @Test public void childModuleWithStaticInjection() {
    @Module(includes = ModuleWithStaticInjection.class)
    class TestModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    ObjectGraph objectGraph = ObjectGraph.get(new TestModule());
    TestStaticInjection.s = null;
    objectGraph.injectStatics();
    assertThat(TestStaticInjection.s).isEqualTo("injected");
  }

  @Module
  static class ModuleWithBinding {
    @Provides String provideString() {
      return "injected";
    }
  }

  @Test public void childModuleWithBinding() {
    class TestEntryPoint {
      @Inject String s;
    }

    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithBinding.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.get(new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  @Module(includes = ModuleWithBinding.class)
  static class ModuleWithChildModule {
  }

  @Test public void childModuleWithChildModule() {
    class TestEntryPoint {
      @Inject String s;
    }

    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithChildModule.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.get(new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  @Module
  static class ModuleWithConstructor {
    private final String value;

    ModuleWithConstructor(String value) {
      this.value = value;
    }

    @Provides String provideString() {
      return value;
    }
  }

  @Test public void childModuleMissingManualConstruction() {
    @Module(includes = ModuleWithConstructor.class)
    class TestModule {
    }

    try {
      ObjectGraph.get(new TestModule());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void childModuleWithManualConstruction() {
    class TestEntryPoint {
      @Inject String s;
    }

    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithConstructor.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.get(new ModuleWithConstructor("a"), new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("a");
  }

  // Legacy Tests //

  @Test public void childrenButNoIncludes() {
    class TestEntryPoint {
      @Inject String s;
    }
    @Module(entryPoints = TestEntryPoint.class, children = ModuleWithBinding.class)
    class TestModule {
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertThat(ep.s).isEqualTo("injected");
  }

  @Module(complete = false)
  static class ModuleWithInteger {
    @Provides Integer provideString() { return 1; }
  }

  @Test public void bothIncludesAndChildren() {
    class TestEntryPoint {
      @Inject String s;
      @Inject Integer i;
    }
    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithInteger.class,
        children = ModuleWithBinding.class)
    class TestModule {
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertThat(ep.s).isEqualTo("injected");
    assertThat(ep.i).isEqualTo(1);
  }

  private <T> T injectWithModule(T ep, Object ... modules) {
    // TODO(cgruber): Make og.inject(foo) return foo properly.
    ObjectGraph og = ObjectGraph.get(modules);
    og.inject(ep);
    return ep;
  }

}
