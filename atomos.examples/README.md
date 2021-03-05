# Atomos Examples

The following examples are available:
1. [Atomos Index example](atomos.examples.index/README.md) - Assembles an executable JAR which uses an Atomos index for bundle entry content.
1. [Atomos jlink example](atomos.examples.jlink/README.md) - Assembles a jlink image that loads the framework and a set of bundles as modules included in a fit for purpose JVM image
1. [Atomos Spring Loader example](atomos.examples.springloader/README.md) - Assembles an executable JAR that uses the Spring Jar loader to load bundle content from embedded JAR files.
1. [Atomos native-image lib folder example](atomos.examples.substrate.lib/README.md) - Assembles a native image using Graal Substrate to load the framework and a set of bundles
1. [Atomos native-image maven plugin example](atomos.examples.substrate.maven/README.md) - Assembles a native image using Graal Substrate with the atomos-maven-plugin
1. [Atomos Android example](atomos.examples.android/README.md) - Uses the result of the [Atomos index example](atomos.examples.index/README.md) to build an Android application
1. [Atomos JAX-RS example](atomos.examples.jaxrs/README.md) - Uses Aries JAX-RS [whiteboard](https://github.com/apache/aries-jax-rs-whiteboard) implementation for a simple JAX-RS resource example.
