package com.example.coffeeshop.control;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

import com.example.coffeeshop.control.exception.CoffeeAlreadyExistsException;
import com.example.coffeeshop.control.exception.CoffeeNotFoundException;
import com.example.coffeeshop.control.exception.InvalidPagingException;
import com.example.coffeeshop.entity.Coffee;
import com.example.coffeeshop.entity.CoffeeRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Use cases for the coffee catalogue. Owns the rules that are not bean validation: trimming,
 * name uniqueness (pre-check plus the database constraint), paging validation and the
 * transaction boundaries. Returns entities and page objects or throws typed domain exceptions;
 * it never sees HTTP and never imports a DTO (spec sections 1.1, 5).
 */
@ApplicationScoped
public class CoffeeService {

    private static final Logger LOG = Logger.getLogger(CoffeeService.class);

    /** {@code page} is 0-based; {@code size} is bounded by the contract (spec section 3.1). */
    static final int MAX_PAGE_SIZE = 100;

    /** Unique-constraint violation, identical on H2 and PostgreSQL. */
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    /** Constraint the unique name rule is enforced by (spec section 4.4). */
    private static final String UNIQUE_CONSTRAINT_NAME = "uq_coffee_name";

    private final CoffeeRepository repository;

    @Inject
    public CoffeeService(CoffeeRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a new coffee. {@code id}, {@code createdAt} and {@code updatedAt} are generated
     * by the entity, never taken from the caller.
     */
    @Transactional
    public Coffee createCoffee(Coffee candidate) {
        trimText(candidate);
        requireNameAvailable(candidate.getName(), null);
        try {
            repository.persist(candidate);
            // flush inside the transaction, so a lost race is reported as 409 here rather than
            // blowing up on commit as an unmapped 500
            repository.flush();
        } catch (PersistenceException e) {
            throw translateUniqueViolation(e, candidate.getName());
        }
        LOG.debugf("created coffee %s named '%s'", candidate.getId(), candidate.getName());
        return candidate;
    }

    @Transactional
    public Coffee getCoffee(UUID id) {
        return repository.findByIdOptional(id).orElseThrow(() -> new CoffeeNotFoundException(id));
    }

    /**
     * One page of the catalogue, ordered {@code name} asc then {@code id} asc by the repository.
     *
     * @throws InvalidPagingException if {@code page < 0} or {@code size} is outside {@code 1..100}
     */
    @Transactional
    public CoffeePage listCoffees(int page, int size) {
        if (page < 0) {
            throw new InvalidPagingException("page", "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPagingException("size", "size must be between 1 and 100");
        }
        return new CoffeePage(repository.findAllPaged(page, size), page, size, repository.count());
    }

    /**
     * Full replacement of the mutable fields; {@code id} and the timestamps are untouched.
     * {@code updatedAt} only advances when the entity is actually dirty, because
     * {@code @PreUpdate} runs on dirty checking alone.
     */
    @Transactional
    public Coffee updateCoffee(UUID id, Coffee changes) {
        Coffee existing = repository.findByIdOptional(id).orElseThrow(() -> new CoffeeNotFoundException(id));
        trimText(changes);
        requireNameAvailable(changes.getName(), id);

        existing.setName(changes.getName());
        existing.setRoastLevel(changes.getRoastLevel());
        existing.setOrigin(changes.getOrigin());
        existing.setPrice(changes.getPrice());
        existing.setStock(changes.getStock());

        try {
            repository.flush();
        } catch (PersistenceException e) {
            throw translateUniqueViolation(e, changes.getName());
        }
        LOG.debugf("updated coffee %s named '%s'", id, existing.getName());
        return existing;
    }

    @Transactional
    public void deleteCoffee(UUID id) {
        Coffee coffee = repository.findByIdOptional(id).orElseThrow(() -> new CoffeeNotFoundException(id));
        repository.delete(coffee);
        LOG.debugf("deleted coffee %s", id);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Trimmed before uniqueness and before persistence (spec section 2). Bean validation rejects
     * blank/oversized values before control is reached; nulls are tolerated here so a direct
     * control call in a unit test fails on the unique index rather than on an NPE.
     */
    private static void trimText(Coffee coffee) {
        coffee.setName(trim(coffee.getName()));
        coffee.setOrigin(trim(coffee.getOrigin()));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Pre-check for a clean 409. Uniqueness is exact and case-sensitive, so no folding here.
     *
     * @param selfId the coffee being updated, whose own name is of course taken by itself
     */
    private void requireNameAvailable(String name, UUID selfId) {
        repository.findByName(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new CoffeeAlreadyExistsException(name);
                });
    }

    /**
     * Maps the database unique constraint (the concurrent-POST race the pre-check cannot close)
     * to the domain conflict. Any other persistence failure is a genuine error and propagates.
     *
     * <p>Detection stays on {@code jakarta.persistence} + {@code java.sql}: the cause chain is
     * walked for a {@link SQLException} whose SQLState is {@code 23505} (the same value on H2 and
     * PostgreSQL), so {@code control} needs no vendor import and the spec's import matrix holds.
     */
    private static RuntimeException translateUniqueViolation(PersistenceException failure, String name) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlFailure && isUniqueViolation(sqlFailure)) {
                LOG.debugf("unique constraint rejected the name '%s'", name);
                return new CoffeeAlreadyExistsException(name);
            }
        }
        return failure;
    }

    private static boolean isUniqueViolation(SQLException failure) {
        if (UNIQUE_VIOLATION_SQL_STATE.equals(failure.getSQLState())) {
            return true;
        }
        // last resort for a driver that reports no SQLState: the constraint the contract defines
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
        return message.contains(UNIQUE_CONSTRAINT_NAME);
    }
}
