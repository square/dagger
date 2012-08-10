package coffee;

import javax.inject.Inject;

class CoffeeMaker {
  @Inject Heater heater;
  @Inject Pump pump;

  public void brew() {
    heater.on();
    pump.pump();
    System.out.println(" [_]P coffee! [_]P ");
    heater.off();
  }
}
