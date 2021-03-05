# Atomos jlink Example

This example uses `jlink` in order to build a Java image that includes a set of bundle and the modules they require from the base JVM installation.  This allows for a set of bundle modules to be used to build a fit for purpose Java image that only includes the required modules from the JVM.

In order to successfully build a jlink image all bundles included in the image must contain a `module-info.class`, they cannot be automatic modules. This example uses the `1.0.0.Beta2` version of the `moditect-maven-plugin` to add `module-info.class` as necessary to the bundles used in the image.

When this example is built a jlink image will be created under `target/atomos`. Executing the following command against the jlink image should produce a gogo shell prompt:

`./bin/atomos`

You should see the following output:

```
Registered Echo service from activator.
____________________________
Welcome to Apache Felix Gogo

g!
```

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