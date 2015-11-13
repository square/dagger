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
package test.subcomponent;

import java.util.Set;
import javax.inject.Inject;

class RequiresMultibindingsInParent {
  private final RequiresMultiboundObjects requiresMultiboundObjects;
  private final RequiresMultiboundStrings requiresMultiboundStrings;
  private final Set<RequiresMultiboundObjects> setOfRequiresMultiboundObjects;

  @Inject
  RequiresMultibindingsInParent(
      RequiresMultiboundObjects requiresMultiboundObjects,
      RequiresMultiboundStrings requiresMultiboundStrings,
      Set<RequiresMultiboundObjects> setOfRequiresMultiboundObjects) {
    this.requiresMultiboundObjects = requiresMultiboundObjects;
    this.requiresMultiboundStrings = requiresMultiboundStrings;
    this.setOfRequiresMultiboundObjects = setOfRequiresMultiboundObjects;
  }

  RequiresMultiboundObjects requiresMultiboundObjects() {
    return requiresMultiboundObjects;
  }

  RequiresMultiboundStrings requiresMultiboundStrings() {
    return requiresMultiboundStrings;
  }

  Set<RequiresMultiboundObjects> setOfRequiresMultiboundObjects() {
    return setOfRequiresMultiboundObjects;
  }
}
