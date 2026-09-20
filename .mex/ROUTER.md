---
name: router
description: Relevant Control architecture and failure-mode memory.
edges:
  - target: patterns/configuration-change.md
    condition: when changing configuration operations
  - target: patterns/webui-editor-change.md
    condition: when changing browser editor state
last_updated: 2026-09-20
---

# Control memory routes

| Task | Read |
| --- | --- |
| Overall boundary and peer contract | `context/architecture.md` |
| Configuration or revisions | `context/operations.md`, `patterns/configuration-change.md` |
| WebUI state | `context/webui.md` |
| Authentication or persistence | `context/decisions.md` |
| Runtime/build details | `context/stack.md`, `context/setup.md` |

Load only relevant memory, then verify current Java or JavaScript source, tests, and formal docs. Root `AGENTS.md` remains the workflow guide.
