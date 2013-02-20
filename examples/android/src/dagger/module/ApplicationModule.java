package dagger.module;

import android.content.Context;
import dagger.Module;
import dagger.Provides;
import dagger.activity.StabbedActivity;
import dagger.application.DaggerApplication;
import dagger.fragment.StabbedFragment;

import javax.inject.Singleton;

@Module (
  entryPoints = {
    DaggerApplication.class,
    StabbedActivity.class,
    StabbedFragment.class
  },
  includes = AndroidServicesModule.class
)
public class ApplicationModule {
  private Context context;

  public ApplicationModule(Context context) {
    this.context = context.getApplicationContext();
  }

  @Provides @Singleton Context provideContext() {
    return context;
  }
}
