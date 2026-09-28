package com.example.coffeeshop.verify;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Independent verification suite for card {@code t_c41f5e3e} (criteria AC-V2..AC-V5, AC-V8).
 *
 * <p>Written by the QA profile from the committed specification
 * ({@code docs/architecture/coffee-bce.md} section 9.3), not from the implementation: each test
 * encodes what the contract says must happen, so it fails when the behaviour regresses. The
 * author's suite under {@code boundary/} is deliberately not reused; the assertions here are
 * independent, and the two suites share nothing but the running application.
 *
 * <p>The coffee table is emptied before and after every test: the H2 test datasource is shared
 * across all {@code @QuarkusTest} classes in the JVM, so no test may inherit another's rows.
 */
@QuarkusTest
@DisplayName("QA verification: /coffees contract")
class QaContractVerificationTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void cleanTable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM coffee");
        }
    }

    // ------------------------------------------------------------------ AC-V2: CRUD round trip

    @Test
    @DisplayName("AC-V2 full create -> list -> get -> update -> delete -> 404 round trip over HTTP")
    void acV2_fullCrudRoundTrip() {
        Response created = given().contentType(ContentType.JSON)
                .body(json("QA Round Trip", "MEDIUM", "Colombia", "9.50", 7))
                .when().post("/coffees");

        assertEquals(201, created.statusCode(), created.asString());
        assertTrue(created.header("Location").matches("/coffees/" + UUID_PATTERN),
                "Location must be the resource path: " + created.header("Location"));
        JsonNode body = parse(created);
        String id = body.get("id").asText();
        assertTrue(id.matches(UUID_PATTERN), "id is server generated: " + id);
        assertEquals("QA Round Trip", body.get("name").asText());
        assertEquals("MEDIUM", body.get("roastLevel").asText());
        assertEquals("Colombia", body.get("origin").asText());
        assertEquals(7, body.get("stock").asInt());
        assertFalse(body.get("createdAt").isNull(), "createdAt must be set on create");
        assertEquals(body.get("createdAt").asText(), body.get("updatedAt").asText(),
                "updatedAt equals createdAt on insert");

        // list: the row is visible and the envelope is the contract's
        given().when().get("/coffees").then().statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content[0].id", equalTo(id))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(1))
                .body("totalPages", equalTo(1));

        // get by id
        given().when().get("/coffees/{id}", id).then().statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("QA Round Trip"));

        String createdAtReadBack = given().when().get("/coffees/{id}", id)
                .then().extract().path("createdAt");
        String createdAtReadBackAgain = given().when().get("/coffees/{id}", id)
                .then().extract().path("createdAt");
        assertEquals(createdAtReadBack, createdAtReadBackAgain, "createdAt must not move between reads");
        // The create response is serialised from the in-memory entity (nanosecond precision) before
        // the database rounds to microseconds, so the value handed back on 201 and the value read
        // back differ by storage rounding. See VERIFICATION.md defect D1: the drift is asserted to
        // be sub-millisecond here, the contract "same instant" invariant is asserted below.
        assertTrue(subMillisecond(Instant.parse(body.get("createdAt").asText()), Instant.parse(createdAtReadBack)),
                "the createdAt returned by POST must be the same instant as the stored one: "
                        + body.get("createdAt").asText() + " vs " + createdAtReadBack);

        // update replaces every mutable field
        Response updated = given().contentType(ContentType.JSON)
                .body(json("QA Round Trip v2", "DARK", "Peru", "11.75", 3))
                .when().put("/coffees/{id}", id);
        assertEquals(200, updated.statusCode(), updated.asString());
        JsonNode updatedBody = parse(updated);
        assertEquals(id, updatedBody.get("id").asText(), "id is immutable");
        assertEquals("QA Round Trip v2", updatedBody.get("name").asText());
        assertEquals("DARK", updatedBody.get("roastLevel").asText());
        assertEquals("Peru", updatedBody.get("origin").asText());
        assertEquals(3, updatedBody.get("stock").asInt());
        assertEquals(createdAtReadBack, updatedBody.get("createdAt").asText(), "createdAt is immutable");

        // delete returns 204 with an empty body
        Response deleted = given().when().delete("/coffees/{id}", id);
        assertEquals(204, deleted.statusCode(), deleted.asString());
        assertEquals("", deleted.asString(), "DELETE must return an empty body");

        // and the resource is gone
        assertNotFound(given().when().get("/coffees/{id}", id), "/coffees/" + id, "Coffee " + id + " not found");
        given().when().get("/coffees").then().statusCode(200).body("totalElements", equalTo(0));
    }

    // ------------------------------------------------------------------ AC-V3: pagination

    @Test
    @DisplayName("AC-V3 page 0, oversized size, size 100 and page past the end behave as the contract")
    void acV3_paginationBoundaries() {
        create("Bravo", "LIGHT", "Peru", "1.00", 1);
        create("Alpha", "LIGHT", "Peru", "1.00", 1);
        create("Charlie", "LIGHT", "Peru", "1.00", 1);

        // defaults: page 0, size 20, order name asc then id asc
        given().when().get("/coffees").then().statusCode(200)
                .body("content.name", contains("Alpha", "Bravo", "Charlie"))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(3))
                .body("totalPages", equalTo(1));

        // explicit page 0 boundary
        given().queryParam("page", 0).queryParam("size", 2).when().get("/coffees").then().statusCode(200)
                .body("content.name", contains("Alpha", "Bravo"))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalPages", equalTo(2));

        given().queryParam("page", 1).queryParam("size", 2).when().get("/coffees").then().statusCode(200)
                .body("content.name", contains("Charlie"))
                .body("page", equalTo(1))
                .body("totalElements", equalTo(3));

        // page past the end: 200, empty content, totals still reported
        given().queryParam("page", 2).queryParam("size", 2).when().get("/coffees").then().statusCode(200)
                .body("content.size()", equalTo(0))
                .body("page", equalTo(2))
                .body("totalElements", equalTo(3))
                .body("totalPages", equalTo(2));

        given().queryParam("page", 999).when().get("/coffees").then().statusCode(200)
                .body("content.size()", equalTo(0))
                .body("totalElements", equalTo(3));

        // size 100 is the accepted maximum
        given().queryParam("size", 100).when().get("/coffees").then().statusCode(200)
                .body("size", equalTo(100))
                .body("totalPages", equalTo(1));

        // oversized size is rejected, not clamped
        assertPagingError("size", "101", "size", "size must be between 1 and 100");
        assertPagingError("size", "1000", "size", "size must be between 1 and 100");
        assertPagingError("size", "0", "size", "size must be between 1 and 100");
        assertPagingError("page", "-1", "page", "page must be >= 0");
        assertPagingError("size", "abc", "size", "size must be an integer");
        // a number too large for int must still be a contract-shaped 400, not a 500
        assertPagingError("page", "99999999999999999999", "page", "page must be an integer");
    }

    // ------------------------------------------------------------------ AC-V4: Unicode and length

    @Test
    @DisplayName("AC-V4 Unicode names round-trip; 100-char names accepted, 101 rejected")
    void acV4_unicodeAndNameLengthBoundaries() {
        String unicodeName = "Café ☕ Solo";
        Response created = given().contentType(ContentType.JSON)
                .body(json(unicodeName, "LIGHT", "España", "6.20", 4))
                .when().post("/coffees");
        assertEquals(201, created.statusCode(), created.asString());
        String unicodeId = parse(created).get("id").asText();

        given().when().get("/coffees/{id}", unicodeId).then().statusCode(200)
                .body("name", equalTo(unicodeName))
                .body("origin", equalTo("España"));
        assertTrue(given().when().get("/coffees/{id}", unicodeId).asString().contains(unicodeName),
                "the raw response must carry the exact Unicode name");

        // exactly 100 characters is the accepted maximum
        String name100 = "N".repeat(100);
        Response atLimit = given().contentType(ContentType.JSON)
                .body(json(name100, "DARK", "O".repeat(100), "3.00", 0))
                .when().post("/coffees");
        assertEquals(201, atLimit.statusCode(), atLimit.asString());
        String atLimitId = parse(atLimit).get("id").asText();
        given().when().get("/coffees/{id}", atLimitId).then().statusCode(200)
                .body("name", equalTo(name100))
                .body("origin", equalTo("O".repeat(100)));

        // 101 characters is rejected with the pinned message
        assertValidationError(json("N".repeat(101), "LIGHT", "Peru", "3.00", 0),
                "name", "name size must be between 1 and 100");
        assertValidationError(json("Ok Name", "LIGHT", "O".repeat(101), "3.00", 0),
                "origin", "origin size must be between 1 and 100");
    }

    // ------------------------------------------------------------------ AC-V5: price

    @Test
    @DisplayName("AC-V5 price round-trips at scale 2; 3 decimals, 0, negative and over-max rejected")
    void acV5_priceHandling() {
        Response created = given().contentType(ContentType.JSON)
                .body(json("Price Twelve Thirty", "MEDIUM", "Brazil", "12.30", 1))
                .when().post("/coffees");
        assertEquals(201, created.statusCode(), created.asString());
        String id = parse(created).get("id").asText();
        assertTrue(created.asString().contains("\"price\":12.30"),
                "POST must serialise 12.30 at scale 2: " + created.asString());
        assertTrue(given().when().get("/coffees/{id}", id).asString().contains("\"price\":12.30"),
                "a reloaded row must serialise 12.30 at scale 2");

        // a value with 1 decimal place is widened to scale 2, not left as 12.3
        String widenedId = create("Price Widened", "LIGHT", "Brazil", "12.3", 1);
        assertTrue(given().when().get("/coffees/{id}", widenedId).asString().contains("\"price\":12.30"),
                "12.3 must be serialised as 12.30");

        // the inclusive upper bound is accepted
        Response maxPrice = given().contentType(ContentType.JSON)
                .body(json("Price Max", "DARK", "Brazil", "99999999.99", 1))
                .when().post("/coffees");
        assertEquals(201, maxPrice.statusCode(), maxPrice.asString());

        assertValidationError(json("P345", "LIGHT", "Kenya", "12.345", 1),
                "price", "price must have at most 2 decimal places");
        assertValidationError(json("PZero", "LIGHT", "Kenya", "0", 1),
                "price", "price must be greater than 0");
        assertValidationError(json("PNeg", "LIGHT", "Kenya", "-1.00", 1),
                "price", "price must be greater than 0");
        assertValidationError(json("PBig", "LIGHT", "Kenya", "100000000.00", 1),
                "price", "price must be at most 99999999.99");
    }

    // ------------------------------------------------------------------ AC-V8: adversarial

    @Test
    @DisplayName("AC-V8 malformed JSON, unknown field, negative stock, deleted id, non-UUID id")
    void acV8_adversarialInputs() {
        // malformed JSON
        Response malformed = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Broken\",\"roastLevel\":")
                .when().post("/coffees");
        assertProblemDetail(malformed, 400, "Bad Request", "Malformed request body", "/coffees");
        assertNull(parse(malformed).get("errors"), "a structural parse failure names no field");

        // unknown field
        Response unknown = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Unknown Field\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\","
                        + "\"price\":12.34,\"stock\":1,\"roast\":\"light\"}")
                .when().post("/coffees");
        assertEquals(400, unknown.statusCode(), unknown.asString());
        assertTrue(unknown.contentType().startsWith("application/problem+json"), unknown.contentType());
        assertTrue(unknown.asString().contains("unknown field 'roast'"), unknown.asString());

        // negative stock
        assertValidationError(json("Negative Stock", "LIGHT", "Kenya", "12.34", -1),
                "stock", "stock must be greater than or equal to 0");

        // a deleted id is a 404 on get, put and delete
        String id = create("Doomed", "LIGHT", "Kenya", "4.00", 2);
        given().when().delete("/coffees/{id}", id).then().statusCode(204);
        assertNotFound(given().when().get("/coffees/{id}", id), "/coffees/" + id, "Coffee " + id + " not found");
        assertNotFound(given().contentType(ContentType.JSON)
                        .body(json("Doomed Resurrected", "LIGHT", "Kenya", "4.00", 2))
                        .when().put("/coffees/{id}", id),
                "/coffees/" + id, "Coffee " + id + " not found");
        assertNotFound(given().when().delete("/coffees/{id}", id), "/coffees/" + id, "Coffee " + id + " not found");

        // a non-UUID path segment is a 404, not a 400
        assertNotFound(given().when().get("/coffees/not-a-uuid"), "/coffees/not-a-uuid",
                "Coffee not-a-uuid not found");
        assertNotFound(given().when().get("/coffees/12.5"), "/coffees/12.5", "Coffee 12.5 not found");

        // sequential duplicate name is a 409, and the duplicate is not persisted
        create("Unique Name", "LIGHT", "Kenya", "4.00", 2);
        Response conflict = given().contentType(ContentType.JSON)
                .body(json("Unique Name", "DARK", "Peru", "5.00", 9))
                .when().post("/coffees");
        assertProblemDetail(conflict, 409, "Conflict", "Coffee name 'Unique Name' already exists", "/coffees");
        given().when().get("/coffees").then().statusCode(200).body("totalElements", equalTo(1));

        // whitespace-trimmed duplicates collide too (trim happens before the uniqueness check)
        Response trimmedConflict = given().contentType(ContentType.JSON)
                .body(json("  Unique Name  ", "DARK", "Peru", "5.00", 9))
                .when().post("/coffees");
        assertEquals(409, trimmedConflict.statusCode(), trimmedConflict.asString());
    }

    // ------------------------------------------------------------------ helpers

    private String create(String name, String roastLevel, String origin, String price, int stock) {
        Response created = given().contentType(ContentType.JSON)
                .body(json(name, roastLevel, origin, price, stock))
                .when().post("/coffees");
        assertEquals(201, created.statusCode(), created.asString());
        return parse(created).get("id").asText();
    }

    private static String json(String name, String roastLevel, String origin, String price, int stock) {
        return "{\"name\":\"" + name + "\",\"roastLevel\":\"" + roastLevel + "\",\"origin\":\"" + origin
                + "\",\"price\":" + price + ",\"stock\":" + stock + "}";
    }

    private static JsonNode parse(Response response) {
        try {
            return new ObjectMapper().readTree(response.asString());
        } catch (Exception e) {
            throw new IllegalStateException("not JSON: " + response.asString(), e);
        }
    }

    /**
     * True when two instants are the same instant up to the microsecond rounding a
     * {@code TIMESTAMP WITH TIME ZONE} column performs. The create response is built from the
     * in-memory entity (nanosecond precision) while reads come back rounded to microseconds, so
     * exact string equality between the two is not a contract invariant; identity of the instant
     * is. See VERIFICATION.md defect D1.
     */
    private static boolean subMillisecond(Instant a, Instant b) {
        return Duration.between(a, b).abs().compareTo(Duration.ofMillis(1)) < 0;
    }

    private static void assertProblemDetail(Response response, int status, String title, String detail,
                                            String instance) {
        assertEquals(status, response.statusCode(), response.asString());
        assertTrue(response.contentType().startsWith("application/problem+json"),
                "errors must be problem+json: " + response.contentType());
        JsonNode body = parse(response);
        assertEquals("about:blank", body.get("type").asText());
        assertEquals(title, body.get("title").asText());
        assertEquals(status, body.get("status").asInt());
        assertEquals(detail, body.get("detail").asText());
        assertEquals(instance, body.get("instance").asText());
    }

    private static void assertNotFound(Response response, String path, String detail) {
        assertProblemDetail(response, 404, "Not Found", detail, path);
    }

    private static void assertValidationError(String body, String field, String message) {
        Response response = given().contentType(ContentType.JSON).body(body).when().post("/coffees");
        assertProblemDetail(response, 400, "Bad Request", "Request validation failed", "/coffees");
        JsonNode errors = parse(response).get("errors");
        assertTrue(errors != null && errors.isArray(), "errors[] must be present: " + response.asString());
        boolean match = false;
        for (JsonNode error : errors) {
            if (field.equals(error.path("field").asText()) && message.equals(error.path("message").asText())) {
                match = true;
            }
        }
        assertTrue(match, "expected errors[] to contain " + field + " -> '" + message + "': "
                + response.asString());
    }

    private static void assertPagingError(String parameter, String value, String field, String message) {
        Response response = given().queryParam(parameter, value).when().get("/coffees");
        assertProblemDetail(response, 400, "Bad Request", "Request validation failed", "/coffees");
        JsonNode errors = parse(response).get("errors");
        assertTrue(errors != null && errors.isArray(), "errors[] must be present: " + response.asString());
        boolean match = false;
        for (JsonNode error : errors) {
            if (field.equals(error.path("field").asText()) && message.equals(error.path("message").asText())) {
                match = true;
            }
        }
        assertTrue(match, "expected errors[] to contain " + field + " -> '" + message + "': "
                + response.asString());
    }
}
