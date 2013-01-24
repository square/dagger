package dagger.assisted;

import dagger.*;
import junit.framework.Assert;
import org.junit.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

public final class AssistedTest {
  @Test
  public void simpleAssistedTest() {
    AFactory factory = ObjectGraph.create(TestModule.class).get(AFactory.class);
    A a = factory.create(new C("the C"));
    Assert.assertEquals("the C", a.getC().getName());
    Assert.assertEquals("b", a.getB().getName());
  }

  @Test
  public void singletonFactoryTest() {
    ObjectGraph objectGraph = ObjectGraph.create(TestModule.class);
    AFactory factory1 = objectGraph.get(AFactory.class);
    AFactory factory2 = objectGraph.get(AFactory.class);
    Assert.assertSame(factory1, factory2);
  }


  @Module(entryPoints = AFactory.class, complete = false)
  static class TestModule {

    @Provides
    @Factory(AFactory.class)
    public A provideA(A a) {
      return a;
    }

    @Provides
    @Singleton
    public B provideB(BImpl b) {
      return b;
    }

  }

  static class A {

    private B b;

    private C c;

    @Inject
    public A(B b, @Assisted C c) {
      this.b = b;
      this.c = c;
    }

    public C getC() {
      return c;
    }

    public B getB() {
      return b;
    }
  }


  static interface AFactory {

    A create(C c);

  }

  static interface B {
    String getName();
  }

  static class D {

    @Inject
    D() {
    }
  }

  static class BImpl implements B {

    @Inject
    private D d;

    @Override
    public String getName() {
      return "b";
    }
  }

  static class C {
    private String name;

    public C(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
