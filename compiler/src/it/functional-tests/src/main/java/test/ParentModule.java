package test;

import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;

@Module
abstract class ParentModule<A extends Number & Comparable<A>, B, C extends Iterable<A>> {
  @Provides Iterable<A> provideIterableOfAWithC(A a, C c) {
    return new ImmutableList.Builder<A>().add(a).addAll(c).build();
  }
}
