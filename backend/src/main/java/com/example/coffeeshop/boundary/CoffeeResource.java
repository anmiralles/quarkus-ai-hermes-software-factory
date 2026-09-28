package com.example.coffeeshop.boundary;

import java.util.UUID;

import com.example.coffeeshop.boundary.dto.CoffeePageResponse;
import com.example.coffeeshop.boundary.dto.CoffeeRequest;
import com.example.coffeeshop.boundary.dto.CoffeeResponse;
import com.example.coffeeshop.control.CoffeePage;
import com.example.coffeeshop.control.CoffeeService;
import com.example.coffeeshop.control.exception.CoffeeNotFoundException;
import com.example.coffeeshop.control.exception.InvalidPagingException;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * HTTP adapter for the coffee catalogue (spec section 3). It translates HTTP to control calls
 * and DTOs back to JSON, and does nothing else: no persistence, no repository, no transactions,
 * no business rules, and no entity type in any signature (spec section 1.1).
 */
@Path("/coffees")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "coffees", description = "Coffee catalogue")
public class CoffeeResource {

    private static final Logger LOG = Logger.getLogger(CoffeeResource.class);

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;

    private final CoffeeService coffeeService;

    @Inject
    public CoffeeResource(CoffeeService coffeeService) {
        this.coffeeService = coffeeService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a coffee", description = "Creates a coffee and returns it with the "
            + "server-generated id and timestamps. The name is unique (exact, case-sensitive).")
    @APIResponse(responseCode = "201", description = "Created",
            headers = @Header(name = "Location", description = "URI of the created coffee, e.g. /coffees/{id}"),
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CoffeeResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation failure, malformed body or unknown field",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @APIResponse(responseCode = "409", description = "A coffee with that name already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    public Response create(@Valid CoffeeRequest request) {
        CoffeeResponse created = CoffeeResponse.from(coffeeService.createCoffee(request.toEntity()));
        LOG.debugf("created coffee %s", created.id());
        // the header is set as a plain path, not through Response.created(URI): the UriBuilder would
        // resolve it against the request base URI and emit an absolute URL with host, while the
        // contract wants the resource path (spec section 3.1)
        return Response.status(Response.Status.CREATED)
                .header("Location", "/coffees/" + created.id())
                .entity(created)
                .build();
    }

    @GET
    @Operation(summary = "List coffees", description = "One page of the catalogue, ordered by name ascending "
            + "then id ascending. page is 0-based; size is 1..100. Out-of-range values are rejected with 400.")
    @APIResponse(responseCode = "200", description = "A page of coffees",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CoffeePageResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid page or size",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    public CoffeePageResponse list(
            @Parameter(description = "0-based page index", schema = @Schema(type = SchemaType.INTEGER, defaultValue = "0"))
            @QueryParam("page") String page,
            @Parameter(description = "Page size, 1..100", schema = @Schema(type = SchemaType.INTEGER, defaultValue = "20"))
            @QueryParam("size") String size) {
        CoffeePage coffees = coffeeService.listCoffees(
                paging("page", page, DEFAULT_PAGE),
                paging("size", size, DEFAULT_SIZE));
        return CoffeePageResponse.of(coffees);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a coffee by id", description = "A malformed (non-UUID) id is a 404: it does not "
            + "address a resource in this API.")
    @APIResponse(responseCode = "200", description = "The coffee",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CoffeeResponse.class)))
    @APIResponse(responseCode = "404", description = "No coffee with that id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    public CoffeeResponse get(@PathParam("id") String id) {
        return CoffeeResponse.from(coffeeService.getCoffee(coffeeId(id)));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Replace a coffee", description = "Full replacement of the mutable fields; there is no "
            + "partial merge and sending id/createdAt/updatedAt is an unknown property (400).")
    @APIResponse(responseCode = "200", description = "The updated coffee",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CoffeeResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation failure, malformed body or unknown field",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @APIResponse(responseCode = "404", description = "No coffee with that id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    @APIResponse(responseCode = "409", description = "Another coffee already has that name",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    public CoffeeResponse update(@PathParam("id") String id, @Valid CoffeeRequest request) {
        return CoffeeResponse.from(coffeeService.updateCoffee(coffeeId(id), request.toEntity()));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a coffee", description = "204 with an empty body. Deleting an unknown id is a 404: "
            + "we report the truth rather than swallow it.")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "No coffee with that id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetailSchema.class)))
    public Response delete(@PathParam("id") String id) {
        coffeeService.deleteCoffee(coffeeId(id));
        return Response.noContent().build();
    }

    // ------------------------------------------------------------------ HTTP-level helpers

    /**
     * A non-UUID path segment is a 404, not a 400 (spec section 3.1): the path addresses a
     * resource, and a malformed id simply does not address one.
     */
    private static UUID coffeeId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new CoffeeNotFoundException(raw);
        }
    }

    /**
     * Query parameters are bound as strings so that a non-numeric value is a 400 problem detail
     * rather than a JAX-RS conversion failure with a runtime-chosen body. The range rules stay in
     * {@code control} (spec section 5).
     */
    private static int paging(String field, String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidPagingException(field, field + " must be an integer");
        }
    }

    /**
     * OpenAPI-only view of {@code boundary.error.ProblemDetail}, which is package-private to the
     * error package. Reference-only: it is never instantiated or serialised.
     */
    static final class ProblemDetailSchema {
        String type;
        String title;
        int status;
        String detail;
        String instance;
    }
}
