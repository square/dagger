/*
 * Copyright (C) 2009 The JSR-330 Expert Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atinject.tck.auto;

import org.atinject.tck.auto.accessories.Cupholder;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Seat {

    private final Cupholder cupholder;

    @Inject
    Seat(Cupholder cupholder) {
        this.cupholder = cupholder;
    }

    public Cupholder getCupholder() {
        return cupholder;
    }
}
