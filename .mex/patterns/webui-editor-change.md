---
name: webui-editor-change
description: Preserve file content presence and approval invalidation in the browser.
triggers: [webui, editor, empty file, approval]
last_updated: 2026-09-20
mex:
  id: mx_01M3074CH83ZX5G15W4J13AWC0
  type: pattern
  status: promoted
  revision: 1
  title: webui-editor-change
---

# WebUI editor change

A successful empty file is present content, not a missing read. Trace generation counters, selected node/session/file, dirty draft, and approval invalidation together. A cached read may populate the editor, but PREVIEW still checks the live revision. See `context/webui.md`, `src/main/resources/web/app.js`, and the browser fixtures under `src/test/`.
