# Architecture Decision Records

One decision per file, numbered sequentially, never edited once accepted (supersede it
instead). Format and rules: the `architecture-decision-records` skill.

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-001](ADR-001-bce-layering.md) | Boundary–Control–Entity layering for the Coffee service | accepted | 2026-09-27 |
| [ADR-002](ADR-002-panache-repository-vs-active-record.md) | Panache repository pattern instead of Panache active record | accepted | 2026-09-27 |

Related specification: [`../architecture/coffee-bce.md`](../architecture/coffee-bce.md)
(BCE rules, Coffee domain contract, REST contract, acceptance criteria for cards
`t_f9e0a903`, `t_8046549a`, `t_c41f5e3e`). Decisions deliberately deferred rather than
taken are listed in §7 of that document — they are open questions, not ADRs.
