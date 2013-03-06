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
package web.servlet;

import dagger.Module;
import dagger.Provides;
import web.ServletConfiguration;

import java.util.ArrayList;
import java.util.List;

@Module
public class ServletModule {

  @Provides List<ServletConfiguration> providesServletConfigurations() {
    List<ServletConfiguration> servletConfigs = new ArrayList<ServletConfiguration>();
    servletConfigs.add(new ServletConfiguration(
        new HelloWorldServlet(), new String[]{"/hello", "/helloworld"}));
    servletConfigs.add(new ServletConfiguration(
        new FooServlet(), new String[]{"/foo"}));
    return servletConfigs;
  }
}
