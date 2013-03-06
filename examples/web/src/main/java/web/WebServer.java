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

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import web.api.WebConfiguration;
import web.api.ManagedServer;

import javax.inject.Inject;
import javax.servlet.Servlet;
import java.util.ArrayList;
import java.util.List;

public class WebServer implements ManagedServer<Server> {

  private final List<Handler> handlers = new ArrayList<Handler>();

  private Server server;
  private final WebConfiguration config;
  private boolean initialized = false;

  @Inject public WebServer(WebConfiguration config) {
    this.config = config;
  }

  @Override public Server getServer() {
    return server;
  }

  @Override public void start() {

    if (!this.initialized) {
      init();
      initialized = true;
    }

    try {
      server.start();
      server.join();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  @Override public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void init() {
    server = new Server();
    setupConnectors();
    setupServlets();
    setupHandlers();
  }

  private void addHandler(Handler handler) {
    this.handlers.add(handler);
  }

  private void setupHandlers() {

    HandlerCollection hc = new HandlerCollection();

    // static file handler is not used but added here for completeness
    ResourceHandler staticFileHandler = new ResourceHandler();
    staticFileHandler.setDirectoriesListed(false);
    staticFileHandler.setWelcomeFiles(new String[]{"index.html"});
    staticFileHandler.setResourceBase("web");
    hc.addHandler(staticFileHandler);

    // servlets, etc.
    for (Handler h : handlers) {
      hc.addHandler(h);
    }

    // handles 404, favicon, etc.
    hc.addHandler(new DefaultHandler());

    server.setHandler(hc);
  }

  private void setupServlets() {
    if (config.getServletMappings().isEmpty()) {
      return;
    }

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath(config.getContextPath());

    if (!config.getServletMappings().isEmpty()) {
      for (String servletPath : config.getServletMappings().keySet()) {
        Servlet servlet = config.getServletMappings().get(servletPath);
        context.addServlet(new ServletHolder(servlet), servletPath);
      }
    }
    addHandler(context);
  }

  private void setupConnectors() {
    SelectChannelConnector defaultConnector = new SelectChannelConnector();
    defaultConnector.setName("Default");
    defaultConnector.setPort(config.getPort());
    this.server.addConnector(defaultConnector);
  }
}
