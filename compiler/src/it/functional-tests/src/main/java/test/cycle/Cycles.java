/*
 * Copyright (C) 2015 Google, Inc.
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
package test.cycle;

import dagger.Component;
import dagger.Lazy;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Cycle classes used for testing cyclic dependencies.
 * A <- (E <- D <- B <- C <- Provider<A>, Lazy<A>), (B <- C <- Provider<A>, Lazy<A>)
 * S <- Provider<S>, Lazy<S>
 *
 * @author Tony Bentancur
 * @since 2.0
 */

final class Cycles {
  private Cycles() {}

  static class A {
    public final B b;
    public final E e;

    @Inject
    A(E e, B b) {
      this.e = e;
      this.b = b;
    }
  }

  static class B {
    public final C c;

    @Inject
    B(C c) {
      this.c = c;
    }
  }

  static class C {
    public final Provider<A> aProvider;
    @Inject public Lazy<A> aLazy;

    @Inject
    C(Provider<A> aProvider) {
      this.aProvider = aProvider;
    }
  }

  static class D {
    public final B b;

    @Inject
    D(B b) {
      this.b = b;
    }
  }

  static class E {
    public final D d;

    @Inject
    E(D d) {
      this.d = d;
    }
  }

  static class S {
    public final Provider<S> sProvider;
    @Inject public Lazy<S> sLazy;

    @Inject
    S(Provider<S> sProvider) {
      this.sProvider = sProvider;
    }
  }

  @SuppressWarnings("dependency-cycle")
  @Component
  interface CycleComponent {
    A a();

    C c();
  }

  @SuppressWarnings("dependency-cycle")
  @Component
  interface SelfCycleComponent {
    S s();
  }
}
