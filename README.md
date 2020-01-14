# Atomos
Atomos - A Java Module Framework using OSGi Connect

Atomos requires an Equinox OSGi Framework implementation which supports OSGi Connect initially described in this OSGi [blog post](https://blog.osgi.org/2019/09/osgi-connect-revisited.html).  The OSGi Connect specification is currently being developed as an RFC with the OSGi Alliance and the current version of the RFC can be found [here](https://github.com/osgi/design/blob/master/rfcs/rfc0243/rfc-0243-Connect.pdf).  Currently a snapshot of the Equinox OSGi Framework is being used that implements the proposed OSGi Connect specification for an upcoming OSGi R8 Core specification.  Source for the snapshot is found in https://git.eclipse.org/c/equinox/rt.equinox.framework.git in the `osgiR8` branch and tempary binaries are pushed to https://github.com/tjwatson/atomos-temp-m2repo for Atomos.
Atomos is an implementation of an OSGi Connect factory that can be used to create an OSGi Framework instance. Framework instances created
with Atomos add support to the OSGi Framework that enables bundles to be installed
which are managed outside of the OSGi Framework module layer.  Currently Atomos supports three different modes for
loading bundles from outside the OSGi module layer:

1. Module Path:  Using the Java Platform Module System (JPMS) Atomos will discover the 
modules on the module path and will make any OSGi bundles found available for installation into the Framework.  This also allows
for Atomos and a set of OSGi bundles to be packaged into a jlink image resulting in a small fit-for-purpose JVM
1. Class Path:  When loaded from the class path Atomos will discover the JARs on the class path
and will make any OSGi bundles found available for installation into the Framework.
1. Graal Substrate Native Image:  When compiled into a Substrate native image Atomos will discover the bundles that were
included into the image.  This requires configuration to enable the necessary reflection for things like bundle activators
and declarative service components.


# Build

Java 11 must be used to build Atomos.  Atomos build uses the latest 1.0.0.Beta2 version of the moditect plugin (https://github.com/moditect/moditect.git).  
This plugin provides some cool utilities for adding module-infos to existing dependency JARs and building `jlink` images.  You can build the Atomos with the following:

`mvn clean install -Pjava8`

This should create a jlink image under `atomos/atomos.tests/service.image/target/atomos`.  Executing the following command
against the jlink image should produce a gogo shell prompt:

`./bin/atomos`

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

You can also load additional modules into atomos at:

 - System start
by using the `atomos.modules` option when launching `atomos`.
For example:

```
atomos/bin/atomos atomos.modules=/path/to/more/modules
```

 - Runtime
by using the gogo command `atomos:install`
For example:

```
atomos:install MyLayerName OSGI /path/to/more/modules
```


When doing that the additional modules will be loaded into a child layer where the Atomos OSGi Framework
will control the class loaders.  This will produce a class loader per module bundle installed.  This has
advantages because it allows the module class loader for the bundle to implement the
`org.osgi.framework.BundleReference` interface.

# Substrate

An example project of using Graal Substrate is located at `atomos/atomos.tests/service.substrate`.  This project is not built as part of the main
Atomos build because it requires an installation of GraalVM CE 19.3.0 (Java 8 or Java 11 can be used) and the native-image tools for Substrate.
The Java 11 version of Graal Substrate does not currently support full introspection at image runtime of the Java Platform Module System.
Atomos Module support expects to have full introspection of the Java Platform Module System when running on Java versions greater than Java 8.
Therefore the example will run in basic class path mode for both Java 8 and Java 11 when running with a native substrate image.
To build the Substrate example the main Atomos build must first be built using the Java 8 profile:

`mvn clean install -Pjava8`

Note that `install` target must be used so that Atomos is installed into your local m2 repository.  This still requires Java 11 to be used to 
build but the result allows the `atomos.framework` JAR to be used on Java 8.

To build the native image you must to install the native image support for Graal (see https://www.graalvm.org/docs/reference-manual/native-image/).  You need to 
run the `gu` command that comes with Graal VM: 

`gu install native-image`

Next you must switch to a Java installation of Graal with the Substrate native-image tools installed and then change into the `atomos/atomos.tests/service.substrate` and
run `mvn clean package`

This will create a `target/atomos` executable.  It also creates a `target/substrate_lib/`.  This contains all the original bundle JARs that
got compiled into the native image `atomos`.  In order to launch the native `atomos` you must be in the directory containing both `atomos`
and the `substrate_lib/` folder.  This is currently used as a simple way for Atomos to discover the available bundles and load additional
resources from them at runtime. Hopefully substrate will add full introspection to the Java Platform Module System in the future which would
allow Atomos to discover the modules within the image and load them as bundles.  Otherwise, more work is needed to map all of the bundle resources
into the native image at build time and provide more meta-data in the image for Atomos to use to discover the bundles included.

If you launch `atomos` it will give you a gogo `g!` prompt to run gogo commands.  Also included in this example is a version of the Felix
web console.  The web console can be access with http://localhost:8080/system/console/bundles and the id/password is admin/admin.


