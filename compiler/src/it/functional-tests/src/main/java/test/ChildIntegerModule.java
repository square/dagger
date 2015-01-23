package test;

import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import java.util.List;

@Module
class ChildIntegerModule extends ParentModule<Integer, String, List<Integer>> {
  
  @Provides Integer provideInteger() {
    return 1;
  }
  
  @Provides List<Integer> provideListOfInteger() {
    return ImmutableList.of(2);
  }

}
