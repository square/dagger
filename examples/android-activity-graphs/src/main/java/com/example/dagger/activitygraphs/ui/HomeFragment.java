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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.dagger.activitygraphs.DemoBaseFragment;
import javax.inject.Inject;

import static android.view.Gravity.CENTER;

public class HomeFragment extends DemoBaseFragment {
  public static HomeFragment newInstance() {
    return new HomeFragment();
  }

  @Inject ActivityTitleController titleController;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    TextView tv = new TextView(getActivity());
    tv.setGravity(CENTER);
    tv.setText("Hello, World");
    return tv;
  }

  @Override public void onResume() {
    super.onResume();

    // Fragments should not modify things outside of their own view. Use an external controller to
    // ask the activity to change its title.
    titleController.setTitle("Home Fragment");
  }
}
