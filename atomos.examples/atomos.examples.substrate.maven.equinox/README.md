# Atomos Substrate Example (atomos-maven-plugin)

This example builds a native image that includes the Equinox Framework implementation with a set of bundles using Graal Substrate.  See the substrate [README](../SUBSTRATE.md) for instructions on building the Substrate examples. This example uses the `atomos-maven-plugin` plugin to generate an Atomos index which is then included in the native-image. This allows for a native-image that does not require the presence of the original bundle JARs at runtime in an `atomos_lib/` folder.

Buiding this example will create a `target/atomos` executable. If you launch `atomos` it will give you a gogo `g!` prompt to run gogo commands.  Also included in this example is a version of the Felix web console.  The web console can be access with http://localhost:8080/system/console/bundles and the id/password is admin/admin.

