package com.example.coffeeshop.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.UuidGenerator;

/**
 * A coffee in the catalogue. The only place that knows how a coffee is persisted.
 *
 * <p>There is no {@code @Version} column: the contract exposes no ETag/If-Match, so
 * optimistic locking would be carried but never honoured (spec section 2, section 7).
 */
@Entity
@Table(name = "coffee")
public class Coffee {

    /** Server-generated (UUID v4) on persist. Never settable from the API, immutable after insert. */
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "name must not be blank")
    @Size(min = 1, max = 100, message = "name size must be between 1 and 100")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull(message = "roastLevel must be one of LIGHT, MEDIUM, DARK")
    @Enumerated(EnumType.STRING)
    @Column(name = "roast_level", nullable = false, length = 16)
    private RoastLevel roastLevel;

    @NotBlank(message = "origin must not be blank")
    @Size(min = 1, max = 100, message = "origin size must be between 1 and 100")
    @Column(name = "origin", nullable = false, length = 100)
    private String origin;

    @NotNull(message = "price must not be null")
    @DecimalMin(value = "0", inclusive = false, message = "price must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "price must be at most 99999999.99")
    @Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Min(value = 0, message = "stock must be greater than or equal to 0")
    @Column(name = "stock", nullable = false)
    private int stock;

    /** Set once on insert (UTC). Never settable from the API, immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set on insert (equal to {@code createdAt}) and advanced by {@code @PreUpdate}. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Public because the boundary layer builds a candidate instance from a request body
     * ({@code CoffeeRequest.toEntity()}) before handing it to control. It buys no encapsulation —
     * every field already has a public setter — and it keeps the mapping in {@code boundary}, as
     * spec section 1.2 requires, instead of putting a factory on the entity.
     */
    public Coffee() {
        // JPA, and the boundary -> entity mapping step
    }

    @PrePersist
    void onPersist() {
        Instant now = storedInstant();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = storedInstant();
    }

    /**
     * {@link Instant#now()} is nanosecond-precision on this JVM, but {@code created_at}/{@code
     * updated_at} are {@code TIMESTAMP WITH TIME ZONE}, whose resolution is the microsecond on both
     * H2 and PostgreSQL. Persisting the nanosecond value would make the timestamp in the create
     * response (serialised from the in-memory entity) differ from the value every later read gets
     * back from the database, which rounds to microseconds. Truncating to microseconds makes what is
     * written exactly what is returned, so a client that creates a coffee and immediately re-reads
     * it sees the same value (spec sections 2 and 3.2). Truncation, not rounding: the value must
     * never be advanced past the insert instant.
     */
    private static Instant storedInstant() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RoastLevel getRoastLevel() {
        return roastLevel;
    }

    public void setRoastLevel(RoastLevel roastLevel) {
        this.roastLevel = roastLevel;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
