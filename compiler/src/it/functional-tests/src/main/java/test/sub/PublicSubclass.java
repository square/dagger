package test.sub;

import test.Generic;

import javax.inject.Inject;

public class PublicSubclass extends Generic<PackagePrivate> {
  @Inject public PublicSubclass(PackagePrivate pp) {
    super(pp);
  }
}
