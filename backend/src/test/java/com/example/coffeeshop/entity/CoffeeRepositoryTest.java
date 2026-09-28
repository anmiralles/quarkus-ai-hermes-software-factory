package com.example.coffeeshop.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.sql.DataSource;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

/**
 * Entity-layer slice test: JPA mapping, Bean Validation, the repository queries and the
 * Flyway migration, all on the H2 test profile. Every assertion checks a concrete value.
 */
@QuarkusTest
class CoffeeRepositoryTest {

    @Inject
    CoffeeRepository repository;

    @Inject
    Validator validator;

    @Inject
    Config config;

    @Inject
    DataSource dataSource;

    // ------------------------------------------------------------------ persistence

    @Test
    @TestTransaction
    void persistAndReadBackAssertsEveryConcreteFieldValue() {
        repository.deleteAll();
        Coffee coffee = newCoffee("Ethiopia Yirgacheffe", RoastLevel.LIGHT, "Ethiopia", "12.34", 40);

        repository.persistAndFlush(coffee);
        UUID id = coffee.getId();
        assertNotNull(id, "the id must be generated on persist");

        // detach, so the read comes back from the database rather than the persistence context
        repository.getEntityManager().clear();
        Coffee reloaded = repository.findById(id);

        assertNotNull(reloaded);
        assertEquals(id, reloaded.getId());
        assertEquals("Ethiopia Yirgacheffe", reloaded.getName());
        assertEquals(RoastLevel.LIGHT, reloaded.getRoastLevel());
        assertEquals("Ethiopia", reloaded.getOrigin());
        assertEquals(0, new BigDecimal("12.34").compareTo(reloaded.getPrice()));
        assertEquals(2, reloaded.getPrice().scale(), "price must be stored at scale 2");
        assertEquals(40, reloaded.getStock());
    }

    @Test
    @TestTransaction
    void lifecycleHooksSetTimestampsOnInsertAndAdvanceUpdatedAtOnUpdate() throws Exception {
        repository.deleteAll();
        Coffee coffee = newCoffee("Colombia Huila", RoastLevel.MEDIUM, "Colombia", "10.00", 7);
        repository.persistAndFlush(coffee);

        Instant createdAt = coffee.getCreatedAt();
        Instant updatedAtOnInsert = coffee.getUpdatedAt();
        assertNotNull(createdAt, "@PrePersist must set createdAt");
        assertEquals(createdAt, updatedAtOnInsert, "@PrePersist must set updatedAt equal to createdAt");

        // distinct clock ticks, so the assertion below fails if @PreUpdate does not run
        Thread.sleep(10);
        coffee.setStock(8);
        repository.flush();

        Instant updatedAtAfterUpdate = coffee.getUpdatedAt();
        assertTrue(updatedAtAfterUpdate.isAfter(createdAt),
                "updatedAt must be advanced by @PreUpdate, was " + updatedAtAfterUpdate + " created " + createdAt);
        assertEquals(8, coffee.getStock());
        assertEquals(createdAt, coffee.getCreatedAt(), "createdAt must not change on update");
    }

    // ------------------------------------------------------------------ repository queries

    @Test
    @TestTransaction
    void findByNameMatchesTheStoredNameExactlyAndIsCaseSensitive() {
        repository.deleteAll();
        repository.persist(newCoffee("Cafe Solo", RoastLevel.MEDIUM, "Brazil", "9.99", 5));

        Optional<Coffee> exact = repository.findByName("Cafe Solo");
        assertTrue(exact.isPresent(), "an exact name match must be found");
        assertEquals("Brazil", exact.get().getOrigin());
        assertEquals(0, new BigDecimal("9.99").compareTo(exact.get().getPrice()));

        assertTrue(repository.findByName("cafe solo").isEmpty(), "matching must be case-sensitive");
        assertTrue(repository.findByName("Cafe Solo ").isEmpty(), "matching must be exact, no trimming here");
    }

    @Test
    @TestTransaction
    void findByRoastLevelFiltersByLevelAndOrdersByNameAscending() {
        repository.deleteAll();
        repository.persist(newCoffee("B Brazil", RoastLevel.LIGHT, "Brazil", "8.00", 3));
        repository.persist(newCoffee("A Ethiopia", RoastLevel.LIGHT, "Ethiopia", "12.00", 4));
        repository.persist(newCoffee("C Colombia", RoastLevel.MEDIUM, "Colombia", "9.00", 5));

        List<Coffee> light = repository.findByRoastLevel(RoastLevel.LIGHT);

        assertEquals(List.of("A Ethiopia", "B Brazil"), names(light));
        assertEquals(List.of(RoastLevel.LIGHT, RoastLevel.LIGHT), light.stream().map(Coffee::getRoastLevel).toList());
        assertTrue(repository.findByRoastLevel(RoastLevel.DARK).isEmpty(), "no DARK coffee was stored");
    }

    @Test
    @TestTransaction
    void findAllPagedReturnsZeroBasedPagesOrderedByName() {
        repository.deleteAll();
        for (String name : List.of("Charlie", "Alpha", "Echo", "Bravo", "Delta")) {
            repository.persist(newCoffee(name, RoastLevel.LIGHT, "Peru", "1.00", 1));
        }
        repository.flush();

        assertEquals(List.of("Alpha", "Bravo"), names(repository.findAllPaged(0, 2)));
        assertEquals(List.of("Charlie", "Delta"), names(repository.findAllPaged(1, 2)));
        assertEquals(List.of("Echo"), names(repository.findAllPaged(2, 2)));
        assertTrue(repository.findAllPaged(3, 2).isEmpty(), "page past the end must be empty");
        assertEquals(5L, repository.count(), "control needs the total to compute totalPages");
    }

    // ------------------------------------------------------------------ Bean Validation

    @Test
    void validationAcceptsACompleteCoffee() {
        assertTrue(validator.validate(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "14.50", 0)).isEmpty(),
                "a complete coffee must have no violations");
        assertTrue(validator.validate(newCoffee("x".repeat(100), RoastLevel.DARK, "y".repeat(100), "99999999.99", 5)).isEmpty(),
                "100-character name/origin and the maximum price must be accepted");
    }

    @Test
    void validationRejectsBlankName() {
        assertViolation(newCoffee("   ", RoastLevel.LIGHT, "Ethiopia", "12.34", 1),
                "name", "name must not be blank");
    }

    @Test
    void validationRejectsNameLongerThan100Characters() {
        assertViolation(newCoffee("x".repeat(101), RoastLevel.LIGHT, "Ethiopia", "12.34", 1),
                "name", "name size must be between 1 and 100");
    }

    @Test
    void validationRejectsBlankOrigin() {
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "  ", "12.34", 1),
                "origin", "origin must not be blank");
    }

    @Test
    void validationRejectsOriginLongerThan100Characters() {
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "y".repeat(101), "12.34", 1),
                "origin", "origin size must be between 1 and 100");
    }

    @Test
    void validationRejectsNullRoastLevel() {
        assertViolation(newCoffee("Kenya AA", null, "Kenya", "12.34", 1),
                "roastLevel", "roastLevel must be one of LIGHT, MEDIUM, DARK");
    }

    @Test
    void validationRejectsNullPrice() {
        Coffee coffee = newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "12.34", 1);
        coffee.setPrice(null);
        assertViolation(coffee, "price", "price must not be null");
    }

    @Test
    void validationRejectsZeroAndNegativePrice() {
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "0", 1),
                "price", "price must be greater than 0");
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "-0.01", 1),
                "price", "price must be greater than 0");
    }

    @Test
    void validationRejectsPriceAboveTheNumericColumnMaximum() {
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "100000000.00", 1),
                "price", "price must be at most 99999999.99");
    }

    @Test
    void validationRejectsPriceWithAThirdDecimalPlace() {
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "12.345", 1),
                "price", "price must have at most 2 decimal places");
    }

    @Test
    void validationRejectsNegativeStock() {
        assertViolation(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "12.34", -1),
                "stock", "stock must be greater than or equal to 0");
    }

    // ------------------------------------------------------------------ migration / database constraints

    @Test
    void migrationV1AppliedCleanlyAndCreatedTheExpectedTable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet history = statement.executeQuery(
                     "SELECT \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '1'")) {
            assertTrue(history.next(), "V1__create_coffee.sql must be recorded in flyway_schema_history");
            assertTrue(history.getBoolean("success"), "V1 must have applied successfully");
            assertFalse(history.next(), "exactly one V1 row");
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            Set<String> columns = new TreeSet<>();
            try (ResultSet rs = metaData.getColumns(null, null, null, null)) {
                while (rs.next()) {
                    if ("coffee".equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
            assertEquals(Set.of("id", "name", "roast_level", "origin", "price", "stock", "created_at", "updated_at"),
                    columns);

            assertEquals(Set.of("ID", "NAME"), indexedColumns(metaData, true),
                    "the primary key on id and the unique constraint on name must both be indexes");
            assertEquals(Set.of("ID", "NAME", "ROAST_LEVEL"), indexedColumns(metaData, false),
                    "the primary key plus ix_coffee_name and ix_coffee_roast_level must exist");
        }
    }

    @Test
    void duplicateNameIsRejectedByTheUniqueConstraintInTheDatabase() throws SQLException {
        insertRawCoffee("Cafe Solo", RoastLevel.MEDIUM, "Brazil", new BigDecimal("9.99"), 5);

        SQLException failure = assertThrows(SQLException.class,
                () -> insertRawCoffee("Cafe Solo", RoastLevel.LIGHT, "Peru", new BigDecimal("4.00"), 2),
                "a second row with the same name must be rejected");

        assertEquals("23505", failure.getSQLState(), "duplicate key SQLState: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("UQ_COFFEE_NAME"), failure.getMessage());
        assertEquals(1, rawCoffeeCount(), "only the first row must be stored");
        deleteAllRawCoffees();
    }

    @Test
    void checkConstraintsRejectNonPositivePriceAndNegativeStockInTheDatabase() throws SQLException {
        SQLException priceFailure = assertThrows(SQLException.class,
                () -> insertRawCoffee("Zero Price", RoastLevel.LIGHT, "Peru", new BigDecimal("0"), 1));
        assertTrue(priceFailure.getMessage().contains("CK_COFFEE_PRICE_POSITIVE"), priceFailure.getMessage());

        SQLException stockFailure = assertThrows(SQLException.class,
                () -> insertRawCoffee("Negative Stock", RoastLevel.LIGHT, "Peru", new BigDecimal("1.00"), -1));
        assertTrue(stockFailure.getMessage().contains("CK_COFFEE_STOCK_NON_NEGATIVE"), stockFailure.getMessage());

        deleteAllRawCoffees();
    }

    // ------------------------------------------------------------------ configuration

    @Test
    void flywayOwnsTheSchemaAndHibernateDoesNotGenerateDdl() {
        assertEquals("none", config.getValue("quarkus.hibernate-orm.database.generation", String.class),
                "Hibernate must never write DDL");
        assertEquals("true", config.getValue("quarkus.flyway.migrate-at-start", String.class),
                "Flyway must migrate on startup");
    }

    @Test
    void testProfileUsesH2WithTheExpectedUrl() {
        assertEquals("h2", config.getValue("quarkus.datasource.db-kind", String.class));
        assertEquals("jdbc:h2:mem:coffee-test;DB_CLOSE_DELAY=-1",
                config.getValue("quarkus.datasource.jdbc.url", String.class));
    }

    // ------------------------------------------------------------------ helpers

    private static Coffee newCoffee(String name, RoastLevel roastLevel, String origin, String price, int stock) {
        Coffee coffee = new Coffee();
        coffee.setName(name);
        coffee.setRoastLevel(roastLevel);
        coffee.setOrigin(origin);
        coffee.setPrice(price == null ? null : new BigDecimal(price));
        coffee.setStock(stock);
        return coffee;
    }

    private static List<String> names(List<Coffee> coffees) {
        return coffees.stream().map(Coffee::getName).toList();
    }

    private void assertViolation(Coffee coffee, String property, String message) {
        Set<ConstraintViolation<Coffee>> violations = validator.validate(coffee);
        List<String> seen = new ArrayList<>();
        for (ConstraintViolation<Coffee> violation : violations) {
            seen.add(violation.getPropertyPath() + " -> " + violation.getMessage());
        }
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(property)
                        && v.getMessage().equals(message)),
                "expected violation " + property + " -> '" + message + "' but got " + seen);
    }

    private static Set<String> indexedColumns(DatabaseMetaData metaData, boolean unique) throws SQLException {
        Set<String> columns = new TreeSet<>();
        try (ResultSet rs = metaData.getIndexInfo(null, null, "COFFEE", unique, false)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (column != null) {
                    columns.add(column.toUpperCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private void insertRawCoffee(String name, RoastLevel roastLevel, String origin, BigDecimal price, int stock)
            throws SQLException {
        String sql = "INSERT INTO coffee (id, name, roast_level, origin, price, stock, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, name);
            statement.setString(3, roastLevel.name());
            statement.setString(4, origin);
            statement.setBigDecimal(5, price);
            statement.setInt(6, stock);
            statement.setObject(7, now);
            statement.setObject(8, now);
            statement.executeUpdate();
        }
    }

    private void deleteAllRawCoffees() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM coffee");
        }
    }

    private int rawCoffeeCount() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM coffee")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
