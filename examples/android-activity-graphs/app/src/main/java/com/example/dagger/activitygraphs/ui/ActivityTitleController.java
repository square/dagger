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
package com.example.dagger.activitygraphs.ui;

import android.app.Activity;

/**
 * A simple abstraction which provides the ability to set the title on an activity.
 * <p>
 * Fragments should not directly modify any part of an activity outside of the view or dialog that
 * it creates. This class provides a way for fragments to inject a controller that will allow for
 * control of the activity title. While not exceedingly useful in practice, this concept could be
 * expanded to things like facilitating control over the action bar, dialogs, notifications, etc.
 */
public class ActivityTitleController {
  private final Activity activity;

  public ActivityTitleController(Activity activity) {
    this.activity = activity;
  }

  public void setTitle(CharSequence title) {
    activity.setTitle(title);
  }
}
