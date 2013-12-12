/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.platform.impl;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.PlatformManagerException;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;

import javax.script.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tim Fox timvolpe@gmail.com
 */
public class NashornVerticleFactory implements VerticleFactory {

  private Container container;
  private Vertx vertx;
  private ClassLoader cl;
  private ScriptEngine engine;
  private ScriptContext coffeeScriptCompilerContext;

  @Override
  public void init(Vertx vertx, Container container, ClassLoader classloader) {
    this.container = container;
    this.vertx = vertx;
    this.cl = classloader;
    ScriptEngineManager mgr = new ScriptEngineManager();
    this.engine = mgr.getEngineByName("nashorn");
    if (engine == null) {
      throw new PlatformManagerException("Nashorn engine not found, probably you are not using Java 8 or later");
    }
  }

  @Override
  public Verticle createVerticle(String main) throws Exception {
    return new NashornVerticle(main);
  }

  @Override
  public void reportException(Logger logger, Throwable t) {
    logger.error("Exception in Nashorn JavaScript verticle", t);
  }

  @Override
  public void close() {
  }

  private void createCoffeeScriptCompilerContext() {
    coffeeScriptCompilerContext = new SimpleScriptContext();
    Bindings bindings = engine.createBindings();
    coffeeScriptCompilerContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    try (InputStream is = cl.getResourceAsStream("coffee-script.js")) {
      if (is == null) {
        throw new FileNotFoundException("Cannot find coffee-script.js");
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder builder = new StringBuilder();
      readAll(builder, reader);
      engine.eval(builder.toString(), coffeeScriptCompilerContext);
    } catch (Exception e) {
      throw new PlatformManagerException(e);
    }
  }

  public synchronized String coffeeScriptToJS(String jsSource) {
    if (coffeeScriptCompilerContext == null) {
      createCoffeeScriptCompilerContext();
    }
    try {
      coffeeScriptCompilerContext.getBindings(ScriptContext.ENGINE_SCOPE).put("coffeeScriptSource", jsSource);
      Object res = engine.eval("CoffeeScript.compile(coffeeScriptSource);", coffeeScriptCompilerContext);
      return res == null ? null : res.toString();
    } catch (Exception e) {
      throw new PlatformManagerException(e);
    }
  }

  static void readAll(StringBuilder builder, BufferedReader reader) throws IOException {
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      builder.append(line).append("\n");
    }
  }

  public class NashornVerticle extends Verticle {

    private final Map<String, Object> cachedRequires = new HashMap<>();
    private final VertxRequire scriptContext;
    private final String scriptName;

    public NashornVerticle(String scriptName) {
      this.scriptContext = new VertxRequire(cl, NashornVerticleFactory.this.vertx,
          NashornVerticleFactory.this.container, engine, NashornVerticleFactory.this, cachedRequires);
      this.scriptName = scriptName;
    }

    @Override
    public void start() {
      scriptContext.require(scriptName);
    }

    @Override
    public void stop() {
      scriptContext.callVertxStop();
    }
  }

}
