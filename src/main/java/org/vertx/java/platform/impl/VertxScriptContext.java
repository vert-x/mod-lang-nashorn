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

import org.vertx.java.core.Vertx;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.PlatformManagerException;

import javax.script.*;
import java.io.*;
import java.util.Map;

/**
 * @author Tim Fox timvolpe@gmail.com
 */
public class VertxScriptContext {

  private final String scriptName;
  private final ClassLoader mcl;
  private final Vertx vertx;
  private final Container container;
  private final ScriptEngine engine;
  private final NashornVerticleFactory factory;
  private final boolean module;
  private final Map<String, Object> cachedRequires;
  private final boolean inheritContext;
  private ScriptContext ctx;
  private Object moduleExports;

  public VertxScriptContext(String scriptName, ClassLoader mcl, Vertx vertx, Container container, ScriptEngine engine,
                            NashornVerticleFactory factory, Map<String, Object> cachedRequires) {
    this(null, scriptName, false, mcl, vertx, container, engine, factory, cachedRequires, false);
    createContext();
  }

  private VertxScriptContext(ScriptContext ctx, String scriptName, boolean module, ClassLoader mcl, Vertx vertx,
                             Container container, ScriptEngine engine, NashornVerticleFactory factory,
                             Map<String, Object> cachedRequires, boolean inheritContext) {
    this.ctx = ctx;
    this.scriptName = scriptName;
    this.module = module;
    this.mcl = mcl;
    this.vertx = vertx;
    this.container = container;
    this.engine = engine;
    this.factory = factory;
    this.cachedRequires = cachedRequires;
    this.inheritContext = inheritContext;
  }

  private void createContext() {
    ctx = new SimpleScriptContext();
    Bindings bindings = engine.createBindings();
    bindings.put("__jvertx", vertx);
    bindings.put("__jcontainer", container);
    bindings.put("__jscriptcontext", this);
    ctx.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
  }

  private VertxScriptContext createModuleContext(String moduleScript) {
    VertxScriptContext ctx =  new VertxScriptContext(null, moduleScript, true, mcl, vertx, container, engine,
                                                     factory, cachedRequires, false);
    ctx.createContext();
    return ctx;
  }

  private VertxScriptContext createLoadContext(String scriptName) {
    return new VertxScriptContext(ctx, scriptName, false, mcl, vertx, container, engine, factory, cachedRequires, true);
  }

  public Object require(String moduleName) {
    if (!moduleName.endsWith(".js")) {
      moduleName += ".js";
    }
    Object res = cachedRequires.get(moduleName);
    if (res == null) {
      VertxScriptContext newContext = createModuleContext(moduleName);
      newContext.executeScript();
      res = newContext.moduleExports;
      cachedRequires.put(moduleName, res);
    }
    return res;
  }

  public void load(String scriptName) {
    VertxScriptContext newContext = createLoadContext(scriptName);
    Bindings bindings = newContext.ctx.getBindings(ScriptContext.ENGINE_SCOPE);
    bindings.put("__vertxload", "true");
    newContext.executeScript();
    bindings.remove("__vertxload");
  }

  public void setModuleExports(Object exports) {
    this.moduleExports = exports;
  }

  public void callVertxStop() {
    try {
      engine.eval("if (typeof vertxStop === 'function') vertxStop();", ctx);
    } catch (ScriptException e) {
      throw new PlatformManagerException(e);
    }
  }

  private void readAll(StringBuilder builder, BufferedReader reader) throws IOException {
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      builder.append(line).append("\n");
    }
  }

  public Object executeScript() {
    System.out.println("Executing script " + scriptName);
    try (InputStream is = mcl.getResourceAsStream(scriptName)) {
      if (is == null) {
        throw new FileNotFoundException("Cannot find script: " + scriptName);
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sWrap = new StringBuilder();
      if (!inheritContext) {
        sWrap.append("function require(moduleName) { return __jscriptcontext.require(moduleName); }\n");
        sWrap.append("function load(scriptName) { __jscriptcontext.load(scriptName); }\n");
      }
      if (module) {
        // commonjs module
        sWrap.append("module = {}; module.exports = {};\n");
      }
      if (scriptName.endsWith(".coffee")) {
        StringBuilder builder = new StringBuilder();
        readAll(builder, reader);
        sWrap.append(factory.coffeeScriptToJS(builder.toString()));
      } else {
        readAll(sWrap, reader);
      }
      if (module) {
        sWrap.append("__jscriptcontext.setModuleExports(module.exports)");
      }
      return engine.eval(sWrap.toString(), ctx);
    } catch (IOException e) {
      throw new PlatformManagerException(e);
    } catch (ScriptException e) {
      // We need to set the correct filename
      String newMessage = e.getMessage().replace("<eval>", scriptName);
      ScriptException corrected = new ScriptException(newMessage, scriptName, e.getLineNumber(), e.getColumnNumber());
      corrected.setStackTrace(e.getStackTrace());
      throw new PlatformManagerException(corrected);
    }
  }
}
