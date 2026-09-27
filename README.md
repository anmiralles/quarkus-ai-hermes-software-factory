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
