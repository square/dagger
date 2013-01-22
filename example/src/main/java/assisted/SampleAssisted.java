package assisted;

import dagger.Assisted;
import dagger.Factory;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import javax.inject.Inject;

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 27.12.12
 * Time: 19:03
 */
public class SampleAssisted {
  public static void main(String[] args) {
    ObjectGraph.create(new AssistedModule()).get(A.class).run();
  }

  static class A implements Runnable {
    @Inject
    BFactory b;

    @Override
    public void run() {
      b.newB("This is B").run();
    }
  }

  static abstract class B implements Runnable {

  }

  interface BFactory {

    B newB(String name);
  }

  @Module(entryPoints = A.class)
  static class AssistedModule {
    @Provides
    @Factory(BFactory.class)
    public B provideB(BImpl b) {
      return b;
    }
  }

  static class BImpl extends B {

    @Inject
    @Assisted
    String name;

    @Inject
    BImpl() {
    }

    @Override
    public void run() {
      System.out.println("Name: " + name);
    }
  }
}
