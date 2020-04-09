/**
 *
 */
package org.apache.felix.atomos.utils.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.felix.atomos.utils.api.plugin.ComponentDescription;
import org.apache.felix.atomos.utils.api.plugin.ReferenceDescription;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;

public class ComponentDescriptionImpl implements ComponentDescription
{
    private final ComponentMetadata md;

    public ComponentDescriptionImpl(ComponentMetadata md)
    {
        this.md = md;
    }

    @Override
    public String activate()
    {

        return md.getActivate();
    }

    @Override
    public List<String> activationFields()
    {

        return md.getActivationFields();
    }

    @Override
    public List<String> configurationPid()
    {

        return md.getConfigurationPid();
    }

    @Override
    public String configurationPolicy()
    {

        return md.getConfigurationPolicy();
    }

    @Override
    public String deactivate()
    {

        return md.getDeactivate();
    }

    @Override
    public boolean defaultEnabled()
    {

        return md.isEnabled();
    }

    @Override
    public String factory()
    {

        return md.getFactoryIdentifier();
    }

    @Override
    public Map<String, Object> factoryProperties()
    {

        return md.getFactoryProperties();
    }

    @Override
    public boolean immediate()
    {

        return md.isImmediate();
    }

    @Override
    public String implementationClass()
    {

        return md.getImplementationClassName();
    }

    @Override
    public String modified()
    {

        return md.getModified();
    }

    @Override
    public String name()
    {
        return md.getName();
    }

    @Override
    public int numberOfConstructorParameters()
    {

        return md.getNumberOfConstructorParameters();
    }

    @Override
    public Map<String, Object> properties()
    {

        return md.getProperties();
    }

    @Override
    public List<ReferenceDescription> references()
    {
        return md.getDependencies().stream().map(rd -> {
            return new ReferenceDescriptionImpl(rd);
        }).collect(Collectors.toList());

    }

    @Override
    public String scope()
    {

        return md.getServiceScope().toString();
    }

    @Override
    public String[] serviceInterfaces()
    {

        return md.getServiceMetadata().getProvides();
    }
}
