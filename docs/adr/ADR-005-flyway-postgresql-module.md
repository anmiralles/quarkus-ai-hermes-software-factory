# ADR-005: `quarkus-flyway-postgresql` is declared explicitly in the build

Status: accepted
Date: 2026-09-28
Deciders: architect (card `t_da7e20d9`)
Governs: `backend/pom.xml`

## Context

The specification makes Flyway the single writer of DDL in every profile (§4.4) and targets
PostgreSQL in prod (§4.5). Since Flyway 10, per-database support no longer lives in
`flyway-core`; `quarkus-flyway` 3.33.3 depends on `org.flywaydb:flyway-core` alone.

Cards `t_f9e0a903` and `t_c41f5e3e` (observation O2) both reported the consequence as a
defect: the prod PostgreSQL profile has no Flyway PostgreSQL module, because
`./mvnw dependency:list` shows `flyway-core` and nothing else. Card `t_f9e0a903` could not add
it either — AC-E1 required *exactly* the revision-1 §4.2 dependency set, so adding one would
have failed that card's own verification.

Measuring it on the factory box gives a more precise picture:

- `./mvnw dependency:list` (default profile): `org.flywaydb:flyway-core:12.0.0` and
  `io.quarkus:quarkus-flyway:3.33.3`. No PostgreSQL module — what the cards observed.
- `./mvnw -B -DskipTests -Dquarkus.profile=prod package`: `target/quarkus-app/lib/main/`
  contains `io.quarkus.quarkus-flyway-postgresql-3.33.3.jar` **and**
  `org.flywaydb.flyway-database-postgresql-12.0.0.jar`.

So Quarkus already pulls the module into the deployed artifact through its
conditional-dependency mechanism (the extension's own description: "added by Quarkus
automatically when quarkus-flyway and quarkus-jdbc-postgresql are on the classpath"). The
reported defect is therefore **not** that prod is broken. The real gap is visibility: the
database module the prod image ships appears in neither `backend/pom.xml` nor a dependency
audit of the module.

## Decision

We will declare `io.quarkus:quarkus-flyway-postgresql` (scope `runtime`, version managed by
`quarkus-bom:3.33.3`) in `backend/pom.xml`, so that the prod database module is stated where
the build is stated rather than inferred from Quarkus's automatic resolution.

## Alternatives considered

1. **Leave the pom alone and rely on Quarkus's automatic resolution.** This is the option the
   platform actively supports, and it works today — it is the honest counter-argument and the
   reason this ADR records the measurement rather than a defect. Rejected because the module
   that handles schema migrations on the only deployed database is invisible to a dependency
   audit of the module: a vulnerability scan, a licence inventory or a "what does this
   service ship?" review run against `backend/` will not list
   `flyway-database-postgresql`, and a change to the conditional mechanism (or a native-image
   build that trims it) would be silent. One pom line buys auditability.
2. **Declare `org.flywaydb:flyway-database-postgresql` directly.** Rejected: it needs an
   explicit version pinned and hand-maintained against whatever Flyway version the Quarkus
   platform ships, and it bypasses the extension's build-time wiring — exactly the class of
   hand-managed dependency the platform BOM exists to eliminate.
3. **Drop Flyway in prod and let Hibernate generate the schema**
   (`quarkus.hibernate-orm.database.generation=update`). Rejected outright: §4.4 makes Flyway
   the only DDL writer in every profile precisely so the schema dev, test and prod run is the
   same artifact, not whatever Hibernate inferred.
4. **Treat the reported defect as accurate and describe prod as broken.** Rejected: the
   measurement above contradicts it. Recording a defect that a `target/quarkus-app/lib/main/`
   listing disproves would send the next reader hunting a bug that does not exist.

## Consequences

Easier: "what does the prod image ship for migrations, and why?" is answered by reading
`backend/pom.xml`; the production database module appears in any dependency audit of the
module; dev and test on H2 stay unaffected because the module is inert for non-PostgreSQL
databases; and the declaration keeps working under the conditional mechanism (it is the same
extension, resolved once).

Harder, and accepted: the pom carries a runtime dependency that is redundant *today*, so a
reader may reasonably ask why it is there. The answer is one hop away (§4.2's note and this
ADR), and it is a smaller cost than an invisible production dependency. This is the trade
the ADR accepts explicitly.

**Not verified on this box:** with no Docker daemon and no PostgreSQL service (§4.6), the
module's presence can be verified from the packaged prod artifact, but an actual PostgreSQL
migration cannot be executed here. That limitation is recorded in §7 of the specification.

## Revisit when

A PostgreSQL environment (or a Docker daemon usable for Testcontainers) becomes available:
this dependency stops needing the "auditable even if currently redundant" justification and
becomes something `./mvnw verify` proves by running a migration, and §7's known gap closes.
Also revisit if Quarkus removes the conditional-dependency mechanism, at which point this
declaration becomes load-bearing rather than defensive.