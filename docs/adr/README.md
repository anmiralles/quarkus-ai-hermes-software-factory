# Architecture Decision Records

One decision per file, numbered sequentially, never edited once accepted (supersede it
instead). Format and rules: the `architecture-decision-records` skill.

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-001](ADR-001-bce-layering.md) | Boundary–Control–Entity layering for the Coffee service | accepted | 2026-09-27 |
| [ADR-002](ADR-002-panache-repository-vs-active-record.md) | Panache repository pattern instead of Panache active record | accepted, refined by ADR-004 | 2026-09-27 |
| [ADR-003](ADR-003-control-owned-page-read-model.md) | Paging returns a control-owned `CoffeePage`, not Panache's `Page` | accepted | 2026-09-28 |
| [ADR-004](ADR-004-uuid-typed-panache-repository.md) | `CoffeeRepository implements PanacheRepositoryBase<Coffee, UUID>` | accepted, refines ADR-002 | 2026-09-28 |
| [ADR-005](ADR-005-flyway-postgresql-module.md) | `quarkus-flyway-postgresql` is a declared dependency for the prod profile | accepted | 2026-09-28 |

Related specification: [`../architecture/coffee-bce.md`](../architecture/coffee-bce.md)
(BCE rules, Coffee domain contract, REST contract, acceptance criteria for cards
`t_f9e0a903`, `t_8046549a`, `t_c41f5e3e`). Decisions deliberately deferred rather than
taken are listed in §7 of that document — they are open questions, not ADRs.

ADR-003 to ADR-005 were recorded on card `t_da7e20d9` when revision 2 of the specification
was written to match the as-built service; §0 of that document is the revision log that
points each change at the card that reported it. ADR-004 **refines** ADR-002 rather than
superseding it: the decision to use a repository instead of Panache active record is
unchanged, only the declared type arguments are corrected.