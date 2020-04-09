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
package org.apache.felix.atomos.utils.core.scr.mock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.impl.logger.BundleLogger;
import org.apache.felix.scr.impl.logger.ScrLogger;
import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;

public class EmptyBundeLogger extends BundleLogger
{
    static Bundle b = new Bundle()
    {
        @Override
        public <A> A adapt(Class<A> type)
        {
            return null;
        }

        @Override
        public int compareTo(Bundle o)
        {
            return 0;
        }

        @Override
        public Enumeration<URL> findEntries(String path, String filePattern,
            boolean recurse)
        {
            return null;
        }

        @Override
        public BundleContext getBundleContext()
        {
            return null;
        }

        @Override
        public long getBundleId()
        {
            return 0;
        }

        @Override
        public File getDataFile(String filename)
        {
            return null;
        }

        @Override
        public URL getEntry(String path)
        {
            return null;
        }

        @Override
        public Enumeration<String> getEntryPaths(String path)
        {
            return null;
        }

        @Override
        public Dictionary<String, String> getHeaders()
        {
            return null;
        }

        @Override
        public Dictionary<String, String> getHeaders(String locale)
        {
            return null;
        }

        @Override
        public long getLastModified()
        {
            return 0;
        }

        @Override
        public String getLocation()
        {
            return null;
        }

        @Override
        public ServiceReference<?>[] getRegisteredServices()
        {
            return null;
        }

        @Override
        public URL getResource(String name)
        {
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException
        {
            return null;
        }

        @Override
        public ServiceReference<?>[] getServicesInUse()
        {
            return null;
        }

        @Override
        public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(
            int signersType)
        {
            return null;
        }

        @Override
        public int getState()
        {
            return 0;
        }

        @Override
        public String getSymbolicName()
        {
            return null;
        }

        @Override
        public Version getVersion()
        {
            return null;
        }

        @Override
        public boolean hasPermission(Object permission)
        {
            return false;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException
        {
            return null;
        }

        @Override
        public void start() throws BundleException
        {
        }

        @Override
        public void start(int options) throws BundleException
        {
        }

        @Override
        public void stop() throws BundleException
        {
        }

        @Override
        public void stop(int options) throws BundleException
        {
        }

        @Override
        public void uninstall() throws BundleException
        {
        }

        @Override
        public void update() throws BundleException
        {
        }

        @Override
        public void update(InputStream input) throws BundleException
        {
        }
    };
    static BundleContext bc = new BundleContext()
    {
        @Override
        public void addBundleListener(BundleListener listener)
        {
        }

        @Override
        public void addFrameworkListener(FrameworkListener listener)
        {
        }

        @Override
        public void addServiceListener(ServiceListener listener)
        {
        }

        @Override
        public void addServiceListener(ServiceListener listener, String filter)
            throws InvalidSyntaxException
        {
        }

        @Override
        public Filter createFilter(String filter) throws InvalidSyntaxException
        {
            return null;
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter)
            throws InvalidSyntaxException
        {
            return null;
        }

        @Override
        public Bundle getBundle()
        {
            return b;
        }

        @Override
        public Bundle getBundle(long id)
        {
            return null;
        }

        @Override
        public Bundle getBundle(String location)
        {
            return null;
        }

        @Override
        public Bundle[] getBundles()
        {
            return null;
        }

        @Override
        public File getDataFile(String filename)
        {
            return null;
        }

        @Override
        public String getProperty(String key)
        {
            return null;
        }

        @Override
        public <S> S getService(ServiceReference<S> reference)
        {
            return null;
        }

        @Override
        public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference)
        {
            return null;
        }

        @Override
        public <S> ServiceReference<S> getServiceReference(Class<S> clazz)
        {
            return null;
        }

        @Override
        public ServiceReference<?> getServiceReference(String clazz)
        {
            return null;
        }

        @Override
        public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz,
            String filter) throws InvalidSyntaxException
        {
            return null;
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(String clazz, String filter)
            throws InvalidSyntaxException
        {
            return null;
        }

        @Override
        public Bundle installBundle(String location) throws BundleException
        {
            return null;
        }

        @Override
        public Bundle installBundle(String location, InputStream input)
            throws BundleException
        {
            return null;
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service,
            Dictionary<String, ?> properties)
        {
            return null;
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> clazz,
            ServiceFactory<S> factory, Dictionary<String, ?> properties)
        {
            return null;
        }

        @Override
        public ServiceRegistration<?> registerService(String clazz, Object service,
            Dictionary<String, ?> properties)
        {
            return null;
        }

        @Override
        public ServiceRegistration<?> registerService(String[] clazzes, Object service,
            Dictionary<String, ?> properties)
        {
            return null;
        }

        @Override
        public void removeBundleListener(BundleListener listener)
        {
        }

        @Override
        public void removeFrameworkListener(FrameworkListener listener)
        {
        }

        @Override
        public void removeServiceListener(ServiceListener listener)
        {
        }

        @Override
        public boolean ungetService(ServiceReference<?> reference)
        {
            return false;
        }
    };
    static ScrConfiguration conf = new ScrConfiguration()
    {
        @Override
        public int getLogLevel()
        {
            return 0;
        }

        @Override
        public boolean globalExtender()
        {
            return false;
        }

        @Override
        public boolean infoAsService()
        {
            return false;
        }

        @Override
        public boolean isFactoryEnabled()
        {
            return false;
        }

        @Override
        public boolean keepInstances()
        {
            return false;
        }

        @Override
        public long lockTimeout()
        {
            return 0;
        }

        @Override
        public long serviceChangecountTimeout()
        {
            return 0;
        }

        @Override
        public long stopTimeout()
        {
            return 0;
        }
    };
    static ScrLogger scrLogger = new EmptySCRLogger(conf, bc);

    public EmptyBundeLogger()
    {
        super(bc, scrLogger);
    }

    @Override
    public void close()
    {
    }

    @Override
    public boolean isLogEnabled(int level)
    {
        return false;
    }

    @Override
    public boolean log(int level, String message, Throwable ex)
    {
        return true;
    }

    @Override
    public boolean log(int level, String pattern, Throwable ex, Object... arguments)
    {
        return true;
    }
}
