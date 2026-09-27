# ADR-001: Boundary–Control–Entity layering for the Coffee service

Status: accepted
Date: 2026-09-27
Deciders: architect (card `t_db2182b6`)
Governs: `backend/src/main/java/com/example/coffeeshop/{entity,control,boundary}`

## Context

Card `t_da7e20d9` asks for a Coffee resource built "according to BCE architectural
pattern", and card `t_db2182b6` requires the specification to state the direction rules
explicitly. The service is a single bounded context, single deployable, one aggregate
(`Coffee`), owned by a small bot team where `backend-developer` implements, `qa-tester`
verifies, and Angel reviews the PR.

The forces at play:

- Two different profiles must be able to change this code — one writing entity/control
  (card `t_f9e0a903`) and one writing control/boundary (card `t_8046549a`) — without
  negotiating interfaces in chat. The layer contract has to be checkable by reading files.
- `qa-tester`'s card explicitly asks it to assert that boundaries hold ("boundary classes
  must not import entity classes; control must not import boundary DTOs; no SQL or HTTP
  concerns in control"). An architectural rule that cannot be mechanically checked will be
  checked inconsistently.
- The REST contract must stay stable while persistence internals change (H2 now,
  PostgreSQL in prod, possibly a different ORM later).

## Decision

We will structure the service in three packages with a one-directional dependency graph:

- `entity/` — JPA entity, domain enum, Panache repository. Knows JPA and Bean Validation
  only.
- `control/` — use-case services owning transaction boundaries, domain rules (trimming,
  uniqueness, paging validation) and typed domain exceptions. Knows `entity` only.
- `boundary/` — JAX-RS resources, DTO records, exception mappers, entity→DTO mapping.
  Knows `control` and, for mapping only, `entity`.

Enforced rules: **boundary talks to control only** (no resource imports a repository or an
`EntityManager`), **control talks to entity only** (no `jakarta.ws.rs.*`, no DTOs, no
status codes in control), and **DTOs never leak entities** in either direction (no entity
type appears in a `@Path` method signature; no DTO appears in a control signature).

The one deliberate exception: `boundary` may import `entity` to map an entity to a DTO
(static factory `CoffeeResponse.from(Coffee)`). The forbidden thing is an entity crossing
the HTTP edge, not the mapping step that stops it.

## Alternatives considered

1. **Spring-style layered packages** (`controller`/`service`/`repository`) with entities
   returned directly from controllers. Rejected: it is exactly what "BCE" was asked to
   replace, and returning a JPA entity from a resource leaks lazy-loading and persistence
   concerns into the wire format (today every field is eagerly loaded, tomorrow someone adds
   a collection).
2. **Hexagonal / ports-and-adapters** (`domain`, `application`, `adapter.in.rest`,
   `adapter.out.persistence`). Rejected for this service: it is a superset of the same idea
   with more indirection than one aggregate justifies, and the card asked for BCE by name —
   matching the requested vocabulary costs nothing here.
3. **Package-by-feature** (`coffee/{Coffee,CoffeeResource,CoffeeService,...}`). Rejected:
   with exactly one feature it degenerates into a flat package, so the dependency rules
   would stop being visible in the import list — the property we are buying.
4. **Strict BCE with no boundary→entity import at all** (control returns DTO-shaped
   projections). Rejected: it forces DTO construction inside control, which means `control`
   imports `boundary.dto` — the exact coupling QA is asked to fail. Mapping belongs at the
   edge.
5. **Lean BCE as prescribed by the `bce-microprofile-server` skill** — entities serve as
   DTOs, no separate request/response records, boundary may call entities directly for
   trivial CRUD. Rejected because the card is not trivial CRUD: `id`/`createdAt`/`updatedAt`
   are server-owned and must be absent from requests, `price` has a scale rule, and card
   `t_8046549a` requires a strict no-entity-on-the-wire rule that QA verifies. Where the
   lean variant and the card disagree, the card wins — the deviation is itemised in §1.4 of
   the specification.

## Consequences

Easier: the layer of any class is readable from its imports; `qa-tester` can assert the
rules with `grep` or a compile-time architecture test; the REST contract can be changed
without touching persistence and vice versa; the entity never determines the wire format.

Harder, and accepted: one extra mapping step and one extra class per DTO group (records
plus static factories); a new field must be touched in entity, DTO and mapping instead of
one place — a deliberate tax that keeps the API surface explicit. `control` must not return
`Page<CoffeeResponse>`, so paging responses are assembled in `boundary`, which means
`boundary` needs the page metadata (a small, stable, Panache-shaped object).

## Revisit when

A second bounded context shares the `entity` package, or a consumer needs a response shape
materially different from the entity (a projection/reporting API) — at that point the
mapping layer needs its own home, or the entity needs to stop being the only read model.
