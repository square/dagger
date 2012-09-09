package coffee;

import javax.inject.Singleton;

import com.squareup.objectgraph.Module;
import com.squareup.objectgraph.Provides;

@Module(
    entryPoints = CoffeeApp.class,
    includes = PumpModule.class
)
class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }
}
