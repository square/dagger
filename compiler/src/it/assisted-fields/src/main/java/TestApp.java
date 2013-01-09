package coffee;

import dagger.Assisted;
import dagger.Factory;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import javax.inject.Inject;
import java.lang.Override;
import java.lang.Runnable;
import java.lang.String;

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 27.12.12
 * Time: 19:03
 */
public class TestApp implements Runnable {

  @Override
  public void run() {
    ObjectGraph.create(new AssistedModule()).get(A.class).run();
  }

  static class A implements Runnable {
    @Inject
    BFactory b;

    @Override
    public void run() {
      b.newB("This is B", "a", "b").run();
    }
  }

  static abstract class B implements Runnable {

  }

  interface BFactory {

    B newB(String name, @Assisted("a") String a, @Assisted("b") String b);
  }

  @Module(entryPoints = A.class)
  static class AssistedModule {
    @Provides
    @Factory(BFactory.class)
    public B provideB(BImpl2 b) {
      return b;
    }
  }

  static class BImpl2 extends BImpl {
    @Inject
    @Assisted("a")
    String a;

    String b;

    @Inject
    BImpl2(@Assisted("b") String b) {
      this.b = b;
    }

    @Override
    public void run() {
      // Do nothing
    }
  }

  static abstract class BImpl extends B {

    @Inject
    @Assisted
    String name;
  }
}
