# Frontend runbook — running the Coffee API and the UI together

Audience: anyone (human or bot) who needs the Coffee catalogue UI reachable in a browser on
this box. Implements the operating notes of `docs/architecture/coffee-frontend.md` §9 and
the accepted consequence of `docs/adr/ADR-003-frontend-integration-without-cors.md`.

## The two processes

| Tier | Command | URL | Port |
|---|---|---|---|
| API (Quarkus, dev profile) | `cd backend && ./mvnw quarkus:dev` | `http://localhost:8080/coffees` | 8080 (fixed) |
| UI (Vite dev server) | `cd frontend && npm run dev` | `http://localhost:5173/` | 5173 (strict) |
| UI (built bundle) | `cd frontend && npm run build && npm run preview` | `http://localhost:4173/` | 4173 (strict) |

Start the API first: the first `./mvnw quarkus:dev` resolves Maven dependencies and takes
longer than later ones. The dev profile activates automatically and prints
`Listening on: http://localhost:8080`.

If `java`/`mvn` are not on `PATH` in your session:

```bash
. /opt/data/toolchains/env.sh
```

Check both tiers in one shot:

```bash
curl -s -o /dev/null -w 'api %{http_code}\n' http://localhost:8080/coffees
curl -s -o /dev/null -w 'ui  %{http_code}\n' http://localhost:5173/
```

## The proxy contract (why the UI is same-origin)

The API serves no CORS headers and is frozen (`coffee-bce.md` §4.5). `frontend/vite.config.ts`
proxies `/coffees` to `http://localhost:8080` on **both** the dev server (5173) and the
preview server (4173), so the browser sees one origin. The client in `src/api/client.ts`
therefore uses relative paths only.

```bash
# the proxy is what makes this work — a direct call to 5173, not 8080
curl -s http://localhost:5173/coffees
```

Override the backend target (a second API instance, a container, a remote dev box):

```bash
VITE_BACKEND_URL=http://localhost:9090 npm run dev
```

Three things that look like defects and are not:

- **Opening `frontend/dist/index.html` from the filesystem does not work.** There is no
  server and therefore no proxy: the browser tries to call `/coffees` on a `file://` origin.
  Serve the bundle with `npm run preview` instead.
- **The UI shows "Cannot reach the server. Check that the API is running, then try again."**
  That is the client's network-failure message, produced when the proxy target is down. It
  is a real state of the app (§6), not a stack trace and not a crash.
- **The first API start is slow.** Maven resolution dominates; subsequent starts are a few
  seconds.

## Dev-profile data persists — use a unique name prefix

`%dev` uses a file-backed H2 database at `backend/target/h2/`, so rows survive a restart
(deliberate, `coffee-bce.md` §4.5). When you run a check against a live backend:

- prefix names with something unique per run, e.g. `RUN-20260928A-Ethiopia`;
- delete what you created when you are done;
- do not assume the catalogue is empty.

Reset the whole dev database by stopping the API and deleting `backend/target/h2/`.

## The dev-profile packaging footgun

**Do not run the jar produced by `./mvnw verify` on the dev profile.** That artefact is
built for the **prod** profile, whose datasource URL is `${DB_URL}` (PostgreSQL), so it
fails to start against the H2 URL. This is recorded in `VERIFICATION.md` (observation O2).

Use `./mvnw quarkus:dev`, or rebuild explicitly for dev:

```bash
./mvnw package -Dquarkus.profile=dev
```

## Tests without a backend

The frontend test suite needs **no** running API — MSW v2 stubs the network at the `fetch`
boundary:

```bash
cd frontend && npm ci && npm run test -- --run
```

`onUnhandledRequest: 'error'` is set, so a test that reaches for an endpoint with no handler
fails loudly rather than silently 404ing. Anything the suite needs at the HTTP level must be
expressed as an MSW handler in `src/test/handlers.ts` or a `server.use(...)` override.

## Deploying this later (out of scope for v1)

There is no deployment topology for this repository yet. When one appears, the proxy
decision has to be revisited: the bundle must be served from the same origin as the API
(reverse proxy or bundled into the service), or CORS becomes a required, `infosec`-reviewed
backend decision. See ADR-003's "Revisit when".
