# Atomos Spring Loader Example

This example uses the `spring-boot-maven-plugin` to create an executable JAR that includes each required bundle as embedded JAR files inside the single executable JAR. This executable JAR includes the `org.springframework.boot.loader` which is then used to load all the bundles included in the JAR.  For this example the necessary bundles are included to have a functional Felix Gogo console and a Felix WebConsole.  The Atomos index can then be included into the final JAR along with all the required packages from all the bundles included in the JAR.

This is not a Spring Boot example itself. It only Spring Boot loader which understands how to discover and load all the included JAR files with a single class loader. This class loader is able to load content from the embedded JAR files without requiring them to be extracted to disk first.

The Atomos launcher class `org.apache.felix.atomos.launch.AtomosLauncher` is used as the `Start-Class` for the executable JAR. If you introspect the JAR produced by this example you will notice it includes a `BOOT-INF/lib/` folder which includes all the embedded JARs for all the dependent bundles. Atomos is able to discover all the included JARs and load the bundle entry resources from them.

The following command should produce a gogo shell prompt:

`java -jar target/org.apache.felix.atomos.examples.springloader-<version>.jar`

Where `<version>` is the current version of this example. When executed all the bundles are loaded using the single class loader provided by the Spring loader. Additional bundles can be installed, but these additional bundles will be loaded by the typical bundle class loader provided by the OSGi Framework.

Once the example is launched you can access the web console with the URL [http://localhost:8080/system/console/bundles](http://localhost:8080/system/console/bundles) with the id/password of admin/admin.
