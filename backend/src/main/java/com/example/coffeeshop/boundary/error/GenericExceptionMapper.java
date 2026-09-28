package com.example.coffeeshop.boundary.error;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Catch-all -> {@code 500} with {@code detail} {@code Unexpected error} (spec section 3.5).
 *
 * <p>The exception is logged in full server-side and nothing about it reaches the client: no
 * stack trace, no SQL fragment, no class name. This is the mapper {@code infosec} reviews for
 * entity/implementation leakage.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    private static final String DETAIL = "Unexpected error";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        LOG.errorf(exception, "unhandled failure on %s", ProblemResponses.instance(uriInfo));
        return ProblemResponses.problem(uriInfo, 500, DETAIL);
    }
}
