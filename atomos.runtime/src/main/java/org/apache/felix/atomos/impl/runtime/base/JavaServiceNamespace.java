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
package org.apache.felix.atomos.impl.runtime.base;

import java.util.ServiceLoader;

/**
 * Java service capability and requirement namespace.
 * <p>
 * This is an Atomos specific namespace that is used to model
 * Java module provides and uses declarations from the module-info class
 * which specify providers and users of Java services through the
 * {@link ServiceLoader}.
 * <p>
 * For example, the following module-info provides translates into the succeeding capability:
 * <pre>
 * provides org.osgi.framework.launch.FrameworkFactory with org.acme.framework.AcmeFrameworkFactory;
 * 
 * Provide-Capability: atomos.java.service;
 *     atomos.java.service=org.osgi.framework.launch.FrameworkFactory;
 *     provides.with=org.acme.framework.AcmeFrameworkFactory;
 *     uses:=org.osgi.framework.launch
 * </pre>
 * 
 * The following module-info uses translates into the succeeding requirement:
 * <pre>
 * uses org.osgi.framework.launch.FrameworkFactory;
 * 
 * Require-Capability: atomos.java.service;
 *     filter:="(atomos.java.service=org.osgi.framework.launch.FrameworkFactory);
 *     resolution:=optional
 * </pre>
 * This namespace is experimental and is not considered API.  Note that the requirements
 * are considered optional to allow a connected bundle to resolve even if there
 * is not another module providing the used Java service.  This is to mimic
 * the behavior of Java Platform Module System resolution for services.
 */
public final class JavaServiceNamespace
{
    /**
     * Namespace name for Java service capabilities and requirements.
     * 
     * <p>
     * Also, the capability attribute used to specify the name of the service interface.
     */
    public static final String JAVA_SERVICE_NAMESPACE = "atomos.java.service";

    /**
     * The capability attribute contains the class name of the implementation of
     * the provided Java service. The value of this attribute must be of type {@code String}.
     */
    public static final String CAPABILITY_PROVIDES_WITH_ATTRIBUTE = "provides.with";
}
