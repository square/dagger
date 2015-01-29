package test.subcomponent;

import dagger.Subcomponent;
import java.util.Set;

@Subcomponent(modules = ChildModule.class)
interface ChildComponent {
  String string();

  Set<Object> objectSet();
}
