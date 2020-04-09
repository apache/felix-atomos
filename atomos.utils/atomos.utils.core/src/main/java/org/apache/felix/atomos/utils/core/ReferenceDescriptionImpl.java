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
package org.apache.felix.atomos.utils.core;

import org.apache.felix.atomos.utils.api.plugin.ReferenceDescription;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;

public class ReferenceDescriptionImpl implements ReferenceDescription
{

    private final ReferenceMetadata rd;

    public ReferenceDescriptionImpl(ReferenceMetadata rd)
    {
        this.rd = rd;
    }

    @Override
    public String bind()
    {

        return rd.getBind();
    }

    @Override
    public String cardinality()
    {

        return rd.getCardinality();
    }

    @Override
    public String collectionType()
    {

        return rd.getFieldCollectionType();
    }

    @Override
    public String field()
    {

        return rd.getField();
    }

    @Override
    public String fieldOption()
    {

        return rd.getFieldOption();
    }

    @Override
    public String interfaceName()
    {

        return rd.getInterface();
    }

    @Override
    public String name()
    {

        return rd.getName();
    }

    @Override
    public Integer parameter()
    {

        return rd.getParameterIndex();
    }

    @Override
    public String policy()
    {

        return rd.getPolicy();
    }

    @Override
    public String policyOption()
    {

        return rd.getPolicyOption();
    }

    @Override
    public String scope()
    {

        return rd.getScope().name();
    }

    @Override
    public String target()
    {

        return rd.getTarget();
    }

    @Override
    public String unbind()
    {

        return rd.getUnbind();
    }

    @Override
    public String updated()
    {

        return rd.getUpdated();
    }

}
