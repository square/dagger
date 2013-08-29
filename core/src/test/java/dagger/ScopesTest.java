// Copyright 2013 Square, Inc.
package dagger;

import dagger.internal.TestingLoader;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

@RunWith(JUnit4.class)
public class ScopesTest {
  @Scope @Documented @Retention(RUNTIME)
  public @interface SerialScope {
  }

  @Test
  public void customScopeValueIsProvidedOnce() {
    class TestEntryPoint {
      @Inject Provider<Integer> integerProvider;
    }

    @Module(scope = SerialScope.class, injects = TestEntryPoint.class) class SerialModule {
      int serial;

      @Provides @SerialScope Integer provideSerial() {
        return serial++;
      }
    }

    TestEntryPoint testEntryPoint = new TestEntryPoint();
    ObjectGraph.createWith(new TestingLoader(), new SerialModule()).inject(testEntryPoint);

    assertThat(testEntryPoint.integerProvider.get()).isZero();
    assertThat(testEntryPoint.integerProvider.get()).isZero();
  }

  @Test
  public void scopesAreIndependent() {
    class TestEntryPoint {
      @Inject Provider<Integer> integerProvider;
    }

    final AtomicInteger serial = new AtomicInteger();

    @Module(scope = SerialScope.class, injects = TestEntryPoint.class) class SerialScopeModule {
      @Provides @SerialScope Integer provideSerial() {
        return serial.getAndIncrement();
      }
    }

    ObjectGraph rootGraph = ObjectGraph.createWith(new TestingLoader());

    TestEntryPoint able = new TestEntryPoint();

    rootGraph.plus(new SerialScopeModule()).inject(able);
    assertThat(able.integerProvider.get()).isZero();
    assertThat(able.integerProvider.get()).isZero();

    TestEntryPoint baker = new TestEntryPoint();
    rootGraph.plus(new SerialScopeModule()).inject(baker);
    assertThat(baker.integerProvider.get()).isEqualTo(1);
    assertThat(baker.integerProvider.get()).isEqualTo(1);

    assertThat(able.integerProvider.get()).isZero();
    assertThat(baker.integerProvider.get()).isEqualTo(1);
  }

  @Test
  public void childrenInheritParentScope() {
    class TestEntryPoint {
      @Inject Provider<Integer> integerProvider;
    }

    final AtomicInteger serial = new AtomicInteger();

    @Module(scope = SerialScope.class, injects = TestEntryPoint.class) class SerialScopeModule {
      @Provides @SerialScope Integer provideSerial() {
        return serial.getAndIncrement();
      }
    }

    ObjectGraph rootGraph = ObjectGraph.createWith(new TestingLoader());

    TestEntryPoint parent = new TestEntryPoint();

    ObjectGraph parentGraph = rootGraph.plus(new SerialScopeModule());
    parentGraph.inject(parent);
    assertThat(parent.integerProvider.get()).isZero();
    assertThat(parent.integerProvider.get()).isZero();

    TestEntryPoint child = new TestEntryPoint();
    ObjectGraph childGraph = parentGraph.plus(new SerialScopeModule());
    childGraph.inject(child);
    assertThat(child.integerProvider.get()).isZero();
    assertThat(child.integerProvider.get()).isZero();

    assertThat(serial.get()).isEqualTo(1);
  }

  @Test
  public void singletonDisallowedByScope() {
    @Module(scope = SerialScope.class) class RootModule {

      @Provides @SerialScope Integer provideSerial() {
        throw new UnsupportedOperationException();
      }

      @Provides @Singleton String provideSingleton() {
        throw new UnsupportedOperationException();
      }
    }

    try {
      ObjectGraph.createWith(new TestingLoader(), new RootModule());
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @SerialScope static class Counter {
    private int serial;

    @Inject Counter() {
    }

    public int getAndIncrement() {
      return serial++;
    }
  }

  @Test
  public void classCanDeclareScope() {

    class TestEntryPoint {
      @Inject Counter counter;
    }

    @Module(scope = SerialScope.class, injects = TestEntryPoint.class) class RootModule {
    }

    TestEntryPoint testEntryPoint = new TestEntryPoint();
    ObjectGraph.createWith(new TestingLoader(), new RootModule()).inject(testEntryPoint);

    assertThat(testEntryPoint.counter.getAndIncrement()).isZero();
    assertThat(testEntryPoint.counter.getAndIncrement()).isZero();
  }

  @Test
  public void classDeclaredScopeIsErrorWithoutMatchingModule() {

    class TestEntryPoint {
      @SuppressWarnings("UnusedDeclaration") @Inject Counter counter;
    }

    @Module(injects = TestEntryPoint.class) class RootModule {
    }

    try {
      ObjectGraph.createWith(new TestingLoader(), new RootModule());
      fail();
    } catch (IllegalStateException expected) {
    }
  }
}
