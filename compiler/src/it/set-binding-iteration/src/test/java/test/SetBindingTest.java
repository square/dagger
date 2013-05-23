package test;

import dagger.ObjectGraph;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static dagger.Provides.Type.SET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public final class SetBindingTest {

  @Test
  public void testOrderOfCompileProcessedSetBindings()  throws Exception {
    // ensure the compile task operated against this
    assertNotNull(Class.forName(FirstStringFromSetBinding.class.getName() + "$$InjectAdapter"));

    FirstStringFromSetBinding ep = ObjectGraph
        .create(new FirstStringFromSetBinding.String1Module(), new FirstStringFromSetBinding.String2Module())
        .get(FirstStringFromSetBinding.class);
    assertEquals("string1", ep.string);
  }

  @Test
  public void testOrderOfCompileProcessedSetBindingsWithIncludedSetBinding() {
    FirstStringFromSetBinding ep = ObjectGraph
        .create(new FirstStringFromSetBinding.IncludeString1Module(), new FirstStringFromSetBinding.String2Module())
        .get(FirstStringFromSetBinding.class);
    assertEquals("string2", ep.string);
  }
}
