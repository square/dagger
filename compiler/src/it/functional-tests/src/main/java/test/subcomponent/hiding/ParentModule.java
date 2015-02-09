package test.subcomponent.hiding;

import dagger.Module;
import dagger.Provides;

@Module
final class ParentModule {
  @Provides String provideString() {
    return "";
  }
}
