package coffee;

import javax.inject.Inject;

import dagger.EntryPoint;
import dagger.ObjectGraph;
import dagger.generated.EntryPointsModule;

@EntryPoint
public class CoffeeApp implements Runnable {
  @Inject CoffeeMaker coffeeMaker;

  @Override public void run() {
    coffeeMaker.brew();
  }

  public static void main(String[] args) {
    ObjectGraph objectGraph = ObjectGraph.create(new DripCoffeeModule(), EntryPointsModule.class);
    CoffeeApp coffeeApp = objectGraph.get(CoffeeApp.class);
    coffeeApp.run();
  }
}
