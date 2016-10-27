package test;

import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Module
abstract class ParentModule<A extends Number & Comparable<A>, B, C extends Iterable<A>> {
  @Provides Iterable<A> provideIterableOfAWithC(A a, C c) {
    List<A> list = new ArrayList<>();
    list.add(a);
    for (A elt : c) {
      list.add(elt);
    }
    return list;
  }

  @Provides static char provideNonGenericBindingInParameterizedModule() {
    return 'c';
  }

  @Provides
  static List<Set<String>> provideStaticGenericTypeWithNoTypeParametersInParameterizedModule() {
    return new ArrayList<>();
  }
}
