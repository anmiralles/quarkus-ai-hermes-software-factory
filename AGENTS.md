# Rules for bots working in this repository

Read this before touching anything. These are not suggestions — a card that ignores
them is not done.

## Repository layout

- `backend/` — Quarkus (Java). One bounded context per deployable.
- `frontend/` — React.
- Backend and frontend stay independent: they meet at an **API contract**, never at
  shared code.

## Definition of Done

A card is done when **all five** hold:

1. **Implemented** — the feature, issue or fix described in the card exists and works.
2. **Covered by tests** — tests that would *fail if the behaviour regressed*. Not
   "tests exist"; tests that assert the behaviour. See `test-strategy`.
3. **Relevant skills applied** — the skill for this work was consulted and followed
   (`quarkus-*`, `backend-java-service`, `frontend-web-app`, `test-strategy`, …),
   not ignored.
4. **PR created in this repository** against `main`, linked to the card.
5. **Left open for human review** — Angel reviews and merges.

Do not mark a card done on the strength of a green local build alone.

## Git rules

- **Never commit to `main`.** Branch as `task/<short-slug>`, slug derived from the
  card title. The card id goes in the commit subject and the PR title.
- **Never force-push.** Never rewrite published history.
- **Never merge a PR.** Opening the PR is the bot's job; merging is Angel's.
- One card → one branch → one PR. Do not bundle unrelated work into one PR.
- Commit messages: `<card-id>: <imperative summary>`.

## Pull requests

`gh` resolves as bare `gh` (via `/opt/data/.local/bin/gh`). Either form works:

```bash
gh pr create --base main --head task/<slug> \
  --title "<card-id>: <summary>" --body-file /tmp/pr-body.md
```

CI runs on every PR and on `main`: jobs `backend-tests` and `frontend-tests`, and
both are **required** on `main` with *"branches must be up to date"*. A PR cannot
merge until they pass **and** the branch contains current `main`. So before opening
a PR:

```bash
git fetch origin main && git rebase origin/main
git diff --stat origin/main...HEAD    # must list ONLY your card's files
```

If that diff lists the whole project, your branch was cut from a stale base. Fix it
before opening — a full-tree PR cannot be reviewed as a delta.

Every PR body states:

- **What** changed and **why**
- **How it was verified** — the exact command(s) run and their result
- The **card id** it implements
- Which **skill(s)** were used

A PR that says "tested manually" with no command is not verified. Report the command
and its output, or say plainly that you could not run it.

## Non-negotiables

- No secrets, tokens, or credentials in code, config, logs, or PR bodies.
- No destructive git or cluster command.
- Don't invent API contracts, table names, or library versions — read the repo or ask.
- If a card is underspecified, say so on the card rather than silently choosing scope.
