# Frontend — React

React app consuming the backend API.

Nothing exists here yet; the first task creates it.

## Conventions

- TypeScript. API types come from the OpenAPI contract in one place, not re-declared.
- Every view handles **loading, empty, error and success** — a happy-path-only
  component is unfinished.
- Accessibility is part of the work: semantic elements, labels, keyboard reachable,
  visible focus.
- Test behaviour with Vitest + React Testing Library, querying by role and
  accessible name.

See `../AGENTS.md` for the Definition of Done and the PR rules.
