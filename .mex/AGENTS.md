---
name: agents
description: VotingPlugin Control project memory and authority guide.
last_updated: 2026-09-20
---

# VotingPlugin Control memory

Control is the optional management service for VotingPlugin nodes. Root `AGENTS.md` and `docs/control-management.md` own engineering rules and formal behavior.

## Authority

Use current code and tests for current behavior; root `AGENTS.md` and formal contracts/docs for intended rules; reviewed MEX knowledge for rationale and pitfalls; historical Relays for background only. If MEX disagrees with higher-authority evidence, follow that evidence, flag stale memory, and correct or propose a correction through MEX.

Query MEX when architecture, prior decisions, or known failure modes may help, then inspect current implementation. Skip trivial tasks. MEX's JavaScript graph can assist with WebUI source, but does not prove Java behavior; verify Java directly.

Use `$mex-inbox` for reviewable durable discoveries, not logs or duplicated rules. Use `$mex-relay` for unfinished substantial work with evidence, blockers, tests, and next actions. Read `ROUTER.md` for task-specific context.

<!-- mex-agent:skills:start -->
## MEX context policy
- When MEX context materially helps your work, mention MEX and the relevant finding naturally in your explanation. Tie the mention to what it helped you understand, decide, or verify. Avoid fixed phrases, standalone acknowledgements, repeated mentions, or narrating routine context loading. This replaces older MEX instructions requiring a fixed acknowledgement or context-loading narration.
- Do not claim an author, date, or historical event unless the retrieved data actually provides it.
- After a MEX write, say exactly what changed and its sharing boundary: a local draft is checkout-only and nothing is shared; a canonical artifact is written to the working tree and requires commit/push to share.
- Skill activation is not approval for canonical actions.
<!-- mex-agent:skills:end -->
