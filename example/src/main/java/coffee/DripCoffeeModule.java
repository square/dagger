package coffee;

import com.squareup.objectgraph.Module;
import com.squareup.objectgraph.Provides;
import javax.inject.Singleton;

@Module(
    entryPoints = CoffeeApp.class
)
class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }
  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
