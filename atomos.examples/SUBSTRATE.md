# Building Substrate Examples

The example projects that build Graal Substrate native-images are not built as part of the main Atomos build because they require an installation of GraalVM CE 19.3.1 (Java 8 or Java 11 can be used) and the native-image tools for Substrate. The Java 11 version of Graal Substrate does not currently support full introspection at image runtime of the Java Platform Module System. Atomos Module support expects to have full introspection of the Java Platform Module System when running on Java versions greater than Java 8. Therefore the example will run in basic class path mode for both Java 8 and Java 11 when running with a native substrate image.

To build the native image examples you must install the native image support for Graal (see https://www.graalvm.org/docs/reference-manual/native-image/).  You need to run the `gu` command that comes with Graal VM:

`gu install native-image`

If you are using GraalVM CE 19.3.1 Java 11 then you can build all of Atomos, including the substrate examples, with the following single maven build using the `substrate` profile:

`./mvnw clean install -Pjava8 -Psubstrate -Pequinox`

If using GraalVM CE 19.3.1 Java 8 then you must first use Java 11 for the main Atomos build using the Java 8 profile:

`./mvnw clean install -Pjava8 -Pequinox`

Note that `install` target must be used so that Atomos is installed into your local m2 repository. This still requires Java 11 to be used to build but the result allows the `atomos.framework` JAR to be used on Java 8. Next you must switch to a Java installation of Graal with the Substrate native-image tools installed and then run the maven builds for the substrate example projects:

This will create a `target/atomos` executable in each substrate example project. If you launch `atomos` it will give you a gogo `g!` prompt to run gogo commands.  Also included in this example is a version of the Felix web console.  The web console can be access with http://localhost:8080/system/console/bundles and the id/password is admin/admin.

For the Felix and Equinox example a directory `target/atomos_lib/` is created.  This contains all the original bundle JARs that got compiled into the native image `atomos`.  In order to launch the native `atomos` you must be in the directory containing both `atomos` and the `atomos_lib/` folder.  This is a simple way for Atomos to discover the available bundles and load additional bundle entries at runtime.

Alternatively a substrate image can be created that does not rely on the directory `target/atomos_lib/` to discover the bundles.  Instead the bundle entry resources can be placed in an `atomos/` folder which is placed on the classpath during native image compilation. The resources from the `atomos/` folder can then be included in the native image.  The `atomos/` folder has a file `bundles.index` that contains information for Atomos to discover the bundles and their entries that are included in the native image. In order to use this approach effectively Atomos needs a maven plugin to assist in the generation of the Atomos `bundles.index`.

If substrate adds full introspection to the Java Platform Module System in the future it could allow Atomos to discover the modules within the image and load them as bundles.  If a proper module reader could be obtained and contain the necessary resources from the original bundle JARs then it would eliminate the need for the `atomos_lib/` or `atomos/` resource folder.
