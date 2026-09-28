# Quarkus AI Hermes Software Factory

A monorepo worked by a team of Hermes bots on a Kanban board, with a human reviewing
every pull request.

| Path | What lives there |
|---|---|
| `backend/` | Quarkus service(s), Java |
| `frontend/` | React app |
| `docs/workflow.md` | The end-to-end flow, including where humans gate it |
| `AGENTS.md` | The rules every bot follows — read this first |

## How work happens

A request arrives as a Kanban card, is decomposed into a linked task graph by the
`architect`, **approved by Angel**, implemented on a branch by `backend-developer` /
`frontend-developer`, verified by `qa-tester`, and delivered as a pull request.

**No bot merges. Angel merges.**

---

## Running it locally (dev profile)

Two processes, two terminals. The API is fixed on **8080**; the UI talks to it through
its own Vite proxy, so the backend needs no CORS configuration
([ADR-006](docs/adr/ADR-006-frontend-integration-without-cors.md)). Full operating notes
are in [docs/operations/frontend-runbook.md](docs/operations/frontend-runbook.md).

### Prerequisites

| | Version | Notes |
|---|---|---|
| JDK | **17 or newer** | `./mvnw` uses the pinned Maven 3.9.16; it still needs a JDK on `JAVA_HOME` |
| Node.js | **22 (LTS)** | matches CI |
| npm | bundled with Node | `npm ci` installs from the committed lockfile |

Nothing else is required: the `dev` profile runs against a **file-backed H2 database**,
so there is no PostgreSQL and no Docker to start.

On the factory box itself, JDK and Maven are user-level and live outside `PATH` in a
fresh shell — see [docs/operations/build-toolchain.md](docs/operations/build-toolchain.md):

```bash
. /opt/data/toolchains/env.sh
```

### Terminal 1 — the API (Quarkus)

```bash
cd backend
./mvnw quarkus:dev
```

The `dev` profile activates automatically and prints:

```
Listening on: http://localhost:8080
Profile dev activated. Live Coding activated.
```

The first start resolves Maven dependencies and takes noticeably longer than later
ones. Live Coding is on: saving a Java file recompiles and restarts the app without a
fresh `mvnw` run.

Check it answers:

```bash
curl -s http://localhost:8080/coffees
# {"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

The OpenAPI contract is served at http://localhost:8080/q/openapi (Swagger UI at
http://localhost:8080/q/swagger-ui).

### Terminal 2 — the UI (React + Vite)

Start the API **first**; the UI proxy targets `http://localhost:8080`.

```bash
cd frontend
npm ci        # once, or whenever package-lock.json changes
npm run dev   # http://localhost:5173 (strict port)
```

Open http://localhost:5173. The same request now also works through the proxy, which is
what the browser actually uses:

```bash
curl -s http://localhost:5173/coffees
# {"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

To point the UI at a backend on another port:

```bash
VITE_BACKEND_URL=http://localhost:9090 npm run dev
```

### Both tiers in one shot

```bash
curl -s -o /dev/null -w 'api %{http_code}\n' http://localhost:8080/coffees
curl -s -o /dev/null -w 'ui  %{http_code}\n' http://localhost:5173/
```

`api 200` / `ui 200` means the stack is up.

### Tests

Neither suite needs a running API — the backend tests use in-memory H2 and the frontend
tests stub the network with MSW.

```bash
cd backend  && ./mvnw verify               # unit + integration tests, then package
cd frontend && npm run typecheck && npm test -- --run
```

### Things that look like defects and are not

- **Dev data persists.** `%dev` uses H2 at `backend/target/h2/`, so rows survive a
  restart. Stop the API and delete `backend/target/h2/` to reset the catalogue.
- **Do not run the jar from `./mvnw verify` on the `dev` profile.** That artefact is
  built for `prod`, whose datasource is PostgreSQL via `${DB_URL}`, so it will not start.
  Use `./mvnw quarkus:dev`, or `./mvnw package -Dquarkus.profile=dev`.
- **Opening `frontend/dist/index.html` from the filesystem does not work.** There is no
  server and therefore no proxy. Serve the bundle with `npm run build && npm run preview`
  (http://localhost:4173) instead.
- **"Cannot reach the server. Check that the API is running, then try again."** in the UI
  is the client's network-failure state, produced when the proxy target is down — not a
  crash.
