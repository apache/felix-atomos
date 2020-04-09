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
package org.apache.felix.atomos.utils.api.plugin;

import java.util.List;
import java.util.Map;

public interface ComponentDescription
{

    String activate();

    List<String> activationFields();

    List<String> configurationPid();

    String configurationPolicy();

    String deactivate();

    boolean defaultEnabled();

    String factory();

    Map<String, Object> factoryProperties();

    boolean immediate();

    String implementationClass();

    String modified();

    String name();

    int numberOfConstructorParameters();

    Map<String, Object> properties();

    List<ReferenceDescription> references();

    String scope();

    String[] serviceInterfaces();

}
