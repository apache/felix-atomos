# Atomos [![Java CI](https://github.com/apache/felix-atomos/workflows/Java%20CI/badge.svg)](https://github.com/apache/felix-atomos/actions?query=workflow%3A%22Java%20CI%22) [![Felix Atomos Chat](.github/asf-slack-felix-atomos-yellow.svg)](https://join.slack.com/share/IV58N2A1L/2uiZ00qrH7wuBi1Dpgdv263g/enQtOTkxMjk0MDc4MDU0LWU3M2ZiYTczZGY5ZTNhZjI5M2NhMzNjYTdmN2VlMzg0NTU3NzEyOGI0MWJmYzU1YjI1ZjNhMTMzMzg4Y2RmNDk) [![Gitpod Ready-to-Code](https://img.shields.io/badge/Gitpod-Ready--to--Code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/apache/felix-atomos)

Atomos - A Java Module Runtime using OSGi Connect

Atomos requires an OSGi Framework implementation that supports the OSGi Connect Specification. The Connect Specification will be released as part of the OSGi Core Release 8 Specification which is currently in draft form available [here](https://osgi.org/download/osgi.core-8.0.0-early-draft-2020-03.pdf). The Connect Specification can be found in chapter 60.

Currently snapshots of the Equinox and Felix OSGi Frameworks are being used that implement the proposed OSGi Connect specification. Source for the snapshots can be found at:
1. Equinox - The `osgiR8` branch in the git repo https://git.eclipse.org/c/equinox/rt.equinox.framework.git
1. Felix - https://github.com/apache/felix-dev/tree/connect

The snapshot JARs and source JARs are pushed to https://github.com/tjwatson/atomos-temp-m2repo for Atomos. The Atomos build is currently configured to use this as a repository for getting the OSGi Framework implementations: https://github.com/tjwatson/atomos-temp-m2repo/raw/master/repository

Atomos is an implementation of an OSGi `ModuleConnector` which is defined by the Connect specification. A `ModuleConnector` can be used to create an OSGi Framework instance that allows a Framework to connect bundles installed in the framework to content managed outside of the Framework. Framework instances created with the Atomos `ModuleConnector` add support to the OSGi Framework that enables bundles to be connected to three different sources of content from outside the OSGi module layer:

1. Module Path:  Using the Java Platform Module System (JPMS) Atomos will discover the modules on the module path and will make any modules found available for installation into the Framework as connected bundles.  This also allows for Atomos and a set of OSGi bundles to be packaged into a jlink image resulting in a small fit-for-purpose JVM.
1. Class Path:  When loaded from the class path Atomos will discover the JARs on the class path and will make any OSGi bundles found available for installation into the Framework.
1. Graal Substrate Native Image:  When compiled into a Substrate native image Atomos will discover the bundles that were included into the image.  This requires configuration to enable the necessary reflection for things like bundle activators and declarative service components.
1. Atomos Bundle Index: Allows a single executable JAR to contain multiple bundles.  The bundles included in the executable JAR have their resources indexed to allow for duplicate resource names to be included in each bundle.  For example, the `META-INF/MANIFEST.MF` bundle manifest file.


# Build

Java 11 or higher must be used to build Atomos.  Atomos build uses the 1.0.0.Beta2 version of the moditect plugin (https://github.com/moditect/moditect.git). This plugin provides some utilities for adding module-infos to existing dependency JARs and building `jlink` images.  You can build the Atomos with the following:

`./mvnw clean install -Pjava8 -Pequinox`

Or if you want to use the Felix Framework

`./mvnw clean install -Pjava8 -Pfelix`

If you build with no profile specified then the default will build with Equinox and the resulting Atomos runtime will only work with Java 11+. The build also includes a number of example projects that showcase how Atomos can be used in different modes. The Graal Substrate native-image examples are not built by default. For information on how to build the native-image examples see the substrate [README](atomos.examples/SUBSTRATE.md)

For more information on each example see Atomos examples [README](atomos.examples/README.md)
