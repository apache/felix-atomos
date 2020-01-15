/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atomos.framework.modules;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    private final HashMap<String, ClassLoader> edges = new HashMap<String, ClassLoader>();

    public ModuleConnectLoader(ResolvedModule resolvedModule, AtomosRuntimeModules atomosRuntimeModules) throws IOException
    {
        super("ModuleConnectLoader-" + resolvedModule.name(), null);

        this.resolvedModule = resolvedModule;
        //TODO remove reference and reader? Or Reader needs to be closed?
        this.reference = resolvedModule.reference();
        this.reader = reference.open();
        this.atomosRuntime = atomosRuntimeModules;
    }

    
    /** explicit call from AtomosRuntimBundle to allow resources to be freed.
     *  
     */
    public static void close() {
       //TODO 
    }
    
    /** Setup the package -> class loader mapping to other module loaders
     *
     * @param module module associated with this class loader
     * @param loaderConfig configuration containing
     * @param parentLayers
     * @param loaders
     */
    void initEdges(Module module, Configuration loaderConfig, Map<String, ? extends ClassLoader> loaders)
    {
        this.module.set(module);
        for (ResolvedModule moduleRead : resolvedModule.reads() ) {
            ClassLoader loaderForModuleRead;
            if (moduleRead.configuration().equals(loaderConfig) ) {
                loaderForModuleRead = loaders.get(moduleRead.name());
            }
            else {
                ClassLoader cl = module.getLayer().findLoader(moduleRead.name());
                loaderForModuleRead = (cl != null) ? cl : ClassLoader.getPlatformClassLoader();
            }
            moduleRead.reference().descriptor().exports().forEach( packageExport -> {
                if (!packageExport.isQualified() || packageExport.targets().contains(module.getName())) {
                    edges.putIfAbsent(packageExport.source(), loaderForModuleRead);
                }});
        }
    }

    private String packageName(String name) {
        int lSlash = name.lastIndexOf('/');
        if (lSlash < 0) {
            return "";
        }
        else {
            return name.substring(0, lSlash).replace('/','.');
        }        
    }
    
    // -- resources --

    /**
     * Returns a URL to a resource of the given name in a module defined to
     * this class loader.
     */
    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        URL resource=null;
        if (!this.resolvedModule.name().equals(moduleName)) {
            resource=null;
        }
        else {
            try {
                resource = AccessController.doPrivileged((PrivilegedExceptionAction<URL>)(() -> {
                    URI rURI = this.reader.find(name).orElse(null);
                    return rURI==null ? null : rURI.toURL();
                }));
            } catch (PrivilegedActionException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                else {
                    throw new IOException(e);
                }
            }
        }
    	return resource;
    }

    
    @Override
    public URL findResource(String name) {
    	URL retVal = null;
    	try {
    		retVal  = findResource(this.module.get().getName(), name);
    	} catch (IOException e) {
    		//ignore
    	}
    	String pkg = packageName(name);
    	if (this.module.get().getDescriptor().packages().contains(pkg)) {
    		// non-class resources should be returned by this method if the package is open unconditionally
    		if (!name.endsWith(".class") && !name.endsWith("/") && !this.module.get().isOpen(pkg)) {
    			retVal = null;
    		}
    	}
    	return retVal;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
    	// TODO
    	return null;
    }
    
    @Override
    public URL getResource(String name) {
        URL retVal = null;
        retVal = findResource(name);
        if (retVal == null ) {
        	retVal = ClassLoader.getSystemResource(name);
        }
    	return retVal;
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
        Class<?> cls=null;
        ByteBuffer clsBytes=null;
        try {
            Optional<ByteBuffer> optBB = this.reader.read(className.replace('.', '/')+".class");
            if (optBB.isPresent()) {
                clsBytes = optBB.get();
                cls = defineClass(className, clsBytes, (CodeSource) null);
            }
        } catch (IOException e) {
            throw new LinkageError("Could not find class: "+className, e);
        } finally {
            if (clsBytes!=null) reader.release(clsBytes);
        }
        
        if (cls==null) {
            throw new ClassNotFoundException("Could not find class: "+className);
        }
    	return cls;
    }

    @Override
    protected Class<?> findClass(String moduleName, String className) {
        // single-module class loader so ignore passed in module name.
        
        //TODO log a warning or error here if a classname is specified.
        try {
            return findClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Loads the class with the specified binary name.
     */
    @Override
    protected Class<?> loadClass(String className, boolean resolve)
            throws ClassNotFoundException
    {
        Class<?> cls=null;
        // synchronize on getClassLoadingLock(className)
        synchronized (getClassLoadingLock(className)) {
            // find if the class is already loaded and return it if so.
            cls = findLoadedClass(className);
            if (cls==null) {
                // otherwise; check ModuleDescriptor.pacakges to see if it contains the package for the requested class
                //   if so do the same thing as findClass(String, String)
                String pkg = className.substring(0, className.lastIndexOf('.'));
                if (module.get().getDescriptor().packages().contains(pkg)) {
                    cls = findClass(module.get().getName(), className);
                }
                else {
                    // otherwise; check for packages this module can read from and if you have another loader for the package
                    //   Then call the other loader.loadClass(className)
                    ClassLoader l = edges.get(pkg);
                    if (l != null) {
                        cls = l.loadClass(className);
                    }
                }
            }
            if (cls==null) {
                throw new ClassNotFoundException("Could not find class: "+className);
            }
            // if any class is found then call resolveClass on it if the resolve param is true
            else if (resolve) {
                resolveClass(cls);
            }
        }
        return cls;
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
