# Coffee shop frontend — React application specification

Status: **proposed** (awaiting plan approval on card `t_be0fd4f5`)
Card: `t_be0fd4f5` — "Coffee shop front end development"
Supersedes: nothing. This is the contract the two frontend cards build against:

| Card | Title | Owner | Branch |
|---|---|---|---|
| `t_5f9dc91a` | Implement the React coffee CRUD app | `frontend-developer` | `task/t_5f9dc91a-coffee-frontend` |
| `t_62a65b0b` | Independently verify the frontend against the real backend | `qa-tester` | `task/t_62a65b0b-frontend-verify` |

`t_62a65b0b` is linked as a child of `t_5f9dc91a`, so the board enforces verification after
implementation. Both are linked to `t_be0fd4f5` transitively, and neither starts until Angel
approves this plan.

Consumes: [`coffee-bce.md`](coffee-bce.md) §3 (the REST contract), which is frozen and
independently verified (`VERIFICATION.md`, VERDICT: PASS, 75/75 tests). **The backend is
not modified by either frontend card** — `git diff --stat` must show no file under
`backend/`.

**This document is a specification.** No production code is written on card `t_be0fd4f5`.
An implementer that needs to deviate comments on this card first; the acceptance criteria
in §8/§9 are what QA verifies, so an undocumented deviation becomes a failed verification.

---

## 1. Deliverable and non-goals

**Deliverable.** A React + TypeScript single-page application under `frontend/` that
performs every CRUD operation exposed by the Coffee API — create, list (paginated), read,
update, delete — reachable in a browser, covered by behaviour-level tests, and verified
independently against the running backend.

**Non-goals (v1).**

- No backend change, no backend test change, no CORS work on the backend (see ADR-003).
- No authentication or session handling — the API has none (spec §7 of `coffee-bce.md`).
- No deployment artefact: no Dockerfile, no static hosting, no CI. "Accessible via
  browser" means the frontend's own dev/preview server on this box (§8 AC-F15).
- No client-side re-implementation of the backend's validation rules (§5 decision D-4).
- No search, filter or sort controls: the contract has no query parameters for them and
  adding one silently would make the frontend test the backend's absence of a feature.

---

## 2. Decisions already made — binding on both cards

These are settled here so two cards cannot choose differently. They are restated in each
card body.

| id | Decision |
|---|---|
| **D-1 Stack** | Vite + React + TypeScript, package manager **npm** (Node 26.5.1 / npm 11.17.0 are on the box; `pnpm` and `yarn` are **not** installed — do not introduce them). Test stack **Vitest + React Testing Library + `@testing-library/jest-dom` + `@testing-library/user-event`**, `jsdom` environment, **MSW v2** for network-level mocks. Server state via **TanStack Query v5**. |
| **D-2 No router, no client-state library** | One page. No `react-router`, no Redux/Zustand/MobX/Jotai. The only client state is transient form/selection state, which is `useState` inside the component that owns it. |
| **D-3 Integration without CORS** | The API client uses **same-origin relative paths** (`/coffees…`). The Vite dev server *and* preview server proxy `/coffees` to `http://localhost:8080`, overridable with the Vite env var `VITE_BACKEND_URL`. The backend is not touched. See **ADR-003**. |
| **D-4 Errors come from the server, verbatim** | The frontend does **not** re-implement the backend's field rules (lengths, ranges, scale, enum values). It does minimal guards (a field is required, a number parses) and renders the server's RFC 7807 `errors[].message` strings against the named fields. Two copies of the validation rules will drift; one will not. |
| **D-5 Request bodies carry exactly five fields** | Every `POST`/`PUT` body is `{name, roastLevel, origin, price, stock}` and nothing else. `id`, `createdAt`, `updatedAt` are **never** sent, and the edit form must **not** spread the `Coffee` object into the payload. The backend runs `quarkus.jackson.fail-on-unknown-properties=true`, so an extra field is a `400` (`coffee-bce.md` §3.4), not a silently ignored one. |
| **D-6 `price` is a JSON number, never a string, never pre-rounded** | The input is parsed to a `number` before being sent. The UI does **not** round a 3-decimal input: it sends what the user typed and renders the server's `400` (`price must have at most 2 decimal places`) as a field error. Silent rounding is forbidden by `coffee-bce.md` §2 and §3.2. |
| **D-7 `roastLevel` is always one of `LIGHT`/`MEDIUM`/`DARK`** | A `<select>` bound to exactly those three upper-case values. Never a free-text field: the enum is case-sensitive on the wire and a lower-case value is a `400`. |
| **D-8 `page` is 0-based on the wire, 1-based on screen** | Requests send the 0-based index the API pins; the UI displays `Page N of M` with `N = page + 1`. Mixing the two is the classic off-by-one here. |
| **D-9 API types live in one file** | `frontend/src/api/types.ts` is the single declaration of `Coffee`, `CoffeeRequest`, `CoffeePage`, `RoastLevel` and `ProblemDetail`, mirroring `coffee-bce.md` §3.2 field for field. Types are **not** generated at build time from `/q/openapi`: that would make the frontend build depend on a running backend. `frontend/README.md` states the intent — "API types come from the OpenAPI contract in one place, not re-declared" — and one hand-written file is that one place. |
| **D-10 Verified by behaviour, queried by role** | Tests mount the real components with only the **network** stubbed (MSW). Queries are by role and accessible name — no `data-testid`, no `container.querySelector`. This is the `frontend/README.md` convention and it is also what makes the UI drivable by a browser agent in QA. |
| **D-11 The accessible surface is part of the contract** | §5.6 pins roles and accessible names. QA drives the real UI by those names; a rename is a contract change, not a refactor. |

---

## 3. Configuration on the box

Verified on 2026-09-28 before this spec was written:

| Check | Result |
|---|---|
| `node -v` | `v26.5.1` |
| `npm -v` | `11.17.0` |
| `pnpm` / `yarn` | **not installed** |
| `curl https://registry.npmjs.org/react` | HTTP 200 — installs work |
| `java -version` | Temurin 21.0.12.1+1 (`/opt/data/toolchains/env.sh`) |
| `./mvnw` | Maven 3.9.16 via the committed wrapper |

Consequence for the implementer: `npm install` resolves and the **lockfile is committed**;
`npm ci` is what CI and QA run. If the registry is unreachable on the day, the card is
**BLOCKED** with the error — never vendor `node_modules` into the repository.

Reference versions resolved from the registry on 2026-09-28 (use as a sanity check, do not
hard-code ranges beyond what npm writes into the lockfile): React 19.3, Vite 8.3,
TypeScript 7.0, Vitest 5.0, `@testing-library/react` 16.3, MSW 2.15,
`@tanstack/react-query` 5.104, jsdom 30.1.

---

## 4. Application structure

```
frontend/
  package.json                 scripts: dev, build, preview, test, typecheck, lint
  package-lock.json            committed
  tsconfig.json, tsconfig.node.json
  vite.config.ts               port, strictPort, dev+preview proxy (D-3)
  index.html                   <div id="root">
  README.md                    updated: install / run / test / build / the proxy contract
  src/
    main.tsx                   React root + QueryClientProvider
    App.tsx                    the single page: form panel + list + pager
    api/
      types.ts                 D-9: the only API type declarations
      client.ts                the only module that calls fetch(); ApiError
      coffees.ts               listCoffees / getCoffee / createCoffee / updateCoffee / deleteCoffee
    components/
      CoffeeForm.tsx           create + edit (mode by props), field errors, submit
      CoffeeTable.tsx          rows, per-row Edit/Delete, price formatting
      DeleteConfirm.tsx        inline confirm step (no window.confirm, no modal)
      ProblemBanner.tsx        role="alert" region for non-field failures
      Pager.tsx                Previous/Next + "Page N of M" + rows-per-page
    test/
      setup.ts                 jest-dom matchers, MSW server lifecycle
      server.ts                MSW node server
      handlers.ts              default happy-path handlers for the 5 endpoints
      renderApp.tsx            render + fresh QueryClient (retry disabled)
    App.test.tsx, components/*.test.tsx, api/client.test.ts
```

The `architecture` of this app is deliberately thin — one page, one data layer, five API
functions — because the interesting complexity is the contract, not the app.

---

## 5. Behaviour specification

### 5.1 Load and list

On mount the page requests `GET /coffees?page=0&size=20` (the API's own default size; the
frontend sends it explicitly so the request is self-describing). Rows render `name`,
`roastLevel`, `origin`, `price` and `stock`. **Ordering is the server's** (`name` asc, then
`id` asc — `coffee-bce.md` §3.1); the frontend must not re-sort, because a client-side sort
of one page is not the same list as the server's.

### 5.2 Create

`New coffee` reveals the form panel in create mode. Submit sends `POST /coffees` with D-5's
five fields. On `201` the form resets, the status region announces the creation, and the
list is refetched (the new row appears without a manual reload). The `Location` response
header is not needed — the created body carries the `id`.

### 5.3 Read (single)

Every row's data comes from the list response. There is **no** extra `GET /coffees/{id}`
call on the happy path — deliberately, because it would be a request per row for data the
list already returned. The client function `getCoffee` exists and is unit-tested (a refresh
of a single coffee after a mutation may use it), but the UI must not fan out N requests.

### 5.4 Update

`Edit <name>` loads that row into the form panel in edit mode (heading
`Edit coffee: <name>`), fields prefilled from the **`Coffee`** row. Submit sends
`PUT /coffees/{id}` with exactly D-5's five fields. On `200` the row shows the new values.

### 5.5 Delete

`Delete <name>` reveals an inline confirmation step: the text
`Delete <name>? This cannot be undone.` plus `Confirm delete` and `Cancel delete`. Only
`Confirm delete` issues `DELETE /coffees/{id}`. On `204` the row disappears; on `404`
(already deleted elsewhere) the problem `detail` is shown and the list is refetched, so the
stale row goes away. `window.confirm` is forbidden — it is untestable and inaccessible.

### 5.6 Required states, and the accessible surface (D-11)

Every list/mutation path handles four states. These are the names QA and the tests use:

| Element | Role / accessible name | Notes |
|---|---|---|
| Page heading | `heading` — `Coffee catalogue` | level 1 |
| Status region | `status` | announces `Coffee created`, `Coffee updated`, `Coffee deleted` |
| Error region | `alert` | carries the problem `detail` for non-field failures |
| New button | `button` — `New coffee` | |
| Form panel heading | `heading` — `New coffee` / `Edit coffee: <name>` | |
| Name input | `textbox` — label `Name` | `aria-invalid` + message via `aria-describedby` |
| Roast select | `combobox` — label `Roast level` | options `LIGHT`, `MEDIUM`, `DARK` |
| Origin input | `textbox` — label `Origin` | as Name |
| Price input | `spinbutton` — label `Price` | as Name |
| Stock input | `spinbutton` — label `Stock` | as Name |
| Submit | `button` — `Save coffee` (create) / `Save changes` (edit) | disabled while the mutation is in flight |
| Cancel | `button` — `Cancel` | |
| Table | `table`, with `<th scope="col">` headers `Name`, `Roast`, `Origin`, `Price`, `Stock` | |
| Row edit | `button` — `Edit <name>` | unique name per row, so queries are unambiguous |
| Row delete | `button` — `Delete <name>` | unique name per row |
| Confirm | `button` — `Confirm delete` / `Cancel delete` | |
| Pager | `button` — `Previous page` / `Next page`, `combobox` — `Rows per page` | disabled at the bounds |
| Page indicator | text `Page N of M`, `M = totalPages` | `Page 1 of 0` renders as `Page 1 of 1` with both buttons disabled when `totalElements` is 0 |
| Empty state | text `No coffees yet.` | shown when `content` is empty |
| Loading state | text `Loading coffees…` | shown while the first list request is pending |
| Field error | text — the server's `errors[].message` | rendered next to its field |

Accessibility is not optional here: semantic `table`/`form`/`label`, every input labelled,
row actions uniquely named, keyboard reachable, visible focus, and an `alert` for failures.

### 5.7 Paging

`Rows per page` offers `10`, `20`, `50`, `100` with `20` selected; changing it resets the
page to `0` (`coffee-bce.md` §3.1 pins `size` 1..100). `Previous page` is disabled on page
0, `Next page` is disabled on the last page. The request sends the 0-based index (D-8).

---

## 6. Error model

`api/client.ts` is the only place `fetch` is called. A non-2xx response becomes an
`ApiError extends Error` carrying `status` and a parsed `ProblemDetail`; a body that is not
`application/problem+json` (a proxy error, an HTML error page) becomes a synthetic problem
detail rather than an exception during parsing — a client that throws while reporting a
failure hides the failure. Network failures are a synthetic problem detail too.

| Server response | UI behaviour |
|---|---|
| `400` with `errors[]` | each `errors[].message` shown against `errors[].field`; form values preserved |
| `400` without `errors[]` (malformed body) | `detail` in the `alert` region; form values preserved |
| `404` | `detail` in the `alert` region; list refetched |
| `409` | `detail` (`Coffee name 'x' already exists`) in the `alert` region; form values preserved |
| `5xx` | `detail` in the `alert` region; **no** retry storm (TanStack Query `retry: false` in tests; a single retry in the app is acceptable) |
| network failure | a fixed message in the `alert` region, not a stack trace |

Nothing from the response is rendered as HTML — plain text only, so a hostile `name` is
escaped by React and never executed.

---

## 7. Testing strategy (card A)

Level: **behaviour via the real components, the real API client, and a stubbed network.**
MSW v2 intercepts at the `fetch` boundary, so `client.ts` and `coffees.ts` are exercised
for real; only the server is fake. `onUnhandledRequest: 'error'` so an unexpected call
fails loudly instead of silently 404ing.

Required test cases — each must fail if its behaviour regresses:

1. list renders a row from a stubbed `200` page response
2. `Loading coffees…` is shown before the response resolves
3. `No coffees yet.` is shown for `content: []`
4. a `500` on the list shows the problem `detail` in the `alert` region
5. create posts **exactly** `{name, roastLevel, origin, price, stock}` (asserted on the
   captured request body — D-5) and the refetched list contains the new row
6. `price` in the captured body is a JSON **number** (asserted with `typeof` — D-6)
7. update puts to `/coffees/{id}` with exactly the five fields and no `id`/timestamps
8. delete issues `DELETE /coffees/{id}` only after `Confirm delete`, and the row is gone
9. `Cancel delete` issues no request at all
10. a `400` with `errors: [{field: "price", message: "price must have at most 2 decimal places"}]`
    renders that message next to the Price field and marks it `aria-invalid`
11. a `409` shows `Coffee name 'x' already exists` and keeps the typed values
12. `Next page` requests `page=1`; `Previous page` is disabled on page 0 and `Next page`
    disabled when `page + 1 >= totalPages`
13. changing `Rows per page` to 50 requests `size=50&page=0`
14. `price` `12.30` from the server renders as `12.30` (2 decimals, D-6)
15. a `404` on delete surfaces the detail and the stale row disappears after the refetch

Test-quality bar (the same one `VERIFICATION.md` applied to the backend): no
`assertNotNull`-only assertions, no snapshot-only tests, no test that asserts the component
tree instead of the behaviour. At least one assertion in the suite is **mutation-checked**
by the author (change the behaviour, watch the test go red, revert) and the result recorded
in the PR body — the QA card repeats it independently.

---

## 8. Acceptance criteria — card A (`frontend-developer`)

| id | Criterion | Verified by |
|---|---|---|
| AC-F1 | `frontend/package.json` declares the D-1 stack and a committed `package-lock.json`; `npm ci` succeeds from the lockfile | `cd frontend && npm ci` |
| AC-F2 | No router and no client-state library in `dependencies` (D-2) | read `package.json` |
| AC-F3 | `src/api/types.ts` mirrors `coffee-bce.md` §3.2 field for field; `fetch` appears in exactly one module | read the two files; `grep -rn "fetch(" src/` |
| AC-F4 | Create sends exactly the five fields (D-5) | test 5, raw `npm run test` output |
| AC-F5 | List sends `page`/`size` and renders the server's order | test 1, 12, 13 |
| AC-F6 | Update sends exactly the five fields to `/coffees/{id}` (D-5) | test 7 |
| AC-F7 | Delete requires the confirmation step and then sends `DELETE` (D-5/§5.5) | tests 8, 9 |
| AC-F8 | Loading, empty, error and success states all exist and are asserted | tests 2, 3, 4 |
| AC-F9 | Server field errors render against the named field (D-4) | test 10 |
| AC-F10 | `409`/`404` show the problem `detail` and preserve form values | tests 11, 15 |
| AC-F11 | Paging: bounds disabling, 0-based request vs 1-based display (D-8), size change resets page | tests 12, 13 |
| AC-F12 | `price` is sent as a JSON number and displayed at 2 decimals (D-6) | tests 6, 14 |
| AC-F13 | The §5.6 accessible surface exists as specified (labels, unique row names, `alert`, `status`, `th scope`) | tests + `grep`; QA re-checks in a browser |
| AC-F14 | `npm run test -- --run`, `npm run build` and `npm run typecheck` all pass | raw output of all three in the PR body |
| AC-F15 | Reachable in a browser: `npm run dev` serves on a documented port, `curl -s -o /dev/null -w '%{http_code}' http://localhost:<port>/` is `200` and the HTML contains the root element; a screenshot of the app loaded against the real backend (at least one coffee) is attached to the card | commands + screenshot; `docs/operations/frontend-runbook.md` written |
| AC-F16 | No file under `backend/` is modified | `git diff --stat origin/main...HEAD` |
| AC-F17 | The mutation check of §7 was performed and recorded | PR body |
| AC-F18 | `frontend/README.md` documents install / run / test / build / the proxy contract | read the file |
| AC-F19 | No secrets, credentials or absolute paths outside the repo are committed; `node_modules/`, `dist/` are gitignored | `git status`, read `.gitignore` |

---

## 9. Acceptance criteria — card B (`qa-tester`)

Authored by the architect, executed by a bot that did **not** implement the app. Report
format mirrors `VERIFICATION.md`: verdict, criterion-by-criterion table, defects with
reproductions, commands run. Output file: `FRONTEND-VERIFICATION.md` at the repository root,
committed on the QA branch.

| id | Criterion | Verified by |
|---|---|---|
| AC-Q1 | `cd frontend && npm ci && npm run test -- --run` passes on the implementation branch; raw counts recorded | command output |
| AC-Q2 | `npm run build` and `npm run typecheck` pass | command output |
| AC-Q3 | QA-authored tests the implementer's suite does not have: (a) a page response with `totalElements: 0`/`totalPages: 0` → empty state and both pager buttons disabled; (b) a `404` on delete; (c) the captured create/edit request body contains **exactly** the five keys (the `fail-on-unknown-properties` trap, D-5); (d) an edit round-trip of a row whose price is `12.30` does not send a string | new tests under `frontend/src`, raw output |
| AC-Q4 | End-to-end against the **real** backend: start `cd backend && ./mvnw quarkus:dev` (port 8080, dev profile) and the Vite dev server, drive create → list → edit → delete **through the UI in a browser**, and capture a screenshot showing a coffee created via the UI | browser session + screenshot attached to the card |
| AC-Q5 | Real-backend error paths through the UI: duplicate name → `409` detail visible; a 3-decimal price → `400` field message visible; a stale delete (row already removed) → `404` handled | screenshots or recorded DOM text |
| AC-Q6 | Keyboard-only create flow works (tab to each field, submit) and the browser console shows no errors during the E2E run | browser session notes |
| AC-Q7 | The frontend branch modifies nothing under `backend/` (`git diff --stat origin/main...HEAD`) and no request body carried `id`/`createdAt`/`updatedAt` | `git diff --stat`, recorded request log |
| AC-Q8 | A mutation check independent of the author's: break one behaviour in the app (or in a test's expectation) and show the relevant test go red, then revert | raw output |
| AC-Q9 | Every criterion above is reported pass/fail with the exact command run; defects are listed with reproduction steps | `FRONTEND-VERIFICATION.md` |
| AC-Q10 | If the browser tool or the npm registry is unavailable on the box, the criteria that could not be executed are reported **BLOCKED with the reason** — never marked pass | verdict + reason |

Notes for QA that save an hour:

- The dev-profile H2 database is **file-backed and persists** (`backend/target/h2/`). Use a
  unique name prefix per run (e.g. `QA-<timestamp>-…`) and delete what you create.
- Do **not** run the jar produced by `./mvnw verify` on the dev profile — it is built for
  the prod profile and fails to start with the H2 URL (`VERIFICATION.md` O2). Use
  `./mvnw quarkus:dev`, or rebuild with `-Dquarkus.profile=dev`.
- The frontend must be reached at `http://localhost:<vite-port>/` — the proxy is what makes
  `/coffees` same-origin. Opening the built `index.html` from the filesystem will not work
  and is not a defect.

---

## 10. Decisions deliberately deferred (not defects)

- **Authentication / authorisation** — the API has none; adding it is a backend card with
  `infosec` review, not a frontend card.
- **Deployment and hosting** (static bundle behind nginx, container image, CI pipeline) —
  no deployment topology exists in this repo for either tier; "accessible via browser" is
  satisfied by the dev/preview server. The first real deployment is where ADR-003's revisit
  trigger fires.
- **Bundle-into-Quarkus** (`quarkus-quinoa` or static resources served by the service) —
  rejected in ADR-003 for v1; revisit when a single deployable is actually wanted.
- **Optimistic updates, real-time refresh, ETag/`If-Match`** — the API has no version field
  (`coffee-bce.md` §7); a client cannot do conditional writes against a contract that has
  no conditional-write vocabulary.
- **Search / filter / sort controls** — the API exposes no such parameters (§1 non-goals).
- **i18n, theming, offline/PWA, telemetry, analytics** — not asked for; each is its own card.
- **Generated API types from `/q/openapi`** — deferred with a reason (D-9): the build must
  not depend on a running backend. Revisit if the contract starts changing often enough
  that hand-syncing `types.ts` becomes a source of defects — at which point the generator
  runs as a dev-time task against a checked-in `openapi.json`, not against a live server.

---

## 11. Skills applied

- `factory-decomposition` — plan gate, decisions settled before fan-out, children linked to
  this card, one card / one branch / one PR, assignee-by-role.
- `architecture-decision-records` — ADR-003 (frontend↔backend integration), format and
  one-decision-per-file rules.
- `java-cloud-native-stack` — README says the frontend meets the backend at an API contract
  and never at shared code; the proxy decision keeps the backend release frozen.
- `frontend-web-app` / `test-strategy` — named in the implementer and QA cards as the skills
  those bots must follow (accessibility, four states, behaviour-level tests that bite).
