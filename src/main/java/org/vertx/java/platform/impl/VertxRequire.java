/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.vertx.java.platform.impl;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import javax.script.*;
import org.vertx.java.core.Vertx;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.PlatformManagerException;

/**
 * Implementation of CommonJS require()
 * We wrap each module in an anonymous function so they live in their own scope
 * We enforce strict mode for modules so they can't e.g. use non var global variables to leak state
 * Module exports are also cached
 *
 * @author Tim Fox timvolpe@gmail.com
 */
public class VertxRequire {

  private final ClassLoader mcl;
  private final ScriptEngine engine;
  private final NashornVerticleFactory factory;
  private final Map<String, Object> cachedRequires;
  private final ScriptContext ctx;
  private Object moduleExports;
  private Runnable vertxStop;
  private Module module;

  private static final String GLOBAL_FUNCTIONS =
      "function require(moduleName) { return __jscriptcontext.require(moduleName); }\n";
  
  public static final String MODULE_HEADER =
      "(function(module) {\n" +
      "  \"use strict\";\n";
  
  public static final String MODULE_FOOTER =
      "__jscriptcontext.setModuleExports(module.exports);\n" +
      "if (typeof vertxStop === 'function') {\n" +
      "  __jscriptcontext.setVertxStop(vertxStop);\n" +
      "}\n" +
            "})({ id: __jscriptcontext.getModule().getId(),\n" +
            "    exports: {},\n" +
            "    uri: __jscriptcontext.getModule().getUri()\n" +
            "});";

  public VertxRequire(ClassLoader mcl, Vertx vertx, Container container, ScriptEngine engine,
                      NashornVerticleFactory factory, Map<String, Object> cachedRequires) {
    this.mcl = mcl;
    this.engine = engine;
    this.factory = factory;
    this.cachedRequires = cachedRequires;
    this.ctx = new SimpleScriptContext();
    Bindings bindings = engine.createBindings();
    bindings.put("__jvertx", vertx);
    bindings.put("__jcontainer", container);
    bindings.put("__jscriptcontext", this);
    ctx.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    addGlobalFunctions();
  }

  public Object require(String moduleName) {
    if (!moduleName.endsWith(".js") && !moduleName.endsWith(".coffee")) {
      moduleName += ".js";
    }
    Object res = cachedRequires.get(moduleName);
    if (res == null) {
      executeModule(moduleName);
      res = moduleExports;
      cachedRequires.put(moduleName, res);
    }
    return res;
  }

  public void setModuleExports(Object exports) {
    this.moduleExports = exports;
  }

  public void setVertxStop(Runnable vertxStop) {
    this.vertxStop = vertxStop;
  }

  public Module getModule() {
    return module;
  }

  public void callVertxStop() {
    if (vertxStop != null) {
      vertxStop.run();
    }
  }

  private void addGlobalFunctions() {
    executeScript(GLOBAL_FUNCTIONS, "addGlobals");
  }

  private Object executeScript(String script, String scriptName) {
    try {
      return engine.eval(script, ctx);
    } catch (ScriptException e) {
      // We need to set the correct filename
      String newMessage = e.getMessage().replace("<eval>", scriptName);
      ScriptException corrected = new ScriptException(newMessage, scriptName, e.getLineNumber(), e.getColumnNumber());
      corrected.setStackTrace(e.getStackTrace());
      throw new PlatformManagerException(corrected);
    }
  }

  public Object executeModule(String moduleName) {
    URL url = mcl.getResource(moduleName);
    if (url == null) {
      throw new PlatformManagerException("Cannot find script: " + moduleName);
    }
    URI uri;
    try {
      uri = url.toURI();
    } catch (URISyntaxException e) {
      throw new PlatformManagerException(e);
    }
    
    module = new Module(moduleName, uri.toString());
    StringBuilder script = new StringBuilder();

    if (moduleName.endsWith(".coffee")) {
      try (InputStream is = url.openStream()) {
       BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder coffeeScript = new StringBuilder();
        NashornVerticleFactory.readAll(coffeeScript, reader);
        script.append(MODULE_HEADER)
                .append(factory.coffeeScriptToJS(coffeeScript.toString()))
                .append(MODULE_FOOTER);
      } catch (IOException e) {
        throw new PlatformManagerException(e);
      }
    } else {
      try {
        URL wrappedUrl = new URL(null, url.toExternalForm(), new ModuleWrapper());
        ctx.getBindings(ScriptContext.ENGINE_SCOPE).put("__filename", wrappedUrl);
        script.append("load(__filename)");
      } catch (MalformedURLException e) {
        throw new PlatformManagerException(e);
      }
    }
    
    return executeScript(script.toString(), moduleName);
  }

}
