# Atomos Substrate Felix Example

This example builds a native image that includes the Felix Framework implementation with a set of bundles using Graal Substrate.  See the substrate [README](../SUBSTRATE.md) for instructions on building the Substrate examples.

Buiding this example will create a `target/atomos` executable. If you launch `atomos` it will give you a gogo `g!` prompt to run gogo commands.  Also included in this example is a version of the Felix web console.  The web console can be access with http://localhost:8080/system/console/bundles and the id/password is admin/admin.

For this example a directory `target/atomos_lib/` is created.  This contains all the original bundle JARs that got compiled into the native image `atomos`.  In order to launch the native `atomos` you must be in the directory containing both `atomos` and the `atomos_lib/` folder.  This is a simple way for Atomos to discover the available bundles and load additional bundle entries at runtime.

