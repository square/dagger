package coffee;

import com.squareup.objectgraph.Module;
import com.squareup.objectgraph.Provides;

@Module(complete = false)
class PumpModule {
  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }
}
