# Frontend — React

React + TypeScript single-page app that performs every CRUD operation of the Coffee API
(create, list, read, update, delete). It talks to the API through its **own dev/preview
server proxy**, so `/coffees` is same-origin and the backend needs no CORS — see
[`../docs/adr/ADR-006-frontend-integration-without-cors.md`](../docs/adr/ADR-006-frontend-integration-without-cors.md).

The specification this app implements is
[`../docs/architecture/coffee-frontend.md`](../docs/architecture/coffee-frontend.md);
the harness notes are in [`../docs/operations/frontend-runbook.md`](../docs/operations/frontend-runbook.md).

## Install, run, test, build

```bash
npm ci                 # install exactly the committed lockfile
npm run dev            # dev server on http://localhost:5173 (strict port)
npm run test -- --run  # Vitest + React Testing Library, once, no watch
npm run typecheck      # tsc --noEmit
npm run build          # production bundle into dist/
npm run preview        # serve dist/ on http://localhost:4173 (strict port)
```

`npm run dev` and `npm run preview` both need the API running on port 8080:

```bash
cd ../backend && ./mvnw quarkus:dev
```

## The proxy contract

`vite.config.ts` proxies `/coffees` to `http://localhost:8080` for **both** `server` (5173)
and `preview` (4173). Override the target with the Vite env var:

```bash
VITE_BACKEND_URL=http://localhost:9090 npm run dev
```

The API client uses same-origin relative paths (`/coffees`, `/coffees/{id}`) and never an
absolute backend URL. Two consequences worth knowing before reporting a bug:

- Opening `dist/index.html` straight from the filesystem does **not** work — there is no
  proxy and no same-origin API. Serve it through `npm run preview`.
- If the proxy target is unreachable, the UI shows a "Cannot reach the server" alert. That
  is the proxy failing, not a missing feature.

## Conventions

- TypeScript. API types are declared once, in `src/api/types.ts`, mirroring
  `docs/architecture/coffee-bce.md` §3.2. They are deliberately not generated at build
  time, so the frontend build does not depend on a running backend.
- `src/api/client.ts` is the only module that calls `fetch`; every failure becomes an
  `ApiError` carrying the server's RFC 7807 problem detail.
- Every view handles **loading, empty, error and success** — a happy-path-only component
  is unfinished.
- Accessibility is part of the work: semantic `table`/`form`/`label`, every input
  labelled, unique per-row action names, keyboard reachable, visible focus, `role="alert"`
  for failures and `role="status"` for successful mutations.
- Tests assert behaviour with Vitest + React Testing Library, querying by **role and
  accessible name** — no `data-testid`, no `container.querySelector`. Only the network is
  stubbed (MSW v2), so the real client and the real components run.
- The server owns validation. Field rules are not re-implemented here: the client sends the
  five request fields and renders the server's `errors[].message` against the named field.

See `../AGENTS.md` for the Definition of Done and the PR rules.
