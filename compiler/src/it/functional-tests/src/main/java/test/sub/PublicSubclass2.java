package test.sub;

import javax.inject.Inject;
import test.Generic;

public class PublicSubclass2 extends Generic<PackagePrivateContainer.PublicEnclosed> {
  @Inject public PublicSubclass2(PackagePrivateContainer.PublicEnclosed pp) {
    super(pp);
  }
}
