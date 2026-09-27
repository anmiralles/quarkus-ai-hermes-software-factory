# Backend — Quarkus

Java service(s) built with Quarkus, following the team conventions in the
`java-cloud-native-stack` skill.

Nothing exists here yet; the first task creates it.

## Conventions

- Contract-first: the OpenAPI spec is the reviewable artefact, the implementation
  follows it.
- Persistence changes ship as migrations, never hand-edited schema.
- Integration tests run against real infrastructure (Testcontainers), not mocks of it.

See `../AGENTS.md` for the Definition of Done and the PR rules.
