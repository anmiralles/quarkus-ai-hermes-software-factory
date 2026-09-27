package com.example.coffeeshop.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.coffeeshop.control.exception.CoffeeAlreadyExistsException;
import com.example.coffeeshop.control.exception.CoffeeNotFoundException;
import com.example.coffeeshop.control.exception.InvalidPagingException;
import com.example.coffeeshop.entity.Coffee;
import com.example.coffeeshop.entity.CoffeeRepository;
import com.example.coffeeshop.entity.RoastLevel;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CoffeeService} with the repository mocked (spec section 9.2, AC-CB1 and
 * AC-CB10): every use case plus both domain error paths, and the flush-time constraint mapping
 * the pre-check cannot cover.
 */
@QuarkusTest
class CoffeeServiceTest {

    @InjectMock
    CoffeeRepository repository;

    @Inject
    CoffeeService service;

    // ------------------------------------------------------------------ createCoffee

    @Test
    void createCoffeeTrimsNameAndOriginBeforePersisting() {
        when(repository.findByName("Kenya AA")).thenReturn(Optional.empty());
        Coffee candidate = newCoffee("  Kenya AA  ", RoastLevel.LIGHT, "  Kenya ", "14.50", 3);

        Coffee created = service.createCoffee(candidate);

        assertEquals("Kenya AA", created.getName(), "name must be trimmed before persistence");
        assertEquals("Kenya", created.getOrigin());
        assertSame(candidate, created);
        verify(repository).findByName("Kenya AA");
        verify(repository).persist(candidate);
        verify(repository).flush();
    }

    @Test
    void createCoffeeRejectsADuplicateNameWithTheDomainConflict() {
        Coffee existing = existingCoffee("Kenya AA");
        when(repository.findByName("Kenya AA")).thenReturn(Optional.of(existing));

        CoffeeAlreadyExistsException failure = assertThrows(CoffeeAlreadyExistsException.class,
                () -> service.createCoffee(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "14.50", 3)));

        assertEquals("Coffee name 'Kenya AA' already exists", failure.getMessage());
        assertEquals("Kenya AA", failure.getName());
        verify(repository, never()).persist(org.mockito.ArgumentMatchers.any(Coffee.class));
    }

    @Test
    void createCoffeeMapsTheUniqueConstraintViolationOnFlushToTheDomainConflict() {
        when(repository.findByName("Kenya AA")).thenReturn(Optional.empty());
        doThrow(uniqueViolation()).when(repository).flush();

        CoffeeAlreadyExistsException failure = assertThrows(CoffeeAlreadyExistsException.class,
                () -> service.createCoffee(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "14.50", 3)));

        assertEquals("Coffee name 'Kenya AA' already exists", failure.getMessage());
    }

    @Test
    void createCoffeeRethrowsAPersistenceFailureThatIsNotAUniqueViolation() {
        when(repository.findByName("Kenya AA")).thenReturn(Optional.empty());
        PersistenceException broken = new PersistenceException(new SQLException("connection reset", "08006"));
        doThrow(broken).when(repository).flush();

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> service.createCoffee(newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "14.50", 3)));

        assertEquals(PersistenceException.class, failure.getClass(),
                "an infrastructure failure must not be reported to the client as a conflict");
        assertSame(broken, failure);
    }

    // ------------------------------------------------------------------ getCoffee

    @Test
    void getCoffeeReturnsTheStoredEntity() {
        Coffee stored = existingCoffee("Kenya AA");
        when(repository.findByIdOptional(stored.getId())).thenReturn(Optional.of(stored));

        assertSame(stored, service.getCoffee(stored.getId()));
    }

    @Test
    void getCoffeeThrowsNotFoundForAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdOptional(id)).thenReturn(Optional.empty());

        CoffeeNotFoundException failure = assertThrows(CoffeeNotFoundException.class, () -> service.getCoffee(id));

        assertEquals("Coffee " + id + " not found", failure.getMessage());
        assertEquals(id.toString(), failure.getId());
    }

    // ------------------------------------------------------------------ listCoffees

    @Test
    void listCoffeesReturnsTheRequestedPageAndTheTotal() {
        when(repository.findAllPaged(1, 2)).thenReturn(List.of(existingCoffee("Alpha"), existingCoffee("Bravo")));
        when(repository.count()).thenReturn(5L);

        CoffeePage page = service.listCoffees(1, 2);

        assertEquals(List.of("Alpha", "Bravo"), page.content().stream().map(Coffee::getName).toList());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5L, page.totalElements());
        verify(repository).findAllPaged(1, 2);
    }

    @Test
    void listCoffeesRejectsANegativePageInsteadOfClampingIt() {
        InvalidPagingException failure = assertThrows(InvalidPagingException.class, () -> service.listCoffees(-1, 20));

        assertEquals("page must be >= 0", failure.getMessage());
        assertEquals("page", failure.getField());
        verify(repository, never()).findAllPaged(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void listCoffeesRejectsASizeOutsideOneToHundred() {
        InvalidPagingException zero = assertThrows(InvalidPagingException.class, () -> service.listCoffees(0, 0));
        assertEquals("size must be between 1 and 100", zero.getMessage());
        assertEquals("size", zero.getField());

        InvalidPagingException tooLarge = assertThrows(InvalidPagingException.class, () -> service.listCoffees(0, 101));
        assertEquals("size must be between 1 and 100", tooLarge.getMessage());

        verify(repository, never()).findAllPaged(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void listCoffeesAcceptsTheContractBoundaries() {
        when(repository.findAllPaged(0, 1)).thenReturn(List.of(existingCoffee("Alpha")));
        when(repository.findAllPaged(0, 100)).thenReturn(List.of(existingCoffee("Alpha")));
        when(repository.count()).thenReturn(1L);

        assertEquals(1, service.listCoffees(0, 1).content().size());
        assertEquals(1, service.listCoffees(0, 100).content().size());
    }

    // ------------------------------------------------------------------ updateCoffee

    @Test
    void updateCoffeeReplacesEveryMutableField() {
        Coffee existing = existingCoffee("Kenya AA");
        when(repository.findByIdOptional(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findByName("Kenya Peaberry")).thenReturn(Optional.empty());

        Coffee updated = service.updateCoffee(existing.getId(),
                newCoffee(" Kenya Peaberry ", RoastLevel.DARK, " Kenya ", "19.99", 12));

        assertSame(existing, updated);
        assertEquals("Kenya Peaberry", updated.getName());
        assertEquals(RoastLevel.DARK, updated.getRoastLevel());
        assertEquals("Kenya", updated.getOrigin());
        assertEquals(0, new BigDecimal("19.99").compareTo(updated.getPrice()));
        assertEquals(12, updated.getStock());
        verify(repository).flush();
    }

    @Test
    void updateCoffeeKeepsItsOwnNameWithoutReportingAConflict() {
        Coffee existing = existingCoffee("Kenya AA");
        when(repository.findByIdOptional(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findByName("Kenya AA")).thenReturn(Optional.of(existing));

        Coffee updated = service.updateCoffee(existing.getId(),
                newCoffee("Kenya AA", RoastLevel.MEDIUM, "Kenya", "15.00", 4));

        assertEquals("Kenya AA", updated.getName());
        assertEquals(RoastLevel.MEDIUM, updated.getRoastLevel());
    }

    @Test
    void updateCoffeeThrowsNotFoundForAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdOptional(id)).thenReturn(Optional.empty());

        CoffeeNotFoundException failure = assertThrows(CoffeeNotFoundException.class,
                () -> service.updateCoffee(id, newCoffee("Kenya AA", RoastLevel.LIGHT, "Kenya", "14.50", 3)));

        assertEquals("Coffee " + id + " not found", failure.getMessage());
        verify(repository, never()).flush();
    }

    @Test
    void updateCoffeeRejectsANameAlreadyUsedByAnotherCoffee() {
        Coffee existing = existingCoffee("Kenya AA");
        Coffee other = existingCoffee("Colombia Huila");
        when(repository.findByIdOptional(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findByName("Colombia Huila")).thenReturn(Optional.of(other));

        CoffeeAlreadyExistsException failure = assertThrows(CoffeeAlreadyExistsException.class,
                () -> service.updateCoffee(existing.getId(),
                        newCoffee("Colombia Huila", RoastLevel.LIGHT, "Colombia", "9.00", 2)));

        assertEquals("Coffee name 'Colombia Huila' already exists", failure.getMessage());
        assertEquals("Kenya AA", existing.getName(), "the entity must be untouched when the update is rejected");
    }

    @Test
    void updateCoffeeMapsTheUniqueConstraintViolationOnFlushToTheDomainConflict() {
        Coffee existing = existingCoffee("Kenya AA");
        when(repository.findByIdOptional(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findByName(anyString())).thenReturn(Optional.empty());
        doThrow(uniqueViolation()).when(repository).flush();

        CoffeeAlreadyExistsException failure = assertThrows(CoffeeAlreadyExistsException.class,
                () -> service.updateCoffee(existing.getId(),
                        newCoffee("Colombia Huila", RoastLevel.LIGHT, "Colombia", "9.00", 2)));

        assertEquals("Coffee name 'Colombia Huila' already exists", failure.getMessage());
    }

    // ------------------------------------------------------------------ deleteCoffee

    @Test
    void deleteCoffeeRemovesTheStoredEntity() {
        Coffee stored = existingCoffee("Kenya AA");
        when(repository.findByIdOptional(stored.getId())).thenReturn(Optional.of(stored));

        service.deleteCoffee(stored.getId());

        verify(repository).delete(stored);
    }

    @Test
    void deleteCoffeeThrowsNotFoundForAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdOptional(id)).thenReturn(Optional.empty());

        CoffeeNotFoundException failure = assertThrows(CoffeeNotFoundException.class, () -> service.deleteCoffee(id));

        assertEquals("Coffee " + id + " not found", failure.getMessage());
        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any(Coffee.class));
    }

    // ------------------------------------------------------------------ helpers

    /** The failure Hibernate raises when {@code uq_coffee_name} rejects the insert. */
    private static PersistenceException uniqueViolation() {
        return new ConstraintViolationException("could not execute statement",
                new SQLException("Unique index or primary key violation: UQ_COFFEE_NAME", "23505"),
                "uq_coffee_name");
    }

    private static Coffee existingCoffee(String name) {
        Coffee coffee = newCoffee(name, RoastLevel.LIGHT, "Kenya", "14.50", 3);
        try {
            java.lang.reflect.Field id = Coffee.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(coffee, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return coffee;
    }

    private static Coffee newCoffee(String name, RoastLevel roastLevel, String origin, String price, int stock) {
        Coffee coffee = new Coffee();
        coffee.setName(name);
        coffee.setRoastLevel(roastLevel);
        coffee.setOrigin(origin);
        coffee.setPrice(price == null ? null : new BigDecimal(price));
        coffee.setStock(stock);
        return coffee;
    }

    @Test
    void theRepositoryIsConstructorInjectedNotFieldInjected() {
        assertNotNull(repository, "the repository must be the injected mock");

        for (java.lang.reflect.Field field : CoffeeService.class.getDeclaredFields()) {
            assertFalse(field.isAnnotationPresent(jakarta.inject.Inject.class),
                    "the repository must arrive through the constructor, not a field: " + field);
        }
        long repositoryConstructors = java.util.Arrays.stream(CoffeeService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.getParameterCount() == 1
                        && constructor.getParameterTypes()[0] == CoffeeRepository.class)
                .count();
        assertEquals(1L, repositoryConstructors, "exactly one constructor taking the repository");
    }
}
