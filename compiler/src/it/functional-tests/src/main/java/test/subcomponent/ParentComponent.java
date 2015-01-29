package test.subcomponent;

import dagger.Component;
import java.util.Set;
import javax.inject.Singleton;

@Component(modules = ParentModule.class)
@Singleton
interface ParentComponent {
  ChildComponent newChildComponent();

  Set<Object> objectSet();
}
