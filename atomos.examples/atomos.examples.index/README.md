# Atomos Index Example

This example uses the `atomos-maven-plugin` to create an Atomos index. An Atomos index contains all the bundle entry resources of the bundles that are required by the example.  For this example the necessary bundles are included to have a functional Felix Gogo console and a Felix WebConsole.  The Atomos index can then be included into the final JAR along with all the required packages from all the bundles included in the JAR.

This example uses `maven-assembly-plugin` to package all the dependent bundles into a single executable JAR which also includes the Atomos index. The Atomos launcher class `org.apache.felix.atomos.launch.AtomosLauncher` is used as the `main-class` for the executable JAR. If you introspect the JAR produced by this example you will notice it includes an `atomos/` folder which includes all the bundle entry resources for all the dependent bundles. This allows for duplicate bundle entry paths to be included in the JAR which Atomos can discover for each bundle included in the single JAR.

The following command should produce a gogo shell prompt:

`java -jar target/org.apache.felix.atomos.examples.index-<version>.jar`

Where `<version>` is the current version of this example. When executed all the bundles are loaded using the single application class loader provided by the JVM. Additional bundles can be installed, but these additional bundles will be loaded by the typical bundle class loader provided by the OSGi Framework.

Once the example is launched you can access the web console with the URL [http://localhost:8080/system/console/bundles](http://localhost:8080/system/console/bundles) with the id/password of admin/admin.

On its own the Atomos Index example is not that interesting. But it does open the possibility to include it in other examples that need to compose the bundles into a single artifact. For example, to produce an Android application or a native-image which includes all the bundles of your application.