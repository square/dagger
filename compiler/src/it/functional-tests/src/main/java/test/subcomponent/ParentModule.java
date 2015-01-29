package test.subcomponent;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import static dagger.Provides.Type.SET;

@Module
final class ParentModule {
  @Provides(type = SET) Object provideUnscopedObject() {
    return new Object() {
      @Override public String toString() {
        return "unscoped in parent";
      }
    };
  }

  @Provides(type = SET) @Singleton Object provideSingletonObject() {
    return new Object() {
      @Override public String toString() {
        return "singleton";
      }
    };
  }
}
