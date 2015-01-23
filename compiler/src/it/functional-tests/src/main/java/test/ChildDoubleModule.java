package test;

import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import java.util.List;

@Module
class ChildDoubleModule extends ParentModule<Double, String, List<Double>> {
  
  @Provides Double provideDouble() {
    return 3d;
  }
  
  @Provides List<Double> provideListOfDouble() {
    return ImmutableList.of(4d);
  }

}
