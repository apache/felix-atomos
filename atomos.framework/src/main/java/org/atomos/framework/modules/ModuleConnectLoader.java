package org.atomos.framework.modules;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URL;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

public final class ModuleConnectLoader extends SecureClassLoader implements BundleReference {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ResolvedModule resolvedModule;
    private final ModuleReference reference;
    private final ModuleReader reader;
    private final AtomosRuntimeModules atomosRuntime;
    private final AtomicReference<Module> module = new AtomicReference<>();

    public ModuleConnectLoader(ResolvedModule resolvedModule, AtomosRuntimeModules atomosRuntimeModules) throws IOException
    {
        super("ModuleConnectLoader-" + resolvedModule.name(), null);

        this.resolvedModule = resolvedModule;
        this.reference = resolvedModule.reference();
        this.reader = reference.open();
        this.atomosRuntime = atomosRuntimeModules;
    }

    void initEdges(Module module, Configuration loaderConfig, List<ModuleLayer> parentLayers, Map<String, ? extends ClassLoader> loaders)
    {
    	this.module.set(module);
    	// setup the package -> class loader mapping to other module loaders
    	
    	// Use resolvedModule.reads() to find the set of all other modules this module can read from.
    	// This is the set of modules this module can load classes from.
    	// For each ResolvedModule that the module can read:
    	//   If that module's configuration is the same as the loaderConfig then use the loader provided
    	//     by the loaders map; which is keyed by the module name
    	//   Else find the layer for the module and find the loader using 
    	//     java.lang.ModuleLayer.findLoader(name) using the module name
    	//     It is possible the ModuleLayer will return null, in that case just use the platform class loader

    	//   Once you have the loader find use ModuleDescriptor.exports() of the module.
    	//     Create a mapping for each package name -> loader; pay attention to if the export is qualified or not
    }



    // -- resources --

    /**
     * Returns a URL to a resource of the given name in a module defined to
     * this class loader.
     */
    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
    	// TODO
    	// The moduleName is the module which is trying to find the resource.
    	// If the module name is not equal to this loaders module name, return null
    	
    	// Otherwise use the reader to find the named resource; should protect with doPriv
    	return null;
    }

    @Override
    public URL findResource(String name) {
        // get the package name of the resource
    	// check if the ModuleDescriptor for the module reference contains the package name in its packages()
    	// If not return null
    	// otherwise use findResource(String, String) with the assumption that this module is driving the find
    	// If the resource does not end in ".class" then check if the package is open for reflection.
    	// non-class resources should be returned by this method if the package is open
    	return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
    	// TODO
    	return null;
    }

    @Override
    public URL getResource(String name) {
        // first check this class loader resource same as findResource(String name) does
    	// if not found check for the system resource (i.e. ClassLoader.getSystemResource(name))
    	return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
    	// first check this class loader resources same as findResources(String name) does
    	// then combine the results with ClassLoader.getSystemResources(name);
    	return null;
    }

    // -- finding/loading classes

    /**
     * Finds the class with the specified binary name.
     */
    @Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
    	// map class name to a resource name and use reader to find the bytes
    	// use  SecureClassLoader.defineClass(String, ByteBuffer, CodeSource) to define the class
    	// any unexpected checked exceptions should be wrapped in a LinkageError that LinkageError should be thrown
    	return null;
    }

    @Override
    protected Class<?> findClass(String moduleName, String className) {
        // do the same as findClass(String className) except return null instead of exception on not found
    	return null;
    }

    /**
     * Loads the class with the specified binary name.
     */
    @Override
    protected Class<?> loadClass(String className, boolean resolve)
        throws ClassNotFoundException
    {
    	// synchronize on getClassLoadingLock(className)
    	// find if the class is already loaded and return it if so.
    	// otherwise; check ModuleDescriptor.pacakges to see if it contains the package for the requested class
    	//   if so do the same thing as findClass(String, String)
    	// otherwise; check for packages this module can read from and if you have another loader for the package
    	//   Then call the other loader.loadClass(className)
    	// if any class is found then call resolveClass on it if the resolve param is true
    	throw new ClassNotFoundException();
    }



    @Override
    protected PermissionCollection getPermissions(CodeSource cs) {
        // start with PermissionCollecction from super.getPermissions(cs)
    	// This collection may need another permission added to it if the code source location represents
    	// a directory.  In this case a recursive FilePermission needs to be added to the collection that
    	// points to the directory.
    	return super.getPermissions(cs);
    }

    @Override
    public Bundle getBundle() {
    	return atomosRuntime.getBundle(module.get());
    }
}
