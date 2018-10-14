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
package com.example.dagger.activitygraphs;

import android.content.Context;
import com.example.dagger.activitygraphs.ui.ActivityTitleController;
import com.example.dagger.activitygraphs.ui.HomeActivity;
import com.example.dagger.activitygraphs.ui.HomeFragment;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * This module represents objects which exist only for the scope of a single activity. We can
 * safely create singletons using the activity instance because the entire object graph will only
 * ever exist inside of that activity.
 */
@Module(
    injects = {
        HomeActivity.class,
        HomeFragment.class
    },
    addsTo = AndroidModule.class,
    library = true
)
public class ActivityModule {
  private final DemoBaseActivity activity;

  public ActivityModule(DemoBaseActivity activity) {
    this.activity = activity;
  }

  /**
   * Allow the activity context to be injected but require that it be annotated with
   * {@link ForActivity @ForActivity} to explicitly differentiate it from application context.
   */
  @Provides @Singleton @ForActivity Context provideActivityContext() {
    return activity;
  }

  @Provides @Singleton ActivityTitleController provideTitleController() {
    return new ActivityTitleController(activity);
  }
}
