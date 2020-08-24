package org.apache.felix.atomos.examples.jaxrs.bundle;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

@Component(service = HelloResource.class, scope = ServiceScope.PROTOTYPE)
@JaxrsResource
public class HelloResource
{
    private final HelloService helloService;

    @Activate
    public HelloResource(@Reference HelloService helloService)
    {
        this.helloService = helloService;
        System.out.println(getClass().getSimpleName() + " JAX-RS activated");
    }

    @Deactivate
    void deactivate()
    {
        System.out.println(getClass().getSimpleName() + " JAX-RS deactivated");
    }

    @GET
    @Path("hello/{name}")
    public String sayHello(@PathParam("name") String name)
    {
        return helloService.computeHelloMessage(name);
    }
}
