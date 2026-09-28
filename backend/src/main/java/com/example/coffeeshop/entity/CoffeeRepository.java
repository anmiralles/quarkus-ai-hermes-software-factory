package com.example.coffeeshop.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Persistence for {@link Coffee}. Persistence only: no trimming, no uniqueness policy,
 * no HTTP vocabulary, no transaction management (transactions belong to {@code control}).
 *
 * <p>Deviations from the interface printed in spec section 6, all deliberate and reported
 * on card {@code t_f9e0a903}:
 * <ul>
 *   <li>Implements {@code PanacheRepositoryBase<Coffee, UUID>} rather than
 *       {@code PanacheRepository<Coffee>}. {@code PanacheRepository<T>} is
 *       {@code PanacheRepositoryBase<T, Long>}, so it would expose
 *       {@code findById(Long)} on an entity whose id is a {@code UUID} — a method that
 *       cannot be called correctly. {@code PanacheRepositoryBase<Coffee, UUID>} is the
 *       same persistence-only contract with the id type stated.
 *   <li>{@code findAllPaged} returns the requested page's {@code List<Coffee>} rather than
 *       {@code Page<Coffee>}. {@code io.quarkus.panache.common.Page} is not a generic
 *       result type (it carries no type parameter), so {@code Page<Coffee>} does not
 *       compile against Panache. Callers that need the totals use {@link #count()} and
 *       {@link #count(String, Object...)} inherited from the Panache repository base,
 *       which is what {@code control.listCoffees} needs.
 *   <li>No {@code findAllPaged} overload taking a {@code Sort}: the ordering is fixed by
 *       the contract (name asc, then id asc) to keep paging a total order.
 * </ul>
 */
@ApplicationScoped
public class CoffeeRepository implements PanacheRepositoryBase<Coffee, UUID> {

    /** Exact, case-sensitive match on the stored (already trimmed) name. */
    public Optional<Coffee> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    /** All coffees of one roast level, ordered by name asc, then id asc. */
    public List<Coffee> findByRoastLevel(RoastLevel level) {
        return list("roastLevel = ?1 order by name asc, id asc", level);
    }

    /**
     * One page of the catalogue, ordered by name asc, then id asc.
     *
     * @param page 0-based page index; callers validate it ({@code control} owns the check)
     * @param size page size; callers validate it
     */
    public List<Coffee> findAllPaged(int page, int size) {
        return find("order by name asc, id asc")
                .page(Page.of(page, size))
                .list();
    }
}
