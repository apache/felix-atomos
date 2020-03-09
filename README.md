# Atomos [![Java CI](https://github.com/apache/felix-atomos/workflows/Java%20CI/badge.svg)](https://github.com/apache/felix-atomos/actions?query=workflow%3A%22Java%20CI%22) [![Apache Slack chat](https://img.shields.io/badge/ASF%20Slack-%23felix--atomos-yellow)](https://join.slack.com/share/IV58N2A1L/2uiZ00qrH7wuBi1Dpgdv263g/enQtOTkxMjk0MDc4MDU0LWU3M2ZiYTczZGY5ZTNhZjI5M2NhMzNjYTdmN2VlMzg0NTU3NzEyOGI0MWJmYzU1YjI1ZjNhMTMzMzg4Y2RmNDk) [![Gitpod Ready-to-Code](https://img.shields.io/badge/Gitpod-Ready--to--Code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/apache/felix-atomos)

Atomos - A Java Module Runtime using OSGi Connect

Atomos requires an OSGi Framework implementation which supports OSGi Connect initially described in this OSGi [blog post](https://blog.osgi.org/2019/09/osgi-connect-revisited.html).  The OSGi Connect specification is currently being developed as an RFC with the OSGi Alliance and the current version of the RFC can be found [here](https://github.com/osgi/design/blob/master/rfcs/rfc0243/rfc-0243-Connect.pdf).

Currently a snapshot of the Equinox and Felix OSGi Framework are being used that implements the proposed OSGi Connect specification for an upcoming OSGi R8 Core specification. Source for the snapshots can be found at:
1. Equinox - The `osgiR8` branch in the git repo https://git.eclipse.org/c/equinox/rt.equinox.framework.git
1. Felix - https://svn.apache.org/repos/asf/felix/sandbox/pauls/connect

The snapshot JARs and source JARs are pushed to https://github.com/tjwatson/atomos-temp-m2repo for Atomos. The Atomos build is currently configured to use this as a repository for getting the OSGi Framework implementations: https://github.com/tjwatson/atomos-temp-m2repo/raw/master/repository

Atomos is an implementation of an OSGi `ModuleConnector` which is part of the upcoming OSGi R8 Connect specification. A `ModuleConnector` can be used to create an OSGi Framework instance that allows a Framework to connect bundles installed in the framework to content managed outside of the Framework. Framework instances created with the Atomos `ModuleConnector` add support to the OSGi Framework that enables bundles to be connected to three different sources of content from outside the OSGi module layer:

1. Module Path:  Using the Java Platform Module System (JPMS) Atomos will discover the modules on the module path and will make any modules found available for installation into the Framework as connected bundles.  This also allows for Atomos and a set of OSGi bundles to be packaged into a jlink image resulting in a small fit-for-purpose JVM.
1. Class Path:  When loaded from the class path Atomos will discover the JARs on the class path and will make any OSGi bundles found available for installation into the Framework.
1. Graal Substrate Native Image:  When compiled into a Substrate native image Atomos will discover the bundles that were included into the image.  This requires configuration to enable the necessary reflection for things like bundle activators and declarative service components.


# Build

Java 11 or higher must be used to build Atomos.  Atomos build uses the 1.0.0.Beta2 version of the moditect plugin (https://github.com/moditect/moditect.git). This plugin provides some utilities for adding module-infos to existing dependency JARs and building `jlink` images.  You can build the Atomos with the following:

`./mvnw clean install -Pjava8 -Pequinox`

Or if you want to use the Felix Framework

`./mvnw clean install -Pjava8 -Pfelix`

If you build with no profile specified then the default will build with Equinox and the resulting Atomos runtime will only work with Java 11.

This should create a jlink image under `atomos/atomos.examples/atomos.examples.jlink/target/atomos`. Executing the following command against the jlink image should produce a gogo shell prompt:

`./bin/atomos`

You should see the following output:

```
Registered Echo service from activator.
____________________________
Welcome to Apache Felix Gogo

g!
```

In order to successfully build a jlink image all bundles included in the image must contain a `module-info.class`, they cannot be automatic modules. The `atomos/atomos.examples/atomos.examples.jlink` example uses the `1.0.0.Beta2` version of the `moditect-maven-plugin` to add `module-info.class` as necessary to the bundles used in the image.

You can also load additional modules into atomos at:

 - System start
by using the `atomos.modules` option when launching `atomos`. For example:

```
atomos/bin/atomos atomos.modules=/path/to/more/modules
```

 - Runtime
by using the gogo command `atomos:install`. For example:

```
atomos:install MyLayerName OSGI /path/to/more/modules
```

When doing that the additional modules will be loaded into a child layer where the Atomos OSGi Framework will control the class loaders.  This will produce a class loader per module bundle installed.  This has advantages because it allows the module class loader for the bundle to implement the `org.osgi.framework.BundleReference` interface.

# Substrate

There are two examples projects that build native images using Graal Substrate:
1. `atomos/atomos.examples/atomos.examples.substrate.equinox` - Using Eclipse Equinox Framework
1. `atomos/atomos.examples/atomos.examples.substrate.felix` - Using Apache Felix Framework

These two example projects are not built as part of the main Atomos build because they require an installation of GraalVM CE 19.3.1 (Java 8 or Java 11 can be used) and the native-image tools for Substrate. The Java 11 version of Graal Substrate does not currently support full introspection at image runtime of the Java Platform Module System. Atomos Module support expects to have full introspection of the Java Platform Module System when running on Java versions greater than Java 8. Therefore the example will run in basic class path mode for both Java 8 and Java 11 when running with a native substrate image.

To build the native image you must install the native image support for Graal (see https://www.graalvm.org/docs/reference-manual/native-image/).  You need to run the `gu` command that comes with Graal VM:

`gu install native-image`

If you are using GraalVM CE 19.3.1 Java 11 then you can build all of Atomos, including the substrate examples for Equinox and Felix, with the following single maven build using the `substrate` profile:

`./mvnw clean install -Pjava8 -Psubstrate -Pequinox`

If using GraalVM CE 19.3.1 Java 8 then you must first use Java 11 for the main Atomos build using the Java 8 profile:

`./mvnw clean install -Pjava8 -Pequinox`

Note that `install` target must be used so that Atomos is installed into your local m2 repository. This still requires Java 11 to be used to build but the result allows the `atomos.framework` JAR to be used on Java 8. Next you must switch to a Java installation of Graal with the Substrate native-image tools installed and then run the following maven builds:

`./mvnw clean install -Pjava8 -f atomos.examples/atomos.examples.substrate.equinox/pom.xml`

`./mvnw clean install -Pjava8 -f atomos.examples/atomos.examples.substrate.felix/pom.xml`

This will create a `target/atomos` executable in each substrate example project. If you launch `atomos` it will give you a gogo `g!` prompt to run gogo commands.  Also included in this example is a version of the Felix web console.  The web console can be access with http://localhost:8080/system/console/bundles and the id/password is admin/admin.

For the Felix and Equinox example a directory `target/atomos_lib/` is created.  This contains all the original bundle JARs that got compiled into the native image `atomos`.  In order to launch the native `atomos` you must be in the directory containing both `atomos` and the `atomos_lib/` folder.  This is a simple way for Atomos to discover the available bundles and load additional bundle entries at runtime.

Alternatively a substrate image can be created that does not rely on the directory `target/atomos_lib/` to discover the bundles.  Instead the bundle entry resources can be placed in an `atomos/` folder which is placed on the classpath during native image compilation. The resources from the `atomos/` folder can then be included in the native image.  The `atomos/` folder has a file `bundles.index` that contains information for Atomos to discover the bundles and their entries that are included in the native image. In order to use this approach effectively Atomos needs a maven plugin to assist in the generation of the Atomos `bundles.index`.

If substrate adds full introspection to the Java Platform Module System in the future it could allow Atomos to discover the modules within the image and load them as bundles.  If a proper module reader could be obtained and contain the necessary resources from the original bundle JARs then it would eliminate the need for the `atomos_lib/` or `atomos/` resource folder.
