package com.example.coffeeshop.boundary.error;

import java.util.List;

import com.example.coffeeshop.control.exception.InvalidPagingException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Invalid paging -> {@code 400} with {@code detail} {@code Request validation failed} and the
 * offending field, which is {@code control}'s message (spec section 5):
 * {@code page must be >= 0} / {@code size must be between 1 and 100}.
 *
 * <p>A tighter {@code InvalidPagingException} subtype of {@code IllegalArgumentException} is used
 * so this mapper cannot swallow an unrelated programming error as a client fault.
 */
@Provider
public class PagingExceptionMapper implements ExceptionMapper<InvalidPagingException> {

    private static final String DETAIL = "Request validation failed";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(InvalidPagingException exception) {
        return ProblemResponses.problem(uriInfo, 400, DETAIL,
                List.of(new ProblemDetail.FieldError(exception.getField(), exception.getMessage())));
    }
}
