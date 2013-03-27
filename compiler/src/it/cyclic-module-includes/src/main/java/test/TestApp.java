/*
 * Copyright (C) 2013 Google, Inc.
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

class TestApp {
  
  @Module(includes = SelfReferencingModule.class)
  static class SelfReferencingModule { }

  @Module(includes = Spock.class)
  static class Rock {}

  @Module(includes = Rock.class)
  static class Paper {}

  @Module(includes = Paper.class)
  static class Scissors {}

  @Module(includes = Scissors.class)
  static class Lizard {}

  @Module(includes = Lizard.class)
  static class Spock {}

}
