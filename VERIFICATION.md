# VERIFICATION.md — independent verification of the Coffee BCE REST service

Card: `t_c41f5e3e` — *Verify Coffee BCE REST service against acceptance criteria*
Verifier: `qa-tester` (independent — did not author the implementation)
Artifact under test: `origin/task/t_8046549a-coffee-control-boundary` @ commit `d227647`
(which stacks on `origin/task/t_f9e0a903-coffee-entity` @ `7c74bbe`)
Report committed on branch: `task/t_c41f5e3e-coffee-verify`
Criteria: `docs/architecture/coffee-bce.md` §9.3 (AC-V1 … AC-V10), read from
`origin/task/t_db2182b6-coffee-bce-spec` @ `d249ab4`

## VERDICT

```
VERDICT: PASS
Commit:  d227647   Branch: task/t_8046549a-coffee-control-boundary
Env:     Temurin 21.0.12.1+1, Maven 3.9.16 (committed wrapper), Quarkus 3.33.3,
         H2 in-memory (test profile) / H2 file-backed (dev profile), uid 10000, no Docker daemon
What was tested: AC-V1 … AC-V10, all pass
Evidence: 75/75 tests green; `./mvnw -B clean verify` BUILD SUCCESS; restart-persistence
          reproduced against a real process restart; BCE import matrix grepped by hand
Not covered: the unique-constraint *arbiter* path on H2 (see "Not covered"), PostgreSQL
          profile behaviour (no Docker daemon), security posture (infosec's call)
```

No acceptance criterion is unmet and none of the tests added by this card is red.
Two minor defects are filed with reproductions (D1, D2); neither blocks the contract.

---

## 1. AC-V1 — `./mvnw clean verify` on the implementation branch

```
$ . /opt/data/toolchains/env.sh
$ cd backend && ./mvnw -B clean verify
...
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.coffeeshop.boundary.CoffeeResourceTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.coffeeshop.control.CoffeeServiceTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.coffeeshop.entity.CoffeeRepositoryTest
[INFO] Tests run:  1, Failures: 0, Errors: 0, Skipped: 0 -- in QA verification: concurrent duplicate-name POSTs
[INFO] Tests run:  5, Failures: 0, Errors: 0, Skipped: 0 -- in QA verification: /coffees contract
[INFO] Tests run:  5, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.coffeeshop.boundary.CoffeeBceArchitectureTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 75, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

75 tests = 69 authored on the implementation branch + **6 added by this card**.
Raw log: `/tmp/verify-final.log`.

**AC-V1: PASS.**

---

## 2. Criterion-by-criterion result

| id | Criterion | Result | Evidence |
|---|---|---|---|
| AC-V1 | `./mvnw clean verify` passes, raw output recorded | **PASS** | §1 above; 75 tests, 0 failures, BUILD SUCCESS |
| AC-V2 | Full CRUD round trip through the HTTP API (create → list → get → update → delete → 404) | **PASS** | `QaContractVerificationTest.acV2_fullCrudRoundTrip` |
| AC-V3 | Pagination boundaries (page 0, oversized size, size 100, page past the end) | **PASS** | `QaContractVerificationTest.acV3_paginationBoundaries` |
| AC-V4 | Unicode names and 100-char names accepted; 101 rejected with the §3.3 message | **PASS** | `QaContractVerificationTest.acV4_unicodeAndNameLengthBoundaries` |
| AC-V5 | Price: `12.30` round-trips as `12.30`; `12.345`/`0`/`-1`/`100000000.00` rejected | **PASS** | `QaContractVerificationTest.acV5_priceHandling` |
| AC-V6 | Restart persistence in the dev profile (file-backed H2) | **PASS** | §4 below — real process kill and restart, same id and values |
| AC-V7 | BCE boundaries hold independently of the author's tests | **PASS** | §5 below — `grep -rn` evidence |
| AC-V8 | Adversarial inputs: concurrent duplicate-name POSTs, malformed JSON, unknown fields, negative stock, deleted id, non-UUID id | **PASS** | `QaConcurrencyTest.acV8_concurrentDuplicateNamePosts`, `QaContractVerificationTest.acV8_adversarialInputs` — one limitation recorded in §6 |
| AC-V9 | Every criterion reported pass/fail with the command run; defects with reproductions | **PASS** | this document |
| AC-V10 | If the toolchain were still missing, `BLOCKED` rather than pass | **N/A → PASS** | toolchain present (card `t_e1bded4b`, PR #2); JDK 21.0.12.1+1, Maven 3.9.16; all criteria executed, none inspected-only |

### Tests added by this card

`backend/src/test/java/com/example/coffeeshop/verify/QaContractVerificationTest.java`
`backend/src/test/java/com/example/coffeeshop/verify/QaConcurrencyTest.java`

They are written from the specification, not from the implementation, and share no code
with the author's suite. Every one asserts a concrete value; none is `assertNotNull`-only.
Both classes empty the `coffee` table before and after each test, because the H2 test
datasource is shared across every `@QuarkusTest` class in the JVM.

Notable cases the author's suite did **not** cover, added here:

- Unicode round trip (`Café ☕ Solo`, `España`) and *acceptance* at exactly 100 characters.
- `size=1000` and an integer-overflow page value (`99999999999999999999`) → contract-shaped 400.
- `12.3` widened to `12.30` on the wire, and re-read after a reload (not just the POST echo).
- Uniqueness after trimming: `"  Unique Name  "` collides with `"Unique Name"` → 409.
- Eight 32-way concurrent identical POSTs over five rounds (§6).

### Proof the added tests bite

A green suite is not evidence by itself, so the two central assertions were mutation-tested
against the production sources (mutations reverted afterwards):

1. `MAX_PAGE_SIZE` 100 → 101 (oversized `size` no longer rejected):

```
[ERROR] com.example.coffeeshop.verify.QaContractVerificationTest.acV3_paginationBoundaries <<< FAILURE!
..."size":101,... ==> expected: <400> but was: <200>
```

2. `translateUniqueViolation` neutralised (constraint violation no longer mapped to 409):
the 32×5 concurrency test still passed — see §6, this is a real gap in *what the test can
prove*, not a pass on the behaviour.

Raw logs: `/tmp/qa-mutant.log`, `/tmp/qa-mutant3.log`.

---

## 3. What was inspected rather than executed

Nothing was marked pass on inspection alone. The BCE rules (§5) are a source-level property,
so they are verified by `grep` on the sources and reproduced verbatim; the behaviour
criteria are all executed.

---

## 4. AC-V6 — restart persistence (dev profile, file-backed H2)

Reproduction (the `mvn verify` jar is built with the default **prod** profile, so the jar
has to be rebuilt for the dev profile — see D3/O2):

```
$ cd backend
$ ./mvnw -B -DskipTests -Dquarkus.profile=dev package
$ rm -rf target/h2
$ java -Dquarkus.profile=dev -Dquarkus.http.port=8099 -jar target/quarkus-app/quarkus-run.jar &
$ curl -s -X POST -H 'Content-Type: application/json' \
    -d '{"name":"Restart Persistence","roastLevel":"MEDIUM","origin":"Brazil","price":13.37,"stock":11}' \
    http://localhost:8099/coffees
$ kill -TERM <pid>          # process fully stopped
$ java -Dquarkus.profile=dev -Dquarkus.http.port=8099 -jar target/quarkus-app/quarkus-run.jar &
$ curl -s http://localhost:8099/coffees/ebd90029-58b0-4459-9d90-69d6b9f647e9
```

Observed (raw, `/opt/data/tmp/dev-run1.log`, `/opt/data/tmp/dev-run2.log`):

```
POST RESPONSE: {"id":"ebd90029-58b0-4459-9d90-69d6b9f647e9","name":"Restart Persistence",
                "roastLevel":"MEDIUM","origin":"Brazil","price":13.37,"stock":11,
                "createdAt":"2026-09-27T20:31:09.249908999Z","updatedAt":"2026-09-27T20:31:09.249908999Z"}
H2 FILES BEFORE KILL: -rw-r--r-- 1 hermes hermes 24576 coffee-dev.mv.db
GET AFTER RESTART: HTTP/1.1 200 OK
{"id":"ebd90029-58b0-4459-9d90-69d6b9f647e9","name":"Restart Persistence","roastLevel":"MEDIUM",
 "origin":"Brazil","price":13.37,"stock":11,
 "createdAt":"2026-09-27T20:31:09.249909Z","updatedAt":"2026-09-27T20:31:09.249909Z"}
```

The row survives a full stop/start with identical id and field values.

**AC-V6: PASS.** (The sub-microsecond difference in `createdAt` between the two responses is
defect D1 below.)

---

## 5. AC-V7 — BCE boundaries (§1.2), independent of the author's architecture test

Commands, from `backend/src/main/java/com/example/coffeeshop`:

```
$ grep -rn "^import" boundary | grep -E "jakarta\.persistence|CoffeeRepository|io\.quarkus\.hibernate\.orm\.panache"
NONE
$ grep -rn "^import" control  | grep -E "com\.example\.coffeeshop\.boundary|jakarta\.ws\.rs|io\.quarkus\.panache\.common|org\.hibernate"
NONE
$ grep -rn "^import" entity   | grep -E "com\.example\.coffeeshop\.(control|boundary)|jakarta\.ws\.rs"
NONE
$ grep -rn "@Inject" entity
NONE
$ grep -rn "jakarta\.persistence|@Entity|@Column|@Id\b|@Enumerated" boundary/dto
NONE
$ grep -rn "jakarta\.ws\.rs|Response\.|Response\.Status|UriBuilder" control
NONE
$ grep -nE "public (Response|CoffeeResponse|CoffeePageResponse) [a-zA-Z]+\(" boundary/CoffeeResource.java
69:  public Response create(@Valid CoffeeRequest request)
88:  public CoffeePageResponse list(...)      # String page, String size
107: public CoffeeResponse get(@PathParam("id") String id)
124: public CoffeeResponse update(@PathParam("id") String id, @Valid CoffeeRequest request)
135: public Response delete(@PathParam("id") String id)
```

- No `boundary` import of `jakarta.persistence`, `CoffeeRepository` or Panache — rule 1 holds.
- No `control` import of `boundary`, `jakarta.ws.rs`, `io.quarkus.panache.common` or
  `org.hibernate` — rule 2 holds; control contains no HTTP vocabulary and never builds a status.
- No `@Path` method signature mentions a type from `com.example.coffeeshop.entity` — rule 3's
  HTTP-edge half holds. No DTO in `boundary/dto` carries a JPA annotation.
- The only entity imports outside `entity` are `boundary/dto/CoffeeRequest|CoffeeResponse`
  (mapping step) and `control` — exactly the §1.2 allowance, no more.

**AC-V7: PASS.**

---

## 6. AC-V8 — adversarial inputs

Executed: malformed JSON → 400 `Malformed request body`; unknown field (`roast`) → 400 with
`unknown field 'roast'`; negative stock → 400 `stock must be greater than or equal to 0`;
deleted id → 404 on GET, PUT and DELETE; non-UUID (`not-a-uuid`, `12.5`) → 404; sequential
duplicate → 409 with `Coffee name '...' already exists` detail; whitespace-trimmed
duplicate → 409.

Concurrent duplicate-name POSTs (`QaConcurrencyTest`): 5 rounds × 32 simultaneous
`POST /coffees` with the same body, released from a `CountDownLatch`. Per round exactly one
201, the other 31 are 409, none is 500 and exactly one row survives.

**AC-V8: PASS**, with one limitation that must be stated rather than glossed:

> **Not covered — the constraint is never the arbiter on H2.** The criterion's parenthetical
> ("the DB constraint, not just the pre-check") could not be demonstrated on this box. Three
> independent attempts failed to make `uq_coffee_name` the deciding constraint:
>
> 1. 32 identical POSTs × 5 rounds with the 409 mapping mutated away — zero non-409 responses,
>    so every loser was resolved by the control pre-check, never by the constraint.
> 2. 8 concurrent direct control calls on separate `QuarkusTransaction.requiringNew()`
>    transactions against the real H2 database — 1 created, 7 pre-check conflicts, no
>    persistence exception.
> 3. A forced race: an uncommitted duplicate row held on a second connection while the POST
>    ran — the POST returned the pre-check 409, not a constraint violation, again with the
>    mapping mutated away.
>
> A plain reader on a separate connection does not block behind an uncommitted writer
> (observed `blockedMs=1`, `COUNT(*)=0`, isolation READ_COMMITTED), so the loser's pre-check
> simply executes after the winner's transaction has committed. On the test profile the
> constraint fallback in `CoffeeService.translateUniqueViolation` is therefore not reachable
> end-to-end; its correctness rests on the author's `@InjectMock` unit test. The race the spec
> describes is a PostgreSQL property, and PostgreSQL cannot be run here (no Docker daemon,
> §4.6). **Verifying it needs a Testcontainers run of this card's concurrency test on the prod
> profile** — recommend a follow-up card for `devops`/`backend-developer`.

---

## 7. Defects

### D1 — `createdAt`/`updatedAt` precision differs between the create response and subsequent reads

```
Defect:   The timestamp handed back on 201 is not the timestamp a later GET returns.
Expected: §2 pins createdAt as immutable after insert and §3.2 shows microsecond precision;
          a client that creates a coffee and immediately re-reads it should see the same value.
Actual:   POST serialises the in-memory entity (nanosecond precision), reads come back from a
          TIMESTAMP WITH TIME ZONE column rounded to microseconds.
Reproduce:
  1. build/run the dev profile as in §4
  2. curl -s -X POST -H 'Content-Type: application/json' -d '{"name":"P","roastLevel":"LIGHT",
     "origin":"Kenya","price":1.00,"stock":1}' http://localhost:8099/coffees
     -> "createdAt":"2026-09-27T20:31:09.249908999Z"
  3. curl -s http://localhost:8099/coffees/<id>
     -> "createdAt":"2026-09-27T20:31:09.249909Z"
Evidence: raw output in §4; also the first QA run of this card:
  "createdAt is immutable ==> expected: <2026-09-27T20:19:18.653210803Z>
                                 but was: <2026-09-27T20:19:18.653211Z>"
Severity: minor — the same instant, no data loss; but the value is API-observable and makes
          timestamps non-comparable as strings, and it makes "createdAt is immutable" untestable
          as a literal equality (the QA suite asserts the same-instant invariant instead, and
          says so in a comment).
Suspected owner: backend-developer
```

### D2 — unrecognised Jackson configuration key (spec §4.5 line is a silent no-op)

```
Defect:   application.properties sets quarkus.jackson.serialization.write-dates-as-timestamps,
          which is not a Quarkus configuration key; the build warns and ignores it.
Expected: the property intended by §4.5 should actually be applied.
Actual:   [WARNING] [io.quarkus.config] Unrecognized configuration key
          "quarkus.jackson.serialization.write-dates-as-timestamps" was provided; it will be
          ignored; verify that the dependency extension for this configuration is set or that
          you did not make a typo
Reproduce: cd backend && ./mvnw -B clean verify   (the warning is in the augmentation step)
Evidence: /tmp/verify-final.log, /tmp/verify-baseline.log
Impact:   none today — the emit-ISO-8601 behaviour is correct because the effective default of
          the real property (quarkus.jackson.write-dates-as-timestamps) is already false, and
          both the author's and this card's tests assert the ISO-8601 shape. But the intent is
          not enforced: flipping it would have no effect.
Severity: minor
Suspected owner: backend-developer (spec §4.5, card t_db2182b6, pins the wrong key name —
          needs an architect comment if the spec is to be corrected)
```

### Informational (not defects)

- `quarkus.hibernate-orm.database.generation` is deprecated in 3.33; the warning is harmless
  (Flyway owns the schema either way).
- `quarkus-junit5` and `quarkus-junit5-mockito` are relocated artefacts in 3.33; the build
  warns but resolves them. §4.2 pins these names, so the implementers cannot fix it alone.

---

## 8. Observations worth a follow-up (no verdict impact)

- **O2 — the packaged jar cannot be run on the dev profile.** `mvn verify` builds with the
  default (prod) profile, and `quarkus.datasource.db-kind` — plus the JDBC driver on the
  classpath — is fixed at build time. Running `java -Dquarkus.profile=dev -jar
  target/quarkus-app/quarkus-run.jar` fails at startup:
  `Driver does not support the provided URL: jdbc:h2:file:./target/h2/coffee-dev`.
  Reproducing AC-V6 required a second build with `-Dquarkus.profile=dev`. That is a real
  operational footgun for whoever documents how to run the dev profile; consider a
  `%dev`-aware packaging note or a separate dev-profile build in the deployment docs (`devops`).
- **O3 — `control` imports `jakarta.persistence.PersistenceException`.** That is necessary for
  the flush-time 409 mapping and is not forbidden by §1.1 rule 2 (it forbids `boundary.*` and
  `jakarta.ws.rs.*`), but the §1.2 control column neither allows nor forbids
  `jakarta.persistence.*`. The table is under-specified for control; worth a one-line spec fix.
- **Non-BMP Unicode is counted in UTF-16 code units.** `@Size(max = 100)` counts a
  supplementary-plane character (e.g. 🍵) as two, so 51 such characters exceed the limit
  although they are 51 code points. BMP Unicode (`Café ☕ Solo`) is unaffected and is what
  AC-V4 asks for; recorded so nobody is surprised later.

---

## 9. Commands run (complete list)

```
. /opt/data/toolchains/env.sh                       # JDK 21.0.12.1+1, Maven 3.9.16
cd backend
./mvnw -B clean verify                              # AC-V1: 75 tests, BUILD SUCCESS
./mvnw -B test -Dtest='QaContractVerificationTest,QaConcurrencyTest'
                                                    # the 6 tests added by this card
./mvnw -B -DskipTests -Dquarkus.profile=dev package # jar for the dev-profile restart check
java -Dquarkus.profile=dev -Dquarkus.http.port=8099 -jar target/quarkus-app/quarkus-run.jar
curl ...                                            # AC-V6: POST, kill, restart, GET
grep -rn ...                                        # AC-V7: BCE import matrix (§5)
```

Skills applied: `test-strategy` (falsifiable criteria, cheapest level that can fail,
independent of the author's suite, mutation check that the assertions bite, structured verdict
and defect format), `factory-delivery-workflow` (card as the unit of work, branch/PR rules,
hand-off targets), `java-cloud-native-stack` (real H2 rather than mocks; a mocked repository
cannot tell you the SQL is wrong).
