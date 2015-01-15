package test.sub;

import javax.inject.Inject;
import test.Generic;

public class PublicSubclass extends Generic<PackagePrivate> {
  @Inject public PublicSubclass(PackagePrivate pp) {
    super(pp);
  }
}
