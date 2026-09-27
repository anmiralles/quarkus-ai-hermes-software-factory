package com.example.coffeeshop.verify;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-V8's interesting case: N genuinely-parallel {@code POST /coffees} with the same name.
 *
 * <p>The uniqueness pre-check in {@code control} cannot close the race — two requests can both
 * read "not taken" and both persist. The guarantee has to come from the {@code uq_coffee_name}
 * database constraint, mapped to a 409 instead of an unmapped 500. This test drives the real
 * HTTP stack from a real thread pool (not a sequential pair of calls) and asserts exactly one
 * 201, the rest 409, and one row in the table.
 */
@QuarkusTest
@DisplayName("QA verification: concurrent duplicate-name POSTs")
class QaConcurrencyTest {

    private static final int CONTENDERS = 32;
    private static final int ROUNDS = 5;

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
    @DisplayName("AC-V8 exactly one of N parallel identical POSTs is 201, the rest are 409, none is 500")
    void acV8_concurrentDuplicateNamePosts() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String name = "Race Coffee " + round;
            String body = "{\"name\":\"" + name + "\",\"roastLevel\":\"LIGHT\",\"origin\":\"Kenya\","
                    + "\"price\":4.20,\"stock\":1}";

            ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
            CountDownLatch ready = new CountDownLatch(CONTENDERS);
            CountDownLatch start = new CountDownLatch(1);
            Map<Integer, String> failures = new ConcurrentHashMap<>();
            List<Future<Integer>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < CONTENDERS; i++) {
                    int contender = i;
                    Callable<Integer> post = () -> {
                        ready.countDown();
                        start.await(30, TimeUnit.SECONDS);
                        Response response = given().contentType(ContentType.JSON).body(body)
                                .when().post("/coffees");
                        if (response.statusCode() != 201 && response.statusCode() != 409) {
                            failures.put(contender, response.statusCode() + " " + response.asString());
                        }
                        return response.statusCode();
                    };
                    futures.add(pool.submit(post));
                }

                assertTrue(ready.await(30, TimeUnit.SECONDS), "all contenders must reach the start line");
                start.countDown();

                int created = 0;
                int conflicts = 0;
                for (Future<Integer> future : futures) {
                    int status = future.get(60, TimeUnit.SECONDS);
                    if (status == 201) {
                        created++;
                    } else if (status == 409) {
                        conflicts++;
                    }
                }

                assertTrue(failures.isEmpty(),
                        "round " + round + ": no request may return 500 or any other status: " + failures);
                assertEquals(1, created, "round " + round + ": exactly one POST may win");
                assertEquals(CONTENDERS - 1, conflicts,
                        "round " + round + ": every loser must be a mapped 409");
            } finally {
                pool.shutdownNow();
            }

            given().when().get("/coffees").then().statusCode(200)
                    .body("totalElements", equalTo(round + 1));
        }

        Number total = given().when().get("/coffees").then().extract().path("totalElements");
        assertEquals(ROUNDS, total.longValue(), "exactly one row survives per round");
    }
}
