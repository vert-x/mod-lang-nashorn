# Javascript on Vert.x with Nashorn

This module uses the Nashorn JavaScript engine to power Vert.x JavaScript verticles
This language module uses the generic vert.x Javascript API in [mod-lang-js](https://github.com/vert-x/mod-lang-js).
The API documentation is the same as for `lang-js` and `lang-rhino`, and can be found
on the `lang-js` CI server.

[API Documentation](https://vertx.ci.cloudbees.com/view/Javascript/job/vert.x-mod-lang-js/lastSuccessfulBuild/artifact/target/docs/index.html)

* Important note. The Nashorn engine is an intrinsic part of the Java 8 JDK implementation. In order to use this module
you will need to be running Vert.x with JDK 1.8.0 or later *

## Usage

By default, vert.x runs Javascript with Rhino. Change this by creating a
`langs.properties` file at the root of your project that looks something like this.

    nashorn=io.vertx~lang-nashorn~0.1-SNAPSHOT:org.vertx.java.platform.impl.NashornVerticleFactory
    .js=nashorn

