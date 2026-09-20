---
name: configuration-change
description: Preserve revision-bound, one-time configuration approval and mixed-node semantics.
triggers: [apply, preview, rollback, connector]
last_updated: 2026-09-20
mex:
  id: mx_01M3074CGVDHTY7AZFPZT703VB
  type: pattern
  status: promoted
  revision: 1
  title: configuration-change
---

# Configuration change

Trace Control's operation state, HTTP boundary, node result, and the paired VotingPlugin contract. Check the lost-acknowledgement and mixed-node paths: a successful APPLY is not repeated, a failed target can be retried, and a new PREVIEW requires every original target. Recovered journal entries are history-only. See `context/operations.md` and `src/test/java/com/bencodez/votingplugin/control/domain/ConfigurationOperationsTest.java`.
