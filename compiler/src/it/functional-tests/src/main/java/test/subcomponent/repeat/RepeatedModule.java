package test.subcomponent.repeat;

import static dagger.Provides.Type.SET;

import dagger.Module;
import dagger.Provides;

@Module
final class RepeatedModule {
  @Provides String provideString() {
    return "a string";
  }

  @Provides(type = SET) String contributeString() {
    return "a string in a set";
  }

  @Provides OnlyUsedInParent provideOnlyUsedInParent() {
    return new OnlyUsedInParent() {};
  }

  @Provides OnlyUsedInChild provideOnlyUsedInChild() {
    return new OnlyUsedInChild() {};
  }
}
