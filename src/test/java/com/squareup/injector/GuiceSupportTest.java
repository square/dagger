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
package com.squareup.injector;

import javax.inject.Inject;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

@SuppressWarnings("unused")
public final class GuiceSupportTest {
  static class Injectable {
    @Inject String string;
  }

  @Test public void testGuiceProviderGet() {
    class TestEntryPoint {
      @Inject com.google.inject.Provider<Injectable> provider;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class StringModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.get(new StringModule()).inject(entryPoint);
    Injectable provided = entryPoint.provider.get();
    assertThat(provided.string).isEqualTo("injected");
  }

  @Test public void testGuiceMembersInjector() {
    class TestEntryPoint {
      @Inject com.google.inject.MembersInjector<Injectable> membersInjector;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class StringModule {
      @Provides String provideString() {
        return "injected";
      }
    }

    TestEntryPoint entryPoint = new TestEntryPoint();
    ObjectGraph.get(new StringModule()).inject(entryPoint);
    Injectable injectable = new Injectable();
    entryPoint.membersInjector.injectMembers(injectable);
    assertThat(injectable.string).isEqualTo("injected");
  }
}
