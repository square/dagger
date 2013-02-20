package dagger.application;

import android.app.Application;
import dagger.ObjectGraph;
import dagger.module.ApplicationModule;

public class DaggerApplication extends Application {
  private static ObjectGraph objectGraph;

  @Override public void onCreate() {
    super.onCreate();

    objectGraph = ObjectGraph.create(new ApplicationModule(this));
  }

  public static <T> void inject(T instance) {
    objectGraph.inject(instance);
  }
}
