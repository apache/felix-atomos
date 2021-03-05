# Atomos Substrate Example (atomos-maven-plugin)

This example builds a native image that includes the Equinox Framework implementation with a set of bundles using Graal Substrate.  See the substrate [README](../SUBSTRATE.md) for instructions on building the Substrate examples. This example uses the `atomos-maven-plugin` plugin to generate an Atomos index which is then included in the native-image. This allows for a native-image that does not require the presence of the original bundle JARs at runtime in an `atomos_lib/` folder.

Buiding this example will create a folder `target/native_image_build/bin` folder that contains the executable `org.apache.felix.atomos.examples.jaxrs`. If you launch `org.apache.felix.atomos.examples.jaxrs` it will give you a gogo `g!` prompt to run gogo commands. The example itself contains a bundle that provides a JAX-RS hello resource. The resource can be accessed using `http://localhost:8080/hello/{name}`.  Also included in this example is a version of the Felix web console.  The web console can be access with http://localhost:8080/system/console/bundles and the id/password is admin/admin.

This example also builds an uber JAR using the Atomos Index.  This example can be run with the following command:

`java -jar target/org.apache.felix.atomos.examples.jaxrs-<version>.jar`

Where `<version>` is the current version of this example. The index JAR contains the same bundles as the native image and the Atomos index used for both the native and the Atomos index JAR should be the same.  This can be useful when debugging the behavior difference for the native image vs. a normal Java application running on the classpath.  Such an approach also makes it easy to use the Atomos index JAR to run with the Graal tracing [agent](https://medium.com/graalvm/introducing-the-tracing-agent-simplifying-graalvm-native-image-configuration-c3b56c486271) for discovering the necessary substrate configuration.