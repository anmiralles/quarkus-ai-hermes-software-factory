package com.example.coffeeshop.boundary.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.ws.rs.core.Response;

/**
 * RFC 7807 problem detail as the contract prints it (spec section 3.5). {@code type} is
 * {@code about:blank} in v1; {@code errors[]} is present for validation failures and omitted
 * entirely otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        List<FieldError> errors) {

    public static final String TYPE_ABOUT_BLANK = "about:blank";

    public static final String MEDIA_TYPE = "application/problem+json";

    /** One rejected field. {@code field} is the JSON property name, not the Java one. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(String field, String message) {
    }

    public static ProblemDetail of(int status, String detail, String instance) {
        return of(status, detail, instance, null);
    }

    public static ProblemDetail of(int status, String detail, String instance, List<FieldError> errors) {
        Response.Status reason = Response.Status.fromStatusCode(status);
        return new ProblemDetail(
                TYPE_ABOUT_BLANK,
                reason == null ? "Error" : reason.getReasonPhrase(),
                status,
                detail,
                instance,
                errors == null || errors.isEmpty() ? null : List.copyOf(errors));
    }
}
