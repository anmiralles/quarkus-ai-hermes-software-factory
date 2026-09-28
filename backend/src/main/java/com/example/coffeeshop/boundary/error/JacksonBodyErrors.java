package com.example.coffeeshop.boundary.error;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * The single decision point for "the JSON body could not be turned into a {@code CoffeeRequest}",
 * shared by the two mappers that can see such a failure: Quarkus hands a
 * {@link MismatchedInputException} (unknown field, wrongly typed field) straight to the mapper
 * chain, but wraps a {@link com.fasterxml.jackson.core.exc.StreamReadException} (syntax error) in
 * a {@code WebApplicationException}, so both entry points must produce the same contract body.
 */
final class JacksonBodyErrors {

    private static final String MALFORMED_DETAIL = "Malformed request body";
    private static final String VALIDATION_DETAIL = "Request validation failed";

    private JacksonBodyErrors() {
    }

    static Response problem(UriInfo uriInfo, JsonProcessingException exception) {
        // an enum literal outside the allowed set is a field-level rule (spec section 3.3), so it
        // gets the pinned message rather than a generic parse failure
        if (exception instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            String field = jsonPath(invalid);
            if (field != null) {
                return ProblemResponses.problem(uriInfo, 400, VALIDATION_DETAIL,
                        List.of(new ProblemDetail.FieldError(field,
                                field + " must be one of " + enumConstants(invalid.getTargetType()))));
            }
        }
        if (exception instanceof MismatchedInputException mismatched) {
            List<ProblemDetail.FieldError> errors = fieldErrors(mismatched);
            if (!errors.isEmpty()) {
                return ProblemResponses.problem(uriInfo, 400, MALFORMED_DETAIL, errors);
            }
        }
        // a structural failure: there is no property to name, so no errors[]
        return ProblemResponses.problem(uriInfo, 400, MALFORMED_DETAIL);
    }

    /**
     * A per-field {@code errors[]} entry where the offending JSON property is known.
     *
     * <p>The message is deliberately not Jackson's own: those quote Java class names, which a 400
     * body must not leak.
     */
    private static List<ProblemDetail.FieldError> fieldErrors(MismatchedInputException exception) {
        List<ProblemDetail.FieldError> errors = new ArrayList<>();
        if (exception instanceof UnrecognizedPropertyException unknown && unknown.getPropertyName() != null) {
            String property = unknown.getPropertyName();
            errors.add(new ProblemDetail.FieldError(property, "unknown field '" + property + "'"));
            return errors;
        }
        String field = jsonPath(exception);
        if (field != null) {
            errors.add(new ProblemDetail.FieldError(field, field + " has an invalid value"));
        }
        return errors;
    }

    /** The last JSON property name on Jackson's path, or {@code null} for a structural failure. */
    private static String jsonPath(JsonMappingException exception) {
        String field = null;
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                field = reference.getFieldName();
            }
        }
        return field;
    }

    private static String enumConstants(Class<?> enumType) {
        List<String> names = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return String.join(", ", names);
    }
}
