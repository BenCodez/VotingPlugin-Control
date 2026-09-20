---
name: webui
description: Browser state distinctions that affect safe editing.
triggers: [webui, browser, draft, revision]
last_updated: 2026-09-20
mex:
  id: mx_01M307CW4VZSFJ5KKNS6EFNFEB
  type: component
  status: promoted
  revision: 1
  title: WebUI state boundaries
---

# WebUI state traps

For managed files, content presence is independent of string length: a successful empty read or snapshot restore is a valid editor value and can be previewed. Authentication, node, session, or file changes invalidate the applicable editor/approval state, but a file selection or capability change does not necessarily purge the node/session/file-keyed short-lived read cache. PREVIEW still checks a live revision. Dirty drafts require explicit handling when context changes. Source: root `AGENTS.md`, `src/main/resources/web/app.js` (`configurationContentPresent`, `applyAuthenticatedSession`, `updateConfigurationButtons`).

Browser profiles are local convenience input and do not approve or apply. Snapshots contain redacted read output, not old credentials; restoring one still goes through live preview and single-use approval. Source: `docs/control-management.md`, `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationSnapshots.java`.
