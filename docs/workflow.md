# Factory Workflow

How a request becomes merged code. Two humans gates, everything else is bots.

## The flow

```
  Angel: rough request
        │
        ▼
  ┌─────────────────┐
  │  triage card    │  created on the `default` Kanban board
  └────────┬────────┘
           ▼
  ┌─────────────────────────────────────────┐
  │  architect  —  specify + decompose      │
  │  writes the spec, creates the child     │
  │  cards, links them to this card as      │
  │  their parent, then requests review     │
  └────────┬────────────────────────────────┘
           ▼
   ═══ HUMAN GATE 1 ═══  Angel approves the PLAN
           │             (children stay blocked until he does)
           ▼
  ┌──────────────────┐        ┌──────────────────┐
  │ backend-developer│        │frontend-developer│   run in parallel,
  │ Quarkus + tests  │        │ React + tests    │   capped at 2 workers
  └────────┬─────────┘        └────────┬─────────┘
           └────────────┬──────────────┘
                        ▼
              ┌──────────────────┐
              │    qa-tester     │  independent verification:
              └────────┬─────────┘  PASS / FAIL / BLOCKED + evidence
                        ▼
              ┌──────────────────┐
              │     infosec      │  only when auth, secrets or
              └────────┬─────────┘  data exposure are touched
                        ▼
          each card opens a PR against `main`
                        ▼
   ═══ HUMAN GATE 2 ═══  Angel reviews the PR and merges
```

## The two gates, and why they exist

**Gate 1 — approve the plan.** Before any implementation tokens are spent, Angel sees
what the `architect` intends to build and who it is assigned to. Rejecting here costs
a review; rejecting after five bots have run costs five agents' worth of tokens. This
is deliberately placed where it is cheapest.

**Gate 2 — review the PR.** Every backend, frontend and test change reaches Angel as
an open pull request. This is technically enforced, not just social:

- The board runs with `kanban.review_dispatch: false`, so a card entering `review`
  is **never auto-picked-up by a bot**. It waits for a person.
- `AGENTS.md` forbids bots from merging, and forbids committing to `main`.

## Definition of Done

Feature, issue or fix **implemented**, with the relevant code **covered by tests**,
using the **relevant skills**, and a **PR created** in this repository. Full text in
[`../AGENTS.md`](../AGENTS.md).

## Why the plan is a card and not a chat message

The plan is a Kanban card so that it has an owner, a state, and an audit trail. Its
children are linked to it, which means the board itself enforces the gate — the
implementation cards cannot start until the plan card is `done`. No bot has to
remember to wait, and no bot can forget to.
