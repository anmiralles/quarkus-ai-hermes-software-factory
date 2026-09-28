# ADR-002: Panache repository pattern instead of Panache active record

Status: accepted (type arguments corrected by [ADR-004](ADR-004-uuid-typed-panache-repository.md))
Date: 2026-09-27
Deciders: architect (card `t_db2182b6`)
Governs: `backend/src/main/java/com/example/coffeeshop/entity/Coffee.java`,
`backend/src/main/java/com/example/coffeeshop/entity/CoffeeRepository.java`

## Context

Quarkus's Hibernate ORM with Panache offers two idioms for the same persistence work:

- **Active record** — `extend PanacheEntity`, use generated static finders
  (`Coffee.findById(id)`, `Coffee.listAll()`) and instance methods (`coffee.persist()`).
  Less code, and the default in most Quarkus tutorials.
- **Repository** — `implements PanacheRepository<Coffee>`, an injectable stateless bean
  with the same query API (`find("name", name)`, `.page(...).list()`), one extra class.

Constraints that decide it here:

- Card `t_8046549a` requires **control unit tests with a mocked repository** covering every
  use case. Static finders on the entity cannot be mocked or substituted.
- Card `t_db2182b6` (this specification) fixes the BCE rule that `control` talks to `entity`
  *as a repository* and owns the transaction boundary; `qa-tester` must be able to assert
  "no SQL concerns in control".
- The persistence technology is expected to hold (JPA/Hibernate 6 + Panache classic on
  Quarkus 3.33.3), and H2 (dev/test) and PostgreSQL (prod) are both targets of one migration
  script.

## Decision

We will use the **repository** pattern: `entity/CoffeeRepository implements
PanacheRepository<Coffee>`, injected by constructor into `control/CoffeeService`. Entities
are plain `@Entity` classes that do not extend `PanacheEntity` and carry no static finders;
they carry only mapping, Bean Validation constraints and lifecycle callbacks. Queries live
exclusively in the repository, which contains persistence mechanics and no business rules.

## Alternatives considered

1. **Panache active record** (`Coffee extends PanacheEntity`, static finders). Rejected for
   two concrete reasons, not taste: (a) static methods defeat the mocked-repository tests
   the control card requires; (b) query calls would appear inside `control`, so "control has
   no persistence concerns" stops being a statement the import list and a grep can verify.
   Its advantage — fewer classes — is worth roughly ten lines here.
2. **Plain JPA with an injected `EntityManager` and hand-written JPQL.** Rejected: it buys
   nothing over `PanacheRepository` on Quarkus, re-introduces boilerplate (`createQuery`,
   `TypedQuery`) into the entity layer, and loses Panache's `page()`/`Page` support that the
   paging contract in §3.2 depends on.
3. **Spring Data JPA repositories.** Rejected outright: wrong framework for this stack
   (`java-cloud-native-stack` says never mix frameworks within a bounded context), and it
   would drag Spring into a Quarkus build.
4. **Control wraps persistence directly** (the `bce-microprofile-server` skill's lean BCE:
   `CoffeeService` holds an `EntityManager`, no repository class at all). Rejected: it puts
   JPQL and flush handling inside the layer that must stay free of persistence concerns, and
   it makes "the service is unit-testable without a database" false — every control test
   would need a live persistence context. The card's mocked-repository tests are the
   requirement that settles this.
5. **Repository interface + Panache implementation** (`interface CoffeeRepository` in
   `entity`, implementation in a new `infrastructure` package). Rejected for now: it adds a
   second seam we do not need yet (there is one implementation and no plan for a second),
   and the mocked-repository test works fine with `@InjectMock` on the concrete class. This
   is the alternative to revisit if a second persistence technology ever appears.

## Consequences

Easier: `control` can be unit-tested with `@InjectMock CoffeeRepository` without a database;
`qa-tester` can assert the "no SQL/HTTP in control" rule mechanically; moving from H2 to
PostgreSQL or dropping Panache touches one class; entity state cannot be mutated from
anywhere by calling a static method on the class.

Harder, and accepted: one extra class and one extra injection point per aggregate, and a
mapping/derivation pattern that differs from the majority of online Quarkus examples — a
contributor copying a tutorial will produce active-record code and fail the BCE checks. That
is the intended outcome of the check, not a side effect of it.

## Revisit when

A second persistence technology for the same aggregate appears (e.g. a cache or an
event-sourced store), or the repository starts accumulating business rules that cannot be
expressed as a query — both are signals to extract an interface or move logic back into
`control`.
