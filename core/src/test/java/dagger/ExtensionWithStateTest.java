/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2013 Square Inc.
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

import javax.inject.Inject;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public final class ExtensionWithStateTest {
  static class A { }

  static class B {
    @Inject A a;
  }

  @Module(
      entryPoints = A.class, // for testing
      complete = false
  )
  static class RootModule {
    final A a;
    RootModule(A a) {
      this.a = a;
    }
    @Provides A giveA() { return a; }
  }

  @Module(addsTo = RootModule.class, entryPoints = { B.class })
  static class ExtensionModule { }

  @Test public void basicExtension() {
    A a = new A();
    assertNotNull(ObjectGraph.create(new RootModule(a)).plus(new ExtensionModule()));
  }

  @Test public void basicInjection() {
    A a = new A();
    ObjectGraph root = ObjectGraph.create(new RootModule(a));
    assertThat(root.get(A.class)).isSameAs(a);

    // Extension graph behaves as the root graph would for root-ish things.
    ObjectGraph extension = root.plus(new ExtensionModule());
    assertThat(extension.get(A.class)).isSameAs(a);
    assertThat(extension.get(B.class).a).isSameAs(a);
  }

}
