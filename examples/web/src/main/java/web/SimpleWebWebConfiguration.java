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
package web;

import web.api.WebConfiguration;

import javax.servlet.Servlet;
import java.util.HashMap;
import java.util.Map;

public class SimpleWebWebConfiguration implements WebConfiguration {

  private int port;
  private String contextPath;
  private final Map<String, Servlet> servletMappings = new HashMap<String, Servlet>();

  public SimpleWebWebConfiguration(int port, String contextPath) {
    this.port = port;
    this.contextPath = contextPath;
  }

  @Override public WebConfiguration addServletMapping(Servlet servlet,
                                                      String... paths) {
    if (paths == null) return this;
    for (String path : paths) {
      this.servletMappings.put(path, servlet);
    }
    return this;
  }

  @Override public int getPort() {
    return port;
  }

  @Override public String getContextPath() {
    return contextPath;
  }

  @Override public Map<String, Servlet> getServletMappings() {
    return servletMappings;
  }
}
