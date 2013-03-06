/*
 * Copyright (C) 2012 Google, Inc.
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
package web;

import dagger.Module;
import dagger.Provides;
import web.api.WebConfiguration;
import web.api.ManagedServer;

import javax.inject.Singleton;
import java.util.List;

@Module(
    entryPoints = WebApp.class,
    complete = false
)
class WebModule {

  @Provides
  @Singleton
  WebConfiguration providesConfiguration(List<ServletConfiguration> servletConfigurations) {
    WebConfiguration config = new SimpleWebWebConfiguration(8080, "/");
    if (!servletConfigurations.isEmpty()) {
      for (ServletConfiguration servletConf : servletConfigurations) {
        config.addServletMapping(servletConf.getServlet(), servletConf.getPaths());
      }
    }
    return config;
  }

  @Provides
  @Singleton
  ManagedServer providesServer(WebConfiguration webConfiguration) {
    return new WebServer(webConfiguration);
  }
}
