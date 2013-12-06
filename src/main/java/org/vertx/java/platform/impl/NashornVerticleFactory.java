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

  @Override
  public void init(Vertx vertx, Container container, ClassLoader classloader) {
    this.container = container;
    this.vertx = vertx;
    this.cl = classloader;
    ScriptEngineManager factory = new ScriptEngineManager();
    this.engine = factory.getEngineByName("nashorn");
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

  private class NashornVerticle extends Verticle {

    private final Map<String, Object> cachedRequires = new HashMap<>();
    private final VertxScriptContext scriptContext;

    public NashornVerticle(String scriptName) {
      this.scriptContext = new VertxScriptContext(scriptName, cl, NashornVerticleFactory.this.vertx,
          NashornVerticleFactory.this.container, engine, cachedRequires);
    }

    @Override
    public void start() {
      scriptContext.executeScript();
    }

    @Override
    public void stop() {
      scriptContext.callVertxStop();
    }
  }

}
