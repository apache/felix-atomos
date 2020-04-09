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
package org.apache.felix.atomos.utils.core.plugins.activator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.felix.atomos.utils.api.Config;
import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.plugin.BundleActivatorPlugin;
import org.apache.felix.atomos.utils.core.RegisterServiceCallImpl;

public class InvocatingBundleActivatorPlugin implements BundleActivatorPlugin<Config>
{

    @Override
    public void doBundleActivator(Class<?> bundleActivatorClass, Context context,
        ClassLoader classLoader)
    {

        try
        {

            Object o = bundleActivatorClass.getConstructor().newInstance();

            Method startMethod = null;
            for (Method m : bundleActivatorClass.getMethods())
            {
                if (m.getName().equals("start") && m.getReturnType().equals(void.class)
                    && m.getParameterCount() == 1
                    && m.getParameters()[0].getParameterizedType().getTypeName().equals(
                        "org.osgi.framework.BundleContext"))
                {
                    startMethod = m;
                    break;
                }
            }

            if (startMethod != null)
            {
                InvocationHandler invocationHandler = new InvocationHandler()
                {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                        throws Throwable
                    {
                        try
                        {
                            if (method.getName().equals("registerService")
                                && method.getParameterCount() == 3
                                && method.getParameters()[1].getType().getTypeName().equals(
                                    "java.lang.Object")
                                && method.getParameters()[2].getType().getTypeName().equals(
                                    "java.util.Dictionary"))
                            {

                                RegisterServiceCallImpl registerServiceCallImpl = new RegisterServiceCallImpl(
                                    args);

                                if (registerServiceCallImpl.isValid())
                                {

                                    context.addRegisterServiceCalls(
                                        registerServiceCallImpl);
                                }
                            }
                            if (method.getName().equals("getBundleId"))
                            {
                                return 1L;
                            }
                            if (method.getName().equals("getVersion"))
                            {
                                return classLoader.loadClass(
                                    "org.osgi.framework.Version").getConstructor(
                                        int.class, int.class, int.class).newInstance(0, 0,
                                            0);
                            }
                            else if (method.getReturnType().isInterface())
                            {
                                return Proxy.newProxyInstance(classLoader,
                                    new Class[] { method.getReturnType() }, this);
                            }
                        }
                        catch (Exception e)
                        {
                            // expected
                            //e.printStackTrace();
                        }
                        return null;
                    }
                };
                Object p = Proxy.newProxyInstance(classLoader,
                    new Class[] {
                            classLoader.loadClass("org.osgi.framework.BundleContext") },
                    invocationHandler);
                startMethod.invoke(o, p);
            }
        }
        catch (Exception e)
        { // expected
          // TODO Log and maybe show cp errors
          // e.printStackTrace();
        }
        catch (Throwable e)
        {
            // TODO: handle exception
        }

    }

    @Override
    public void init(Config config)
    {

    }

}
