# ADR-003: The frontend reaches the backend through its own dev-server proxy, not through CORS

Status: accepted
Date: 2026-09-28
Deciders: architect (card `t_be0fd4f5`)
Governs: `frontend/vite.config.ts`, `frontend/src/api/client.ts`

## Context

The Coffee API is served by the Quarkus service on port 8080 with base path `/coffees`
(`backend/src/main/resources/application.properties`, `quarkus.http.root-path=/`). It
configures **no** CORS (`coffee-bce.md` §4.5: "No CORS configuration, no auth, no rate
limiting in v1. If the frontend card needs CORS, it is a new decision on a new card."

That backend is frozen and independently verified: `VERIFICATION.md` records VERDICT: PASS
over 75 tests against the contract in `coffee-bce.md` §9.3. Card `t_be0fd4f5` asks for a
React frontend that performs the CRUD operations "exposed by the backend" and explicitly
scopes backend tests out; `AGENTS.md` states the two tiers "meet at an API contract, never
at shared code".

The forces at play:

- A browser application served from one origin and calling another is blocked by the
  same-origin policy unless the API sends `Access-Control-Allow-Origin`. Without a
  decision, the first `fetch` from the UI simply fails.
- The frontend must be reachable in a browser on this box today (the card's DoD), where
  there is no ingress, no reverse proxy and no deployment topology at all.
- Two different bot profiles own the two tiers. A change to the backend to make the
  frontend work would put backend files in a frontend branch and re-open a component that
  has already passed verification.
- `quarkus.http.cors.origins` is a production-visible policy, not a dev-server tweak: it
  widens which origins may call the API in every profile, and `coffee-bce.md` §7 lists
  auth and CORS together as things needing `infosec` review.

## Decision

We will serve the frontend from its own Vite dev/preview server and **proxy `/coffees` to
`http://localhost:8080` from that server** (`vite.config.ts`, both `server.proxy` and
`preview.proxy`, target overridable with the `VITE_BACKEND_URL` env var). The API client
calls **same-origin relative paths** (`/coffees`, `/coffees/{id}`). The backend is not
modified by any frontend card, and no CORS header is configured anywhere.

Same-origin is bought on the frontend side of the boundary, which is the side that needs
it, and it is bought in development configuration that has no production effect.

## Alternatives considered

1. **Enable CORS on the backend** (`quarkus.http.cors=true` +
   `quarkus.http.cors.origins=http://localhost:5173`). Rejected: it modifies a verified,
   frozen component to satisfy a development-time need, it puts a backend file in a
   frontend PR (violating one-card/one-branch), and it is a security-relevant surface
   change on the production profile that `coffee-bce.md` §7 defers to a card with `infosec`
   review. That is a lot of blast radius for an origin that will not exist in production.
2. **Absolute backend URL in the client** (`http://localhost:8080/coffees`) plus the CORS
   change above. Same objection as (1), plus a hard-coded host and port in application
   code that would have to be undone before any deployment.
3. **Bundle the frontend into the Quarkus service** (`quarkus-quinoa`, or the built assets
   copied under `src/main/resources/META-INF/resources`). This is the only option that
   makes the two tiers genuinely same-origin *in production* without a proxy. Rejected for
   v1: it couples the frontend release cycle to the backend build and reverses the two
   cards into one branch — the separation `AGENTS.md` and `README.md` both state as the
   point of the layout. It is the right answer the day a single deployable is wanted; it is
   not the right answer to make a dev server work.
4. **A reverse proxy in front of both** (nginx/Caddy serving the bundle and forwarding
   `/coffees`). The correct production shape, and rejected for v1 only because no
   deployment exists: this card has no server, no DNS name, and no artifact to deploy.
   Adding infrastructure to satisfy a development need is infrastructure without a
   consumer.

## Consequences

Easier: the backend release is untouched and its verification stays valid; the frontend and
backend branches stay independent; QA can start both tiers with two commands and no
configuration; there is one place (`vite.config.ts`) to point the app at a different
backend for the E2E run.

Harder, and accepted: **the frontend is same-origin only when it is served through its own
proxy.** Opening `dist/index.html` from the filesystem does not work, and a real deployment
that serves the bundle from a different origin than the API will fail until CORS or a
reverse proxy is added — this is a property to document (`docs/operations/frontend-runbook.md`),
not a bug to discover later. The proxy also means the dev server must run for a browser to
reach the API at all, so "the frontend works" and "the proxy is configured" are the same
statement, which the runbook and the acceptance criteria both state explicitly.

## Revisit when

The frontend is deployed anywhere other than behind a proxy that also fronts the API — a
static host, a CDN, a separate container — or when a second tier (a mobile client, a
partner integration) needs to call the API from an origin we do not control. At that point
CORS becomes a required, `infosec`-reviewed decision (an allow-list of origins, not `*`),
or the reverse proxy from alternative 4 is adopted, and this ADR is superseded.
