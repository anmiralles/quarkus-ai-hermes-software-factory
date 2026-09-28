package com.example.coffeeshop.boundary;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Test fixture only (never packaged): an endpoint that fails in a way a service endpoint should
 * not, so the generic 500 mapper can be checked for leakage. The message deliberately looks like
 * the kind of detail that must not reach a client.
 */
@Path("/_test/boom")
public class ExplodingResource {

    @GET
    public String boom() {
        throw new IllegalStateException(
                "SELECT id, name, origin FROM coffee failed at ExplodingResource.boom - see stack trace");
    }
}
