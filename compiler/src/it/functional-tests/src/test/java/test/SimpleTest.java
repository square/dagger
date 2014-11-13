package test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

/**
 */
@RunWith(JUnit4.class)
public class SimpleTest {
  @Test public void testAThing() {
    ThingComponent thingComponent = Dagger_ThingComponent.create();
    assertThat(thingComponent).isNotNull();
    assertThat(thingComponent.thing()).isNotNull();
  }
}
