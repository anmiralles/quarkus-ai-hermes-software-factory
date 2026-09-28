# We stopped chatting with a coding agent and put a team of them on a Kanban board

*A software factory built from Hermes Agent profiles, a shared task board, and two
human gates.*

## The problem with the demo

A coding agent in a chat window is impressive for about twenty minutes. Then you try to
use it for real work and the same three things break:

- **No owner.** Nobody knows which agent is doing what, or whether it finished.
- **No audit trail.** The reasoning lives in a scrollback that nobody reads twice.
- **No gates.** The agent has the same authority as the person who asked, so "review"
  is a social convention rather than a step in the process.

We wanted the opposite: agents doing most of the work, but with a workflow that a
sceptical engineer would actually sign off on. So we built a small software factory
around three ideas.

## 1. Work is a card, not a conversation

Every request becomes a card on a Kanban board. The card body is the contract, and it
has to state three things:

- the **deliverable**,
- the **constraints** (what must not change), and
- **how "done" is verified**.

A card missing any of the three is rejected at intake. This sounds bureaucratic until
you have watched a vague instruction turn into four agent sessions re-reading a repo,
each guessing a different answer. The card is where that ambiguity is cheapest to kill.

The board is the shared truth. Humans read it through a dashboard; the bots drive it
through a task toolset (`create`, `link`, `comment`, `block`, `complete`). No bot has a
private plan — if it is not on the board, it did not happen.

## 2. Bots are roles, not one generalist

We run Hermes Agent profiles as a small, deliberately boring org chart:

| Role | Does |
|---|---|
| `architect` | reads the request, writes the spec, decomposes it into linked child cards |
| `backend-developer` | Quarkus (Java) service, with tests |
| `frontend-developer` | React app, with tests |
| `qa-tester` | independent verification — never the author |
| `infosec` | pulled in only when auth, secrets or data exposure are touched |
| `devops` | CI, toolchain, release plumbing |

Two things matter more than the roster:

**The plan is a card.** The architect does not start building — it produces a plan card
with child cards linked to it. The board itself blocks the children until a human marks
the plan done. No bot has to *remember* to wait for approval, and no bot can forget to.

**Verification is a different bot.** The worker that wrote the change never signs it
off. `qa-tester` is a separate profile with the same tools and no loyalty to the author.
That one rule removed most of the "agent grading its own homework" problem.

Concurrency is capped (two workers at a time on our single-CPU box). A factory that runs
ten agents in parallel is not faster, it is just more expensive and harder to review.

## 3. Two gates, placed by cost, not by ceremony

**Gate 1 — approve the plan, before implementation.** Rejecting a bad plan costs one
review. Rejecting the output of four bots costs four full agent sessions plus the rework.
This is the single biggest token lever in the whole system, and it sits where it is
cheapest.

**Gate 2 — review the pull request.** Branches are cheap and reversible; a merged `main`
is not. Every card ends as an open PR against `main`, with the exact commands run and
their output in the body. A bot may open a PR. A bot may never merge one.

Both gates are enforced by configuration, not by trust. Two settings decide whether the
gates exist at all:

```
kanban.auto_decompose: false   # otherwise the dispatcher builds the child graph itself
                               # and starts work before a human ever sees the plan
kanban.review_dispatch: false  # a card in review is never picked up by a bot
```

That second line is the interesting one. A worker literally cannot approve its own work,
because it cannot run a card that is sitting in the review column waiting for a person.

## The repo holds the rules

The workflow is written down where the bots will read it — `AGENTS.md`, loaded into every
agent's context:

- one card → one branch → one PR (no bundling, no drive-by refactors);
- never commit to `main`, never force-push, never merge;
- a PR body states what changed, why, the card id, and the command output that proves it
  — "tested manually" is not verification;
- don't invent API contracts, table names or versions; read the repo or ask.

CI runs on every PR *and* on `main`, and both `backend-tests` and `frontend-tests` are
required with "branches must be up to date". The CI gate lives on the pull request, where
it applies to everyone — not on the card, where only one CLI could satisfy it.

## What went wrong, and what we changed

The factory has been through a real run, and the failures were more instructive than the
successes:

- **The plan gate silently didn't exist.** The dispatcher's built-in auto-decomposer beat
  the architect to it, built the whole child graph, and started the first task. Nothing
  ever entered review. Fixed by disabling auto-decomposition and verifying the setting.
- **Every PR contained the whole project.** The shared clone's `main` had never advanced
  past the scaffold, so every task branch was cut from an empty base. Four full rebuilds,
  and PRs nobody could review as a delta. Fixed by keeping the base fast-forwarded and
  merging PRs as they arrive, not in a batch.
- **A completion contract nobody could satisfy.** Requiring a published-PR field that the
  human-facing board cannot supply stranded good work in review forever. The lesson: put
  the gate where the tool actually enforces it, not where it merely records intent.

## What we take away

The agents are not the interesting part. Model quality changes every few months; what
made this usable was conventional engineering hygiene applied to agents:

1. **Make work durable and inspectable.** A card with an owner, a state and an audit
   trail beats a chat transcript.
2. **Give each agent one job, and keep the verifier separate from the author.**
3. **Spend human attention where it is cheapest and leverage is highest** — plan first,
   diff second, and never after the merge.
4. **Enforce the gates in configuration, and then check that they are still on.** A gate
   that depends on a bot remembering is not a gate.

The result is not a team of engineers you can forget about. It is a team that needs you
twice per piece of work, in the two places where being wrong is expensive — and that
turns out to be the difference between a demo and something you would let near `main`.

---

*Built with [Hermes Agent](https://hermes-agent.nousresearch.com/docs) profiles on the
Kanban board, in a monorepo of a Quarkus (Java) backend and a React frontend. The rules
live in `AGENTS.md`; the flow is in `docs/workflow.md`; the hard-won details are in
`docs/runbook.md`.*
