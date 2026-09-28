# ADR-004: `CoffeeRepository implements PanacheRepositoryBase<Coffee, UUID>`

Status: accepted
Date: 2026-09-28
Deciders: architect (card `t_da7e20d9`)
Governs: `backend/src/main/java/com/example/coffeeshop/entity/CoffeeRepository.java`
Refines: ADR-002 — the repository-over-active-record decision is unchanged; only the type
arguments are corrected.

## Context

ADR-002 chose the Panache repository pattern and wrote the declared type as
`PanacheRepository<Coffee>`. Implementing it (card `t_f9e0a903`) surfaced that
`io.quarkus.hibernate.orm.panache.PanacheRepository<T>` is a convenience alias for
`PanacheRepositoryBase<T, Long>`.

`Coffee`'s id is a `java.util.UUID` (§2 of the specification, chosen so the API never exposes
a sequential, enumerable identifier). Declaring `PanacheRepository<Coffee>` therefore puts
`findById(Long)`, `deleteById(Long)` and friends on the repository: methods that cannot be
called correctly on this entity, and that fail at runtime rather than at compile time when a
caller reaches for the obvious name.

## Decision

We will declare `entity/CoffeeRepository implements PanacheRepositoryBase<Coffee, UUID>`, so
every inherited id-typed method (`findByIdOptional`, `findById`, `deleteById`) takes and
returns a `UUID`. Everything else in ADR-002 stands.

## Alternatives considered

1. **`PanacheRepository<Coffee>`, as ADR-002 wrote it.** Rejected: it exposes id-typed
   methods with the wrong parameter type on a UUID-identified entity.
2. **Keep `PanacheRepository<Coffee>` and never call the id-typed methods** (query by id with
   `find("id", uuid)` everywhere instead). Rejected: correctness would depend on every future
   contributor remembering to avoid the obvious method name. The type system should refuse
   the wrong call instead of a reviewer catching it.
3. **Make `Coffee`'s id a `Long`.** Rejected in §2 for the API contract: sequential ids are
   trivially enumerable, and nothing in the contract requires them.
4. **Plain JPA with an injected `EntityManager`.** Already rejected in ADR-002; unchanged by
   this decision.

## Consequences

Easier: the id type is part of the repository's face, so
`repository.findByIdOptional(id).orElseThrow(...)` in `control` is type-correct with no cast
and no string-based query; and `@InjectMock` of the repository in the control unit tests
still works identically.

Harder, and accepted: `PanacheRepositoryBase` is the lower-level name, so a contributor
copying a Panache tutorial writes `PanacheRepository` and only meets the mismatch when an
id-typed method is used. §6 of the specification now prints the correct interface, which
moves that discovery to review instead of production.

## Revisit when

The id strategy changes — a composite key, client-supplied ids, or a move away from UUID —
which is a §2 revision with its own ADR, not an edit here.