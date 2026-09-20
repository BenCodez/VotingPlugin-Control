---
name: operations
description: Configuration approval, retry, and partial-result rationale.
triggers: [apply, preview, read, rollback, connector]
last_updated: 2026-09-20
mex:
  id: mx_01M307CW4V7HCFFWZXWT6BXB1N
  type: component
  status: promoted
  revision: 1
  title: Configuration operation constraints
---

# Configuration operation traps

READ and PREVIEW are separate from a one-time APPLY approval. Control sends each successful preview's expected node revision with APPLY; the paired VotingPlugin node contract requires rejecting a stale revision. A multi-node operation can contain mixed successes and failures; retrying READ or APPLY targets failed nodes, while a PREVIEW retry needs all original targets and a new approval. A coordinated proxy-method apply needs a new preview because topology may change. Source: `docs/control-management.md` configuration workflow and `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationOperations.java`.

Nodes pull leased tasks and return the exact claiming session and attempt. Control's operation journal keeps redacted history across restart, but recovered operations are history-only; recovery does not resume in-flight work. A successful node APPLY must not be repeated just to make a mixed operation look uniform. Source: root `AGENTS.md`, `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationOperationJournal.java`, `src/test/java/com/bencodez/votingplugin/control/domain/ConfigurationOperationsTest.java`.

Node-side YAML staging, reload, and rollback are implemented in VotingPlugin. The paired contract there is authoritative for exact node behavior; this repository must preserve the received reload/rollback result rather than infer success from a write response. Source: `docs/control-management.md`, root `AGENTS.md`.
