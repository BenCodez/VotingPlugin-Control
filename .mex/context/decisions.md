---
name: decisions
description: Reasons for Control trust and persistence boundaries.
triggers: [decision, security, credential, audit]
last_updated: 2026-09-20
mex:
  id: mx_01M307CW4VJ4K40DCAZPSX9D9K
  type: decision
  status: promoted
  revision: 1
  title: Control trust and persistence decisions
---

# Trust and persistence decisions

Node credentials are bound to exact node IDs; browser sessions and API automation use distinct authentication. A node's advertised capability is insufficient until Control accepts it. Keep secrets and proposed content out of journals/audit; operation history is deliberately less detailed than live results. Source: `README.md`, root `AGENTS.md`, `docs/control-management.md`.

Configuration snapshots store redacted content with restricted access. The audit log stores bounded metadata and is hash-chained; it is not a configuration-value store. An interrupted operation becomes history-only after restart. These limits reduce secret exposure and prevent stale work from being resumed as if its approval were still live. Source: `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationSnapshots.java`, `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationAuditLog.java`, `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationOperationJournal.java`.
