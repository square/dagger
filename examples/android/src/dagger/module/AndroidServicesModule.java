package dagger.module;

import android.content.Context;
import android.content.res.Resources;
import android.location.LocationManager;
import android.view.LayoutInflater;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module(complete = false)
public class AndroidServicesModule {

  @Provides @Singleton LocationManager provideLocationManager(Context context) {
    return (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  @Provides @Singleton LayoutInflater provideLayoutInflater(Context context) {
    return (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  @Provides @Singleton Resources provideResources(Context context) {
    return context.getResources();
  }

}
