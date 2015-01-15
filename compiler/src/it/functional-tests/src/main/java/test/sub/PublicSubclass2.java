package test.sub;

import test.Generic;

import javax.inject.Inject;

public class PublicSubclass2 extends Generic<PackagePrivateContainer.PublicEnclosed> {
  @Inject public PublicSubclass2(PackagePrivateContainer.PublicEnclosed pp) {
    super(pp);
  }
}
