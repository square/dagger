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
  @Test public void basicInjection() {
    G g = new Injector().inject(G.class, new Object() {
      @Provides E provideE(F f) {
        return new E(f);
      }
      @Provides F provideF() {
        return new F();
      }
    });

    assertThat(g.a).isNotNull();
    assertThat(g.b).isNotNull();
    assertThat(g.c).isNotNull();
    assertThat(g.d).isNotNull();
    assertThat(g.e).isNotNull();
    assertThat(g.e.f).isNotNull();
  }

  @Test public void memberInjection() {
    MembersInjectors membersInjectors = new Injector().inject(MembersInjectors.class, new Object() {
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

  public static class MembersInjectors {
    @Inject MembersInjector<G> gInjector;
  }

  @Test public void providerInjection() {
    H h = new Injector().inject(H.class);
    assertThat(h.aProvider.get()).isNotNull();
    assertThat(h.aProvider.get()).isNotNull();
    assertThat(h.aProvider.get()).isNotSameAs(h.aProvider.get());
  }

  public static class H {
    @Inject Provider<A> aProvider;
  }

  @Test public void singletons() {
    J j = new Injector().inject(J.class, new Object() {
      @Provides @Singleton F provideK() {
        return new F();
      }
    });
    assertThat(j.fProvider.get()).isSameAs(j.fProvider.get());
    assertThat(j.iProvider.get()).isSameAs(j.iProvider.get());
  }

  @Singleton
  static class I {
    @Inject I() {}
  }

  static class J {
    @Inject Provider<F> fProvider;
    @Inject Provider<I> iProvider;
    @Inject J() {}
  }

  @Test public void bindingAnnotations() {
    final A one = new A();
    final A two = new A();

    K k = new Injector().inject(K.class, new Object() {
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

  public static class K {
    @Inject A a;
    @Inject @Named("one") A aOne;
    @Inject @Named("two") A aTwo;
  }

  @Test public void singletonBindingAnnotationAndProvider() {
    final AtomicReference<A> a1 = new AtomicReference<A>();
    final AtomicReference<A> a2 = new AtomicReference<A>();

    L l = new Injector().inject(L.class, new Object() {
      @Provides @Singleton @Named("one") F provideF(Provider<A> aProvider) {
        a1.set(aProvider.get());
        a2.set(aProvider.get());
        return new F();
      }
    });

    assertThat(a1.get()).isNotNull();
    assertThat(a2.get()).isNotNull();
    assertThat(a1.get()).isNotSameAs(a2.get());
    assertThat(l).isSameAs(l.lProvider.get());
  }

  @Singleton
  public static class L {
    @Inject @Named("one") F f;
    @Inject Provider<L> lProvider;
  }

  @Test public void singletonInGraph() {
    M m = new Injector().inject(M.class, new Object() {
      @Provides @Singleton F provideF() {
        return new F();
      }
    });

    assertThat(m.f1).isSameAs(m.f2);
    assertThat(m.f1).isSameAs(m.n1.f1);
    assertThat(m.f1).isSameAs(m.n1.f2);
    assertThat(m.f1).isSameAs(m.n2.f1);
    assertThat(m.f1).isSameAs(m.n2.f2);
    assertThat(m.f1).isSameAs(m.n1.fProvider.get());
    assertThat(m.f1).isSameAs(m.n2.fProvider.get());
  }

  public static class M {
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
      new Injector().inject(O.class);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  public static class O {
    @Inject @Named("a") A a;
  }

  @Test public void subclasses() {
    Q q = new Injector().inject(Q.class, new Object() {
      @Provides F provideF() {
        return new F();
      }
    });
    assertThat(q.f).isNotNull();
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
    new Injector().inject(A.class, new Object() {
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
      new Injector().inject(G.class, new Object() {
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
    H h = new Injector().inject(H.class, new Object() {
      @Provides @Singleton A provideA() {
        return new A();
      }
    });
    assertThat(h.aProvider.get()).isSameAs(h.aProvider.get());
  }

  @Test public void moduleOverrides() {
    Object base = new Object() {
      @Provides F provideF() {
        throw new AssertionError();
      }
      @Provides E provideE(F f) {
        return new E(f);
      }
    };

    Object overrides = new Object() {
      @Provides F provideF() {
        return new F();
      }
    };

    E e = new Injector().inject(E.class, Modules.override(base, overrides));
    assertThat(e).isNotNull();
    assertThat(e.f).isNotNull();
  }
}
