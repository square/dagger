package test.sub;

import javax.inject.Inject;

class PackagePrivateContainer {  
  public static class PublicEnclosed {
    @Inject PublicEnclosed() {}
  }
}
