package com.example.coffeeshop.boundary.error;

import java.util.List;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Builds the {@code application/problem+json} responses so every mapper emits the same shape
 * and the {@code instance} rule (request path, no query string, no host — spec section 3.5)
 * lives in one place.
 */
final class ProblemResponses {

    private ProblemResponses() {
    }

    /** The request path, e.g. {@code /coffees/3f1c...}; {@code /} if the runtime cannot tell us. */
    static String instance(UriInfo uriInfo) {
        if (uriInfo == null || uriInfo.getRequestUri() == null) {
            return "/";
        }
        String path = uriInfo.getRequestUri().getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    static Response problem(UriInfo uriInfo, int status, String detail) {
        return problem(uriInfo, status, detail, null);
    }

    static Response problem(UriInfo uriInfo, int status, String detail, List<ProblemDetail.FieldError> errors) {
        return Response.status(status)
                .type(ProblemDetail.MEDIA_TYPE)
                .entity(ProblemDetail.of(status, detail, instance(uriInfo), errors))
                .build();
    }
}
