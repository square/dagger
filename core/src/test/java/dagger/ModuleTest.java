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
package dagger;

import java.io.Serializable;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public final class ModuleTest {
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

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule());
    TestEntryPoint entryPoint = objectGraph.get(TestEntryPoint.class);
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

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule());
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

    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithBinding.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("injected");
  }

  @Module(includes = ModuleWithBinding.class)
  static class ModuleWithChildModule {
  }

  @Test public void childModuleWithChildModule() {

    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithChildModule.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule());
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
      ObjectGraph.create(new TestModule());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void childModuleWithManualConstruction() {

    @Module(
        entryPoints = TestEntryPoint.class,
        includes = ModuleWithConstructor.class
    )
    class TestModule {
    }

    ObjectGraph objectGraph = ObjectGraph.create(new ModuleWithConstructor("a"), new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.s).isEqualTo("a");
  }

  static class A {}

  static class B { @Inject A a; }

  @Module(entryPoints = A.class) public static class TestModuleA {
    @Provides A a() { return new A(); }
  }

  @Module(includes = TestModuleA.class, entryPoints = B.class) public static class TestModuleB {}

  @Test public void autoInstantiationOfModules() {
    // Have to make these non-method-scoped or instantiation errors occur.
    ObjectGraph objectGraph = ObjectGraph.create(TestModuleA.class);
    assertThat(objectGraph.get(A.class)).isNotNull();
  }

  @Test public void autoInstantiationOfIncludedModules() {
    // Have to make these non-method-scoped or instantiation errors occur.
    ObjectGraph objectGraph = ObjectGraph.create(new TestModuleB()); // TestModuleA auto-created.
    assertThat(objectGraph.get(A.class)).isNotNull();
    assertThat(objectGraph.get(B.class).a).isNotNull();
  }

  static class ModuleMissingModuleAnnotation {}

  @Module(includes = ModuleMissingModuleAnnotation.class)
  static class ChildModuleMissingModuleAnnotation {}

  @Test(expected = IllegalArgumentException.class)
  public void childModuleMissingModuleAnnotation() {
    ObjectGraph.create(new ChildModuleMissingModuleAnnotation());
  }

  @Module
  static class ThreadModule extends Thread {}

  @Test public void moduleExtendingClassThrowsException() {
    try {
      ObjectGraph.create(new ThreadModule());
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).startsWith("Modules must not extend from other classes: ");
    }
  }

  @Module(entryPoints = Serializable.class)
  public static class InterfaceModule1 {}

  @Test public void interfaceEntryPointsDisallowed() {
    try {
      ObjectGraph.create(new InterfaceModule1());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage(
          "Non-class java.io.Serializable not allowed as entry point on dagger.ModuleTest$InterfaceModule1.");
    }
  }

  @Module(staticInjections = Serializable.class)
  public static class InterfaceModule2 {}

  @Test public void interfaceStaticInjectionsDisallowed() {
    try {
      ObjectGraph.create(new InterfaceModule2());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage(
          "Non-class java.io.Serializable not allowed as static injection on dagger.ModuleTest$InterfaceModule2.");
    }
  }

  @Module(includes = Serializable.class)
  public static class InterfaceModule3 {}

  @Test public void interfaceIncludesDisallowed() {
    try {
      ObjectGraph.create(new InterfaceModule3());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage(
          "Non-class java.io.Serializable not allowed as includes on dagger.ModuleTest$InterfaceModule3.");
    }
  }

  @Module(addsTo = Serializable.class)
  public static class InterfaceModule4 {}

  @Test public void interfaceAddsToDisallowed() {
    try {
      ObjectGraph.create(new InterfaceModule4());
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage(
          "Non-class java.io.Serializable not allowed as adds to on dagger.ModuleTest$InterfaceModule4.");
    }
  }
}
