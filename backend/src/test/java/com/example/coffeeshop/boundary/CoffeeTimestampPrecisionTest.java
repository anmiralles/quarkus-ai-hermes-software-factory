package com.example.coffeeshop.boundary;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
 * Regression guard for defect D1 (card {@code t_db17ce81}): the {@code createdAt}/{@code updatedAt}
 * a write hands back must equal the value a later read returns.
 *
 * <p>{@link java.time.Instant#now()} is nanosecond-precision, while {@code created_at}/{@code
 * updated_at} are {@code TIMESTAMP WITH TIME ZONE} and resolve to the microsecond on H2 and
 * PostgreSQL. Serialising the create/update response from the in-memory entity therefore exposed a
 * timestamp that a subsequent {@code GET} never reproduced. The entity now truncates to
 * microseconds, so the two must be byte-for-byte identical.
 *
 * <p>The test creates several coffees rather than one: a single {@code Instant.now()} value is
 * microsecond-aligned roughly once in a thousand calls, so one sample could pass by luck against
 * the defect. Ten independent samples make an accidental pass negligible, and the fractional-digit
 * assertion pins the intent (at most six digits) regardless of the clock's resolution.
 */
@QuarkusTest
class CoffeeTimestampPrecisionTest {

    /** ISO-8601 UTC with an optional fractional part of at most six digits (microsecond storage). */
    private static final String ISO_UTC_AT_STORAGE_PRECISION =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z";

    /** Samples per run: see the class comment — one sample is not a reliable trigger for D1. */
    private static final int CREATES = 10;

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

    @Test
    void theCreateResponseTimestampsEqualWhatALaterReadReturns() {
        for (int i = 0; i < CREATES; i++) {
            Response created = given().contentType(ContentType.JSON)
                    .body(body("Precision " + i, "LIGHT", "Kenya", "1.00", 1))
                    .when().post("/coffees");
            assertEquals(201, created.statusCode(), created.asString());

            JsonNode onCreate = parse(created);
            String id = onCreate.get("id").asText();
            String createdAtOnCreate = onCreate.get("createdAt").asText();
            String updatedAtOnCreate = onCreate.get("updatedAt").asText();

            assertTrue(createdAtOnCreate.matches(ISO_UTC_AT_STORAGE_PRECISION),
                    "the createdAt in the 201 body must be ISO-8601 UTC at storage precision: "
                            + createdAtOnCreate);
            assertTrue(updatedAtOnCreate.matches(ISO_UTC_AT_STORAGE_PRECISION),
                    "the updatedAt in the 201 body must be ISO-8601 UTC at storage precision: "
                            + updatedAtOnCreate);
            assertEquals(createdAtOnCreate, updatedAtOnCreate,
                    "updatedAt equals createdAt on insert");

            JsonNode read = parse(given().when().get("/coffees/{id}", id));
            assertEquals(createdAtOnCreate, read.get("createdAt").asText(),
                    "createdAt from POST must equal createdAt from a later GET (D1): "
                            + createdAtOnCreate + " vs " + read.get("createdAt").asText());
            assertEquals(updatedAtOnCreate, read.get("updatedAt").asText(),
                    "updatedAt from POST must equal updatedAt from a later GET (D1): "
                            + updatedAtOnCreate + " vs " + read.get("updatedAt").asText());
        }
    }

    @Test
    void theUpdateResponseUpdatedAtEqualsWhatALaterReadReturns() throws InterruptedException {
        Response created = given().contentType(ContentType.JSON)
                .body(body("Precision Update", "MEDIUM", "Brazil", "9.99", 5))
                .when().post("/coffees");
        assertEquals(201, created.statusCode(), created.asString());
        JsonNode onCreate = parse(created);
        String id = onCreate.get("id").asText();
        String createdAt = onCreate.get("createdAt").asText();
        String updatedAtOnCreate = onCreate.get("updatedAt").asText();

        // long enough for the microsecond timestamps to differ, so a real @PreUpdate is exercised
        Thread.sleep(10);
        Response updated = given().contentType(ContentType.JSON)
                .body(body("Precision Update", "MEDIUM", "Brazil", "9.99", 6))
                .when().put("/coffees/{id}", id);
        assertEquals(200, updated.statusCode(), updated.asString());

        JsonNode onUpdate = parse(updated);
        String updatedAtOnUpdate = onUpdate.get("updatedAt").asText();
        assertEquals(createdAt, onUpdate.get("createdAt").asText(), "createdAt is immutable");
        assertTrue(!updatedAtOnUpdate.equals(updatedAtOnCreate),
                "a dirty update must advance updatedAt: " + updatedAtOnUpdate);
        assertTrue(updatedAtOnUpdate.matches(ISO_UTC_AT_STORAGE_PRECISION),
                "the updatedAt in the 200 body must be ISO-8601 UTC at storage precision: "
                        + updatedAtOnUpdate);

        JsonNode read = parse(given().when().get("/coffees/{id}", id));
        assertEquals(updatedAtOnUpdate, read.get("updatedAt").asText(),
                "updatedAt from PUT must equal updatedAt from a later GET (D1): "
                        + updatedAtOnUpdate + " vs " + read.get("updatedAt").asText());
        assertEquals(createdAt, read.get("createdAt").asText(), "createdAt is immutable across reads");
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
}
