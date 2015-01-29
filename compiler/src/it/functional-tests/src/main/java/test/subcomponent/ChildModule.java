package test.subcomponent;

import dagger.Module;
import dagger.Provides;

import static dagger.Provides.Type.SET;

@Module
final class ChildModule {
  @Provides String stringRequiresSingleton(SingletonType singletonType) {
    return singletonType.toString();
  }

  @Provides(type = SET) Object provideUnscopedObject() {
    return new Object() {
      @Override public String toString() {
        return "unscoped in child";
      }
    };
  }
}
