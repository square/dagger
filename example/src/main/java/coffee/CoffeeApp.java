package coffee;

import dagger.ObjectGraph;
import javax.inject.Inject;

class CoffeeApp implements Runnable {
  @Inject CoffeeMaker coffeeMaker;

  @Override public void run() {
    coffeeMaker.brew();
  }

  public static void main(String[] args) {
    ObjectGraph objectGraph = ObjectGraph.get(new DripCoffeeModule());
    CoffeeApp coffeeApp = new CoffeeApp();
    objectGraph.inject(coffeeApp);
    coffeeApp.run();
  }
}
