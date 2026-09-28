# FRONTEND-VERIFICATION.md — independent verification of the React coffee CRUD frontend

Card: `t_62a65b0b` — *Independently verify the React coffee CRUD frontend*
Verifier: `qa-tester` (independent — did not author the application; authored every test below)
Artifact under test: `origin/main` @ commit `0e02b46` — the frontend as merged by
PR #10 (merge commit `7e666b5`), which is the state the implementation card
`t_5f9dc91a` left behind
Report committed on branch: `task/t_62a65b0b-frontend-verify`
Criteria: `docs/architecture/coffee-frontend.md` §9 (AC-Q1 … AC-Q10); behaviour from §5/§6;
accessible surface from §5.6; the consumed contract from `coffee-bce.md` §3
Environment: Node v26.5.1, npm 11.17.0, Vitest 5.0.2, Vite 8.3.1, jsdom 30.1.1, MSW 2.15.0,
Temurin 21.0.12.1+1, Quarkus 3.33.3 (dev profile, file-backed H2 2.4.240), uid 10000,
no Docker daemon

## VERDICT

```
VERDICT: PASS
Commit:  0e02b46   Branch: main (frontend merged from PR #10, merge commit 7e666b5)
Env:     Node v26.5.1 / npm 11.17.0 / Vitest 5.0.2 / Vite 8.3.1; Temurin 21.0.12.1+1;
         Quarkus 3.33.3 dev profile on file-backed H2; uid 10000, no Docker daemon
What was tested: AC-Q1 … AC-Q10, all pass; 4 verifier-authored tests added (30/30 green)
Evidence: npm ci 155 packages 0 vulnerabilities; 26/26 author tests; 30/30 with this card's
          4 tests; typecheck exit 0; vite build OK; real-backend browser E2E
          create→list→edit→delete with screenshot; real-backend 409/400/404 paths;
          keyboard-only create flow; console recorder empty; 2 independent mutations
          shown red then reverted
Not covered: real-backend paging beyond one page (server-side pagination was verified on card
          t_c41f5e3e, PASS); security posture (infosec's call); PostgreSQL profile
Qualification: one minor defect filed (D1) — a fail-silent error path that today's
          D-5-compliant request bodies cannot reach. No acceptance criterion is unmet.
```

Every criterion was executed. Nothing is marked pass on inspection alone.

---

## 1. Criterion-by-criterion result

| id | Criterion | Result | Evidence |
|---|---|---|---|
| AC-Q1 | `cd frontend && npm ci && npm run test -- --run` passes; raw counts recorded | **PASS** | §2.1 — `npm ci`: 155 packages, 0 vulnerabilities; `Test Files 3 passed (3) / Tests 26 passed (26)` |
| AC-Q2 | `npm run build` and `npm run typecheck` pass | **PASS** | §2.2 — `tsc --noEmit` exit 0; `vite build` 68 modules, built in 584 ms |
| AC-Q3 | Verifier-authored tests the author's suite does not have: (a) empty page, (b) delete 404, (c) exactly five body keys, (d) 12.30 edit round-trip stays numeric | **PASS** | §3 — 4 tests added in `frontend/src/qa/qa-verification.test.tsx`, all green; overlap with author tests stated honestly in §3.1 |
| AC-Q4 | Real-backend browser E2E: create → list → edit → delete, screenshot of a coffee created via the UI | **PASS** | §4 — full round trip recorded, request log quoted, screenshot attached to the card |
| AC-Q5 | Real-backend error paths: duplicate → 409 detail; 3-decimal price → 400 field message; stale delete → 404 handled | **PASS** | §5 — all three reproduced against Quarkus dev on :8080 |
| AC-Q6 | Keyboard-only create flow works; browser console shows no errors | **PASS** | §6 — Tab/Enter/char key events only; `window.__qaConsole === []` |
| AC-Q7 | `git diff --stat origin/main...HEAD` shows no file under `backend/`; no request body carried `id`/`createdAt`/`updatedAt` | **PASS** | §7 — no `backend/` path in the diff; recorded request log contains no such key |
| AC-Q8 | Independent mutation check: break one behaviour, watch the matching test go red, revert | **PASS** | §8 — two mutations, both red on the named guarantee, both reverted (`git diff` clean) |
| AC-Q9 | Every criterion reported pass/fail with the exact command; defects with reproductions | **PASS** | this document; §9 defects; §11 complete command list |
| AC-Q10 | If the browser tool or npm registry were unavailable, mark those criteria BLOCKED, never pass | **N/A → PASS** | both available: npm registry reachable (`npm ci` succeeded), browser driven for real (§4–§6); no criterion needed BLOCKED |

### Tests added by this card

`frontend/src/qa/qa-verification.test.tsx` — 4 tests, one per AC-Q3 sub-criterion.
They are written from the specification, share the harness (MSW server + handlers) with the
author's suite but no assertion, and every one asserts a concrete value.

---

## 2. AC-Q1 and AC-Q2 — the suite, the build, the typecheck

### 2.1 AC-Q1 — `npm ci` + `npm run test -- --run`

```
$ cd frontend && npm ci
added 155 packages, and audited 156 packages in 17s
31 packages are looking for funding
found 0 vulnerabilities
npm warn allow-scripts 1 package has install scripts not yet covered by allowScripts:
npm warn allow-scripts   msw@2.15.0 (postinstall: ...)

$ npm run test -- --run
 RUN  v5.0.2 /opt/data/repos/quarkus-ai-hermes-software-factory/.worktrees/t_62a65b0b/frontend

 Test Files  3 passed (3)
      Tests  26 passed (26)
   Duration  9.53s
```

The `msw` `allow-scripts` warning is the one spec §3.1 predicted; it is harmless (Node-mode
MSW does not use the browser service worker) and is **not** counted as a defect.

**AC-Q1: PASS.** 26 tests / 3 files, 0 failures, from a clean `npm ci`.

### 2.2 AC-Q2 — build and typecheck

```
$ npm run typecheck
> tsc --noEmit
typecheck exit=0

$ npm run build
vite v8.3.1 building client environment for production...
transforming...
✓ 68 modules transformed.
dist/index.html                   0.40 kB │ gzip:  0.27 kB
dist/assets/index-BMOzEQsf.css    0.57 kB │ gzip:  0.36 kB
dist/assets/index-YPsqZuBM.js   263.93 kB │ gzip: 81.27 kB
✓ built in 584ms
build exit=0
```

**AC-Q2: PASS.**

---

## 3. AC-Q3 — tests authored by the verifier

File: `frontend/src/qa/qa-verification.test.tsx`. Written from spec §5/§6 and `coffee-bce.md`
§3 before reading the implementation's assertions.

```
$ npx vitest --run src/qa/qa-verification.test.tsx

 Test Files  1 passed (1)
      Tests  4 passed (4)
   Duration  2.63s
```

| sub-criterion | test | what it asserts |
|---|---|---|
| AC-Q3a | *shows the empty state and disables both pager buttons* | `totalElements: 0`/`totalPages: 0` → `No coffees yet.`, **both** `Previous page` and `Next page` disabled, `Page 1 of 1` (never `Page 1 of 0`), no `table` in the DOM, request `GET /coffees?page=0&size=20` |
| AC-Q3b | *surfaces the problem detail and drops the stale row after the refetch* | `DELETE` → 404 → the problem `detail` appears in `role="alert"`, the stale row disappears, the `DELETE` URL is `/coffees/{id}`, **and a refetch was actually issued** (GET count increases) |
| AC-Q3c | *POST /coffees and PUT /coffees/{id} send … and nothing else* | `Object.keys(body).sort()` is exactly `['name','origin','price','roastLevel','stock']`, length 5, and `id`/`createdAt`/`updatedAt` are absent — on **both** POST and PUT |
| AC-Q3d | *sends price as a JSON number, never a string* | edit round trip of a `12.30` row: `typeof body.price === 'number'`, `body.price === 12.3`, and the serialised JSON contains no `"price":"` |

Two bugs in my own first draft were fixed before the run above (an MSW override that
returned an empty page from the first request, and a refetch counter that only counted the
default handler's requests). They are bugs in the verifier's tests, not in the app, and are
recorded here so the red runs in this card's history are not mistaken for app failures.

### 3.1 Honest note on AC-Q3's novelty claim

The card says these tests are ones "the implementer's suite does not have". That is true of
the **test code** (authored independently) but not fully of the **coverage**:

- AC-Q3a overlaps the author's test 3 (same scenario, same assertions plus the "no table"
  and request-shape checks).
- AC-Q3b overlaps the author's test 15; mine adds the explicit refetch assertion.
- AC-Q3c overlaps the author's tests 5 and 7, which already assert the exact key set.
- **AC-Q3d has no author counterpart** — the author asserts `typeof price === 'number'` for
  create (test 6) and two-decimal display (test 14), but never that an *edit round trip of a
  `12.30` row* stays numeric. That is the genuinely novel case.

This is recorded rather than glossed: three of the four sub-criteria are independent
re-derivations, one is new coverage. The criterion is met as written (the tests are authored
by QA and were absent from the implementer's suite as code); the overlap is a note on the
criterion's framing, not a failure.

**AC-Q3: PASS.**

---

## 4. AC-Q4 — end-to-end create → list → edit → delete against the real backend

Both servers run for real:

```
$ cd backend && ./mvnw quarkus:dev
2026-09-28 18:13:51,801 INFO  [io.quarkus] (Quarkus Main Thread) coffee-shop 1.0.0-SNAPSHOT
  on JVM (powered by Quarkus 3.33.3) started in 20.127s. Listening on: http://localhost:8080
2026-09-28 18:13:51,802 INFO  [io.quarkus] (Quarkus Main Thread) Profile dev activated.

$ cd frontend && npm run dev
  VITE v8.3.1  ready in 1017 ms
  ➜  Local:   http://localhost:5173/
```

Proxy and reachability, before touching the UI:

```
$ curl -s -o /dev/null -w 'backend /coffees -> %{http_code}\n' http://localhost:8080/coffees
backend /coffees -> 200
$ curl -s -o /dev/null -w 'vite proxy /coffees -> %{http_code}\n' http://localhost:5173/coffees
vite proxy /coffees -> 200
$ curl -s -o /dev/null -w 'vite / -> %{http_code}\n' http://localhost:5173/
vite / -> 200
```

The UI was driven through the app's own accessible surface (§5.6), by role and accessible
name, in a real browser. A recorder installed with `Page.addScriptToEvaluateOnNewDocument`
wrapped `window.fetch`, so the request log below is what the page **actually put on the
wire**, not what a mock saw.

Create (typed values → UI → wire):

```
values: {"n":"QA-1790619374-Alpha","o":"Colombia","p":"9.75","s":"7","r":"DARK"}
--- page after create ---
Coffee catalogue / Coffee created / New coffee
Name  Roast  Origin    Price  Stock  Actions
QA-1790619374-Alpha  DARK  Colombia  9.75  7   [Edit …] [Delete …]
Previous page  Page 1 of 1  Next page  Rows per page 20     (both pager buttons disabled)
```

Edit and delete:

```
heading: Edit coffee: QA-1790619374-Alpha
prefilled: {"n":"QA-1790619374-Alpha","p":"9.75","s":"7","r":"DARK"}
price set: 12.30   stock set: 9
--- page after edit ---   QA-1790619374-Alpha  DARK  Colombia  12.30  9   (status: Coffee updated)
--- confirm step visible ---
Delete QA-1790619374-Alpha? This cannot be undone.   [Confirm delete] [Cancel delete]
--- page after delete ---  No coffees yet.        (status: Coffee deleted)
```

The complete recorded wire log for that session:

```
GET  /coffees?page=0&size=20                       body=null
POST /coffees  body={"name":"QA-1790619374-Alpha","roastLevel":"DARK","origin":"Colombia","price":9.75,"stock":7}
GET  /coffees?page=0&size=20                       body=null
PUT  /coffees/43806358-a505-4dca-ad7a-0a9b83609b67
     body={"name":"QA-1790619374-Alpha","roastLevel":"DARK","origin":"Colombia","price":12.3,"stock":9}
GET  /coffees?page=0&size=20                       body=null
DELETE /coffees/43806358-a505-4dca-ad7a-0a9b83609b67   body=null
GET  /coffees?page=0&size=20                       body=null
```

Observed, and worth noting against §5.3: **no `GET /coffees/{id}` was issued** — the list is
the only read on the happy path, exactly as specified.

A screenshot showing coffees created through the UI is attached to this card
(`qa-e2e-created.png`: the catalogue with `QA-…-Bravo` and `QA-…-Foxtrot`, both created in
the browser, status `Coffee created`).

**AC-Q4: PASS.**

---

## 5. AC-Q5 — real-backend error paths through the UI

### 5.1 Duplicate name → 409

```
$ click "New coffee", fill the name already present, "Save coffee"
alert: Coffee name 'QA-1790619374-Bravo' already exists
form values preserved: {"n":"QA-1790619374-Bravo","o":"Brazil"}
```

The `409` `detail` is visible in the `role="alert"` region and the typed values survive.

### 5.2 3-decimal price → 400 field message

```
price field error: {"message":"price must have at most 2 decimal places",
                    "ariaInvalid":"true","ariaDescribedby":"coffee-price-error"}
--- visible body ---
Priceprice must have at most 2 decimal places
```

The server's message is rendered next to the **Price** field, the field is `aria-invalid`
and points at the message with `aria-describedby`. The client did not round the input
(D-4/D-6 hold).

### 5.3 Stale delete → 404

A row was created through the UI and then removed out of band (a raw `DELETE` from the page,
i.e. a second client — the app's cache was deliberately not informed):

```
row still listed by the API: {... "id":"49c18c51-…", "name":"QA-1790619374-Charlie" ...}
out-of-band DELETE status: 204
API GET after: 404
stale row still on screen: True            <- the UI is now showing a stale row
--- then: click "Delete QA-…-Charlie", "Confirm delete" ---
alert: Coffee 49c18c51-ef61-41e3-8d02-e5ce72ec8833 not found
stale row gone: True
```

### 5.4 Two further adversarial cases not in the author's suite

**(a) a `400` whose `errors[].field` is `name`** (101-character name) — proves the
field-error mapping is generic, not hard-coded to `price`:

```
field error span: name size must be between 1 and 100
aria-invalid: true
role=alert elements: ["name size must be between 1 and 100"]
problem banner present outside the form: False
```

Exactly one `role="alert"` element, and it is the field error — the page-level banner is
correctly suppressed for a body that carries `errors[]` (§6).

**(b) an edit-time `409`** — the conflict path on `PUT`:

```
role=alert elements: ["Coffee name 'QA-1790619374-Golf' already exists"]
form still open, name preserved: QA-1790619374-Golf
```

**AC-Q5: PASS.**

---

## 6. AC-Q6 — keyboard-only create flow, and console cleanliness

No `fill_input`, no programmatic `element.click()`: focus was moved with real `Tab`
key events and the form activated with `Enter` (`Input.dispatchKeyEvent`).

```
=== tab 1 (expect New coffee) === {"tag":"BUTTON","id":"","text":"New coffee"}
form heading after Enter: New coffee

keyboard trail:
   start  -> {"tag":"BUTTON","id":"","text":"New coffee"}
   name   -> {"tag":"INPUT","id":"coffee-name","text":""}
   roast  -> {"tag":"SELECT","id":"coffee-roast-level","text":"LIGHTMEDIUMDARK"}
   origin -> {"tag":"INPUT","id":"coffee-origin","text":""}
   price  -> {"tag":"INPUT","id":"coffee-price","text":""}
   stock  -> {"tag":"INPUT","id":"coffee-stock","text":""}
   submit -> {"tag":"BUTTON","id":"","text":"Save coffee"}

values: {"n":"QA-1790619374-Foxtrot","o":"Tanzania","p":"7.25","s":"4","r":"MEDIUM"}
=== after keyboard submit ===
QA-1790619374-Foxtrot  MEDIUM  Tanzania  7.25  4
status: Coffee created
```

The tab order is the visual order, the roast select is reachable and changeable with
`ArrowDown`, and the submit button is reachable and fires on `Enter`.

Console recorder for the whole E2E session (`console.error`, `console.warn`, `window.onerror`,
`unhandledrejection`, all captured in page context):

```
=== console recorder ===
[]
```

**AC-Q6: PASS** — no console errors, warnings or unhandled rejections across every E2E step
(create, edit, delete, 409, 400, 404, keyboard flow).

---

## 7. AC-Q7 — no `backend/` change, no forbidden request body

```
$ git fetch origin main -q && git diff --stat origin/main...HEAD
(empty — no tracked file differs from main before this card's own files are added)

$ git diff --name-only origin/main...HEAD | grep '^backend/'
NONE
```

The only difference this card introduces is its own verification artefacts (this report and
the 4 tests under `frontend/src/qa/`); after committing, `git diff --stat origin/main...HEAD`
lists exactly those and still **no file under `backend/`**.

Request bodies — the union of the recorded wire log (§4, §5, §6 plus the out-of-band
cleanup) was scanned for the forbidden keys:

```
bodies carrying id/createdAt/updatedAt: []
```

Every `POST`/`PUT` body observed on the wire carried exactly `name, roastLevel, origin,
price, stock`. `id`, `createdAt` and `updatedAt` appear only in **server responses**, never
in a request body.

**AC-Q7: PASS.**

---

## 8. AC-Q8 — independent mutation check

Two mutations were applied to the production sources, each aimed at the specific guarantee a
named test claims, then reverted. `git diff` was confirmed clean afterwards.

### Mutation 1 — empty-catalogue pager bound neutralised

`frontend/src/App.tsx`, §5.7 bound:

```
- nextDisabled={data.totalPages === 0 || data.page + 1 >= data.totalPages}
+ nextDisabled={data.totalPages > 0 && data.page + 1 >= data.totalPages}
```

```
FAIL  src/qa/qa-verification.test.tsx > AC-Q3a — an empty page (totalElements 0 / totalPages 0)
      > shows the empty state and disables both pager buttons
Error: expect(element).toBeDisabled()
Received element is not disabled:
  <button
  type="button"
/>
   ❯ src/qa/qa-verification.test.tsx:51:63

FAIL  src/App.test.tsx > list, loading, empty, error > 3. shows the empty state with both
      pager buttons disabled for an empty page
 Test Files  2 failed (2)
      Tests  2 failed | 17 passed (19)
```

The verifier's AC-Q3a and the author's test 3 both go red. Reverted.

### Mutation 2 — an extra key on the wire (the `fail-on-unknown-properties` trap, D-5)

`frontend/src/App.tsx`:

```
- mutationFn: createCoffee,
+ mutationFn: (request: CoffeeRequest) =>
+   createCoffee({ ...request, id: 'mutated' } as CoffeeRequest),
```

```
FAIL  src/qa/qa-verification.test.tsx > AC-Q3c — the wire body carries exactly the five
      contract keys > POST /coffees and PUT /coffees/{id} send name, roastLevel, origin,
      price, stock and nothing else
AssertionError: expected [ 'id', 'name', 'origin', …(3) ] to deeply equal
                [ 'name', 'origin', 'price', …(2) ]
- Expected
+ Received
+   "id",

FAIL  src/App.test.tsx > create > 5. posts exactly the five request fields and shows the
      refetched row   (same assertion)
 Test Files  2 failed (2)
      Tests  2 failed | 17 passed (19)
```

The cast `as CoffeeRequest` keeps the mutation type-valid, so the compiler does not catch it —
the test does. That is the point of the check. Reverted; `git diff -- frontend/src/App.tsx` is
empty.

**AC-Q8: PASS** — both mutations were caught by the assertion that claims the guarantee, not
by a neighbouring one.

---

## 9. Defects

### D1 — a `400` whose `errors[]` name no form field renders nothing at all

```
Defect:   A 400 whose errors[] entries name fields the form does not have is swallowed
          silently: no field message and no alert banner reach the user.
Expected: §6 maps "400 with errors[]" to "each errors[].message shown against errors[].field".
          When a field cannot be matched, the run's guidance is that a failure must not be
          hidden — the client's own design note (§6) says a client "that throws while
          reporting a failure hides the failure". Here the failure is hidden by omission
          instead: the user presses Save and the screen does not change.
Actual:   App.tsx suppresses the page-level banner whenever problem.errors is non-empty
          (alertProblem = ... errors.length === 0), while CoffeeForm renders an error only
          for the five known field names. An errors[].field outside those five therefore
          produces zero visible output.
Reproduce (component level; the app and its real client are exercised, only the network is
        stubbed with MSW, as the whole suite does):
  1. write a test that stubs POST /coffees with
        problem(400, 'Malformed request body', [{field: 'id', message: "unknown field 'id'"}])
     (that exact body is the one spec §3.1 records from the real backend for an extra key)
  2. mount <App/>, open the create form, fill valid values, click "Save coffee"
  3. observe the DOM
Evidence: raw run of the temporary harness:
  REPRO-D2 role=alert elements = []
  REPRO-D2 field-error elements = []
  => no alert element, no field-error element, form unchanged.
  The harness was deleted after the run; the evidence is the stdout above.
Reachability: NOT reachable through the UI as shipped. §4/§5/§6 prove every POST/PUT body
          carries exactly the five permitted keys, so the backend never produces this shape
          for an app request. It becomes reachable the moment the contract drifts — a
          renamed or removed request field, or a body built by spreading an object — which
          is precisely the change D-5 exists to guard against.
Severity: minor (fail-silent defensive gap, unreachable today; no criterion is violated —
          §6 does not state what to do when errors[].field matches nothing)
Suspected owner: frontend-developer — suggest falling back to the problem `detail` in the
          alert region for any errors[] entry whose field is not rendered (one `else` in
          the alertProblem computation).
```

No other defect was found. In particular:

- No flaky test was observed: the full suite was run 4 times (26/26, 30/30 twice, plus
  per-file runs) with identical results.
- No acceptance criterion is unmet.
- No test added by this card is red.

---

## 10. Observations worth a follow-up (no verdict impact)

- **O1 — the `status` region is not cleared on failure.** After a successful create, a later
  failed submit leaves `role="status"` reading `Coffee created` while the alert shows the new
  error (observed in §5.4(b)). §5.6 does not require clearing it, and a live region does
  announce the new alert, so this is cosmetic — but "Coffee created" visible next to an error
  banner is misleading. `frontend-developer`, minor.
- **O2 — `role="alert"` is used for the page banner *and* for every field error.** Multiple
  live regions on one screen are announced in DOM order; §5.6 mandates both, so this is the
  specified design, not a defect. Recorded because a screen-reader user hears a field error
  and a banner with no way to tell them apart by role.
- **O3 — re-opening "New coffee" while the create form is open does not clear typed values.**
  The form's `key` is the constant `'create'`, so React keeps the instance and the state.
  Spec §5.2 only says the form resets *after a successful 201*, which it does. Cosmetic.
- **O4 — the `frontend-web-app` skill named in this card's Skills section is not installed on
  the `qa-tester` profile** (`skill_view` returned "not found"; the profile offers
  `test-strategy`, `factory-delivery-workflow`, `java-cloud-native-stack`, …). The card's
  `test-strategy` and `factory-delivery-workflow` skills were loaded and followed. Whoever
  owns profile provisioning may want to add `frontend-web-app` to `qa-tester`; it did not
  block this verification.
- **O5 — the author's suite already covered three of AC-Q3's four sub-criteria** (see §3.1).
  The criterion as written is satisfiable but its "which the implementer's suite does not
  have" clause over-claims. Worth tightening in a future revision of §9.

---

## 11. Commands run (complete list)

```
. /opt/data/toolchains/env.sh                                  # JDK 21.0.12.1+1, Maven 3.9.16
cd frontend
npm ci                                                         # AC-Q1: 155 packages, 0 vulnerabilities
npm run test -- --run                                          # AC-Q1: 3 files, 26 tests, all pass
npm run typecheck                                              # AC-Q2: tsc --noEmit, exit 0
npm run build                                                  # AC-Q2: vite build, exit 0
npx vitest --run src/qa/qa-verification.test.tsx               # AC-Q3: 4 verifier tests, all pass
npx vitest --run src/qa/tmp-repro-d2.test.tsx                  # D1 reproduction (temporary, deleted)
npm run test -- --run                                          # final: 4 files, 30 tests, all pass

. /opt/data/toolchains/env.sh && cd backend && ./mvnw quarkus:dev   # AC-Q4: real API on :8080, dev profile
cd frontend && npm run dev                                     # AC-Q4: Vite on :5173
curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/coffees
curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/coffees
curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/
# browser (CDP): Page.addScriptToEvaluateOnNewDocument (fetch + console recorder),
#   goto http://localhost:5173/, click New coffee, set fields, Save coffee,
#   Edit …, Save changes, Delete …, Confirm delete            # AC-Q4
#   duplicate name -> 409; price 12.345 -> 400; out-of-band DELETE -> stale-delete 404   # AC-Q5
#   Tab/Enter/ArrowDown only, then read window.__qaConsole                                 # AC-Q6

git fetch origin main && git diff --stat origin/main...HEAD    # AC-Q7: no backend/ path
# two mutations applied to frontend/src/App.tsx, suite re-run, both reverted               # AC-Q8
git diff -- frontend/src/App.tsx                               # empty after revert
```

Servers were stopped after the run. Every coffee this card created carried the prefix
`QA-1790619374-` and was deleted at the end; `GET /coffees` returned
`{"count":0,"names":[]}` before the run finished.

Skills applied: `test-strategy` (criteria restated as falsifiable statements, the cheapest
level that can fail, independence from the author's suite, mutation checks that bite the
named guarantee, structured verdict and defect format), `factory-delivery-workflow` (card as
the unit of work, branch/PR rules, hand-off targets), `java-cloud-native-stack` (drive the
real service rather than a mock — a stubbed repository cannot tell you the SQL is wrong, and
a stubbed HTTP client cannot tell you the contract drifted).