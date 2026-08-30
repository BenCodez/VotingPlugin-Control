# Maintainer and AI-agent guide

This repository is the standalone VotingPlugin Control service. Treat it as an optional management plane: it may inspect
and configure enrolled VotingPlugin nodes, but it must never become part of vote processing. VotingPlugin must continue to
start, accept votes, reward players, route proxy traffic, and stop normally when Control is absent or unavailable.

## Build and verification

Requirements: JDK 17+ and Maven 3.9+.

```shell
mvn -B clean verify
node --check src/main/resources/web/app.js
```

Use a focused Maven test while iterating, then run the complete command before opening a PR:

```shell
mvn -B -Dtest=InspectionOperationsTest test
mvn -B -Dtest=ControlHttpServerTest test
```

The CI definition is `.github/workflows/maven.yml`. The shaded runnable artifact is
`target/votingplugin-control-<version>-all.jar`.

## Architecture and file map

- `ControlApplication` parses owner commands/environment, creates durable stores, and wires the server.
- `http/ControlHttpServer` is the only HTTP boundary. It owns routing, authentication, CSRF enforcement, bounded JSON
  parsing, status/error mapping, and static WebUI delivery.
- `auth/` stores credential verifiers and short-lived browser sessions. Never persist or log raw credentials.
- `domain/InMemoryNodeRegistry` owns current node sessions, liveness, negotiated capabilities, plugin inventory, and
  topology. Current topology is deliberately in memory.
- `domain/ConfigurationOperations` coordinates live typed `READ`, `PREVIEW`, and `APPLY` tasks plus same-process retries.
  Nodes pull tasks; Control never connects inbound to a Minecraft server.
- `domain/ConfigurationOperationJournal` stores redacted operation history across restarts. It never stores proposal values,
  file contents, approval tokens, result messages/changes, credentials, or task attempts. Completed-result session IDs are
  retained only to preserve restart-required UI state; recovered operations are history-only and cannot resume work.
  Quick-setup history uses an internal, non-serialized `redacted` marker, and proposal validation must reject that marker.
- `domain/InspectionOperations` coordinates the separate, read-only `data.inspect.v1` lane.
- `domain/ConfigurationSnapshots` stores bounded copies of the redacted content returned by completed managed-file reads.
  The list API omits content, full reads are admin-only, and durable files are owner-permissioned where the platform
  supports POSIX modes. Restoring still uses the normal preview and one-time approval path.
- `domain/ConfigurationAuditLog` is the durable, bounded, hash-chained metadata audit log. Configuration values and query
  filters do not belong there.
- `protocol/` contains the wire DTOs and capability-to-domain mapping. Keep them immutable and validate at construction or
  at the HTTP boundary.
- `src/main/resources/web/` is a dependency-free browser client over the same `/api/v1` API.
- `src/test/java/` mirrors the security and protocol boundaries. Add regression tests at the narrowest responsible layer.
- `docs/control-management.md` is the human and AI reference for the management suite and inspection contract.

## Non-negotiable invariants

1. Control is optional and local-first. Do not add a vote-processing dependency, cloud requirement, or inbound listener to
   VotingPlugin.
2. Do not add arbitrary command execution, raw SQL, generic filesystem access, unrestricted configuration paths, or an
   untyped player/database/settings browser. New functions must be a narrow typed capability. Quick-setup presets and
   option names are fixed in Control and revalidated by the node for the requested phase; the WebUI settings catalog is a
   static versioned reference, not an arbitrary setting-write surface.
3. Capability negotiation is authoritative. Queue work only for an online node whose accepted capabilities contain the
   exact versioned capability. Unknown advertised capabilities remain unaccepted.
4. Configuration writes follow `READ`/`PREVIEW`/`APPLY`. Apply consumes the one-time approval from a completely successful
   preview and carries the node revisions that were previewed. Do not create a shortcut around this workflow.
5. A claimed task has a two-minute lease and a unique `attemptId`. A result must echo the current node session and attempt;
   stale attempts cannot complete reissued work.
6. The inspection lane is read-only. Its allow-listed kind and bounded string filters are the whole request; results are a
   structured JSON envelope whose serialized size is at most 512 KiB, and are retained only briefly. Audit the kind, never
   player names or other filter values.
7. Mask passwords, credentials, tokens, API keys, authorization values, webhook secrets, and comparable secrets before a
   read leaves a node. Never put secrets, proposed file contents, approval tokens, or inspection filters in logs/audit.
   Configuration snapshots persist only the node's redacted read result; do not weaken masking, admin authorization, or
   data-directory permissions.
8. Browser writes require both an authenticated session and its CSRF token. API automation uses the separate admin bearer
   credential; node endpoints use a credential bound to the exact node ID.
9. Treat all remote strings, collections, and bodies as hostile. Preserve limits, exact routes/methods, duplicate-field
   rejection, symlink checks, atomic publication, and fail-closed durable-file validation.
10. Keep the HTTP executor, password executor, operation stores, retained messages/content, topology, and inspection data
    bounded. Do not replace limits with unbounded queues, streams, maps, or full database scans.
11. `reward-simulation` and `reward-builder` share one strict proposal schema, but only the latter can persist. Keep the
    builder PREVIEW/APPLY-only, replace only its selected Rewards subtree, strip `proposal` from public/history views, and
    never execute reward actions from Control.
12. Keep discovered service names observational. `vote-site-health` may copy a bounded view of persisted
    `GottenServiceSites`, but it must not call an auto-creating resolver or turn a health read into a create/approve action.
13. The current `vote-logging` quick setup changes configuration but not the runtime VoteLog manager lifecycle. Preserve
    inspection gating on `VoteLogging.Enabled`, document that a restart is required after either toggle, and do not claim
    enabled/available/readable are interchangeable states.

## Paired protocol workflow

The implementation paired with this repository lives in `BenCodez/VotingPlugin`:

- Bukkit configuration adapter: `VotingPlugin/.../control/BackendConfigurationService.java`
- Bukkit outbound connector: `VotingPlugin/.../control/BackendControlConnector.java`
- Bukkit inspection handlers: `VotingPlugin/.../control/ControlInspectionService.java`
- proxy connector/host lifecycle: `VotingPlugin/.../proxy/control/`
- paired contract: `docs/control-agent-contract.md` in that repository

When changing a DTO, endpoint, capability, preset, error code, limit, or lease behavior:

1. inspect both repositories before editing;
2. make the change additive or capability-versioned so either old side remains safe;
3. update server and connector protocol tests in their respective PRs;
4. update `docs/control-management.md`, the VotingPlugin connector docs, and both root `AGENTS.md` files when an invariant
   changes;
5. link the two PRs and state a safe merge order. A Control-only deployment must reject unsupported work cleanly, and a
   VotingPlugin-only deployment must simply leave the new capability unaccepted.

Prefer one cohesive PR per repository for a paired feature (implementation, tests, and docs together). Split further only
when the pieces are independently deployable or need materially different review/rollback risk.

Protocol version `1` describes the registration/heartbeat resource protocol. Feature evolution normally uses a new
capability such as `data.inspect.v1`; do not bump the whole protocol for an optional additive feature.

## Safe change checklist

- Identify the trust boundary and maximum sizes before adding the happy path.
- Validate exact methods, paths, media type, authentication role, session, capability, and request fields.
- Decide whether data may be persisted, audited, returned to the browser, or must be discarded on logout/restart.
- For configuration: prove preview/apply revision binding, approval single use, reload behavior, and rollback reporting.
- For inspection: prove the handler cannot write, enumerate users, accept SQL/commands/paths, or leak sensitive settings.
- Add negative tests for wrong node/session/attempt, lease expiry, unknown fields/kinds, oversized input/result, and lost
  capability where relevant.
- For WebUI changes, escape untrusted text through DOM text nodes, clear sensitive/cached state on logout, keep CSRF on
  every write, and run `node --check`.
- Run the full Maven suite and inspect `git diff --check` before pushing.
- Keep the PR scoped; never mix generated artifacts, credentials, runtime `data/`, or unrelated formatting changes.

## Operational terminology

- A **configuration operation** may read, preview, or apply one typed configuration proposal.
- An **inspection** is a short-lived typed read and can never mutate a node.
- A **snapshot** is a durable Control-side copy of a successful redacted managed-file read result, not a raw server backup
  and not an apply operation.
- A **vote trace** is a timeline of events that VotingPlugin actually wrote to VoteLog for one correlation ID. It is not a
  packet-level or command-by-command delivery trace.
- A **diagnostics bundle** is the bounded, redacted inspection result assembled by the WebUI. It deliberately omits raw
  configuration, logs, player records, credentials, and infrastructure connection details.
