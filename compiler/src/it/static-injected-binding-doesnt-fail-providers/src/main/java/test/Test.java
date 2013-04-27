/*
 * Copyright (C) 2013 Square, Inc.
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

import dagger.ObjectGraph;
import dagger.Provides;
import javax.inject.Inject;

import java.lang.Override;

public class Test {

  public static class InjectsOneField {
    @Inject static String staticallyInjectedString;
  }

  @Module(staticInjections = { InjectsOneField.class })
  public static class TestModule {
    @Provides String string() {
      return "string";
    }
  }
}
