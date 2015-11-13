/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test.multipackage;

import dagger.Component;
import test.multipackage.a.AGrandchild;
import test.multipackage.a.AModule;
import test.multipackage.a.AParent;
import test.multipackage.b.BChild;

/**
 * A component that tests members injection across packages and subclasses.
 */
@Component(modules = {AModule.class})
public interface MembersInjectionVisibilityComponent {
  void inject(AParent aParent);

  void inject(BChild aChild);

  void inject(AGrandchild aGrandchild);
}
