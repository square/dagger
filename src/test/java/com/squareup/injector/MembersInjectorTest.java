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
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests MembersInjector injection, and how injector features interact with
 * types unconstructable types (types that support members injection only).
 */
@SuppressWarnings("unused")
public final class MembersInjectorTest {
  @Test public void injectMembers() {
    InjectInjectable injector = new InjectInjectable().inject();
    Injectable injectable = new Injectable();
    injector.membersInjector.injectMembers(injectable);
    assertThat(injectable.injected).isEqualTo("injected");
  }

  static class Injectable {
    @Inject String injected;
  }

  @Injector(modules = StringModule.class)
  static class InjectInjectable extends AbstractInjector<InjectInjectable> {
    @Inject MembersInjector<Injectable> membersInjector;
  }

  static class Unconstructable {
    final String constructor;
    @Inject String injected;
    Unconstructable(String constructor) {
      this.constructor = constructor;
    }
  }

  @Test public void membersInjectorOfUnconstructableIsOkay() {
    UnconstructableMembersInjector injector = new UnconstructableMembersInjector().inject();
    Unconstructable object = new Unconstructable("constructor");
    injector.membersInjector.injectMembers(object);
    assertThat(object.constructor).isEqualTo("constructor");
    assertThat(object.injected).isEqualTo("injected");
  }

  @Injector(modules = StringModule.class)
  static class UnconstructableMembersInjector
      extends AbstractInjector<UnconstructableMembersInjector> {
    @Inject MembersInjector<Unconstructable> membersInjector;
  }

  @Test public void injectionOfUnconstructableFails() {
    try {
      ObjectGraph.get(new UnconstructableInjector());
      fail();
    } catch (Exception expected) {
    }
  }

  @Injector(modules = StringModule.class)
  static class UnconstructableInjector {
    @Inject Unconstructable unconstructable;
  }

  @Test public void instanceInjectionOfMembersOnlyType() {
    try {
      ObjectGraph.get(new UnconstructableProviderInjector());
      fail();
    } catch (Exception expected) {
    }
  }

  @Injector(modules = StringModule.class)
  static class UnconstructableProviderInjector {
    @Inject Provider<Unconstructable> provider;
  }

  @Test public void rejectUnconstructableSingleton() {
    try {
      ObjectGraph.get(new UnconstructableSingletonInjector());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Injector(modules = StringModule.class)
  static class UnconstructableSingletonInjector
      extends AbstractInjector<UnconstructableSingletonInjector> {
    @Inject MembersInjector<UnconstructableSingleton> membersInjector;
  }

  @Singleton
  static class UnconstructableSingleton {
    final String constructor;
    @Inject String injected;
    UnconstructableSingleton(String constructor) {
      this.constructor = constructor;
    }
  }

  class NonStaticInner {
    @Inject String injected;
  }

  @Injector(modules = StringModule.class)
  static class NonStaticInnerMembersInjector
      extends AbstractInjector<NonStaticInnerMembersInjector> {
    @Inject MembersInjector<NonStaticInner> membersInjector;
  }

  @Test public void membersInjectorOfNonStaticInnerIsOkay() {
    NonStaticInnerMembersInjector injector = new NonStaticInnerMembersInjector().inject();
    NonStaticInner nonStaticInner = new NonStaticInner();
    injector.membersInjector.injectMembers(nonStaticInner);
    assertThat(nonStaticInner.injected).isEqualTo("injected");
  }

  @Injector(modules = StringModule.class)
  static class NonStaticInnerInjector {
    @Inject NonStaticInner nonStaticInner;
  }

  @Test public void instanceInjectionOfNonStaticInnerFailsEarly() {
    try {
      ObjectGraph.get(new NonStaticInnerInjector());
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  public static abstract class AbstractInjector<T> {
    @SuppressWarnings("unchecked")
    public T inject(Object... modules) {
      ObjectGraph.get(this, modules).inject(this);
      return (T) this;
    }
  }

  static class StringModule {
    @Provides String provideString() {
      return "injected";
    }
  }
}