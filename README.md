Atomos - A Java Module Runtime using OSGi Connect

With the release of [OSGi Core Release 8](https://docs.osgi.org/specification/osgi.core/8.0.0/), the [Connect](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.connect.html) Specification enables OSGi frameworks to be extended to support environments not previously possible in a standard way. Atomos uses the Connect specification to extend the use of the OSGi framework into many different environments.

# Examples

A number of example projects are contained in the Atomos git repository under the folder `atomos.examples`. 

1. Run from the [class path](atomos.examples/atomos.examples.springloader/README.md)
1. Run from the module path with [jlink](atomos.examples/atomos.examples.jlink/README.md)
1. Run with a [native image](atomos.examples/atomos.examples.substrate.maven/README.md)

See Atomos examples [README](atomos.examples/README.md) for more information.

# Module Path and Class Path Usage

Two simple ways to use Atomos is with the flat class path or the module path. Typically an OSGi framework requires complete control over loading classes and resources out of bundles. This allows the framework to provide the isolation defined by the OSGi specification through the use of its own class loader implementation. For example, only allowing a bundle to load classes and resources which are either local to the bundle or imported with the `Import-Package` or `Require-Bundle` header. Using Atomos with the module path or the class path takes that control away from the framework and delegates it to the class loader implementation provided by the JVM for the module path or class path. This will result in different behavior with respect to class loading which may reduce the level of isolation enforced at runtime. This is particularly true when using the class path. Use of the module path will use the enforcement rules of the Java Platform Module System (JPMS), which has some differences with the OSGi Module Layer.

The `Atomos` class provides a convenient way to launch an OSGi framework implementation with support for the module path or the class path. For example, to load all bundles included in a directory called `bundles` the following `java` command can be run using the class path:

`java -cp "bundles/*" org.apache.felix.atomos.Atomos`

The following `java` command can be run using the module path:

`java -p bundles -m org.apache.felix.atomos`

In both cases Atomos will discover all the JARs contained in the `bundles/` directory.  For each bundle JAR included in `bundles/` the launcher will install and start each bundle in an OSGi framework instance. The bundles are loaded using the class loader provided by the JVM itself. In this case the framework is not in control of the class loading for the bundles contained on the class path or the module path. 

Atomos requires a compliant OSGi R8 framework implementation. In addition, when running on the module path, Atomos requires a module named `osgi.core` to represent the framework implementation. Atomos provides two `osgi.core` modules to represent Equinox (`org.apache.felix.atomos:osgi.core:8.0.0:jar:AtomosEquinox`) and Felix (`org.apache.felix.atomos:osgi.core:8.0.0:jar:AtomosFelix`). One of these modules along with the corresponding framework implementation JAR (`org.eclipse.osgi` or `org.apache.felix.framework`) need to be included on the module path.

For example, consider a `bundles/` folder containing the necessary bundles to run the Gogo console:

```
jline-3.13.3.jar
org.apache.felix.atomos-1.0.0.jar
org.apache.felix.gogo.command-1.1.2.jar
org.apache.felix.gogo.jline-1.1.8.jar
org.apache.felix.gogo.runtime-1.1.4.jar
org.eclipse.osgi-3.16.100.jar
osgi.core-8.0.0-AtomosEquinox.jar
```
The JARs `osgi.core-8.0.0-AtomosEquinox.jar` and `org.eclipse.osgi-3.16.100.jar` provide the framework implementation for Equinox.  When running on Java 11 or higher, regardless of using the class path or module path, Atomos will discover the available modules from the boot layer and represent them as bundles in the framework. For example, running `java -p bundles -m org.apache.felix.atomos` on the above `bundles/` folder will get you this result for the Gogo `lb -s` command:

```
g! lb -s
START LEVEL 1
   ID|State      |Level|Symbolic name
    0|Active     |    0|org.eclipse.osgi (3.16.100.v20201030-1916)|3.16.100.v20201030-1916
    1|Active     |    1|java.base (11.0.8)|11.0.8
    2|Active     |    1|java.compiler (11.0.8)|11.0.8
    3|Active     |    1|java.datatransfer (11.0.8)|11.0.8
    4|Active     |    1|java.desktop (11.0.8)|11.0.8
   ...
   71|Active     |    1|org.apache.felix.atomos (1.0.0)|1.0.0
   72|Active     |    1|org.apache.felix.gogo.command (1.1.2)|1.1.2
   73|Active     |    1|org.apache.felix.gogo.jline (1.1.8)|1.1.8
   74|Active     |    1|org.apache.felix.gogo.runtime (1.1.4)|1.1.4
   75|Active     |    1|org.jline (3.13.3)|3.13.3
   76|Active     |    1|osgi.core (0.0.0)|0.0.0
```

Running with the module path will load all modules discovered within the `bundles/` folder and the JVM modules in the boot layer as bundles in the framework. This is true even for modules that do not contain an OSGi bundle manifest. Running with `java -cp "bundles/*" org.apache.felix.atomos.Atomos` will produce nearly identical results with the exception that the `osgi.core` module will not be installed. This is because the `osgi.core` module from Atomos does not contain a bundle manifest and therefore it is not recognized as something to represent as a bundle in the framework. Only the modules contained in the boot layer will be represented as bundles installed in the framework when using the class path.

Atomos currently can support Java 8 as well which does not have support for the module path. When using Java 8, the module path mode is not available.

# Connecting Content

The OSGi Connect specification allows an implementation of a [ModuleConnector](https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/connect/ModuleConnector.html) to be used to create a new framework instance with the [ConnectFrameworkFactory.newFramework](https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/connect/ConnectFrameworkFactory.html#newFramework-java.util.Map-org.osgi.framework.connect.ModuleConnector-) method.

Atomos provides a `ModuleConnector` that adds support to enable bundles to be connected to four different sources of content:

1. Module Path:  Using the Java Platform Module System (JPMS) Atomos will discover the modules on the module path and will make any modules found available for installation into the framework as bundles.  This also allows for Atomos and a set of bundles to be packaged into a jlink image resulting in a small fit-for-purpose JVM.
1. Class Path:  When loaded from the class path Atomos will discover the JARs on the class path and will make any bundles found available for installation into the framework.
1. Graal Substrate Native Image:  When compiled into a Substrate native image Atomos will discover the bundles that were included into the image.  This requires configuration to enable the necessary reflection for things like bundle activators and declarative service components.
1. Atomos Bundle Index: Allows a single executable JAR to contain multiple bundles.  The bundles included in the executable JAR have their resources indexed to allow for duplicate resource names to be included in each bundle.  For example, the `META-INF/MANIFEST.MF` bundle manifest file. One usecase for the bundle index is in the creation Android applications.

# Atomos API

The `Atomos` class provides convenient methods to create and launch an OSGi framework implementation with Atomos support. The `main` method can be used to discover, install and start all the bundles found into a framework instance. In some scenarios more control is necessary to configure the framework and its set of installed bundles. The `Atomos` class allows more control over the configuration, bundle installation and launching of the framework instance.

## Creating A Framework

Atomos provides a [ModuleConnector](https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/connect/ModuleConnector.html)
implemenation that can be used to create a [Framework](https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/Framework.html) instance. The following is an example of how to use a [ConnectFrameworkFactory](https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/connect/ConnectFrameworkFactory.html) to create a framework instance that uses Atomos:

```java
     ServiceLoader<ConnectFrameworkFactory> loader = ServiceLoader.load(ConnectFrameworkFactory.class);
     ConnectFrameworkFactory factory = loader.findFirst().get();
     Framework framework = factory.newFramework(
                               Map.of(
                                  Constants.FRAMEWORK_SYSTEMPACKAGES, ""),
                               Atomos.newAtomos().getModuleConnector());
```

The framework must be configured with `org.osgi.framework.system.packages=""` when running on Java 9+ to configure the `system.bundle` to not export any of the packages provided by the JVM boot layer. Alternatively, a framework can be constructed using the `Atomos.newFramework` method like the following:

```java
     Framework framework = Atomos.newAtomos().newFramework(Map.of());
```

In this case the `org.osgi.framework.system.packages` configuration will be set appropriately for the running Java version. In most all cases the `Atomos.newFramework` method can be used.

When the framework is initialized Atomos will install and start all bundles that have been discovered by default. To disable starting the bundles on framework initialization the `atomos.content.start` framework configuration property can be used:

```java
     Framework framework = Atomos.newAtomos().newFramework(Map.of("atomos.content.start", "false"));
```

To disable installing the bundles on framework initialization the `atomos.content.install` framework configuration property can be used:

```java
     Framework framework = Atomos.newAtomos().newFramework(Map.of("atomos.content.install", "false"));
```
## Installing Bundles

Special handling is needed to install content discovered by Atomos as bundles in a framework. Atomos has layers which are used to contain content it discovers.  For example, the modules on the module path or the JARs on the class path are considered content. When an `Atomos` instance is created it discovers the available content in the environment it is running (e.g. the module path or class path).  This initial content is placed into an `AtomosLayer` that is called the boot layer. Each content that is discovered is represented by an `AtomosContent` instance. An `AtomosContent` can be used to install a connected bundle.

### Connected Bundles

As mentioned, `AtomosContent` is contained with an `AtomosLayer`. The following code can be used to install and start all the content discovered by Atomos from the boot layer:

```java
    Atomos atomos = Atomos.newAtomos();
    // Set atomos.content.install to false to prevent automatic bundle installation
    Framework framework = atomos.newFramework(Map.of("atomos.content.install", "false"));
    // framework must be initialized before any bundles can be installed
    framework.init();
    List<Bundle> bundles = new ArrayList<>();
    for (AtomosContent content: atomos.getBootLayer().getAtomosContents()) {
        // The resulting bundle will use a bundle location of
        // "atomos:" + atomosContent.getAtomosLocation();
        bundles.add(content.install());
    }
    for (Bundle b : bundles) {
        b.start();
    }
    // The installed bundles will not actually activate until the framework is started
    framework.start();
```

This allows for control over what gets exposed as a bundle from the discovered Atomos content.  An alternative to using the `AtomosContent.install` method is to use a `BundleContext` to install the bundles. Before this can be done the `AtomosContent` must be connected with the bundle location that will be used to install the bundle. The following code can be used to do that instead:

```java
    Atomos atomos = Atomos.newAtomos();
    // Set atomos.content.install to false to prevent automatic bundle installation
    Framework framework = atomos.newFramework(Map.of("atomos.content.install", "false"));
    // framework must be initialized before any bundles can be installed
    framework.init();
    BundleContext systemContext = framework.getBundleContext();
    List<Bundle> bundles = new ArrayList<>();
    Random random = new Random();
    for (AtomosContent content: atomos.getBootLayer().getAtomosContents()) {
        String location = String.valueOf(new Random().nextLong()) + "-location";
        // Connect the content to a specific location.
        content.connect(location);
        // Use the same location to install the bundle.
        // Note that an input stream cannot be provided for installing connected bundles,
        // only the location string can be used.
        bundles.add(systemContext.installBundle(location));
    }
    for (Bundle b : bundles) {
        b.start();
    }
    // The installed bundles will not actually activate until the framework is started
    framework.start();
```

This allows for complete control over the bundle location string used to install the bundles.

### Standard Bundles

Bundles may be installed which are not contained in an `AtomosLayer`. This is done by using one of the [BundleContext.install](https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/BundleContext.html#installBundle-java.lang.String-) methods using a location string which has not been connected with an `AtomosContent`. In this case the bundle class loading and resource access is under the complete control of the framework. Standard bundles may depend on any other bundles installed in the framework, including the connected bundles contained in an `AtomosLayer`. However, bundles contained in an `AtomosLayer` cannot depend on standard bundles.

## Adding Layers

When running on Java 9+ additional JPMS layers can be added and removed dynamically using the `AtomosLayer` API. An `AtomosLayer` is added as a child layer to one or more existing `AtomosLayer` instances. The following can be used to install two different sibling layers as a child of the boot layer:

```java
    Atomos atomos = Atomos.newAtomos();
    AtomosLayer bootLayer = atomos.getBootLayer();

    // Add a layer that loads all modules contained in "modules-child1" directory
    Path modulesChild1 = new File("modules-child1").toPath();
    AtomosLayer child1 = bootLayer.addLayer("child1", LoaderType.SINGLE, modulesChild1);

    // Add a layer that loads all modules contained in "modules-child1" directory
    Path modulesChild2 = new File("modules-child2").toPath();
    AtomosLayer child2 = bootLayer.addLayer("child2", LoaderType.SINGLE, modulesChild2);

    // Create a new framework to use the layers configured with Atomos
    Framework framework = atomos.newFramework(Map.of());
    // Starting the framework will automatically install and start the content from all
    // Atomos layers known.  This will include the content in child1 and child2.
    // The "atomos.content.install" configuration can be used to disable that.
    framework.start();

    // stopping the framework will persist the added layers in the framework storage area
    framework.stop();
```

A layer can be added before or after creating and starting the framework instance with Atomos. If added before initializing the framework then all of the known layers configured with Atomos will have their bundles installed and started according to the `atomos.content.install` framework configuration setting.

When adding a layer there is a choice of the loader type.  This refers to the class loader behavior.  Atomos has three different loader types available.

1. `SINGLE` - All modules in the layer will be loaded with a single class loader
1. `MANY` - All modules in the layer will get their own individual class loader
1. `OSGI` - All modules in the layer will get their own individual class loader that behaves the same as `MANY` except the class loader will implement the `BundleReference` interface.

Once a layer is added the content can be installed just like the example above with the boot layer.  When a framework is initialized, the storage area for the framework will be used to save the layer information to persistent storage.  When the framework is created again, using the same framework storage location, the Atomos layers will be loaded back into Atomos.

A layer can be dynamically uninstalled using the AtomosLayer.uninstall() method. This will result in the layer and all of its children layers being removed from Atomos.  Any bundles that are installed from the removed layers will also be uninstalled from the framework.

It is possible to get the `Module` that backs an `AtomosContent` by calling the method `AtomosContent.adapt(Module.class)`.  Similarly it is possible to get the `ModuleLayer` that backs an `AtomosLayer` by calling the method `AtomosLayer.adapt(ModuleLayer.class)`.

The examples above demostrate the commonly used hierarchy with single parent layers. More advanced scenarios that require multi-parent layers must use the `Atomos.addLayer(List<AtomosLayer>, String, LoaderType, Path...)` method instead of the `AtomosLayer.addLayer` method.

## OSGi Capability Resolution and Service Visibility

When running Atomos on the module path the class loading rules are defined by the Java Platform Module System (JPMS). This implies that there is a resolution step done within JPMS when configuring the `ModuleLayer` that backs the `AtomosLayer`. When an OSGi bundle is connected to `AtomosContent`, the framework will attempt to resolve the bundle within the framework as well. The resolution done by JPMS for the modules contained in an `AtomosLayer` will influence how the bundle connected to the JPMS module will resolve within the OSGi framework.

### OSGi Bundle Resolution

The OSGi bundles that are connected to `AtomosContent` located in a JPMS layer will resolve against capabilities for the namespaces `osgi.wiring.package`  and `osgi.wiring.bundle` according the behavior of the `java.lang.Module.canRead(Module)` method.  All other namespaces are resolved against capabilities provided by modules within the same layer or modules contained in the parent layer hierarchy. This implies that the requirements for an AtomosContent bundle cannot be resolved against capabilities from standard installed bundles or from bundles contained in child or peer layers.

### OSGi Service Visibility

The service availability in the OSGi service registry is not influenced by the layer hierarchy. Standard bundles are able to use OSGi services registered by connected bundles and vise versa as long as the consumer and producer bundles are using the same source for the service package.  There is no additional isolation of OSGi services between the different layers in Atomos.

# Build

Java 11 or higher must be used to build Atomos.  Atomos build uses the 1.0.0.RC1 version of the moditect plugin (https://github.com/moditect/moditect.git). This plugin provides some utilities for adding module-infos to existing dependency JARs and building `jlink` images.  You can build the Atomos with the following:

`./mvnw clean install -Pjava8 -Pequinox`

Or if you want to use the Felix framework

`./mvnw clean install -Pjava8 -Pfelix`

To build you must specify one of the framework implementation profiles (`equinox` or `felix`). If you build without the `java8` profile then the resulting Atomos runtime will only work with Java 11+. The build also includes a number of example projects that demonstrate how Atomos can be used in different modes. The Graal Substrate native-image examples are not built by default. For information on how to build the native-image examples see the substrate [README](atomos.examples/SUBSTRATE.md)

