package test.parentpackage;

import javax.inject.Inject;
import test.grandparentpackage.GrandparentWithNoInjectedMembers;

public class ParentWithInjectedMember extends GrandparentWithNoInjectedMembers {
  @Inject Integer integer;
}
