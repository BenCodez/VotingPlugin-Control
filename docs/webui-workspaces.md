# WebUI workspaces

Authentication opens Home, where an administrator chooses one or more registered Bukkit backends or Global Settings.
Proxy nodes remain visible but are not Bukkit configuration targets. Offline backends can be inspected; unsupported
capabilities remain unavailable. The select-all action includes only online Bukkit nodes with a supported management
capability, within the existing target bound.

The browser keeps three separate concepts for the authenticated page session:

- `managementScope`: `SERVER`, `MULTI_SERVER`, or `GLOBAL` (unset before choosing).
- `selectedTargetIds`: the backend workspace, not the targets of every operation.
- `inspectedServerId`: an optional individual overview, independent of the workspace selection.

Opening an individual overview does not replace a multi-server selection. The scope bar names the workspace and the
single-server tool source. Home supports search, chips, clear, and continuing to an overview. Global Settings remembers
the previous backend selection, offers an explicit return, and never means “all servers.” Signing out clears the state.
Only scope, target IDs, inspected ID and route are saved in browser `sessionStorage` for the authenticated tab.
On reload, stored IDs are reconciled against current nodes; preview tokens, configuration documents and secrets are
never persisted. Signing out clears the saved workspace, and a new login opens Home.

## Workspace safety boundary

General Settings, Vote Sites and Rewards use independent per-target reads and dirty-only proposals (see their linked
documents). The legacy guided presets, current reward builder, and Full YAML editing remain **single-source tools**,
not mixed-value editors.
Their ordinary previews contain only the identified workspace source. Inspecting a backend outside the workspace does
not authorize editing it. Read-only Compare uses selected readable backends. Explicit proxy-method and VoteSites-sync
workflows keep their own target selection and preview/approval rules. Plugin staging also retains its separate explicit
eligible-target approval; navigation never stages a JAR or restarts a server.

Existing automatic reads, single-flight loading, session/revision guards, inline retry, and post-apply invalidation and
confirmed reads remain the loading system. Scope/navigation changes invalidate approvals, not apply configuration.
Navigation itself introduces no write or reload semantics. Typed editors and named reward files add narrow authenticated
API/capability contracts documented on their respective pages.

Hash routes include `#home`, `#servers/<node-id>/overview`, `#workspace/overview`, `#workspace/settings`,
`#workspace/vote-sites`, `#workspace/rewards`, `#workspace/configuration`, and `#global/network`.
Back/forward changes the view without replacing the backend workspace. Legacy panel routes still work after choosing
a scope. Configuration Full YAML and legacy guided tools explicitly identify their one document/source; the visual
editors use the selected backend workspace. Unsupported advanced structures remain accessible in Full YAML.

Overview cards use registry connectivity, reported versions/capabilities, proxy-reported presence, bounded health
inspections, and retained configuration operations. Unknown is not healthy. Website latency, server capacity, host
resources, player join logs, and invented analytics are not shown.

## Local verification

```shell
node --check src/main/resources/web/app.js
node --check src/main/resources/web/workspace.js
node --test src/test/web/*.test.cjs
mvn test
```

The Node tests exercise the workspace model and application helpers using deterministic DOM/API stubs. Separate
Chromium fixture smoke checks cover actual bundled WebUI assets at desktop and mobile widths.
