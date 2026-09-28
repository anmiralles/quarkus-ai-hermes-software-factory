# Architecture Decision Records

One decision per file, numbered sequentially, never edited once accepted (supersede it
instead). Format and rules: the `architecture-decision-records` skill.

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-001](ADR-001-bce-layering.md) | Boundary–Control–Entity layering for the Coffee service | accepted | 2026-09-27 |
| [ADR-002](ADR-002-panache-repository-vs-active-record.md) | Panache repository pattern instead of Panache active record | accepted | 2026-09-27 |
| [ADR-003](ADR-003-frontend-integration-without-cors.md) | Frontend reaches the backend through its own dev-server proxy, not CORS | accepted | 2026-09-28 |

Related specifications:

- [`../architecture/coffee-bce.md`](../architecture/coffee-bce.md) — BCE rules, Coffee domain
  contract, REST contract, acceptance criteria for cards `t_f9e0a903`, `t_8046549a`,
  `t_c41f5e3e`.
- [`../architecture/coffee-frontend.md`](../architecture/coffee-frontend.md) — the React
  application specification and the acceptance criteria for the frontend implementation and
  verification cards.

Decisions deliberately deferred rather than taken are listed in §7 (backend) and §10
(frontend) of those documents — they are open questions, not ADRs.
