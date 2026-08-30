# VotingPlugin Control management suite

This document is the implementation-oriented reference for maintainers, API clients, and AI agents. VotingPlugin Control
is an optional local-first management plane. It discovers enrolled nodes, coordinates bounded configuration changes, and
requests typed read-only inspections. It never receives or processes votes, and VotingPlugin remains fully operational
when Control is stopped.

## Mental model

There are three independent lanes:

| Lane | Capability examples | Direction | Can mutate a node? | Persistence |
| --- | --- | --- | --- | --- |
| Discovery | `discovery.read`, `presence.snapshot` | Node pushes registration/heartbeat/presence | No | Current topology is in memory |
| Configuration | `config.files.v1`, `config.quick-setup.v1` | Browser queues; node polls and reports | Only after preview and approval | Redacted history and audit are durable; live task input is in memory |
| Inspection | `data.inspect.v1` | Browser queues; Bukkit node polls and reports | Never | Short-lived result is in memory; kind-only audit is durable |

Connectors always initiate outbound HTTP(S) to Control. No Control feature adds an inbound port to a Minecraft process.
One node credential is bound to one stable node ID. Browser sessions and the API automation credential are separate from
node credentials.

## Capability map

Capability negotiation is the compatibility boundary. A node advertises capabilities during registration/heartbeat;
Control accepts only the intersection with its own allow-list.

| Capability | Role |
| --- | --- |
| `discovery.read` | Current node identity and status |
| `presence.snapshot` | Full replacement backend presence snapshots from a proxy |
| `config.proxy-routing.v1` | `SendVotesToAllServers` and `BlockedServers` on a proxy |
| `config.files.v1` | Bounded reads/previews/applies for managed Bukkit YAML files |
| `config.file-comments.v1` | Preserves Control-managed comment metadata where supported |
| `config.quick-setup.v1` | Typed guided settings and reward/site presets |
| `config.vote-sites-sync.v1` | Reward-safe VoteSites merge from one backend to selected targets |
| `config.transport-test.v1` | Typed, bounded proxy-to-backend communication check |
| `config.proxy-method.v1` | Coordinated preview/apply of a supported network proxy method |
| `data.inspect.v1` | Typed read-only data, health, simulation, and diagnostics requests |

Do not infer support from plugin version strings. Check `acceptedCapabilities` for the exact capability.

## WebUI feature map

The dependency-free WebUI is an API client, not a privileged implementation path. Every write below still uses the same
authenticated, CSRF-protected endpoint and node capability checks as an external client.

| Area | What the WebUI does | Safety/accuracy boundary |
| --- | --- | --- |
| Network Doctor | Runs `diagnostics` (which includes the overview fields), combines node health with Control's current topology, and displays checks for connector, configuration, Votifier, vote sites, rewards, logging, and proxy topology | Read-only; “healthy” is bounded reported state, not a synthetic vote |
| Diagnostics download | Downloads the last Network Doctor result as local JSON | Redacted status bundle only; no raw configuration/logs/player records/infrastructure secrets |
| Activity | Loads the newest 50 live/recovered operation views, labels phases, lineage, reload/rollback, resumes eligible guided preview approvals, and offers retry only when `retryable` | Recovered history cannot be retried; approval is single-use and apply is CSRF-protected; proxy-method apply needs a new preview |
| Fast file reads | Caches a successful file read for 30 seconds by node ID, node session, and file | Browser memory only; cleared on logout and successful relevant writes; session binding prevents reuse after reconnect |
| Configuration drift | Reads the same redacted managed file from two or more selected capable nodes, groups exact revisions, and compares each target with the first successful baseline | Read-only; renders at most 50 differing line pairs per target and truncates each redacted line to 200 characters |
| Snapshots | Creates a named durable snapshot from the last completed file read and loads one document into the editor | Stores the full redacted read result; restore is proposed content and must be freshly previewed/approved |
| Settings catalog | Filters a static schema of commonly managed setting key, file, type, default, and effect | Reference for guided forms, not a generic setting API or claim to cover every VotingPlugin option |
| Setup checklist | Uses live node state plus `overview` to mark enrollment, topology, vote-site, reward, logging/storage, and communication readiness | Vote logging is explicitly optional; a check is not an end-to-end vote test |
| Auto-create setup | Reads/previews/applies the dedicated single-setting preset to selected Bukkit targets | Does not overwrite other common settings |
| Vote-logging setup | Reads/previews/applies enabled state, retention, and main-connection choice | Never accepts credentials; dedicated connection details stay in the redacted editor |
| Setup profiles | Stores up to 20 named guided-form profiles in browser `localStorage` | Browser-local, versioned, non-secret values only; no raw YAML or credentials; loading never applies |
| Reward builder/simulator | Builds site/every-site/vote-party proposals with commands, player/broadcast messages, items, money, permissions, chance, and online-only behavior; simulates or previews/applies the exact proposal; can copy one command/message into simple Setup | Simulation has no side effects; persistence replaces only the selected Rewards subtree through normal preview/approval; editing invalidates approval |
| Votes & Data | Shows overview, exact player lookup, vote-site health plus persisted unconfigured-service observations, a 30-day VoteLog summary, exact/bounded logged-event search, and correlation trace | Inspection-capable Bukkit node only; VoteLog reads require logging; results and form inputs are cleared on logout; no player enumeration |
| Safe service-site test | Dry-runs resolution, including optional disabled-site matching and whether auto-create would be considered | Sends no fake vote, creates no site, changes no total, and runs no reward |

The Setup tab replaces the former “Quick Setup” framing but retains existing typed presets, VoteSites sync, detected-plugin
command suggestions, preview, approval, node backup, reload, and rollback. Setup profiles are convenience input only; live
values should be loaded before modifying an existing configuration.

## Configuration operations

### Managed domains

`ManagedConfiguration` is a tagged union:

- `proxy-routing` manages only `sendVotesToAllServers` and `blockedServers`;
- `file` manages one allow-listed file: `Config.yml`, `VoteSites.yml`, `SpecialRewards.yml`, `GUI.yml`, `Shop.yml`,
  `BungeeSettings.yml`, or one validated split file under `VoteSites/`;
- `quick-setup` manages one typed preset with at most 20 bounded string options. Ordinary options are at most 500
  UTF-8 bytes; the internal VoteSites sync source may be 512 KiB and a `reward-builder` proposal may be 64 KiB. Both large
  inputs are stripped from every public operation view.

Control accepts only the fixed preset/option-name schema in `ManagedConfiguration`, including the small safe fields that a
node may return for a READ result. Proposal creation also enforces each exact required option set. VotingPlugin independently
rejects unknown presets/options and applies phase-specific rules (for example, most READ presets take no options and
`reward-builder` has no READ form). The WebUI settings catalog is static/versioned guidance over these typed operations;
it does not turn a displayed YAML key into a generic write request.

`reward-builder` is PREVIEW/APPLY-only and requires exactly `options.proposal`, a JSON-serialized copy of the typed reward
proposal documented below. It deliberately has no READ form: Control rejects that phase before an operation is queued,
and the node independently revalidates the phase. The selected scope determines the managed file and path, and the plugin
replaces only that path so a second preview is deterministic and cannot leave stale actions behind. Control never returns
the proposal in a public operation view, and the durable operation journal records only the domain/preset.
Public/history-only quick-setup selectors carry an internal non-serialized redacted marker that proposal validation rejects,
so they cannot become executable requests. The node's acknowledged result exposes only the derived target file, not
proposal actions/messages.

File content is limited to 512 KiB. Node results mask secret-like YAML paths. A replacement secret may pass through an
authenticated proposal, but Control omits file proposal contents from operation views and never records them in its audit.

### Read, preview, and apply

1. `POST /api/v1/configuration/read` queues a read for 1–100 online nodes that accept the selected capability.
2. `POST /api/v1/configuration/preview` queues independent parsing/validation and deterministic change reporting. A fully
   successful preview returns a random one-time `approvalToken`.
3. `POST /api/v1/configuration/apply` accepts only that preview ID and exact unused token. It carries each node revision
   from preview so a concurrent edit becomes a stale-revision failure.
4. Nodes stage and atomically replace managed YAML, reload VotingPlugin, and restore the local `.control-backup` if reload
   fails. The result distinguishes reload and rollback from a successful save.

Each target state is `QUEUED`, `IN_PROGRESS`, or `COMPLETE`; the aggregate state is `RUNNING`, `SUCCEEDED`, or
`COMPLETED_WITH_ERRORS`. A claim has a two-minute lease and new `attemptId`. The result must echo that attempt and the
current node session, preventing a stale execution from completing reissued work.

### Retry behavior

`POST /api/v1/operations/{operationId}/retry` creates a new operation; it never mutates the historical view.

- The original operation must be complete and have at least one failed node.
- A failed `READ` or `APPLY` retries only failed nodes. Already successful applies are never repeated.
- Retrying a `PREVIEW` includes every original target and returns a new approval token after all targets pass.
- A coordinated proxy-method apply returns `PREVIEW_REQUIRED` instead of reusing old topology assumptions.
- Retry still revalidates current online state and capability support.

Operations are bounded to 1,000 retained entries, with at most 16 file/VoteSites-sync-source operations retained. An
unleased active operation expires 15 minutes after creation; a completed operation is pruned 24 hours after creation.
`GET /api/v1/operations` returns at most the newest 100, newest first; each view includes `sourceOperationId`, `recovered`,
and `retryable`. Its separate `voteLoggingRestartSessions` map is derived from every retained operation, so restart-required
warnings are not lost when the WebUI renders only its newest 50 history rows. List entries are summaries whose retained file
bodies are omitted. Fetching
`GET /api/v1/operations/{operationId}` returns that operation's full bounded redacted result bodies. Both list and detail
views may include an unused completed-preview approval token so an authenticated UI can resume after refresh; applying
still requires the admin role/browser CSRF protection and consumes the token exactly once.
Vote-logging applies (including retries) that share any target are serialized with `OPERATION_CONFLICT`, making the newest
successful apply's retained session unambiguous even if several previews were prepared concurrently.

Production also atomically maintains an owner-readable, 2 MiB-bounded `data/configuration-operations.json`. It stores only
operation identity/type/time, redacted domain selector (`fileName` or preset), retry lineage, and bounded per-node
completion/success/code/revision/reload/rollback metadata. A completed result's backend session ID is also retained so the
WebUI does not lose restart-required setup state when Control restarts. It deliberately excludes options/proposal values,
file content, approval tokens, result messages/changes, credentials, and task attempt IDs.
Journal reads reject unknown or duplicate JSON fields, trailing tokens, completed nodes without a session ID, and
incomplete nodes that claim one rather than accepting ambiguous recovered state. An interrupted recovered node stays
incomplete in the journal on later shutdowns, while each public recovered view still reports `CONTROL_RESTARTED`.

After a restart, journal entries are exposed as `recovered:true` history. Any node that had not completed is shown failed
with `CONTROL_RESTARTED`; a recovered entry is never resumed or retried because its sensitive input, live session binding,
task attempt, and approval are not persisted. A completed result's historical session ID is display state, not a live task
binding. Start a fresh read or preview. This preserves operator visibility without replaying an
ambiguous write.

### Configuration snapshots and restore

Snapshots are durable Control-side copies of the redacted managed-file content returned by successful READ results. A
successful empty file is retained as an empty document; `null` alone means content was omitted. Known secret paths and
sensitive comment values contain `__VOTINGPLUGIN_CONTROL_REDACTED__` rather than credentials:

- `POST /api/v1/snapshots` accepts a name and completed read operation ID;
- `GET /api/v1/snapshots` lists summaries without file content;
- `GET /api/v1/snapshots/{snapshotId}` returns the selected documents with their full stored redacted content to an
  authenticated administrator/browser session.

A snapshot can contain at most 100 documents and 8 MiB of UTF-8 content. The store retains at most 100 snapshots and
64 MiB of encoded files in aggregate; before creation it removes the oldest files by modification time until both the
count and aggregate-byte bounds can fit the new snapshot. Files are validated, published atomically under
`data/configuration-snapshots/<uuid>.json`, and rejected if they are symlinks, malformed, oversized, contain unknown or
duplicate JSON fields/trailing tokens, or contain an invalid managed document identity/revision.

On POSIX-capable filesystems Control creates the snapshot directory owner-only and snapshot files owner read/write. POSIX
modes are not available on every platform, so operators must protect the entire Control data directory and every backup
with equivalent ACLs.

A snapshot is not a server backup and has no privileged restore endpoint. To restore, load one snapshot document as the
proposed content, preview it against the node's current revision, review the diff, and apply with the new one-time
approval. Snapshot retrieval returns the stored redacted document. During preview/apply, an unchanged redaction marker is
resolved against each target's current secret; a snapshot never recovers or overwrites an old credential.

## Read-only inspection protocol

### Request lifecycle

An administrator starts exactly one node query. `nodeId` is required and must match
`[A-Za-z0-9][A-Za-z0-9._-]{0,63}` before Control performs a registry lookup:

```http
POST /api/v1/inspections
Content-Type: application/json

{
  "nodeId": "backend-lobby",
  "query": {"kind": "overview", "filters": {}}
}
```

The node polls its independent inspection queue:

```http
POST /api/v1/nodes/backend-lobby/inspections
Content-Type: application/json

{"sessionId":"<node-session-uuid>"}
```

`204` means no work. A task uses the shape:

```json
{
  "inspectionId": "<uuid>",
  "query": {"kind": "overview", "filters": {}},
  "attemptId": "<uuid>"
}
```

The node posts the result to
`/api/v1/nodes/{nodeId}/inspections/{inspectionId}/result` with its `sessionId`, the same `attemptId`, `success`, a bounded
message, and either `data` or an error `code`. A successful result may omit/set `code` to `null` or send `"OK"` for
connector compatibility. A failed result must omit `data` and use a code matching `[A-Z][A-Z0-9_]{0,63}`.

The Bukkit connector runs these handlers on a dedicated single-thread daemon executor, separate from its
presence/configuration executor and never on the Bukkit primary thread. One slow read delays later inspections only;
shutdown cancels the inspection lane and waits at most five seconds for that worker.

Successful `data` is a JSON object with a common envelope:

```json
{
  "schemaVersion": 1,
  "kind": "overview",
  "generatedAt": "2026-08-30T12:00:00Z",
  "result": {}
}
```

`schemaVersion` must be the JSON integer `1` (not a string), `kind` must exactly match the assigned query,
`generatedAt` must parse as an ISO-8601 instant, and `result` must be a JSON object. Control limits serialized data to
512 KiB and the message to 4 KiB. There are at most 100 retained inspections. The task
lease is two minutes; an unleased active inspection expires five minutes after creation and a complete inspection is
pruned 15 minutes after creation. Retrying after a lost acknowledgement is safe because handlers are read-only.

Control audit records only the inspection kind. Filter values may contain a player identity or vote correlation ID and
must never be copied into audit or ordinary application logs. Claim, completion, and capability-loss cancellation update
in-memory inspection state transactionally with the audit append; an audit failure restores the prior lease/result state
so a retry can emit the missing record.

### Query allow-list

The outer Control DTO permits at most 12 filters. Every filter value is a JSON string on the wire. Each key must match
`[a-z][A-Za-z0-9]{0,39}` and ordinary values are bounded to 500 UTF-8 bytes before the node parses the stricter per-kind
schema below. The sole larger value is `reward-simulation`'s `proposal`, capped at 64 KiB. Examples:
`"days":"30"`, `"limit":"25"`, and `"includeDisabled":"false"`.

API clients should always send that canonical string shape. The current Jackson mapper can coerce some scalar values in an
administrator request before constructing `Map<String,String>`, but that is not a compatibility guarantee; the queued node
task is string-valued and VotingPlugin's handler validates text after selecting the kind.

| Kind | Allowed filters | Result and important semantics |
| --- | --- | --- |
| `overview` | none | Plugin/platform versions; configuration health; bounded data-storage mode; proxy mode; vote-site counts; auto-create state; configured/available/readable VoteLog state |
| `vote-site-health` | string `days` 1–365, default 30 | Configured site state, bounded logged aggregates, unmatched logged services, and bounded persisted service observations with no configured match |
| `player` | exactly one of `name` (1–16 characters) or `uuid` (canonical 36-character UUID) | Exact existing-player lookup; totals, points, streaks, up to 100 per-site last-vote rows, and backend pending-offline count; never player enumeration |
| `vote-log-summary` | string `days` 1–365, default 30 | Vote count, immediate/cached split, unique voters, and top 20 services/servers |
| `vote-log-search` | at most one of exact `player` (1–16 characters), `service` (1–64), or `server` (1–64); optional `event` and string `days`/`limit` | Most recent bounded logged-event rows; default 25 and maximum 100 |
| `vote-trace` | required canonical 36-character UUID `voteId`; optional string `days`/`limit` | Chronological logged events sharing one correlation ID; default 50 and maximum 100 |
| `vote-site-resolution` | required valid `serviceSite` (1–64 characters); optional string boolean `includeDisabled` | Non-creating resolution and whether automatic creation would be attempted; always reports no side effects |
| `reward-simulation` | required `proposal`, a JSON object encoded as one filter string | Validation, normalization, action count, and warnings only; never executes or saves rewards |
| `diagnostics` | none | Bounded redacted runtime/configuration health, VoteLog readability, and detected plugin names |

Unknown kinds, filters, proposal fields, or invalid types/ranges fail with `VALIDATION_ERROR`. VoteLog summary/search/trace
fail with `UNAVAILABLE` when logging is disabled, enabled without an initialized adapter, or unreadable. An oversized result fails with
`RESULT_TOO_LARGE`; an unexpected handler failure becomes `INSPECTION_FAILED` without exposing a stack trace.

Valid event filters are `VOTE_RECEIVED`, `VOTEMILESTONE`, `VOTE_STREAK_REWARD`, `TOP_VOTER_REWARD`, and
`VOTESHOP_PURCHASE`.

### Result field map

Every kind returns its fields under the common envelope's `result` object. Time fields below are VotingPlugin epoch-millis
values; `generatedAt` is the ISO-8601 string generated by the connector.

| Kind | Result fields |
| --- | --- |
| `overview` | `pluginVersion`, `platform`, `serverSoftware`, `serverVersion`, configured/enabled vote-site counts, `autoCreateVoteSites`, `processRewards`, `dataStorage`, `voteLoggingEnabled`, `voteLogAvailable`, `voteLogReadable`, proxy mode/method, `votifierDetected`, `configurationHealthy` |
| `vote-site-health` | `days`, `voteLoggingEnabled`, `voteLoggingAvailable`, `voteLogReadable`, `autoCreateVoteSites`, `sites`, `unmatchedLoggedServices`, `detectedUnconfiguredServices`, and truncation flags. Site rows always include identity/settings/reward presence and status; logged/immediate/cached counts and last-vote time are present only when VoteLog is readable. Status is `ACTIVE`, `DISABLED`, `SERVICE_SITE_MISSING`, `VOTE_LOG_UNAVAILABLE`, `VOTE_LOG_UNREADABLE`, or `NO_RECENT_VOTES` |
| `player` | Either `{found:false, entity:"player"}` or identity/online state, daily/weekly/monthly/all-time totals, points, streaks, `lastVoteTime`, `lastVotes`, `lastVotesTruncated`, and `pendingOfflineVotes` saturated at 100,000. Last-vote rows contain `siteKey`, `displayName`, `serviceSite`, and `time`, and include only stored keys that currently resolve as enabled sites |
| `vote-log-summary` | `days`, `total`, `immediate`, `cached`, `uniqueVoters`, top-20 `topServices` and `topServers` count rows |
| `vote-log-search` | `days`, `limit`, `entries`, `truncated`; each entry has `voteId`, `voteTime`, player UUID/name, service, server, event, context, status, and `cachedTotal` |
| `vote-trace` | normalized `voteId`, `found`, chronological `events` using the same entry schema, and `truncated` |
| `vote-site-resolution` | requested service/options, `matched`, optional matched-site identity/state, `wouldAutoCreate`, and `sideEffects:false` |
| `reward-simulation` | `valid`, `actionCount`, `wouldExecute:false`, `sideEffects:false`, `normalizedProposal`, and bounded warnings |
| `diagnostics` | All overview fields plus build/profile/Java/background-task/storage status, at most 128 detected plugin names, and `omittedSensitiveData` |

### Reward simulation proposal

The decoded `proposal` JSON is:

```json
{
  "scope": "site",
  "site": "PMC",
  "commands": ["eco give %player% 100"],
  "playerMessages": ["Thanks for voting"],
  "broadcastMessages": [],
  "items": [{"material": "DIAMOND", "amount": 2}],
  "money": 0,
  "permissions": [],
  "chancePercent": 100,
  "onlineOnly": false
}
```

The inspection request carries that object as a string, for example
`{"kind":"reward-simulation","filters":{"proposal":"{\"scope\":\"site\",...}"}}`. Use `JSON.stringify` (or the
equivalent standard JSON serializer); do not build this escaped text by concatenating user input.

`scope` is `site`, `every-site`, or `vote-party`; for site scope, `site` must match `[A-Za-z0-9_-]{1,64}` and name an
existing site. For a global scope it must be omitted, null, or empty. Each action collection has at most 20 entries. Command and message
entries are nonblank/single-line and at most 500 characters; permissions have the same rules with a 200-character limit.
Item material names are normalized to uppercase, must match `[A-Z0-9_]{1,80}`, resolve through Bukkit
`Material.matchMaterial`, and be item materials; amounts are 1–64. Money is a finite number from 0–1,000,000,000 and chance is finite from 0–100;
`onlineOnly` is a native boolean. At least one command, message, item, permission, or positive money value is required.
The result always contains
`wouldExecute:false` and `sideEffects:false`. The encoded proposal filter is capped at 64 KiB.

The same object can be persisted only through the PREVIEW/APPLY-only `reward-builder` quick setup. Persistence clears and
rebuilds exactly one selected path:

| Scope | File and replaced path |
| --- | --- |
| `site` | `VoteSites.yml` → `VoteSites.<site>.Rewards`; the named site must already exist |
| `every-site` | `VoteSites.yml` → `EverySiteReward` |
| `vote-party` | `SpecialRewards.yml` → `VoteParty.Rewards` |

Commands map to `Commands`; player and broadcast messages to `Messages.Player` / `Messages.Broadcast`; items to numbered
`Items.ControlItemN` material/amount entries; positive money to `Money`; and permissions to numbered
`AdvancedRewards.ControlPermissionN.TempPermission.{Permission,Expiration}` entries with `Expiration: 2147483647`.
Chance maps to `Chance`, while
online-only maps to `RewardType: ONLINE` instead of `BOTH`. The preset never executes the proposal and never changes a
different site, every-site rewards, or unrelated VoteParty settings. The regular revision, approval, backup, reload, and
rollback guarantees still apply.

## Vote-site and VoteLog semantics

The dedicated `auto-create-vote-sites` quick setup reads/writes only `Config.yml -> AutoCreateVoteSites`. Use it for the
prominent toggle rather than submitting the six unrelated fields in `common-settings`; its READ form takes no options.
Disabling automatic creation does not make vote-site resolution writable and does not delete previously observed service
names. It gates only inbound
unknown-service generation; explicit administrator command/GUI creation remains available.

`vote-site-health` keeps two unconfigured-service signals distinct. `unmatchedLoggedServices` is derived from retained
VoteLog rows and remains empty/non-authoritative unless `voteLogReadable` is true. `detectedUnconfiguredServices` is the
case-insensitive, deduplicated, sorted, at-most-100 view of VotingPlugin's persisted `GottenServiceSites` observations that do not match a
configured site's `ServiceSite`; its separate truncation flag reports overflow. That observation list remains useful when
VoteLogging is disabled and when automatic site creation is turned off. It is an inbox for review, not an automatic create
or approval action.

The dedicated `vote-logging` quick setup owns only `VoteLogging.Enabled`, `VoteLogging.PurgeDays` (`-1` disables automatic
purging, otherwise `1`–`3650`; `0` and other negatives are invalid), and `VoteLogging.UseMainMySQL`. It rejects unknown
options and never accepts or returns database connection fields or credentials. Its READ form takes no options and
round-trips `-1`. Configuring a dedicated connection remains a full redacted-editor change.

VoteLog is optional and SQL-backed. `voteLoggingEnabled` reports the current configuration. `voteLogAvailable` is true
only when logging is enabled and its table adapter exists; `voteLogReadable` additionally requires a bounded live probe
whose JDBC statement timeout is 10 seconds. The quick setup writes and reloads `Config.yml`, but it does not create or
close the runtime VoteLog manager. Restart VotingPlugin after changing `VoteLogging.Enabled`: immediately after disabling,
all inspection reads gate the possibly stale adapter and report available/readable false; immediately after enabling a
previously disabled instance, overview can report enabled true but available false until restart.

Summary, search, and trace require enabled, available, and readable state; otherwise they fail with `UNAVAILABLE` instead
of presenting legacy empty/zero SQL fallbacks as real data. Vote-site health remains useful without SQL: it reports
`voteLogReadable:false`, skips aggregates, and uses `VOTE_LOG_UNAVAILABLE` or `VOTE_LOG_UNREADABLE` rather than
`NO_RECENT_VOTES` for enabled rows with a configured service; `DISABLED` and `SERVICE_SITE_MISSING` retain precedence.
Readability is a point-in-time probe, not a transaction around the later query. A database failure after
a successful probe can still hit the legacy table method's empty/zero fallback; fully eliminating that narrow race needs a
future table API that propagates query errors.

The data views expose **logged events**, not a complete vote-delivery trace. Logged event rows can contain correlation ID,
event time, player UUID/name, service, server, event, context, `IMMEDIATE` or
`CACHED` status, and cached total. The log does not promise a row for every validation rejection, transport hop, duplicate
decision, executed reward command, command result, or expiry. A vote trace therefore means “all retained logged events
with this `voteId`”, not packet tracing.

Do not add an inspection escape hatch for SQL, table names, connection settings, raw logs, arbitrary player fields, fuzzy
player search, or all-player enumeration.

## HTTP resource reference

All write requests from a browser session require its `X-CSRF-Token`. API automation uses the admin bearer credential;
node resources require the bearer credential bound to the path node ID.

| Method | Resource | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/health` | public | Application, instance, protocol, and optional hosted-launch identity |
| `GET/POST` | `/api/v1/auth/setup` | public/setup code | First-run password state and one-time setup |
| `POST` | `/api/v1/auth/login` | password | Create bounded browser session |
| `GET` | `/api/v1/auth/session` | browser | Restore CSRF token after refresh |
| `POST` | `/api/v1/auth/logout` | browser + CSRF | Revoke current session |
| `GET/POST` | `/api/v1/enrollments` | admin/browser | List node IDs or rotate one node credential |
| `DELETE` | `/api/v1/enrollments/{nodeId}` | admin/browser | Revoke one node credential |
| `GET` | `/api/v1/nodes` | admin/browser | Stable paginated current topology |
| `POST` | `/api/v1/nodes/register` | matching node | Register/replace a process session |
| `PUT` | `/api/v1/nodes/{nodeId}/heartbeat` | matching node | Refresh liveness and capabilities |
| `PUT` | `/api/v1/nodes/{nodeId}/presence` | matching node | Replace proxy backend presence |
| `POST` | `/api/v1/configuration/{read,preview,apply}` | admin/browser + CSRF | Queue typed configuration work |
| `GET` | `/api/v1/operations` | admin/browser | List at most 100 newest-first summaries without retained file bodies; unused preview approval may be present |
| `GET` | `/api/v1/operations/{operationId}` | admin/browser | Read full bounded redacted aggregate/per-node detail and any unused preview approval |
| `POST` | `/api/v1/operations/{operationId}/retry` | admin/browser + CSRF | Reissue safe failed work as a new operation |
| `POST` | `/api/v1/nodes/{nodeId}/operations` | matching node | Claim one configuration task or `204` |
| `POST` | `/api/v1/nodes/{nodeId}/operations/{operationId}/result` | matching node | Complete one claimed configuration task |
| `POST` | `/api/v1/inspections` | admin/browser + CSRF | Queue one typed read-only query |
| `GET` | `/api/v1/inspections/{inspectionId}` | admin/browser | Read short-lived inspection status/result |
| `POST` | `/api/v1/nodes/{nodeId}/inspections` | matching node | Claim one inspection or `204` |
| `POST` | `/api/v1/nodes/{nodeId}/inspections/{inspectionId}/result` | matching node | Complete one claimed inspection |
| `GET/POST` | `/api/v1/snapshots` | admin/browser | List snapshot summaries or create from a completed read |
| `GET` | `/api/v1/snapshots/{snapshotId}` | admin/browser | Load one snapshot's full stored redacted documents |

Routes and methods are exact. Known resources return structured `405`; unknown paths return structured `404`. Errors use:

```json
{"error":{"code":"VALIDATION_ERROR","message":"Request validation failed","details":[]}}
```

## Threat model and hard limits

Control assumes an authenticated administrator may make intended changes, but it does not trust HTTP clients, nodes,
their clocks, returned strings, or durable files. Authentication does not encrypt traffic; use HTTPS or a trusted private
tunnel/network outside loopback.

| Boundary | Limit/behavior |
| --- | --- |
| HTTP request | 4 MiB; bounded Jackson depth/string/number constraints; duplicate and trailing JSON rejected |
| HTTP execution | 8 active request workers plus queue of 32; bounded request/response time |
| Browser sessions | 100; 30-minute idle and 8-hour absolute expiry; HttpOnly, SameSite=Strict cookie |
| Node operation targets | 1–100 distinct online capable nodes |
| Managed YAML / VoteSites sync source | 512 KiB per document/source |
| Ordinary option/filter | 500 UTF-8 bytes per value; a reward simulation/builder proposal is the sole 64 KiB exception |
| Retained operation detail | 8 MiB file content, 256 KiB changes, 256 KiB messages across live retained operations |
| Durable operation history | 1,000 entries, 100 nodes each, 24 hours, and 2 MiB; metadata only |
| Inspection | 100 retained; 512 KiB data; 4 KiB message; 2-minute lease |
| Snapshot | 100 snapshots and 64 MiB encoded aggregate; 100 documents and 8 MiB content per snapshot |
| Topology | 4,096 backends per snapshot; 65,536 retained across registry; 128 plugins per node and 16,384 total |
| Audit | bounded hash-chained JSONL; rotates at 5 MiB; values, credentials, tokens, and filters excluded |

Sensitive output must omit credentials, password/token/API-key/authorization values, database/Redis/MQTT connection
details, webhook URLs, raw logs, raw configuration in diagnostics, and unrestricted player records. Configuration reads
and snapshots contain bounded managed YAML with known secrets replaced by redaction markers. The diagnostics result
includes an explicit list of categories it omitted so a support recipient does not assume completeness.

## Change recipes for agents

### Add an inspection field

1. Confirm it is read-only, bounded, non-secret, and belongs to an existing kind.
2. Add it in VotingPlugin's typed handler and test the exact result, disabled/unavailable state, and size bound.
3. Render it as text in the WebUI and clear any cached value on logout.
4. Update this document and the paired `docs/control-agent-contract.md`.

### Add an inspection kind

1. Prefer extending an existing kind unless the authorization/data semantics genuinely differ.
2. Add the exact kind to Control's `InspectionQuery` and VotingPlugin's `ControlInspectionService` allow-lists.
3. Define exact filters, types, ranges, default/max rows, error states, and sensitive omissions before implementation.
4. Add coordinator, HTTP, connector, handler, and browser tests. If an old counterpart must not see the request, introduce
   a new versioned capability.

### Add a setting shortcut

1. Give the preset the narrowest ownership possible; do not rewrite unrelated settings.
2. Implement read/preview/apply through the existing quick-setup capability.
3. Preserve deterministic revisions, secret behavior, atomic write, reload, rollback, and audit boundaries.
4. Expose it through the same preview/approval UI; never write directly from a toggle.

### Add persistent Control data

1. Define a hard count and byte limit plus deterministic eviction.
2. Reject symlinks and malformed/oversized files, stage in the destination directory, force file data, publish atomically,
   and force the directory.
3. Never persist browser CSRF tokens, approval tokens, node raw credentials, new configuration secrets, or inspection
   filters/results containing player data. Snapshot content must remain the redacted node read, admin-only,
   owner-permissioned, and bounded.
