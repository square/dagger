package dagger.assisted;

import dagger.*;
import junit.framework.Assert;
import org.junit.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 12.12.12
 * Time: 18:29
 */
public final class FieldAssistedTest {
  @Test
  public void simpleAssistedTest() {
    AFactory factory = ObjectGraph.create(TestModule.class).get(AFactory.class);
    A a = factory.create(new C("the C"));
    Assert.assertEquals("the C", a.getC().getName());
    Assert.assertEquals("b", a.getB().getName());
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

    @Inject
    private B b;

    @Inject
    @Assisted
    private C c;

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
