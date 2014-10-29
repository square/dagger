package coffee;

import dagger.Component;

public class CoffeeApp {
  @Component(modules = { DripCoffeeModule.class })
  public interface Coffee {
    CoffeeMaker maker();
  }

  public static void main(String[] args) {
    Coffee coffee = Dagger_CoffeeApp$Coffee.builder().build();
    coffee.maker().brew();
  }
}
