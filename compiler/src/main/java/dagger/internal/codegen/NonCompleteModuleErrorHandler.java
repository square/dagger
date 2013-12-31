package dagger.internal.codegen;

import dagger.internal.Linker;
import java.util.List;

public class NonCompleteModuleErrorHandler implements Linker.ErrorHandler {

  private boolean shouldBeComplete = true;

  @Override public void handleErrors(List<String> errors) {
    if (errors.size() > 0) {
      shouldBeComplete = false;
    }
  }

  public boolean shouldBeComplete() {
    return shouldBeComplete;
  }
}
