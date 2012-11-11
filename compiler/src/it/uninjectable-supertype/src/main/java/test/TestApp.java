/*
 * Copyright (C) 2012 Square, Inc.
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
package test;

import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;

class TestApp {
  static class NotInjectable {
  }

  static class InjectableSubclass extends NotInjectable {
    @Inject String string;
  }

  @Module(entryPoints = InjectableSubclass.class)
  static class TestModule {
    @Provides String provideString() {
      return "string";
    }
  }
}
