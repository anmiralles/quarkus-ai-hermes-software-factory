package com.example.coffeeshop.boundary.error;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Bean-validation failure on a request body -> {@code 400} with {@code detail}
 * {@code Request validation failed} and one {@code errors[]} entry per violated rule, using the
 * JSON property name and the message the contract pins (spec sections 3.3, 3.5).
 *
 * <p>Duplicate violations for the same field/message (a record component validated twice)
 * are collapsed: the client should not see the same complaint twice.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(ValidationExceptionMapper.class);

    private static final String DETAIL = "Request validation failed";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Map<String, ProblemDetail.FieldError> byFieldAndMessage = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String field = jsonFieldName(violation.getPropertyPath());
            byFieldAndMessage.putIfAbsent(field + "\u0000" + violation.getMessage(),
                    new ProblemDetail.FieldError(field, violation.getMessage()));
        }
        List<ProblemDetail.FieldError> errors = new ArrayList<>(byFieldAndMessage.values());
        LOG.debugf("%d validation failure(s) on %s", errors.size(), ProblemResponses.instance(uriInfo));
        return ProblemResponses.problem(uriInfo, 400, DETAIL, errors);
    }

    /**
     * The JSON property name: the leaf of the constraint path ({@code create.name} -> {@code name}).
     * Falls back to the whole path for a class-level constraint, which has no property name.
     */
    private static String jsonFieldName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name == null ? path.toString() : name;
    }
}
