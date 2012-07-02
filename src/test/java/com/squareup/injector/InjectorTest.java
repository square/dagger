/*
 * Copyright (C) 2010 Google Inc.
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Jesse Wilson
 */
@SuppressWarnings("unused")
public final class InjectorTest {

  @Injector
  public static class GInjector extends AbstractInjector<GInjector> {
    @Inject Provider<G> gProvider;
  }

  @Test public void basicInjection() {
    GInjector gInjector = new GInjector().inject(new Object() {
      @Provides E provideE(F f) {
        return new E(f);
      }

      @Provides F provideF() {
        return new F();
      }
    });
    G g = gInjector.gProvider.get();

    assertThat(g.a).isNotNull();
    assertThat(g.b).isNotNull();
    assertThat(g.c).isNotNull();
    assertThat(g.d).isNotNull();
    assertThat(g.e).isNotNull();
    assertThat(g.e.f).isNotNull();
  }

  @Injector
  public static class GMembersInjector extends AbstractInjector<GMembersInjector> {
    @Inject MembersInjector<G> gInjector;
  }

  @Test public void memberInjection() {
    GMembersInjector membersInjectors = new GMembersInjector().inject(new Object() {
      @Provides E provideE(F f) {
        return new E(f);
      }

      @Provides F provideF() {
        return new F();
      }
    });

    G g = new G(new C(), new D());
    membersInjectors.gInjector.injectMembers(g);
    assertThat(g.a).isNotNull();
    assertThat(g.b).isNotNull();
    assertThat(g.c).isNotNull();
    assertThat(g.d).isNotNull();
    assertThat(g.e).isNotNull();
    assertThat(g.e.f).isNotNull();
  }

  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject B() {}
  }

  @Singleton
  static class C {
    @Inject C() {}
  }

  @Singleton
  static class D {
    @Inject D() {}
  }

  static class E {
    F f;
    E(F f) {
      this.f = f;
    }
  }

  static class F {}

  static class G {
    @Inject A a;
    @Inject B b;
    C c;
    D d;
    @Inject E e;
    @Inject G(C c, D d) {
      this.c = c;
      this.d = d;
    }
  }

  @Test public void providerInjection() {
    AProviderInjector aProviderInjector = new AProviderInjector().inject();
    assertThat(aProviderInjector.aProvider.get()).isNotNull();
    assertThat(aProviderInjector.aProvider.get()).isNotNull();
    assertThat(aProviderInjector.aProvider.get()).isNotSameAs(aProviderInjector.aProvider.get());
  }

  @Injector
  public static class AProviderInjector extends AbstractInjector<AProviderInjector> {
    @Inject Provider<A> aProvider;
  }

  @Test public void singletons() {
    FiInjector fiInjector = new FiInjector().inject(new Object() {
      @Provides @Singleton F provideK() {
        return new F();
      }
    });
    assertThat(fiInjector.fProvider.get()).isSameAs(fiInjector.fProvider.get());
    assertThat(fiInjector.iProvider.get()).isSameAs(fiInjector.iProvider.get());
  }

  @Singleton
  static class I {
    @Inject I() {}
  }

  @Injector
  public static class FiInjector extends AbstractInjector<FiInjector> {
    @Inject Provider<F> fProvider;
    @Inject Provider<I> iProvider;
  }

  @Test public void bindingAnnotations() {
    final A one = new A();
    final A two = new A();

    NamedInjector k = new NamedInjector().inject(new Object() {
      @Provides @Named("one") A getOne() {
        return one;
      }
      @Provides @Named("two") A getTwo() {
        return two;
      }
    });

    assertThat(k.a).isNotNull();
    assertThat(one).isSameAs(k.aOne);
    assertThat(two).isSameAs(k.aTwo);
  }

  @Injector
  public static class NamedInjector extends AbstractInjector<NamedInjector> {
    @Inject A a;
    @Inject @Named("one") A aOne;
    @Inject @Named("two") A aTwo;
  }

  @Test public void singletonBindingAnnotationAndProvider() {
    final AtomicReference<A> a1 = new AtomicReference<A>();
    final AtomicReference<A> a2 = new AtomicReference<A>();

    LInjector lInjector = new LInjector().inject(new Object() {
      @Provides @Singleton @Named("one") F provideF(Provider<A> aProvider) {
        a1.set(aProvider.get());
        a2.set(aProvider.get());
        return new F();
      }
    });
    lInjector.lProvider.get();

    assertThat(a1.get()).isNotNull();
    assertThat(a2.get()).isNotNull();
    assertThat(a1.get()).isNotSameAs(a2.get());
    L l = lInjector.lProvider.get();
    assertThat(l).isSameAs(l.lProvider.get());
  }

  @Injector
  public static class LInjector extends AbstractInjector<LInjector> {
    @Inject Provider<L> lProvider;
  }

  @Singleton
  public static class L {
    @Inject @Named("one") F f;
    @Inject Provider<L> lProvider;
  }

  @Test public void singletonInGraph() {
    MultipleInjector multipleInjector = new MultipleInjector().inject(new Object() {
      @Provides @Singleton F provideF() {
        return new F();
      }
    });

    assertThat(multipleInjector.f1).isSameAs(multipleInjector.f2);
    assertThat(multipleInjector.f1).isSameAs(multipleInjector.n1.f1);
    assertThat(multipleInjector.f1).isSameAs(multipleInjector.n1.f2);
    assertThat(multipleInjector.f1).isSameAs(multipleInjector.n2.f1);
    assertThat(multipleInjector.f1).isSameAs(multipleInjector.n2.f2);
    assertThat(multipleInjector.f1).isSameAs(multipleInjector.n1.fProvider.get());
    assertThat(multipleInjector.f1).isSameAs(multipleInjector.n2.fProvider.get());
  }

  @Injector
  public static class MultipleInjector extends AbstractInjector<MultipleInjector> {
    @Inject N n1;
    @Inject N n2;
    @Inject F f1;
    @Inject F f2;
  }

  public static class N {
    @Inject F f1;
    @Inject F f2;
    @Inject Provider<F> fProvider;
  }

  @Test public void noJitBindingsForAnnotations() {
    try {
      new AnnotatedJitInjector().inject();
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Injector
  public static class AnnotatedJitInjector extends AbstractInjector<AnnotatedJitInjector> {
    @Inject @Named("a") A a;
  }

  @Test public void subclasses() {
    QInjector qInjector = new QInjector().inject(new Object() {
      @Provides F provideF() {
        return new F();
      }
    });
    assertThat(qInjector.q.f).isNotNull();
  }

  @Injector
  public static class QInjector extends AbstractInjector<QInjector> {
    @Inject Q q;
  }

  public static class P {
    @Inject F f;
  }

  public static class Q extends P {
    @Inject Q() {}
  }

  @Test public void singletonsAreNotEager() {
    final AtomicBoolean sInjected = new AtomicBoolean();

    R.injected = false;
    AProviderInjector aProviderInjector = new AProviderInjector().inject(new Object() {
      @Provides F provideF(R r) {
        return new F();
      }

      @Provides @Singleton S provideS() {
        sInjected.set(true);
        return new S();
      }
    });

    assertThat(R.injected).isFalse();
    assertThat(sInjected.get()).isFalse();
  }

  @Singleton
  static class R {
    static boolean injected = false;
    @Inject R() {
      injected = true;
    }
  }

  static class S {}

  @Test public void providerMethodsConflict() {
    try {
      new GInjector().inject(new Object() {
        @Provides A provideA1() {
          throw new AssertionError();
        }

        @Provides A provideA2() {
          throw new AssertionError();
        }
      });
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void singletonsInjectedOnlyIntoProviders() {
    AProviderInjector h = new AProviderInjector().inject(new Object() {
      @Provides @Singleton A provideA() {
        return new A();
      }
    });
    assertThat(h.aProvider.get()).isSameAs(h.aProvider.get());
  }

  @Test public void moduleOverrides() {
    Object overrides = new Object() {
      @Provides F provideF() {
        return new F();
      }
    };

    EProviderInjector injector = new EProviderInjector().inject(overrides);
    E e = injector.eProvider.get();
    assertThat(e).isNotNull();
    assertThat(e.f).isNotNull();
  }

  @Injector(modules = { BaseModule.class })
  public static class EProviderInjector extends AbstractInjector<EProviderInjector> {
    @Inject Provider<E> eProvider;
  }

  static class BaseModule {
    @Provides F provideF() {
      throw new AssertionError();
    }
    @Provides E provideE(F f) {
      return new E(f);
    }
  }

  public static abstract class AbstractInjector<T> {
    @SuppressWarnings("unchecked")
    public T inject(Object... modules) {
      DependencyGraph.get(this, modules).inject(this);
      return (T) this;
    }
  }
}
