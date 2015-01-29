package test.subcomponent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class SubcomponentScopeTest {
  @Test
  public void testSingletonPropagatesUpward() {
    ParentComponent parentComponent = Dagger_ParentComponent.create();
    assertThat(parentComponent.newChildComponent().string())
        .isEqualTo(parentComponent.newChildComponent().string());
  }

  @Test
  public void testMultibindingContributions() {
    ParentComponent parentComponent = Dagger_ParentComponent.create();
    Set<Object> parentObjectSet = parentComponent.objectSet();
    assertThat(parentObjectSet).hasSize(2);
    Set<Object> childObjectSet = parentComponent.newChildComponent().objectSet();
    assertThat(childObjectSet).hasSize(3);
    Set<Object> identitySet = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    identitySet.addAll(parentObjectSet);
    identitySet.addAll(childObjectSet);
    assertThat(identitySet).hasSize(4);
  }
}
