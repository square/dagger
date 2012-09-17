package coffee;

import dagger.Module;
import dagger.Provides;

@Module(complete = false)
class PumpModule {
  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
