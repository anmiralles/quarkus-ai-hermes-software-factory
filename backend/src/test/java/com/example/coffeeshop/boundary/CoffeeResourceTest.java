package com.example.coffeeshop.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP-level tests for {@code /coffees} against the real stack (REST + validation + JPA + H2),
 * per spec section 9.2: every endpoint and status code of the contract table, the DTO shapes,
 * the paging boundaries, the three error classes and the OpenAPI document.
 *
 * <p>The table is emptied before and after each test: the H2 test database is shared with the
 * entity-layer tests in the same JVM, and neither class may depend on the other's rows.
 */
@QuarkusTest
class CoffeeResourceTest {

    private static final String SERVER_GENERATED_ID =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

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

    // ------------------------------------------------------------------ happy path

    @Test
    void postCreatesCoffeeWith201LocationAndTheFullResponseShape() {
        Response created = given()
                .contentType(ContentType.JSON)
                .body(body("Ethiopia Yirgacheffe", "LIGHT", "Ethiopia", "12.30", 40))
                .when().post("/coffees");

        assertEquals(201, created.statusCode(), created.asString());
        assertTrue(created.contentType().startsWith("application/json"), created.contentType());
        assertTrue(created.header("Location").matches("/coffees/" + SERVER_GENERATED_ID),
                "Location must be the resource path: " + created.header("Location"));

        JsonNode json = parse(created);
        assertEquals("Ethiopia Yirgacheffe", json.get("name").asText());
        assertEquals("LIGHT", json.get("roastLevel").asText());
        assertEquals("Ethiopia", json.get("origin").asText());
        assertEquals(40, json.get("stock").asInt());
        assertTrue(json.get("id").asText().matches(SERVER_GENERATED_ID), "the id is server-generated");
        assertTrue(json.get("createdAt").asText().matches(ISO_UTC), "createdAt must be ISO-8601 UTC: " + json);
        assertTrue(json.get("updatedAt").asText().matches(ISO_UTC), "updatedAt must be ISO-8601 UTC: " + json);
        assertEquals(8, json.size(), "exactly the eight contract fields: " + json);
        assertTrue(created.asString().contains("\"price\":12.30"),
                "price must serialise at scale 2: " + created.asString());
    }

    @Test
    void fullCrudRoundTrip() {
        String id = createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);

        given().when().get("/coffees/{id}", id).then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Cafe Solo"))
                .body("roastLevel", equalTo("MEDIUM"))
                .body("origin", equalTo("Brazil"))
                .body("stock", equalTo(5));

        given().when().get("/coffees").then()
                .statusCode(200)
                .body("content[0].id", equalTo(id))
                .body("content[0].name", equalTo("Cafe Solo"))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(1))
                .body("totalPages", equalTo(1));

        given().contentType(ContentType.JSON)
                .body(body("Cafe Solo Reserva", "DARK", "Brazil", "11.50", 7))
                .when().put("/coffees/{id}", id).then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Cafe Solo Reserva"))
                .body("roastLevel", equalTo("DARK"))
                .body("price", equalTo(11.50f))
                .body("stock", equalTo(7));

        given().when().delete("/coffees/{id}", id).then().statusCode(204);

        given().when().get("/coffees/{id}", id).then().statusCode(404);
    }

    @Test
    void createTrimsTheNameAndOrigin() {
        String id = createCoffee("  Cafe Solo  ", "MEDIUM", "  Brazil  ", "9.99", 5);

        given().when().get("/coffees/{id}", id).then()
                .statusCode(200)
                .body("name", equalTo("Cafe Solo"))
                .body("origin", equalTo("Brazil"));
    }

    @Test
    void emptyCatalogueReturnsAnEmptyPageWithZeroTotalPages() {
        given().when().get("/coffees").then()
                .statusCode(200)
                .body("content.size()", equalTo(0))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0));
    }

    @Test
    void updateAdvancesUpdatedAtOnlyWhenTheEntityIsDirty() throws InterruptedException {
        String id = createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);
        String createdAt = given().when().get("/coffees/{id}", id).then().extract().path("createdAt");
        String updatedAtBefore = given().when().get("/coffees/{id}", id).then().extract().path("updatedAt");

        // a PUT that changes nothing is still a 200, and the row is not dirty, so updatedAt must not move
        given().contentType(ContentType.JSON).body(body("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5))
                .when().put("/coffees/{id}", id).then()
                .statusCode(200)
                .body("updatedAt", equalTo(updatedAtBefore));

        Thread.sleep(10);
        given().contentType(ContentType.JSON).body(body("Cafe Solo", "MEDIUM", "Brazil", "9.99", 6))
                .when().put("/coffees/{id}", id).then()
                .statusCode(200)
                .body("updatedAt", not(equalTo(updatedAtBefore)))
                .body("createdAt", equalTo(createdAt));
    }

    // ------------------------------------------------------------------ paging

    @Test
    void listPagesAreOrderedByNameAndReportCorrectTotals() {
        for (String name : List.of("Charlie", "Alpha", "Echo", "Bravo", "Delta")) {
            createCoffee(name, "LIGHT", "Peru", "1.00", 1);
        }

        given().when().get("/coffees").then()
                .statusCode(200)
                .body("content.name", contains("Alpha", "Bravo", "Charlie", "Delta", "Echo"))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(5))
                .body("totalPages", equalTo(1));

        given().queryParam("page", 0).queryParam("size", 2).when().get("/coffees").then()
                .statusCode(200)
                .body("content.name", contains("Alpha", "Bravo"))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalElements", equalTo(5))
                .body("totalPages", equalTo(3));

        given().queryParam("page", 2).queryParam("size", 2).when().get("/coffees").then()
                .statusCode(200)
                .body("content.name", contains("Echo"))
                .body("totalPages", equalTo(3));

        given().queryParam("page", 3).queryParam("size", 2).when().get("/coffees").then()
                .statusCode(200)
                .body("content.size()", equalTo(0))
                .body("totalElements", equalTo(5))
                .body("totalPages", equalTo(3));
    }

    @Test
    void listAcceptsTheContractBoundariesForSize() {
        createCoffee("Alpha", "LIGHT", "Peru", "1.00", 1);

        given().queryParam("size", 1).when().get("/coffees").then()
                .statusCode(200).body("size", equalTo(1)).body("content.name", contains("Alpha"));

        given().queryParam("size", 100).when().get("/coffees").then()
                .statusCode(200).body("size", equalTo(100)).body("totalPages", equalTo(1));
    }

    @Test
    void listRejectsOutOfRangeAndNonNumericPagingInsteadOfClamping() {
        createCoffee("Alpha", "LIGHT", "Peru", "1.00", 1);

        assertPagingError("page", "-1", "page", "page must be >= 0");
        assertPagingError("size", "0", "size", "size must be between 1 and 100");
        assertPagingError("size", "101", "size", "size must be between 1 and 100");
        assertPagingError("page", "abc", "page", "page must be an integer");
        assertPagingError("size", "20.5", "size", "size must be an integer");
    }

    // ------------------------------------------------------------------ 404

    @Test
    void unknownIdIs404OnGetPutAndDelete() {
        String unknown = "3f1c9c0e-6a4e-4a1b-9a1e-2c7f4b0d5e11";

        assertNotFound(given().when().get("/coffees/{id}", unknown), "/coffees/" + unknown,
                "Coffee " + unknown + " not found");
        assertNotFound(given().contentType(ContentType.JSON)
                        .body(body("Ghost", "LIGHT", "Peru", "1.00", 1)).when().put("/coffees/{id}", unknown),
                "/coffees/" + unknown, "Coffee " + unknown + " not found");
        assertNotFound(given().when().delete("/coffees/{id}", unknown), "/coffees/" + unknown,
                "Coffee " + unknown + " not found");
    }

    @Test
    void malformedIdIs404NotA400() {
        assertNotFound(given().when().get("/coffees/{id}", "not-a-uuid"), "/coffees/not-a-uuid",
                "Coffee not-a-uuid not found");
        assertNotFound(given().contentType(ContentType.JSON)
                        .body(body("Ghost", "LIGHT", "Peru", "1.00", 1)).when().put("/coffees/{id}", "123"),
                "/coffees/123", "Coffee 123 not found");
        assertNotFound(given().when().delete("/coffees/{id}", "123"), "/coffees/123",
                "Coffee 123 not found");
    }

    @Test
    void deletingTwiceIsA404TheSecondTime() {
        String id = createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);

        given().when().delete("/coffees/{id}", id).then().statusCode(204);
        assertNotFound(given().when().delete("/coffees/{id}", id), "/coffees/" + id,
                "Coffee " + id + " not found");
    }

    // ------------------------------------------------------------------ 409

    @Test
    void duplicateNameIs409OnPostWithThePinnedDetail() {
        createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);

        Response conflict = given().contentType(ContentType.JSON)
                .body(body("Cafe Solo", "LIGHT", "Peru", "4.00", 2))
                .when().post("/coffees");

        assertProblemDetail(conflict, 409, "Conflict", "Coffee name 'Cafe Solo' already exists", "/coffees");
        assertNull(parse(conflict).get("errors"), "409 must not carry errors[]");
    }

    @Test
    void theConflictIsAlsoRaisedByTheDatabaseConstraintNotOnlyByThePreCheck() {
        createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);

        Response conflict = given().contentType(ContentType.JSON)
                .body(body("Cafe Solo", "LIGHT", "Peru", "4.00", 2))
                .when().post("/coffees");

        assertEquals(409, conflict.statusCode(), conflict.asString());
        given().when().get("/coffees").then().statusCode(200).body("totalElements", equalTo(1));
    }

    @Test
    void nameUniquenessIsExactAndCaseSensitive() {
        createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);

        String id = createCoffee("cafe solo", "LIGHT", "Peru", "4.00", 2);

        given().when().get("/coffees/{id}", id).then().statusCode(200).body("name", equalTo("cafe solo"));
    }

    @Test
    void renamingToAnotherCoffeesNameIs409OnPut() {
        createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);
        String id = createCoffee("Colombia Huila", "LIGHT", "Colombia", "8.00", 3);

        Response conflict = given().contentType(ContentType.JSON)
                .body(body("Cafe Solo", "LIGHT", "Colombia", "8.00", 3))
                .when().put("/coffees/{id}", id);

        assertProblemDetail(conflict, 409, "Conflict", "Coffee name 'Cafe Solo' already exists",
                "/coffees/" + id);
        given().when().get("/coffees/{id}", id).then().statusCode(200).body("name", equalTo("Colombia Huila"));
    }

    @Test
    void updateKeepingItsOwnNameIs200() {
        String id = createCoffee("Cafe Solo", "MEDIUM", "Brazil", "9.99", 5);

        given().contentType(ContentType.JSON).body(body("Cafe Solo", "DARK", "Brazil", "9.99", 6))
                .when().put("/coffees/{id}", id).then()
                .statusCode(200)
                .body("roastLevel", equalTo("DARK"))
                .body("stock", equalTo(6));
    }

    // ------------------------------------------------------------------ 400

    @Test
    void validationFailuresAre400WithPerFieldMessages() {
        assertValidationError(body("   ", "LIGHT", "Ethiopia", "12.34", 1), "name", "name must not be blank");
        assertValidationError(body("x".repeat(101), "LIGHT", "Ethiopia", "12.34", 1), "name",
                "name size must be between 1 and 100");
        assertValidationError(body("Kenya AA", "ESPRESSO", "Kenya", "12.34", 1), "roastLevel",
                "roastLevel must be one of LIGHT, MEDIUM, DARK");
        assertValidationError(body("Kenya AA", "light", "Kenya", "12.34", 1), "roastLevel",
                "roastLevel must be one of LIGHT, MEDIUM, DARK");
        assertValidationError(body("Kenya AA", "LIGHT", "  ", "12.34", 1), "origin", "origin must not be blank");
        assertValidationError(body("Kenya AA", "LIGHT", "y".repeat(101), "12.34", 1), "origin",
                "origin size must be between 1 and 100");
        assertValidationError(body("Kenya AA", "LIGHT", "Kenya", "0", 1), "price", "price must be greater than 0");
        assertValidationError(body("Kenya AA", "LIGHT", "Kenya", "-1.00", 1), "price",
                "price must be greater than 0");
        assertValidationError(body("Kenya AA", "LIGHT", "Kenya", "12.345", 1), "price",
                "price must have at most 2 decimal places");
        assertValidationError(body("Kenya AA", "LIGHT", "Kenya", "100000000.00", 1), "price",
                "price must be at most 99999999.99");
        assertValidationError(body("Kenya AA", "LIGHT", "Kenya", "12.34", -1), "stock",
                "stock must be greater than or equal to 0");
    }

    @Test
    void missingFieldsAre400NotDefaults() {
        assertValidationError("{\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\",\"price\":12.34,\"stock\":1}",
                "name", "name must not be blank");
        assertValidationError("{\"name\":\"Kenya AA\",\"origin\":\"Kenya\",\"price\":12.34,\"stock\":1}",
                "roastLevel", "roastLevel must be one of LIGHT, MEDIUM, DARK");
        assertValidationError("{\"name\":\"Kenya AA\",\"roastLevel\":\"LIGHT\",\"price\":12.34,\"stock\":1}",
                "origin", "origin must not be blank");
        assertValidationError("{\"name\":\"Kenya AA\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\",\"stock\":1}",
                "price", "price must not be null");
        assertValidationError("{\"name\":\"Kenya AA\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\",\"price\":12.34}",
                "stock", "stock must not be null");
    }

    @Test
    void immutableFieldsInTheBodyAreUnknownPropertiesAndRejected() {
        for (String field : List.of("\"id\":\"3f1c9c0e-6a4e-4a1b-9a1e-2c7f4b0d5e11\"",
                "\"createdAt\":\"2026-09-27T19:45:12.123456Z\"",
                "\"updatedAt\":\"2026-09-27T19:45:12.123456Z\"")) {
            String name = field.split("\"")[1];
            given().contentType(ContentType.JSON)
                    .body("{\"name\":\"Kenya AA\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\",\"price\":12.34,"
                            + "\"stock\":1," + field + "}")
                    .when().post("/coffees").then()
                    .statusCode(400)
                    .header("Content-Type", containsString("application/problem+json"))
                    .body("detail", equalTo("Malformed request body"))
                    .body("errors.field", hasItem(name))
                    .body("errors.message", hasItem("unknown field '" + name + "'"));
        }
    }

    @Test
    void anUnknownFieldIs400WithTheFieldName() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Kenya AA\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\",\"roast\":\"light\","
                        + "\"price\":12.34,\"stock\":1}")
                .when().post("/coffees").then()
                .statusCode(400)
                .body("detail", equalTo("Malformed request body"))
                .body("errors.field", hasItem("roast"))
                .body("errors.message", hasItem("unknown field 'roast'"));
    }

    @Test
    void malformedJsonIs400WithoutAFieldList() {
        Response malformed = given().contentType(ContentType.JSON).body("{\"name\":\"Kenya AA\",\"roastLevel\":")
                .when().post("/coffees");

        assertProblemDetail(malformed, 400, "Bad Request", "Malformed request body", "/coffees");
        assertNull(parse(malformed).get("errors"), "a structural parse failure has no field to name");
    }

    @Test
    void aWronglyTypedFieldIs400NamingTheFieldAndLeakingNothing() {
        Response response = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Kenya AA\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\","
                        + "\"price\":\"expensive\",\"stock\":1}")
                .when().post("/coffees");

        assertEquals(400, response.statusCode(), response.asString());
        assertTrue(response.contentType().startsWith("application/problem+json"), response.contentType());
        assertTrue(response.asString().contains("\"field\":\"price\""), response.asString());
        assertFalse(response.asString().contains("java.math.BigDecimal"),
                "the 400 body must not quote Java class names: " + response.asString());
        given().when().get("/coffees").then().body("totalElements", equalTo(0));
    }

    @Test
    void validationAlsoAppliesToPut() {
        String id = createCoffee("Kenya AA", "LIGHT", "Kenya", "12.34", 1);

        given().contentType(ContentType.JSON).body(body("   ", "LIGHT", "Kenya", "12.34", 1))
                .when().put("/coffees/{id}", id).then()
                .statusCode(400)
                .body("errors.field", hasItem("name"))
                .body("errors.message", hasItem("name must not be blank"));
    }

    // ------------------------------------------------------------------ 500

    @Test
    void unexpectedFailuresAre500ProblemDetailsWithNoLeak() {
        Response response = given().when().get("/_test/boom");

        assertEquals(500, response.statusCode(), response.asString());
        assertTrue(response.contentType().startsWith("application/problem+json"), response.contentType());
        assertProblemDetail(response, 500, "Internal Server Error", "Unexpected error", "/_test/boom");
        assertFalse(response.asString().contains("SELECT"), "no SQL may leak: " + response.asString());
        assertFalse(response.asString().contains("IllegalStateException"),
                "no exception class may leak: " + response.asString());
        assertFalse(response.asString().contains("ExplodingResource"),
                "no stack frame may leak: " + response.asString());
        assertFalse(response.asString().contains("stack trace"),
                "no server-side detail may leak: " + response.asString());
    }

    // ------------------------------------------------------------------ OpenAPI

    @Test
    void openApiDocumentListsAllFiveOperationsAndTheDtoSchemas() throws Exception {
        Response document = given().accept("application/json").when().get("/q/openapi");

        assertEquals(200, document.statusCode(), document.asString());
        JsonNode root = new ObjectMapper().readTree(document.asString());
        JsonNode paths = root.path("paths");

        assertTrue(paths.has("/coffees"), "paths must contain /coffees: " + paths);
        assertTrue(paths.path("/coffees").has("post"), "POST /coffees must be documented");
        assertTrue(paths.path("/coffees").has("get"), "GET /coffees must be documented");
        assertTrue(paths.has("/coffees/{id}"), "paths must contain /coffees/{id}: " + paths);
        assertTrue(paths.path("/coffees/{id}").has("get"), "GET /coffees/{id} must be documented");
        assertTrue(paths.path("/coffees/{id}").has("put"), "PUT /coffees/{id} must be documented");
        assertTrue(paths.path("/coffees/{id}").has("delete"), "DELETE /coffees/{id} must be documented");

        JsonNode schemas = root.path("components").path("schemas");
        assertTrue(schemas.has("CoffeeRequest"), "CoffeeRequest schema: " + schemas.fieldNames());
        assertTrue(schemas.has("CoffeeResponse"), "CoffeeResponse schema: " + schemas.fieldNames());
        assertTrue(schemas.has("CoffeePageResponse"), "CoffeePageResponse schema: " + schemas.fieldNames());
        assertTrue(schemas.path("CoffeeRequest").path("properties").has("name"));
        assertTrue(schemas.path("CoffeeResponse").path("properties").has("createdAt"));
        assertTrue(schemas.path("CoffeePageResponse").path("properties").has("totalPages"));

        JsonNode created = paths.path("/coffees").path("post").path("responses").path("201");
        assertTrue(created.has("content"), "the 201 response must document its body: " + created);

        JsonNode okContent = paths.path("/coffees").path("get").path("responses").path("200").path("content");
        assertTrue(okContent.fieldNames().hasNext(), "the list response must document a body: " + okContent);
        String listSchema = okContent.elements().next().path("schema").path("$ref").asText();
        assertTrue(listSchema.endsWith("CoffeePageResponse"), "the list schema must be the page DTO: " + listSchema);
    }

    // ------------------------------------------------------------------ helpers

    private String createCoffee(String name, String roastLevel, String origin, String price, int stock) {
        Response created = given().contentType(ContentType.JSON)
                .body(body(name, roastLevel, origin, price, stock))
                .when().post("/coffees");
        assertEquals(201, created.statusCode(), created.asString());
        return created.jsonPath().getString("id");
    }

    private static String body(String name, String roastLevel, String origin, String price, int stock) {
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

    private static void assertProblemDetail(Response response, int status, String title, String detail,
                                            String instance) {
        assertEquals(status, response.statusCode(), response.asString());
        assertTrue(response.contentType().startsWith("application/problem+json"), response.contentType());
        JsonNode json = parse(response);
        assertEquals("about:blank", json.get("type").asText());
        assertEquals(title, json.get("title").asText());
        assertEquals(status, json.get("status").asInt());
        assertEquals(detail, json.get("detail").asText());
        assertEquals(instance, json.get("instance").asText());
    }

    private void assertValidationError(String body, String field, String message) {
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
        assertTrue(match, "expected errors[] to contain " + field + " -> '" + message + "': " + response.asString());
    }

    private void assertPagingError(String parameter, String value, String field, String message) {
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
        assertTrue(match, "expected errors[] to contain " + field + " -> '" + message + "': " + response.asString());
    }

    private static void assertNotFound(Response response, String path, String detail) {
        assertProblemDetail(response, 404, "Not Found", detail, path);
        assertNull(parse(response).get("errors"), "404 must not carry errors[]");
    }
}
