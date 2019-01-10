# Atomos
Atomos - A Java Module Framework using OSGi and Equinox

Atomos enables the Equinox OSGi framework implementation to be loaded as a Java Module (as in Java Platform Module System)
on the module path.  When launched this way the built-in Java Module loader is used to load all classes contained
in the bundles on the module path.

# Build

Java 11 must be used to build Atomos.  Atomos build uses the latest 1.0.0.Beta2 version of the moditect plugin (https://github.com/moditect/moditect.git).  This plugin provides some cool utilities for adding module-infos to existing dependency JARs and building `jlink` images.  You can build the Atomos with the following:

`mvn clean install`

This should create a jlink image under `atomos/atomos.tests/service.image/target/atomos`.  Executing the following command
against the jlink image should produce a gogo shell prompt:

`atomos/bin/atomos`

You should see the following output:

```
Registered Echo service from activator.
____________________________
Welcome to Apache Felix Gogo

g! 
```

In order to successfully build a jlink image all bundles included in the image must contain a `module-info.class`,
they cannot be automatic modules.
The `atomos.tests/service.image` example uses the latest `1.0.0.Beta2` version of the `moditect-maven-plugin` to
add `module-info.class` as necessary to the bundles used in the image.

You can also load additional modules into atomos by using the `atomos.modules` option when launching `atomos`.
For example:

```
atomos/bin/atomos atomos.modules=/path/to/more/modules
```
When doing that the additional modules will be loaded into a child layer where the Atomos OSGi Framework
will control the class loaders.  This will produce a class loader per module bundle installed.  This has
advantages because it allows the module class loader for the bundle to implement the
`org.osgi.framework.BundleReference` interface.

