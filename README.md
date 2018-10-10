# Atomos
Atomos - A Java Module Framework using OSGi and Equinox

Atomos enables the Equinox OSGi framework implementation to be loaded as a Java Module (as in Java Platform Module System)
on the module path

# Build

Atomos build and tests require using some SNAPSHOT builds of a few different projects on git hub.  The following repositories
must be cloned and built locally before building Atomos

- https://github.com/apache/felix - To fix https://issues.apache.org/jira/browse/FELIX-5958 the gogo component needs to be
built to get the latest org.apache.felix.command bundle built
- https://github.com/moditect/moditect.git - To get the latest SNAPSHOT.  This plugin provides some cool utilities for adding
module-infos to existing dependency JARs and building `jlink` images.

Once you have the above built and installed into your local maven `.m2` repository you can then build the Atomos framework with:

`mvn clean install`

This should create a jlink image under `atomos/atomos.tests/service.image/target/atomos`.  Executing the following command
against the jlink image should produce a gogo shell prompt:

`atomos/bin/java --add-modules ALL-SYSTEM -m atomos.framework`

You should see the following output:

```
Registered Echo service from activator.
____________________________
Welcome to Apache Felix Gogo

g! 
```
