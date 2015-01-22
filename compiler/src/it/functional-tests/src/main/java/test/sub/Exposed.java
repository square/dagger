package test.sub;

import javax.inject.Inject;
import test.Generic;
import test.Generic2;

public class Exposed {
  
  @Inject public Generic2<PackagePrivate> gpp2;
  @Inject public Generic2<PackagePrivateContainer.PublicEnclosed> gppc2;

  public Generic<PackagePrivate> gpp;
  public Generic<PackagePrivateContainer.PublicEnclosed> gppc;
  
  @Inject Exposed(Generic<PackagePrivate> gpp, Generic<PackagePrivateContainer.PublicEnclosed> gppc) {
    this.gpp = gpp;
    this.gppc = gppc;
  }
}
