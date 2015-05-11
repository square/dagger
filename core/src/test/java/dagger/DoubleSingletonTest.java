package dagger;

import dagger.internal.TestingLoader;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class) //
public class DoubleSingletonTest {
  @Module(
      library = true //
  ) //
  static class UnauthenticatedModule {
  }

  @Module(
      library = true, //
      includes = UnauthenticatedModule.class, //
      injects = {
          MockModeApi.class, //
      }, //
      overrides = true //
  ) //
  static class DebugUnauthenticatedModule {
    @Provides @Singleton List<String> provideDebugUnauthenticatedSingleton() {
      System.out.println("Creating @Singleton List<String>");
      return Arrays.asList("debug-unauthenticated");
    }

    @Provides @Singleton Api provideDeliverApi(MockModeApi mockApi) {
      return mockApi;
    }
  }

  @Module(
      library = true, //
      addsTo = UnauthenticatedModule.class, //
      injects = {
      } //
  ) //
  static class AuthenticatedModule {
  }

  @Module(
      addsTo = DebugUnauthenticatedModule.class, //
      includes = AuthenticatedModule.class, //
      overrides = true, //
      library = true, //
      complete = true //
  ) //
  static class DebugAuthenticatedModule {
  }

  @Module(
      library = true, //
      addsTo = UnauthenticatedModule.class, //
      injects = {
          MainActivity.class, //
          AppContainer.class, //
      } //
  ) //
  static class MainActivityUnauthenticatedModule {
  }

  static class AppContainer {
    @Inject List<String> stringList;
  }

  private interface Api {
  }

  @Singleton static class MockModeApi implements Api {
    private final List<String> stringList;

    @Inject MockModeApi(List<String> stringList) {
      this.stringList = stringList;
    }
  }

  private class MainActivity {
    @Inject AppContainer appContainer;
  }

  @Test public void doubleSingleton() throws Exception {
    ObjectGraph unauthenticatedAppGraph =
        ObjectGraph.createWith(new TestingLoader(), new UnauthenticatedModule(),
            new DebugUnauthenticatedModule());

    MockModeApi mockApi = unauthenticatedAppGraph.get(MockModeApi.class);

    ObjectGraph authenticatedAppGraph =
        unauthenticatedAppGraph.plus(new AuthenticatedModule(), new DebugAuthenticatedModule());

    ObjectGraph authenticatedMainActivityGraph =
        authenticatedAppGraph.plus(new MainActivityUnauthenticatedModule());

    MainActivity mainActivity = new MainActivity();
    authenticatedMainActivityGraph.inject(mainActivity);

    AppContainer appContainer = mainActivity.appContainer;
    // If I remove DebugAuthenticatedModule, this passes.
    assertThat(appContainer.stringList == mockApi.stringList) //
        .overridingErrorMessage(appContainer.stringList + " != " + mockApi.stringList) //
        .isTrue();
  }
}
