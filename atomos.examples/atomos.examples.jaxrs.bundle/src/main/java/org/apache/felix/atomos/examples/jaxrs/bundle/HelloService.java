package org.apache.felix.atomos.examples.jaxrs.bundle;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(service = HelloService.class)
public class HelloService
{
    @Activate
    void activate()
    {
        System.out.println(getClass().getSimpleName() + " activated");
    }

    @Deactivate
    void deactivate()
    {
        System.out.println(getClass().getSimpleName() + " deactivated");
    }

    public String computeHelloMessage(String name)
    {
        return "Hello " + name;
    }
}
