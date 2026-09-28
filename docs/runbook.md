# Factory Runbook

How work moves through this repo, what is automatic, what is yours, and where the
tokens go. Written after the first real run of the factory, which exposed two failure
modes — both are recorded here so they don't recur.

---

## 1. The loop, in six steps

```
1. You drop a request            → a card on the `default` board
2. You choose the route          → Specify (small) or Decompose (multi-part)
3. GATE 1 — you approve the plan → nothing is built until you do
4. The bots build it             → max 2 workers at a time, each on its own branch
5. Each card opens a PR          → publication is a required gate, not a request
6. GATE 2 — you review and merge → the only irreversible step, and it is yours
```

**No bot merges. No bot commits to `main`. Nothing runs before you approve a plan.**

---

## 2. What is automatic, and what is deliberately not

| Stage | Who | Notes |
|---|---|---|
| Intake | **You** | Card must state deliverable, constraints, and how "done" is verified |
| Planning | `architect` | Decomposes into child cards linked to its own card |
| Plan approval | **You** | The plan card parks in `review`; children stay blocked behind it |
| Implementation | `backend-developer`, `frontend-developer` | One card, one branch, one PR |
| Verification | `qa-tester` | Independent — never the author. PASS / FAIL / BLOCKED with evidence |
| Security | `infosec` | Only when auth, secrets or data exposure are touched |
| Release | `devops` | CI, toolchain, deployment |
| Merge | **You** | Never a bot |

**Two config settings make the gates real** — if either is wrong, the gate silently
stops existing:

```bash
hermes -p default config get kanban.auto_decompose    # must be false
hermes -p default config get kanban.review_dispatch   # must be false
```

- `auto_decompose: true` (the **default**) makes the dispatcher build the child graph
  itself within one tick — before any bot runs — and dispatch the first child
  immediately. Your plan approval is bypassed entirely.
- `review_dispatch: false` stops bots from *picking up* a card already in `review`. It
  cannot create a gate; it can only protect one.

---

## 3. Your checklist, every time

### Step 1 — drop the request

Dashboard → Kanban → new card on `default`. Or create it from the CLI:

```bash
hermes kanban create "Add GET /coffees/{id}/roast endpoint" --triage \
  --body "Deliverable: …  Constraints: …  Verified by: …"
```

The body is the contract. A card without the three fields produces a PR nobody can
review against anything.

### Step 2 — choose the route

**Small, one owner → Specify.** A one-shot spec rewrite that promotes straight to
`todo`. No fan-out, no plan gate needed.

```bash
hermes kanban specify <card-id>
```

**Multi-part → Decompose.** Either click **Decompose** on the card, or:

```bash
hermes kanban decompose <card-id>
```

Either way the children should be assigned by role and **linked to the plan card**, so
the board itself blocks them until you approve.

### Step 3 — GATE 1: approve the plan

Read the plan card and its children, then check:

- [ ] Are the **child cards** the right decomposition? Nothing missing, nothing bundled?
- [ ] Is each one **assigned to the right bot** for the work?
- [ ] Do the cards carry the **decisions already made** — API shape, naming, error model?
      If two parallel cards each have to choose, they will choose differently.
- [ ] Does each state **how "done" is verified**?

Then **mark the plan card done** to release the children. To reject: comment with the
reason, and leave it blocked. Do not start implementation to "save time" — that is the
thing this gate exists to prevent.

### Step 4 — let it build

Workers claim cards automatically, up to 2 at once. You do not need to do anything.

### Step 5 — GATE 2: review each PR

Before merging, check:

- [ ] Does the PR show **only this card's files**? Run
      `git diff --stat origin/main...HEAD` locally — if it lists the whole project, the
      branch was cut from a stale base and must be rebased.
- [ ] Is the **evidence** real? The PR must quote the command run and its result.
      "Tested manually" is not verification.
- [ ] Do the **tests assert behaviour** rather than restating the implementation?
- [ ] Is `main` **up to date locally** before you branch anything new (see §6)?

### Step 6 — close out

The root card completes when its children do. Periodically prune stale worktrees (§5).

---

## 4. Why the human is in the loop, and where exactly

The gates are placed by **cost of being wrong**, not by ceremony.

**Gate 1 sits before any implementation tokens are spent.** Rejecting a plan costs one
review. Rejecting the output of four bots costs four full agent sessions plus the
rework. This is the single largest lever you have, and it is placed where it is
cheapest.

**Gate 2 is the only irreversible step.** Branches are cheap and reversible; a merged
`main` is neither. Everything upstream is designed so that the irreversible act is a
deliberate human one.

**Gate 2 is enforced by the tool, not by trust.** With `review_dispatch: false`, a card
in `review` is never picked up by a bot. A bot literally cannot approve its own work,
because it cannot run a card that is waiting for a person.

**What you are actually approving:**

| Gate | You are answering |
|---|---|
| 1 | Is this the right *shape* of work, split the right way, assigned to the right owners? |
| 2 | Does this *specific change* do what its card claims, with evidence I believe? |

Neither gate asks you to trust a summary. Gate 1 is a plan you can read in a minute;
Gate 2 is a diff and a command output you can check.

---

## 5. Token economics

Where the tokens actually go, and the levers in order of impact.

**The costs:** the auxiliary planner calls (decompose/specify), each worker session
(a full agent, up to `agent.max_turns: 500`), retries, the `--goal` judge loop, and
redundant re-reading caused by underspecified cards.

| # | Lever | Effect |
|---|---|---|
| 1 | **Gate before fan-out** (on by default now) | Stops four agents' worth of work at the price of one review |
| 2 | **Specify instead of Decompose** for single-owner work | Avoids creating four cards when one would do |
| 3 | **Write the decisions into the card body** | A vague card makes the worker guess, re-read the repo, and retry |
| 4 | **Pin auxiliary models cheap** — `auxiliary.kanban_decomposer`, `triage_specifier`, `profile_describer`, `goal_judge` | These currently fall back to your main model. If you later move workers to a stronger model, pin these four to the cheap one so planning stays cheap |
| 5 | **`--max-runtime 30m`** per card | Bounds a runaway worker instead of letting it grind |
| 6 | **`--max-retries N`** / `kanban.failure_limit` (now 2) | A broken card fails and stops instead of burning |
| 7 | **`--model` / `--provider` per card** | Cheap model for mechanical cards; reserve the strong one for hard design |
| 8 | **`--skill <name>`** on the card | Preloads the right playbook instead of the worker discovering it |
| 9 | **Keep `max_in_progress: 2`** | Concurrency multiplies cost, and this box has 1 CPU |
| 10 | **Merge as you go** | Stale bases caused each PR to re-show the whole tree — and each bot to rebuild the project. That was the biggest waste so far |
| 11 | **Avoid `--goal` by default** | The judge loop can run many turns in one session |

**Leave `completion_contract` at its default, `local-only`.** A contract naming a repo
or PR URL makes completion depend on evidence the *human* closing the card cannot
supply — the dashboard has no `published_pr` field — so the card stalls in `review`
with `PR acceptance missing`, however good the work is.

Nothing is lost by leaving it alone: `backend-tests` and `frontend-tests` are
**required on `main`**, so a red PR cannot merge at all. The CI gate sits on the pull
request, where it applies to everyone and cannot be bypassed — not on the card, where
only the CLI can satisfy it.

```bash
# the default, and what human-reviewed cards should use:
--completion-contract local-only
```

---

## 6. Working with and supervising the bots

**The dashboard is your surface.** `hermes dashboard` → the Kanban page. Workers never
see the dashboard or the CLI — they drive the board through a `kanban_*` toolset
(`kanban_show`, `kanban_complete`, `kanban_block`, `kanban_comment`, `kanban_create`,
`kanban_link`, …). So the board is the shared truth, and it is the thing to read.

**Live stream of finishes and failures:**

```bash
hermes kanban watch --kinds completed,gave_up,timed_out
```

**One card's history** — comments, events, run outcomes, handoff summaries:

```bash
hermes kanban show <card-id>
```

**Stop something:**

```bash
hermes kanban reclaim <card-id>                    # stop the running worker…
hermes kanban block <card-id> "reason" --kind needs_input   # …and stop it re-running
```

`reclaim` alone re-queues the card and the dispatcher picks it up again. `block` is what
actually stops it.

**Warning signs worth acting on:**

- A card sitting in `ready` that never runs → its **assignee doesn't resolve** to a real
  profile. It fails silently as `skipped_nonspawnable`; there is no fallback owner.
- A PR touching files outside its card's scope → **stale base**, rebase before merging.
- A card marked done with no command output in its handoff → the DoD was not met.
- Cards in `review` piling up → that's you. Nothing else will clear them.

**Cadence:** glance at the board after a chain runs; merge or reject the PRs while the
context is fresh; prune worktrees (`git worktree list`, `hermes worktree prune`) when
branches are merged. A weekly five-minute pass over `review` and `ready` catches
everything that has silently stalled.

---

## 7. Pitfalls already hit (do not repeat)

**The plan gate silently not existing.** `kanban.auto_decompose` defaults to `true`. On
the first run it built the whole child graph 79 seconds after the request landed, and
dispatched the first child immediately. The design assumed the `architect` would
decompose and park its own card; the built-in decomposer beat it to it, so no card ever
entered `review`. Fix: keep `auto_decompose: false`.

**Every PR containing the whole project.** The shared clone's `main` was never advanced
past the initial scaffold, so every task worktree branched from an empty repo and each
bot rebuilt the entire project. Four independent full builds, and every PR displayed the
whole tree because GitHub was diffing from the scaffold. Fixes: keep the clone's `main`
fast-forwarded to `origin/main` before worktrees are cut; rebase onto `origin/main`
before opening a PR; **merge PRs as they arrive** rather than in a batch, since a chain
whose PRs merge only at the end cannot produce delta PRs.

**Where `gh` lives.** Resolvable as bare `gh` via `/opt/data/.local/bin/gh` (a symlink
to the token-injecting wrapper). This matters beyond convenience: Kanban's PR-acceptance
check shells out to bare `gh`, so an absolute-path-only install strands every card with
a GitHub completion contract.

**An unsatisfiable completion contract.** `--completion-contract <OWNER/REPO>` requires
the repo to have *required status checks*; with none configured, acceptance returns
`"No repository-required checks are configured"` and the card can never complete. Private
repos on a free plan **cannot** have required checks, so this silently broke every card
the architect created. Fixed by making the repo public plus adding CI (`.github/workflows/ci.yml`)
and requiring `backend-tests` + `frontend-tests` on `main`.

**Stranded by your own error message.** The respawn guard refuses to re-spawn a `ready`
card whose `last_failure_error` matches `\b(quota|rate_limit|429|403|auth\w*|…)\b`. The
acceptance failure text says "check gh **authentication**/API access", which matches
`auth\w*` — so the card parks with only a `respawn_guarded` event as evidence. To clear
it you must `unblock`, whose UPDATE is guarded by `WHERE status IN ('blocked','scheduled')`:
**moving a blocked card to `ready` first makes the unblock a silent no-op.** Unblock while
it is still blocked, or re-block then unblock.

**Completing a card has two gates, not one.** `kanban complete` first runs the goal judge
(`auxiliary.goal_judge`) against the evidence you pass, then the acceptance check. Thin
evidence like `--result "done"` is rejected by the judge; pass the actual command output
or artifact proof. A card in `goal_mode` keeps this gate for its whole life.

