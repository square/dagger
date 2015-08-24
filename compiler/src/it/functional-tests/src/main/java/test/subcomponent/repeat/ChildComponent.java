

package test.subcomponent.repeat;

import dagger.Subcomponent;

import java.util.Set;

@Subcomponent(modules = RepeatedModule.class)
interface ChildComponent {
  String getString();
  Set<String> getMultiboundStrings();
  OnlyUsedInChild getOnlyUsedInChild();
}
