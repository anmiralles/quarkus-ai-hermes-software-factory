# Coffee service — BCE specification and domain contract

Status: proposed (awaiting plan approval on card `t_db2182b6`)
Card: `t_db2182b6` — "Define BCE spec and Coffee domain contract for Quarkus service"
Supersedes: nothing. This is the contract three implementation cards build against:

| Card | Title | Owner | Branch |
|---|---|---|---|
| `t_f9e0a903` | Implement Coffee entity layer: JPA entity, repository, migration | `backend-developer` | `task/t_f9e0a903-coffee-entity` |
| `t_8046549a` | Implement Coffee control and boundary layers with REST API | `backend-developer` | `task/t_8046549a-coffee-control-boundary` |
| `t_c41f5e3e` | Verify Coffee BCE REST service against acceptance criteria | `qa-tester` | `task/t_c41f5e3e-coffee-verify` |

Two of those card bodies name their branch `feature/...`. `AGENTS.md` is authoritative:
branches are `task/<card-id>-<slug>`, the card id goes in the commit subject and the PR
title. The table above is the canonical naming; implementers use it.

**This document is a specification.** No production code is written on this card, and no
implementation decision below may be changed by an implementer without a comment on
`t_db2182b6` and a revision here — the QA card verifies against the numbered criteria at
the end, so an undocumented deviation becomes a failed verification.

---

## 1. Architecture: Boundary–Control–Entity

Three packages, one direction of dependency. The rule is the point of the pattern: a
reader can tell from the import list alone which layer a class belongs to.

```
            HTTP / JSON
                │
      ┌─────────▼──────────┐
      │  boundary/         │  JAX-RS resources, DTOs, exception mappers, field mapping
      │  (adapters in)     │  knows: control (API), its own DTOs, entity (for mapping only)
      └─────────┬──────────┘
                │  calls use cases only; never sees a repository
      ┌─────────▼──────────┐
      │  control/          │  use-case services, transaction boundaries, domain rules,
      │  (application)     │  typed domain exceptions
      └─────────┬──────────┘  knows: entity (model + repository), never boundary
                │  calls repository only; never sees HTTP
      ┌─────────▼──────────┐
      │  entity/           │  JPA entity, enum, repository (persistence only)
      │  (domain + data)   │  knows: JPA/Bean Validation only
      └─────────┬──────────┘
                │
             database
```

### 1.1 The three rules, verbatim

1. **boundary talks to control only.** A resource class may not import or inject a
   repository, an `EntityManager`, or a `Query`. Every use case it needs is a method on
   `control/CoffeeService`.
2. **control talks to entity only.** A control class may not import anything from
   `boundary` (including DTOs), may not import `jakarta.ws.rs.*`, and may not build an
   HTTP status or a response body. It returns entities and page objects, or it throws.
3. **DTOs never leak entities — in either direction.** No entity type appears in a
   boundary signature (`Coffee` must not be a parameter or return type of any
   `@Path` method), no DTO appears in a control signature, and no JPA annotation is ever
   added to a DTO. Mapping entity → DTO happens in `boundary/dto` only.

### 1.2 Import matrix (how the QA card checks rule 1–3 mechanically)

| Package | may import | must not import |
|---|---|---|
| `entity` | `jakarta.persistence.*`, `jakarta.validation.*`, `org.hibernate.annotations.*`, `io.quarkus.hibernate.orm.panache.*`, `java.*` | `control.*`, `boundary.*`, `jakarta.ws.rs.*`, `jakarta.ws.rs.core.*`, Jackson DTO types |
| `control` | `entity.*`, `jakarta.transaction.*`, `jakarta.enterprise.*`, `jakarta.inject.*`, its own `control.exception.*`, `java.*` | `boundary.*` (including `boundary.dto.*`), `jakarta.ws.rs.*`, `jakarta.ws.rs.core.*`, `io.quarkus.panache.common.*` |
| `boundary` | `control.*`, `entity.*` (mapping only), `boundary.dto.*`, `jakarta.ws.rs.*`, `jakarta.ws.rs.core.*`, `jakarta.validation.*`, OpenAPI annotations, Jackson annotations | repository types (`CoffeeRepository`), `jakarta.persistence.*`, `EntityManager` |

`boundary → entity` is allowed **for the mapping step only** (`CoffeeResponse.from(Coffee)`).
It is a deliberate, narrow exception to strict layering: it buys us type-safe mapping and
keeps the DTO factory next to the DTO. What is forbidden is an entity crossing the HTTP
edge — as a `@Path` parameter, a `@Path` return type, or a field of a response DTO.

### 1.4 Where this deviates from the lean BCE default (deliberate, card-mandated)

The `bce-microprofile-server` skill prescribes a *lean* BCE: no dedicated DTO layer
("BCE entities often serve as DTOs"), no repository ("the control wraps persistence
directly"), and boundary→entity direct calls for trivial CRUD. Cards `t_db2182b6` and
`t_8046549a` mandate the stricter variant, and this spec follows the cards. The deviations
are recorded here so it is a decision, not drift:

| Lean BCE default (skill) | This service | Why |
|---|---|---|
| Entities serve as DTOs on the wire | Explicit `boundary/dto` records + mapping | Card requires "DTOs never leak entities"; the wire format must not be the JPA mapping, and `createdAt`/`updatedAt`/`id` rules differ between request and response |
| Control wraps persistence directly | `entity/CoffeeRepository` injected into `control` | Control must be unit-testable with a mocked repository (card `t_8046549a`), which static active-record finders make impossible; see ADR-002 |
| `@Path` methods may call entities directly for trivial CRUD | Boundary calls control only | The paging, trimming, uniqueness and 409 rules are not trivial; the QA card asserts the boundary/control rule mechanically |

Everything else follows the skill: three packages with those exact names, `@ApplicationScoped`
controls, `@Transactional` on control, `@Inject` never in the entity, JAX-RS and OpenAPI
annotations only in boundary, and HTTP-level tests under `src/test/.../boundary/`.

### 1.5 Service method names

Control method names follow card `t_8046549a` verbatim (`createCoffee`, `getCoffee`,
`listCoffees`, `updateCoffee`, `deleteCoffee`) rather than the skill's shorter
`save`/`find`/`all` table, because that card body is the implementer's instruction and two
names for one method is how a spec stops being a contract. The skill's mapping still holds:
boundary `POST` → control `createCoffee`, `GET` → `getCoffee`/`listCoffees`, `PUT` →
`updateCoffee`, `DELETE` → `deleteCoffee`. The exceptions are named
`CoffeeNotFoundException` and `CoffeeAlreadyExistsException` (the card's `CoffeeNotFound` /
`CoffeeAlreadyExists` are shorthand).

### 1.6 Naming and placement conventions

- Transactions: `@Transactional` lives on `control` methods **only**. Resources are never
  transactional; repositories are never transactional.
- Exceptions: domain failures are typed and thrown by `control` and mapped to HTTP by
  `boundary`. Control never throws `WebApplicationException`.
- Mapping: static factories on the DTO records (`CoffeeResponse.from(Coffee)`,
  `CoffeePageResponse.of(...)`), in the `boundary.dto` package.
- Logging: `org.jboss.logging.Logger` in `control` and `boundary` mappers. Never log
  request bodies wholesale (they may contain nothing sensitive today; keep the habit).

---

## 2. Domain model

One aggregate, one entity: `Coffee`. Table `coffee`. The entity is the only place that
knows how Coffee is persisted.

| Field | Java type | Column | Null | Constraints / rules |
|---|---|---|---|---|
| `id` | `java.util.UUID` | `id uuid` | no | Primary key. Generated by Hibernate (`@UuidGenerator`, UUID v4) on persist. Never settable from the API. Immutable after insert. |
| `name` | `String` | `name varchar(100)` | no | **Required. Unique (exact, case-sensitive).** Trimmed before validation and before persistence. After trimming: length 1..100 inclusive. Unicode allowed. Interior whitespace preserved. |
| `roastLevel` | `RoastLevel` (enum) | `roast_level varchar(16)` | no | Required. `@Enumerated(EnumType.STRING)`. Exactly one of `LIGHT`, `MEDIUM`, `DARK` (upper case). Indexed. |
| `origin` | `String` | `origin varchar(100)` | no | Required. Trimmed. Country name as free text: length 1..100. No ISO-3166 validation — a wrong country name is a data-quality issue, not a contract violation. |
| `price` | `java.math.BigDecimal` | `price numeric(10,2)` | no | Required. Must be `> 0` and `<= 99999999.99` (fits `numeric(10,2)`). At most 2 decimal places: a value with a 3rd significant decimal place is **rejected with 400, never silently rounded**. Persisted and serialised at scale 2. |
| `stock` | `int` | `stock integer` | no | Required. `>= 0`. Upper bound is `Integer.MAX_VALUE`. |
| `createdAt` | `java.time.Instant` | `created_at timestamp with time zone` | no | Set once by `@PrePersist` (`Instant.now()`, UTC). Never settable from the API. Immutable after insert (`updatable = false`). |
| `updatedAt` | `java.time.Instant` | `updated_at timestamp with time zone` | no | Set by `@PrePersist` (equal to `createdAt` on insert) and updated by `@PreUpdate`. Never settable from the API. `updatedAt >= createdAt` always holds. |

Decisions inside this table that reviewers will ask about:

- **Uniqueness is exact and case-sensitive.** `"Cafe Solo"` and `"cafe solo"` are two
  different coffees. The database `UNIQUE` constraint is the hard invariant; control does a
  pre-check to return a clean 409. We do **not** use a `lower(name)` expression index: it is
  not portable across H2 and PostgreSQL without divergence, and case-insensitive naming was
  never asked for. (Avoiding silent semantics: whoever wants case-insensitive uniqueness
  files an ADR.)
- **No optimistic locking in v1.** No `@Version` column. Rationale: the contract has no
  `If-Match`/ETag and no version field, so a version column would be carried but never
  honoured — a lost-update window that *looks* protected is worse than one that is known.
  Deferred; see §7.
- **`Instant`, not `LocalDateTime`.** Timestamps are absolute and timezone-free on the wire
  (`2026-09-27T19:45:12.123456Z`).
- **No soft delete, no audit columns, no `owner` field.** Out of scope for this contract.

`entity/RoastLevel.java` is a plain enum (`LIGHT`, `MEDIUM`, `DARK`) in the `entity`
package. It is domain vocabulary, so it is legal in control signatures and in DTOs; the
Java enum type is not a "leaked entity".

---

## 3. REST contract — `/coffees`

Base path is exactly `/coffees` (no `/api` prefix, no version prefix — a prefix would be
invented APIs surface with no consumer asking for it). All request and response bodies are
`application/json; charset=UTF-8` unless stated. All error bodies are
`application/problem+json`.

### 3.1 Endpoint table

| # | Method & path | Request | Success | Errors |
|---|---|---|---|---|
| 1 | `POST /coffees` | `CoffeeRequest` body, `@Valid` | `201 Created`, `CoffeeResponse` body, `Location: /coffees/{id}` | `400` validation / malformed body / unknown field, `409` duplicate name |
| 2 | `GET /coffees?page={page}&size={size}` | no body | `200 OK`, `CoffeePageResponse` | `400` invalid `page`/`size` |
| 3 | `GET /coffees/{id}` | no body | `200 OK`, `CoffeeResponse` | `404` unknown or malformed id |
| 4 | `PUT /coffees/{id}` | full `CoffeeRequest` body, `@Valid` | `200 OK`, `CoffeeResponse` | `400`, `404`, `409` |
| 5 | `DELETE /coffees/{id}` | no body | `204 No Content`, empty body | `404` unknown id |

Behaviour pinned per endpoint:

1. **POST** — server generates `id`, `createdAt`, `updatedAt`. The response body is the
   created resource *including* those three fields. `Location` is the absolute path
   `/coffees/{id}` (a URI, not a URL with host; clients resolve it against the base URI).
2. **GET list** — `page` is **0-based**, default `0`, minimum `0`. `size` default `20`,
   minimum `1`, maximum `100`. `page < 0`, `size < 1`, `size > 100`, or non-integer values
   are `400`, **not clamped**. Empty result set is `200` with `content: []`, `totalPages: 0`.
   Ordering is deterministic and total: **`name` ascending, then `id` ascending** — without
   a total order, paging tests are flaky and clients see duplicates across pages.
   No query filters in v1 (see §7): `?roastLevel=` is not part of the contract.
3. **GET by id** — `{id}` must parse as a UUID; a malformed id yields `404` (not `400`),
   because a non-UUID path segment is not an addressable resource in this API.
4. **PUT** — full replacement of the mutable fields (`name`, `roastLevel`, `origin`,
   `price`, `stock`). The body must be complete: a missing field is `400`, there is no
   partial merge. `id`, `createdAt`, `updatedAt` are ignored... **no** — they are unknown
   properties and therefore `400` (§3.4). Immutable fields are not silently accepted.
   A `PUT` that changes nothing still returns `200`; `updatedAt` is only advanced when the
   entity is actually dirty.
5. **DELETE** — `204` with an empty body. Deleting an already-deleted or never-existing id
   is `404` (the endpoint is *not* idempotent-204: we report truth rather than swallow it).

### 3.2 DTO shapes

`boundary/dto/CoffeeRequest` — a Java `record`. No `id`, no timestamps.

```json
{
  "name": "Ethiopia Yirgacheffe",
  "roastLevel": "LIGHT",
  "origin": "Ethiopia",
  "price": 12.34,
  "stock": 40
}
```

`boundary/dto/CoffeeResponse` — a Java `record`.

```json
{
  "id": "3f1c9c0e-6a4e-4a1b-9a1e-2c7f4b0d5e11",
  "name": "Ethiopia Yirgacheffe",
  "roastLevel": "LIGHT",
  "origin": "Ethiopia",
  "price": 12.34,
  "stock": 40,
  "createdAt": "2026-09-27T19:45:12.123456Z",
  "updatedAt": "2026-09-27T19:45:12.123456Z"
}
```

`boundary/dto/CoffeePageResponse` — a Java `record`.

```json
{
  "content": [ /* CoffeeResponse objects */ ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- `totalPages` = `ceil(totalElements / size)`, and `0` when `totalElements` is `0`.
- `page`/`size` echo the **effective** values used (so a defaulted request is visible to
  the client).
- `price` is serialised as a JSON number at scale 2 (`12.30`, not `12.3`, not `"12.30"`).
  Rationale: money as a JSON string is a different contract; a number at fixed scale is
  what the domain says and what Jackson emits for a `BigDecimal` at scale 2.
- No `_links`/HATEOAS envelope in v1. The `content`/`page`/`size` envelope is deliberately
  Spring-Page-like so a future client library can be reused.

### 3.3 Validation rules (400 — per-field)

Applied identically to `POST` and `PUT`. Validation order: bean validation first, then
control-level rules (`trim`, uniqueness). Field paths use the JSON property name.

| Field | Rule | 400 `detail`/`errors[].message` |
|---|---|---|
| `name` | present, non-blank after trim, length 1..100 | `"name must not be blank"` / `"name size must be between 1 and 100"` |
| `roastLevel` | present, one of `LIGHT`, `MEDIUM`, `DARK` | `"roastLevel must be one of LIGHT, MEDIUM, DARK"` |
| `origin` | present, non-blank after trim, length 1..100 | `"origin must not be blank"` / `"origin size must be between 1 and 100"` |
| `price` | present, `> 0`, `<= 99999999.99`, scale `<= 2` | `"price must be greater than 0"` / `"price must be at most 99999999.99"` / `"price must have at most 2 decimal places"` |
| `stock` | present, `>= 0` | `"stock must be greater than or equal to 0"` |
| body | well-formed JSON, no unknown properties | `"unknown field 'x'"` / `"malformed request body"` |

Duplicate `name` is **not** a 400 — it is a `409` (§3.5).

### 3.4 Unknown and immutable properties

`quarkus.jackson.fail-on-unknown-properties=true`, and the request DTOs carry no `id`,
`createdAt` or `updatedAt` fields. A client sending `"id"` in a POST/PUT body gets `400`
with `"unknown field 'id'"`. Decision: a client that thinks it can set the identity or the
timestamps is wrong in a way we want to hear about immediately, and strict deserialisation
is the cheapest way to hear about it. It costs us forwards-compatibility on requests, so a
future additive request field is a deliberate release decision.

### 3.5 Error body — RFC 7807 problem detail

All error responses use `application/problem+json` with this shape:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/coffees",
  "errors": [
    { "field": "price", "message": "price must be greater than 0" },
    { "field": "name",  "message": "name must not be blank" }
  ]
}
```

| Status | `type` | `title` | `detail` | extra |
|---|---|---|---|---|
| `400` | `about:blank` | `Bad Request` | `Request validation failed` (validation) or `Malformed request body` (parse) | `errors[]` present for validation failures; may be omitted/empty for parse failures |
| `404` | `about:blank` | `Not Found` | `Coffee {id} not found` | — |
| `409` | `about:blank` | `Conflict` | `Coffee name '{name}' already exists` | — |
| `500` | `about:blank` | `Internal Server Error` | `Unexpected error` — **never** a stack trace, SQL fragment, or class name | — |

- `instance` is the request path (`/coffees/3f1c...`), no query string duplication, no host.
- `errors[]` entries use the JSON field name; order is not significant. It is present for
  every `400` caused by bean-validation or JSON-deserialisation failure of a *field*, and
  may be empty for a structural parse error.
- `type` is `about:blank` in v1 rather than bespoke URIs: no client consumes error-type
  URIs yet, and inventing a URI namespace we do not host is worse than `about:blank`,
  which RFC 7807 explicitly allows ("no additional semantics beyond the HTTP status code").
- Unexpected exceptions (anything not mapped) produce the generic `500`. The full stack
  trace goes to the server log only. (Security-relevant: an unresolvable entity leak here
  is what the `infosec` profile reviews.)

---

## 4. Package layout, coordinates and configuration

### 4.1 Maven coordinates

| Item | Value |
|---|---|
| `groupId` | `com.example` |
| `artifactId` | `coffee-shop` |
| `version` | `1.0.0-SNAPSHOT` |
| `name` / `description` | `coffee-shop` — "Coffee catalogue service (Quarkus, BCE)" |
| Project directory | `backend/` (per `AGENTS.md`: one bounded context per deployable) |
| Root package | `com.example.coffeeshop` |
| `maven.compiler.release` | `17` |
| Quarkus platform BOM | `io.quarkus.platform:quarkus-bom:3.33.3` |

**Quarkus 3.33.3 is the LTS line** (3.33 is LTS, community support to 2027-03-25); 3.33.3
is the newest patch published on Maven Central and verified present there on 2026-09-27.
Non-LTS trains (3.34–3.39) exist but have already reached EOL; the LTS line is the boring
choice for a service we intend to run. Pin exactly `3.33.3` — no version ranges, no
`RELEASE`, no `LATEST`. The Maven wrapper (`./mvnw`, `./mvnw.cmd`, `.mvn/wrapper/`) is
committed so the build does not depend on a system Maven.

Dependency versions come from the BOM; only the platform version is stated. All artifacts
below were verified to exist in `quarkus-bom:3.33.3` before being written here.

### 4.2 Dependencies

| Artifact | Scope | Why |
|---|---|---|
| `io.quarkus:quarkus-rest` | compile | JAX-RS (RESTEasy Reactive) — the HTTP engine |
| `io.quarkus:quarkus-rest-jackson` | compile | JSON binding for DTOs |
| `io.quarkus:quarkus-hibernate-orm-panache` | compile | JPA + Panache repository |
| `io.quarkus:quarkus-hibernate-validator` | compile | Bean Validation on DTOs and entity |
| `io.quarkus:quarkus-flyway` | compile | Schema migrations, the only writer of DDL |
| `io.quarkus:quarkus-jdbc-h2` | runtime | H2 driver (dev + test profiles) |
| `io.quarkus:quarkus-jdbc-postgresql` | runtime | PostgreSQL driver (prod profile) |
| `io.quarkus:quarkus-smallrye-openapi` | compile | `/q/openapi` — the contract stays inspectable |
| `io.quarkus:quarkus-junit5` | test | Quarkus test harness |
| `io.quarkus:quarkus-junit5-mockito` | test | `@InjectMock` of the repository for control unit tests |
| `io.rest-assured:rest-assured` | test | HTTP-level integration tests |

Two notes that save an implementer an hour each:

- The **current** artifact names are `quarkus-rest` / `quarkus-rest-jackson`. The
  `quarkus-resteasy-reactive-jackson` name used in card `t_f9e0a903` is the pre-3.9 legacy
  alias; it resolves but drags the deprecated stack in. Use the names in the table.
- Hibernate ORM 6 + Panache classic is what 3.33.3 ships. ("Panache Next" lands in 3.36+;
  do not use it here.)

### 4.3 Package and file layout

```
backend/
  pom.xml
  mvnw, mvnw.cmd, .mvn/wrapper/…
  src/main/java/com/example/coffeeshop/
    entity/
      Coffee.java                 # @Entity, JPA + Bean Validation, lifecycle callbacks
      RoastLevel.java             # enum LIGHT | MEDIUM | DARK
      CoffeeRepository.java       # PanacheRepository<Coffee> — persistence only
    control/
      CoffeeService.java          # @ApplicationScoped, @Transactional, use cases
      exception/
        CoffeeNotFoundException.java
        CoffeeAlreadyExistsException.java
    boundary/
      CoffeeResource.java         # @Path("/coffees")
      error/
        CoffeeNotFoundExceptionMapper.java   # -> 404 problem detail
        CoffeeAlreadyExistsExceptionMapper.java # -> 409 problem detail
        ValidationExceptionMapper.java       # -> 400 problem detail + errors[]
        GenericExceptionMapper.java          # -> 500 problem detail, no leakage
      dto/
        CoffeeRequest.java        # record
        CoffeeResponse.java       # record, static from(Coffee)
        CoffeePageResponse.java   # record, static of(...)
  src/main/resources/
    application.properties
    db/migration/V1__create_coffee.sql
  src/test/java/com/example/coffeeshop/
    entity/CoffeeRepositoryTest.java
    control/CoffeeServiceTest.java
    boundary/CoffeeResourceTest.java
    boundary/CoffeeBceArchitectureTest.java   # optional but recommended (see §8)
```

### 4.4 Migration `V1__create_coffee.sql`

One portable script, valid on both H2 2.3 and PostgreSQL 16. No dialect-specific syntax, no
`serial`, no `IDENTITY`-specific DDL — the id is supplied by Hibernate.

```sql
CREATE TABLE coffee (
    id           UUID                     NOT NULL,
    name         VARCHAR(100)             NOT NULL,
    roast_level  VARCHAR(16)              NOT NULL,
    origin       VARCHAR(100)             NOT NULL,
    price        NUMERIC(10, 2)           NOT NULL,
    stock        INTEGER                  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_coffee PRIMARY KEY (id),
    CONSTRAINT uq_coffee_name UNIQUE (name),
    CONSTRAINT ck_coffee_price_positive CHECK (price > 0),
    CONSTRAINT ck_coffee_stock_non_negative CHECK (stock >= 0)
);

CREATE INDEX ix_coffee_roast_level ON coffee (roast_level);
CREATE INDEX ix_coffee_name ON coffee (name);
```

`uq_coffee_name` already creates a unique index on `name`; `ix_coffee_name` is a
redundant-but-explicit plain index that satisfies the "index on name and roast_level"
requirement of card `t_f9e0a903` literally, at negligible cost on a catalogue table. Keep
both — card text wins over micro-optimisation here; if a reviewer objects, drop
`ix_coffee_name` in a follow-up migration, not silently.

**Flyway owns the schema in every profile.** `quarkus.hibernate-orm.database.generation=none`
everywhere. No `drop-and-create`, ever, not even in test: a schema that Hibernate generated
in tests but Flyway generated in production is a class of bug this rule removes.

### 4.5 Configuration

`application.properties` (property names are Quarkus 3.33 runtime properties):

```properties
# --- common ---
quarkus.http.root-path=/
quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
quarkus.jackson.fail-on-unknown-properties=true
quarkus.jackson.serialization.write-dates-as-timestamps=false

# --- dev: file-backed H2 so data survives a restart ---
%dev.quarkus.datasource.db-kind=h2
%dev.quarkus.datasource.jdbc.url=jdbc:h2:file:./target/h2/coffee-dev;DB_CLOSE_DELAY=-1
%dev.quarkus.datasource.username=sa
%dev.quarkus.datasource.password=

# --- test: in-memory H2, isolated per JVM ---
%test.quarkus.datasource.db-kind=h2
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:coffee-test;DB_CLOSE_DELAY=-1
%test.quarkus.datasource.username=sa
%test.quarkus.datasource.password=

# --- prod: PostgreSQL, credentials from the environment only ---
%prod.quarkus.datasource.db-kind=postgresql
%prod.quarkus.datasource.jdbc.url=${DB_URL}
%prod.quarkus.datasource.username=${DB_USER}
%prod.quarkus.datasource.password=${DB_PASSWORD}
```

- **No secrets in Git.** Prod credentials arrive as environment variables; nothing has a
  default value in the file. (`infosec` owns the eventual secret delivery mechanism; this
  spec only states the contract's side of it.)
- **dev is file-backed on purpose**: it is the only profile where a human relaunches the
  app and expects yesterday's rows to still be there.
- OpenAPI is at `/q/openapi` in dev and test. Do not add an enable-in-prod switch — that is
  a deployment decision for later, not a spec default.
- No CORS configuration, no auth, no rate limiting in v1. If the frontend card needs CORS,
  it is a new decision on a new card.

### 4.6 Build environment constraint (blocking for the implementers)

Verified on the factory box on 2026-09-27, before this spec was written:

| Check | Result |
|---|---|
| `java -version` | **not found** — no JDK, no `/usr/lib/jvm` |
| `mvn -v` | **not found** — no system Maven (the committed `mvnw` covers this, but it still needs a JDK) |
| `docker info` | `Cannot connect to the Docker daemon` — Testcontainers and "just use the Maven image" are **not** available |
| `apt-get` present, uid 10000 (unprivileged) | a system JDK install needs root |
| `https://repo.maven.apache.org/maven2/` | HTTP 200 — dependency download works |

Consequence: **the acceptance criteria below ("`./mvnw test` passes") cannot be executed
until a JDK 17+ is installed.** This is not the implementers' problem to solve by
inventing evidence. It is handed to `devops` as card `t_db2182b6`'s child (see the board;
the card is created alongside this spec) with the requirement: install a JDK 17+ and make
`./mvnw test` runnable for uid 10000, or state plainly why it cannot be done here.
Until then, an implementer who cannot run the build must say so on the card rather than
claim a green build.

Do **not** work around the missing toolchain by committing pre-built artifacts, by
switching to Gradle, or by vendoring dependencies into the repo.

---

## 5. Control layer contract

`control/CoffeeService` — `@ApplicationScoped`, `@Transactional` on mutating methods,
`@Transactional(readOnly = true)`-equivalent on reads. Constructor-injected
`CoffeeRepository`. Method signatures are part of the contract:

```java
Coffee createCoffee(Coffee candidate);                 // throws CoffeeAlreadyExistsException
Coffee getCoffee(UUID id);                             // throws CoffeeNotFoundException
Page<Coffee> listCoffees(int page, int size);          // throws IllegalArgumentException for bad paging
Coffee updateCoffee(UUID id, Coffee changes);          // NotFound | AlreadyExists
void deleteCoffee(UUID id);                            // throws CoffeeNotFoundException
```

Rules the implementation must honour:

- **Trimming and uniqueness** live in control, not in the resource and not in the entity:
  control trims `name`/`origin`, enforces the uniqueness pre-check, and maps a
  `PersistenceException`/`ConstraintViolationException` raised on flush (the concurrent-POST
  race that the pre-check cannot close) to `CoffeeAlreadyExistsException` → `409`.
- **Transaction boundary is the service method.** `create`/`update`/`delete` are atomic.
  The resource never opens a transaction; the repository never does.
- **Paging validation is control's**: `page >= 0`, `1 <= size <= 100`, else
  `IllegalArgumentException("page must be >= 0")` / `("size must be between 1 and 100")`,
  which the boundary mapper turns into a `400` problem detail. (Bean validation on query
  params is the alternative; control-side checks were chosen so the rule is unit-testable
  without HTTP.)
- **No HTTP vocabulary.** No `Response`, no `Status`, no `UriBuilder`, no `@PathParam`
  anywhere in `control`.
- **Reads return entities.** Control returns `Coffee`/`Page<Coffee>`; the boundary maps
  them. This is what makes rule 3 checkable by grep.

`control/exception/CoffeeNotFoundException` and `CoffeeAlreadyExistsException` extend
`RuntimeException` (unchecked, so they do not pollute every signature). They carry the id /
name for the mapper's `detail` string.

---

## 6. Repository contract (entity layer)

`entity/CoffeeRepository implements PanacheRepository<Coffee>` — see ADR-002 for why not
active record. Methods, with zero business logic:

| Method | Semantics |
|---|---|
| `Optional<Coffee> findByName(String name)` | Exact match on the stored (trimmed) name |
| `List<Coffee> findByRoastLevel(RoastLevel level)` | Ordered by `name` asc, then `id` asc |
| `Page<Coffee> findAllPaged(int page, int size)` | Ordered by `name` asc, then `id` asc; `page` is 0-based; `size` is already validated by control |

`findByRoastLevel` is **not** reachable from the API in v1 (no list filter). It exists
because card `t_f9e0a903` requires it and because roast-level browsing is the obvious next
filter; it must be covered by a repository test, not left as dead-but-untested code.

---

## 7. Decisions deliberately deferred (not defects)

Recorded so that a reviewer or a future card does not read them as omissions:

- **Optimistic locking / `@Version`** — deferred until the API exposes `If-Match`/ETag.
  Revisit when two writers to the same coffee become realistic (admin UI editing).
- **List filters and sorting parameters** (`roastLevel`, `origin`, `sort=price,desc`) —
  deferred; the contract table has no such parameter, and adding one silently would make
  the QA card's criteria untestable.
- **PATCH** — not in the contract. PUT is full replacement.
- **Case-insensitive name uniqueness** — deferred (see §2).
- **Money as a decimal string, currency field** — deferred; single implicit currency,
  `BigDecimal` on the wire.
- **OpenAPI in prod, CORS, authN/authZ, rate limiting, pagination links** — deployment- and
  client-driven; each needs its own card and (for auth) `infosec` review.

---

## 8. Architecture fit as executable rules

Recommended (not required by the cards, but cheap and it makes the BCE rules regress-proof):
`boundary/CoffeeBceArchitectureTest` asserts the import matrix of §1.2 by scanning the
compiled classes or the source tree — a REStest that fails if `boundary` ever imports
`jakarta.persistence` or if any `@Path` method signature mentions `Coffee`. If the
implementer skips it, the QA card must still verify §1.2 by inspection and record the
commands/queries used.

---

## 9. Acceptance criteria for the follow-on cards

Each criterion is written to be pass/fail by command. The QA card reports per criterion.

### 9.1 Card `t_f9e0a903` — entity layer

| id | Criterion | Verified by |
|---|---|---|
| AC-E1 | `backend/pom.xml` declares `com.example:coffee-shop:1.0.0-SNAPSHOT`, `maven.compiler.release=17`, `io.quarkus.platform:quarkus-bom:3.33.3`, and exactly the deps of §4.2 | `grep` on `pom.xml`; `./mvnw -q help:evaluate -Dexpression=maven.compiler.release` |
| AC-E2 | `entity/Coffee.java` has the fields, types and JPA mappings of §2, with `@UuidGenerator`, `@Enumerated(EnumType.STRING)`, and **no** `@Version` | read the file; assert no `@Version` |
| AC-E3 | Bean Validation annotations enforce: `name` not blank + size 1..100, `origin` not blank + size 1..100, `price` `@NotNull @DecimalMin(exclusive) @DecimalMax`, `stock` `@Min(0)`, `roastLevel` `@NotNull`; a 3rd decimal place is rejected (`@Digits(integer=8, fraction=2)`) | `CoffeeRepositoryTest` validation cases |
| AC-E4 | `@PrePersist` sets `createdAt` and `updatedAt`; `@PreUpdate` sets `updatedAt`; a persisted coffee read back has non-null timestamps with `updatedAt >= createdAt` | repository test with concrete assertions |
| AC-E5 | `entity/CoffeeRepository.java` provides `findByName`, `findByRoastLevel`, `findAllPaged` with the §6 semantics and contains no business logic (no trimming, no uniqueness policy, no HTTP) | read the file; tests for all three methods asserting concrete values and ordering |
| AC-E6 | `V1__create_coffee.sql` matches §4.4 (table `coffee`, PK, unique name, both checks, both indexes) and applies cleanly from an empty database on the test profile | `./mvnw test` boots Quarkus with Flyway `migrate-at-start`; test asserts the columns and the unique constraint via a duplicate-name insert |
| AC-E7 | `quarkus.hibernate-orm.database.generation=none` and `quarkus.flyway.migrate-at-start=true` are configured; a duplicate `name` insert fails at the database level (not only in control) | config assertion in a test + duplicate insert test |
| AC-E8 | `./mvnw test` passes, and every entity test asserts a concrete field value (no `assertNotNull`-only assertions) | raw command output in the PR body |
| AC-E9 | dev/test use H2, prod uses PostgreSQL with env-var credentials, no secret literals anywhere | read `application.properties`; `grep -ri password backend/src` |
| AC-E10 | No REST resource or DTO exists yet in the diff (entity card must not pre-empt the boundary card) | `git diff --stat`, no `boundary/` files |

### 9.2 Card `t_8046549a` — control and boundary layers

| id | Criterion | Verified by |
|---|---|---|
| AC-CB1 | `control/CoffeeService` implements exactly the §5 signatures, `@ApplicationScoped`, `@Transactional` on mutating methods, constructor-injected repository | read + unit test |
| AC-CB2 | All five endpoints of §3.1 exist with the exact status codes and `Location` header on 201 | `@QuarkusTest` + RestAssured |
| AC-CB3 | `CoffeeRequest`/`CoffeeResponse`/`CoffeePageResponse` are records with exactly the §3.2 fields; `price` serialises at scale 2; timestamps serialise ISO-8601 UTC | integration test asserting the JSON shape field by field |
| AC-CB4 | Paging: `page` default 0, `size` default 20, `size` 1..100, out-of-range or non-numeric → `400` (not clamped), ordering `name` asc then `id` asc, `totalPages` correct including 0 | integration tests at the boundaries (`page=0`, `size=1`, `size=100`, `size=101`, `page=-1`, `size=0`) |
| AC-CB5 | Validation failures (`name` blank, `name` 101 chars, `roastLevel` unknown/lowercase, `price` 0 / negative / 3 decimals / over `numeric(10,2)`, `stock` negative, missing field, malformed JSON, unknown field) → `400` problem detail with `errors[].field`/`message` | RestAssured cases, one per rule in §3.3 |
| AC-CB6 | `404` for unknown id and for a malformed (non-UUID) id on GET, PUT, DELETE; `409` for duplicate name on POST **and** on PUT | RestAssured cases |
| AC-CB7 | Error bodies are `application/problem+json` with `type`/`title`/`status`/`detail`/`instance` per §3.5; the `500` mapper leaks no exception class, SQL or stack trace | integration test asserting content type + body; adversarial test forcing an error |
| AC-CB8 | BCE rules hold (§1.2): no `boundary` import of `jakarta.persistence`/`CoffeeRepository`, no `control` import of `boundary`/`jakarta.ws.rs`, and **no entity type in any `@Path` method signature** | architecture test if written, else documented inspection: `grep -rn` commands recorded in the PR body |
| AC-CB9 | OpenAPI at `/q/openapi` lists all five operations with the §3.2 schemas | fetch the document in a test and assert the paths |
| AC-CB10 | Control unit tests use `@InjectMock` on the repository and cover each use case plus both error paths (not-found, already-exists) | raw `./mvnw test` output |
| AC-CB11 | `./mvnw test` passes and the PR body carries the raw output | command output in the PR |

### 9.3 Card `t_c41f5e3e` — independent verification

| id | Criterion | Verified by |
|---|---|---|
| AC-V1 | `./mvnw clean verify` on the implementation branch passes; raw output recorded in `VERIFICATION.md` | command output |
| AC-V2 | Reviewer-authored full CRUD round-trip through the HTTP API (create → list → get → update → delete → 404) | new test |
| AC-V3 | Pagination boundaries (page 0, oversized size, size 100, page past the end) behave as §3.1/§3.2 | new test |
| AC-V4 | Unicode names (e.g. `Café ☕ Solo`) and 100-char names are accepted; 101-char names rejected with the §3.3 message | new test |
| AC-V5 | Price handling: `12.30` round-trips as `12.30`; `12.345` is rejected `400`; `0`, `-1`, `100000000.00` rejected | new test |
| AC-V6 | Restart persistence: a coffee created in the dev profile is still readable after the process is stopped and started again (dev = file-backed H2 per §4.5) | documented commands: start packaged app on a file H2 URL, POST, kill, start again, GET |
| AC-V7 | BCE boundaries hold independently of the author's tests (§1.2): boundary imports, control imports, entity-free `@Path` signatures | `grep -rn` evidence in `VERIFICATION.md` |
| AC-V8 | Adversarial inputs: concurrent duplicate-name POSTs (exactly one 201, the other 409 — the DB constraint, not just the pre-check), malformed JSON, unknown fields, negative stock, deleted id, non-UUID id | new tests with raw output |
| AC-V9 | Every criterion above is reported pass/fail with the command run, and defects are listed with reproduction steps | `VERIFICATION.md` |
| AC-V10 | If the toolchain is still missing on the box (§4.6), the card is `BLOCKED` with the reason recorded, not marked pass | card state + reason |

---

## 10. ADRs recorded for this specification

| ADR | Decision | File |
|---|---|---|
| ADR-001 | Boundary–Control–Entity layering with a strict import matrix and DTO-never-leaks-entity rule | `docs/adr/ADR-001-bce-layering.md` |
| ADR-002 | Panache **repository** pattern (`PanacheRepository<Coffee>`) instead of Panache active record | `docs/adr/ADR-002-panache-repository-vs-active-record.md` |

The deferred decisions in §7 are not ADRs: they are *not* decisions, they are open
questions with a stated revisit trigger. A future card that settles one writes its own ADR
and supersedes nothing.

## 11. Skills applied

- `bce-microprofile-server` — three-stereotype package layout (`boundary`/`control`/`entity`),
  `@ApplicationScoped` controls, `@Transactional` on control only, `@Inject` never in the
  entity, JAX-RS/OpenAPI annotations in boundary only, HTTP-level tests under the test
  `boundary` package. Deviations from the skill's *lean* defaults (DTO layer, repository
  pattern) are itemised in §1.4 with their reason.
- `architecture-decision-records` — ADR format, one decision per file, alternatives with
  specific reasons, explicit accepted downside, `Revisit when` trigger.
- `java-cloud-native-stack` — Quarkus for a small, container-density-sensitive service;
  pinned BOM version (no ranges); contract-first with OpenAPI; no secrets in Git; env-var
  credentials; config over reflection.
- `factory-delivery-workflow` — card-as-unit-of-work, gate 1 plan approval, branch and PR
  rules, "a green local build is not the bar".
- `quarkus-*` skills (`quarkus-rest`, `quarkus-panache`, `quarkus-flyway`) — the implementer
  cards name the relevant extensions; §4.2 pins the artifact names that actually exist in
  `quarkus-bom:3.33.3`.
