# VotingPlugin Control

VotingPlugin Control is a separate, local-first administration service for a VotingPlugin network. It provides
authenticated discovery of multiple BungeeCord and Velocity proxies, direct Bukkit backend enrollment, full VotingPlugin
YAML configuration control, guided setup, redacted snapshots/drift comparison, durable operation history, and typed
read-only vote/data diagnostics. The local WebUI uses the same versioned API. Control does not process votes, and
VotingPlugin does not depend on it for startup, joins, routing, rewards, or shutdown.

Maintainers and coding agents should read [AGENTS.md](AGENTS.md). The complete management, inspection, limits, and threat
model reference is [docs/control-management.md](docs/control-management.md).

## Trust and deployment boundary

One Control process can observe an entire network. Browsers communicate with its WebUI/API; each proxy and Bukkit
connector independently initiates outbound HTTP(S) requests to that same Control listener. Control works without
Internet or a cloud account. Node calls require explicitly created, node-bound bearer credentials; management reads
require a separate admin credential. Browser management uses a distinct owner-chosen password. Long-lived raw credentials
are never stored by Control: `data/credentials.json` contains SHA-256 verifiers for 256-bit random tokens and a salted,
600,000-iteration PBKDF2-HMAC-SHA256 WebUI password verifier.

Configuration snapshots persist the redacted managed-file content returned by a completed read; known secret paths use a
placeholder rather than storing credentials. Their list API omits content, full retrieval is admin-only, and Control
applies owner-only POSIX permissions where supported. Protect the entire data directory and its backups with equivalent
operating-system access controls.

Control binds to `127.0.0.1` by default. On first start it creates a permission-restricted
`data/web-setup-code.txt` containing a one-time 256-bit setup code; `credentials.json` stores only its SHA-256 verifier.
The WebUI consumes that code when the owner creates the first password and immediately deletes the raw-code file.
A non-loopback first-run listener exposes no management data without that code, but authentication does not encrypt
traffic: use HTTPS or a trusted private tunnel/network whenever traffic can cross an untrusted network. Do not publish
the HTTP service directly to the Internet.

## Requirements, build, and run

- Java 17 or newer (the produced JAR targets Java 17)
- Maven 3.9 or newer

```shell
mvn clean verify
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar
```

The application version comes from the JAR manifest generated from the Maven project version. Development classpath runs
report `development` rather than maintaining a second version literal.

| Variable | Default | Constraint |
| --- | --- | --- |
| `CONTROL_HOST` | `127.0.0.1` | Valid bind host; use `0.0.0.0` inside a container only behind an allocated port and trusted HTTPS/private proxy |
| `CONTROL_PORT` | `8080` | `0`–`65535`; `0` selects a free port |
| `CONTROL_DATA_DIR` | `data` | Writable directory for identity and credential verifiers |
| `CONTROL_OFFLINE_TIMEOUT_SECONDS` | `90` | `1`–`3600` |
| `CONTROL_REQUEST_TIMEOUT_SECONDS` | `10` | `1`–`60`; bounds stalled JDK HTTP exchanges |
| `CONTROL_SECURE_COOKIE` | `false` | Set `true` only when browsers reach Control through a trusted HTTPS reverse proxy |
| `CONTROL_TRUSTED_PROXY_ADDRESSES` | empty | Comma-separated IP literals for reverse proxies allowed to supply `X-Forwarded-For` login admission identity |
| `CONTROL_LAUNCH_ID` | empty | Optional UUID echoed by health checks so a supervising VotingPlugin can verify ownership of the listener |
| `CONTROL_PARENT_PID` | empty | Optional supervising VotingPlugin process ID; hosted Control exits when that parent process ends |

The server also uses a bounded HTTP executor (8 active requests and a 32-request queue), a 4 MiB request limit, bounded
JSON depth/string/number sizes, a two-worker password-verification executor with per-client admission, and a bounded invalid-authentication failure limit. Configure the exact reverse-proxy IP addresses when HTTPS terminates upstream; forwarding headers from every other peer are ignored. Valid enrolled/admin credentials remain
usable even while invalid traffic is throttled. Shutdown stops the server and its daemon request workers without waiting
indefinitely.

Claimed configuration and inspection tasks include an `attemptId`. Nodes must echo it and the exact session that claimed
the lease in the result, so neither an expired/reissued attempt nor a post-claim reconnect can complete stale work.

Open the WebUI after the first start, read the one-time value from `data/web-setup-code.txt` with the server file
manager, and create the WebUI password in the browser. No server command is required. The setup code is shown only in that
permission-restricted file, is rate-limited at the HTTP boundary, becomes invalid as soon as setup completes, and is
deleted after the password verifier is committed.

The legacy `web-password` owner command remains available for offline recovery and rotation. It reads the password twice
without echo and never accepts it as a command-line argument. The static shell is public on the Control listener, but
topology, enrollment, and configuration operations require either the admin bearer token (API automation) or a
password-authenticated browser session. Browser sessions are bounded to 100, expire after 30 minutes idle or 8 hours
absolute, use an HttpOnly/SameSite=Strict cookie, require a per-session CSRF token for writes, and are invalidated
immediately by password rotation. Cookies gain the `Secure` flag when Control is served through HTTPS. Static assets use a
restrictive Content Security Policy and the browser calls the same `/api/v1` endpoints.

## Enrollment

After signing in, use **Node enrollment** in the WebUI to create or rotate a credential for an exact node ID. Copy the
one-time value into that node's configured `control-credential.txt` with the server file manager, then reload or restart
the node. The same page lists enrolled IDs and can revoke them. This is the normal command-free setup path.

Owner commands remain available for offline recovery and automation. They modify the configured data directory and print
a new secret once. Stop shell history capture or otherwise handle the output as a password.

A supervising VotingPlugin may instead use the internal `enroll-verifier` owner command. Each node generates and retains
its own 256-bit credential and sends only its lowercase SHA-256 verifier through the trusted proxy/backend enrollment
handshake. The raw credential never crosses that channel or enters Control's process arguments.

```shell
# Create or rotate a credential bound to proxy-a
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar enroll proxy-a data

# Enroll every Bukkit backend separately when full file control is wanted
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar enroll backend-lobby data

# Install a verifier generated by a supervised node (internal hosting automation)
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar enroll-verifier backend-lobby <sha256-verifier> data

# Immediately revoke it
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar revoke proxy-a data

# Create or rotate the credential used for management GET endpoints
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar admin-token data

# Create or rotate the interactive WebUI password (prompted; never passed in argv)
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar web-password data
```

Rotating replaces the prior verifier immediately. The store is reread for every authentication decision, so a running
Control process observes rotation and revocation without restart. A credential for one node cannot register or update a
different node identity. Credentials belong in the `Authorization: Bearer ...` header, never URLs, logs, health output,
node listings, diagnostics, or plugin messages.

## Protocol and API

Protocol version `1` is exact for this milestone. Unsupported versions and unavailable required capabilities return a
structured `409`. Unknown advertised capabilities are retained but not negotiated; accepted capabilities are the
intersection with Control's supported capabilities. A heartbeat replaces the advertised capability set, so removal is
explicit. Unknown JSON fields are accepted for additive forward compatibility, while duplicate fields and trailing JSON
are rejected.

All errors have the stable form:

```json
{"error":{"code":"VALIDATION_ERROR","message":"Request validation failed","details":[]}}
```

| Method | Endpoint | Authentication | Behavior |
| --- | --- | --- | --- |
| `GET` | `/`, `/app.js`, `/app.css` | none | Minimal local WebUI static assets |
| `GET` | `/api/v1/health` | none | Health plus Control identity/application/protocol version |
| `GET` | `/api/v1/auth/setup` | none | Report whether one-time first-run password setup is required |
| `POST` | `/api/v1/auth/setup` | one-time file-held setup code | Atomically create the first WebUI password, consume the code, and create a browser session |
| `POST` | `/api/v1/auth/login` | WebUI password | Create a bounded browser session and return its CSRF token |
| `GET` | `/api/v1/auth/session` | WebUI session | Restore the in-memory CSRF token after a page refresh; invalid/absent sessions return `204` |
| `POST` | `/api/v1/auth/logout` | WebUI session + CSRF | Revoke the current browser session and expire its cookie |
| `GET` | `/api/v1/enrollments` | admin or WebUI session | List enrolled node IDs without exposing verifiers |
| `POST` | `/api/v1/enrollments` | admin or WebUI session + CSRF | Create/rotate a node-bound credential and return it once |
| `DELETE` | `/api/v1/enrollments/{nodeId}` | admin or WebUI session + CSRF | Revoke the node credential |
| `POST` | `/api/v1/nodes/register` | node | Idempotently create/replace one enrolled proxy or Bukkit session |
| `PUT` | `/api/v1/nodes/{nodeId}/heartbeat` | matching node | Refresh liveness and replace capability advertisement |
| `PUT` | `/api/v1/nodes/{nodeId}/presence` | matching node | Replace that session's backend snapshot |
| `POST` | `/api/v1/nodes/{nodeId}/operations` | matching node | Claim one queued configuration task, or `204` |
| `POST` | `/api/v1/nodes/{nodeId}/operations/{operationId}/result` | matching node | Complete that node's task |
| `GET` | `/api/v1/nodes?offset=0&limit=50` | admin or WebUI session | Stable node-ID ordering; limit `1`–`100` |
| `POST` | `/api/v1/configuration/read` | admin or WebUI session + CSRF | Queue a typed read for selected capable nodes |
| `POST` | `/api/v1/configuration/preview` | admin or WebUI session + CSRF | Queue independent validation and normalized diffs |
| `POST` | `/api/v1/configuration/apply` | admin or WebUI session + CSRF, plus one-time approval | Apply the exact successful preview |
| `GET` | `/api/v1/operations` | admin or WebUI session | List at most 100 newest-first summaries without retained file bodies; unused preview approval may be present |
| `GET` | `/api/v1/operations/{operationId}` | admin or WebUI session | Read full bounded redacted per-node detail and any live approval token |
| `POST` | `/api/v1/operations/{operationId}/retry` | admin or WebUI session + CSRF | Reissue eligible failed work as a new operation |
| `POST` | `/api/v1/inspections` | admin or WebUI session + CSRF | Queue one typed read-only query for a capable Bukkit node |
| `GET` | `/api/v1/inspections/{inspectionId}` | admin or WebUI session | Read short-lived inspection status/result |
| `POST` | `/api/v1/nodes/{nodeId}/inspections` | matching node | Claim one read-only inspection, or `204` |
| `POST` | `/api/v1/nodes/{nodeId}/inspections/{inspectionId}/result` | matching node | Complete that inspection attempt |
| `GET`, `POST` | `/api/v1/snapshots` | admin or WebUI session; CSRF for POST | List summaries or save a named snapshot from a completed file read |
| `GET` | `/api/v1/snapshots/{snapshotId}` | admin or WebUI session | Load one durable snapshot's full redacted file content |

Routes are exact. Child suffixes do not inherit a handler, every known endpoint has an intentional method/structured 405,
and all unknown endpoints return a structured 404.

Registration includes a stable node ID and a random process session ID. A new session replaces the old registration and
clears its old topology. Presence snapshots are full replacements, contain at most 4096 unique backend IDs, and use a
monotonic sequence within the registered session. Control retains at most 65536 backend entries across the whole registry;
an over-capacity replacement is rejected atomically with `409 REGISTRY_LIMIT`. Replayed or out-of-order sequence numbers are idempotently ignored.
Control records its own observation time; it does not trust remote wall-clock time for online/offline decisions.

Configuration is split into independently negotiated capabilities. `config.proxy-routing.v1` exposes typed proxy routing.
`config.files.v1` manages `Config.yml`, `VoteSites.yml`, `SpecialRewards.yml`, `GUI.yml`, `Shop.yml`, and
`BungeeSettings.yml` on enrolled Bukkit nodes through a bounded YAML editor. `config.quick-setup.v1` supplies standalone,
proxy-backend, vote-site, easy-reward, common-settings, auto-create-vote-sites, vote-logging, vote-party, and typed
reward-builder presets.
The auto-create preset owns only `AutoCreateVoteSites`; the logging preset owns only enabled state, purge retention
(`-1` disables purging or `1`–`3650` days), and main-MySQL reuse, never connection credentials. Readable presets load their
installed values before editing.
`easy-reward` appends a command only when it is not already present and sets a player message only when that path is
absent, so it never replaces an existing reward. The PREVIEW/APPLY-only
`reward-builder` accepts the same bounded proposal used by the simulator and replaces only the selected site's,
every-site, or vote-party Rewards subtree; unrelated reward scopes remain intact. Reward-safe VoteSites
synchronization is part of the same Quick Setup workflow: it copies site definitions while preserving rewards,
credentials, reward files, and target-only sites on every destination. Bukkit registration also reports a bounded
set of installed plugin names (at most 16384 entries across the registry) so the WebUI can offer editable Minecraft,
Essentials, CMI, and LuckPerms command suggestions;
these are suggestions, not executed commands, and follow the normal preview/approval path. Password, secret, token, API-key, authorization, and webhook-secret values are masked
on every read; leaving the redaction marker in a proposal preserves the current value. A newly entered secret is accepted
only in the authenticated proposal and is not returned in results or written to the audit log.

Control and VotingPlugin both enforce fixed quick-setup preset/option schemas; unknown presets/options are rejected rather
than becoming arbitrary YAML writes. The WebUI settings catalog is a static versioned reference over these typed paths,
not a generic setting API.

Read actions load only the primary server shown in the configuration header. Preview and apply still cover every server
explicitly included in configuration changes, so one slow secondary node does not delay opening the editor or guided form.

`data.inspect.v1` is a separate read-only lane for overview, vote-site health (including persisted unconfigured service
observations), exact-player data, bounded VoteLog summary/search/correlation trace, non-creating service-site resolution,
no-side-effect reward simulation, and redacted diagnostics. It accepts only allow-listed string filters and bounded result
schemas; there is no raw SQL, arbitrary player
enumeration, command execution, generic file/database browsing, or write operation. VoteLog output is labeled **logged
events**, not a complete network delivery trace. Overview/diagnostics expose a bounded `voteLogReadable` probe;
summary/search/trace fail `UNAVAILABLE` when that probe fails, while vote-site health labels SQL data unavailable/unreadable
instead of turning a query failure into “no recent votes.”

Changing `VoteLogging.Enabled` through guided setup updates and reloads `Config.yml`, but does not recreate or close the
runtime VoteLog manager. Restart VotingPlugin after either transition. Inspections immediately gate disabled logging even
if an old adapter remains; after enabling a previously disabled instance, overview reports enabled but unavailable until
the restart initializes the adapter.

Preview parses YAML and calculates path-level changes without writing. Apply consumes a
single-use random approval token, carries each previewed revision to that node, and reports partial failures instead of a
network-wide success. Proxies and Bukkit nodes create local backups, require atomic replacement, reload, and restore the
backup if reload fails. The WebUI labels a rolled-back node as not saved and displays the bounded reload cause returned by
the plugin. Bukkit reads normalize YAML; administrators should expect comments and formatting to be normalized
when applying through the full editor.

`configuration-audit.jsonl` records bounded, append-only, hash-chained operation metadata and rotates once at 5 MiB. A
cross-process lifetime lock prevents two Control processes from forking the same audit chain. Both
the active and retained segment are verified against a durable atomic tail/count checkpoint before startup accepts new
operations, so record-boundary truncation also fails closed. A durable pending-append record makes audit and checkpoint
publication recoverable when the process stops between their writes. It does not contain configuration
values, credentials, or approval tokens. Operation queues are bounded; abandoned operations expire 15 minutes after
creation and completed operations are pruned 24 hours after creation.

`configuration-operations.json` atomically retains at most 24 hours/1,000 entries/2 MiB of redacted operation history. It
excludes configuration/options values, file contents, approval tokens, result messages/changes, credentials, and task
attempts. Completed-result session IDs are retained so restart-required setup warnings survive a Control restart; the
operations API derives that state from the full retained journal independently of its bounded rendered history. The IDs
cannot authorize or resume work. Overlapping VoteLogging applies and retries for the same backend are rejected until the
running apply completes, so creation order cannot disagree with completion order. After restart, unfinished targets appear
failed with `CONTROL_RESTARTED`; recovered
entries are history-only and require a fresh read or preview. Named snapshots under `configuration-snapshots/` retain bounded redacted file-read
results. Loading one for restore merely fills the editor—the normal preview, revision check, approval, backup, reload, and
rollback workflow still applies; redaction placeholders preserve each target's current secrets. The store is capped at
100 snapshots and 64 MiB of encoded files and prunes the oldest files to admit a new snapshot.

### WebUI management suite

- **Setup** provides a readiness checklist, browser-local non-secret profiles, dedicated auto-create and VoteLogging cards,
  the existing typed setup assistant, a reward builder with simulation plus preview/apply, and a searchable common-setting
  catalog.
- **Votes & Data** provides bounded server overview, exact player lookup, vote-site health and detected unconfigured
  services, a 30-day VoteLog summary, logged-event search, vote-ID correlation, and a non-creating service-site test.
- **Network Doctor** combines redacted node diagnostics with Control's current topology and can download that bounded JSON
  result; it never sends a vote or runs a reward.
- **Configurations** uses a 30-second session-bound read cache, compares exact revisions plus bounded redacted line
  differences across selected nodes, and saves named redacted snapshots for previewed restore.
- **Activity** shows newest-first durable redacted progress/recovery history and offers safe same-process retries only where
  the API marks an operation retryable.

A node is online only while `lastSeen + offlineTimeout` is strictly after Control's current time. The exact timeout boundary
is offline. Backend entries distinguish whether authoritative backend presence is known from the existing VotingPlugin
presence protocol; server addresses and all unrelated configuration are excluded.

The immutable correlated request/response envelope DTOs are reserved for later typed node operations and relay transports.
They are not misleadingly used by these simple HTTP resource endpoints.

## Troubleshooting

- `401 UNAUTHORIZED`: the credential is missing, invalid, revoked, rotated, or enrolled for another node ID.
- `403 CSRF_REQUIRED`: refresh/sign in again and retry the WebUI write from the same session.
- `409 SESSION_MISMATCH`: re-register; Control has a newer process session for that stable node.
- `409 REGISTRY_LIMIT`: reduce a node's backend snapshot before retrying; the previous snapshot remains intact.
- `409 UNSUPPORTED_PROTOCOL` / `INCOMPATIBLE_CAPABILITIES`: upgrade the older side before retrying.
- `409 RETRY_REQUIRES_INPUT`: recovered history deliberately lacks sensitive proposal input; start a fresh read/preview.
- Vote/data view says unavailable: select an online Bukkit node that negotiated `data.inspect.v1`; VoteLog-specific reads
  also require enabled, initialized, and currently readable SQL VoteLogging. Restart VotingPlugin after changing
  `VoteLogging.Enabled` through guided setup so the manager lifecycle matches the new configuration.
- Node stays offline: verify its connector is enabled, the node ID matches enrollment, and heartbeat timeouts permit
  the configured interval.
- Container/LAN access: bind `0.0.0.0` inside the container rather than the VM's address, allocate the port, and place it
  behind trusted HTTPS/private routing. Complete the one-time browser setup using the code file before normal sign-in.
- Corrupt `instance-id` or credential data fails closed; restore a known backup or deliberately recreate the affected file
  and re-enroll nodes.

## Intentionally out of scope

This milestone has no arbitrary command execution, direct backup-rollback endpoint, topology persistence/history, raw
support archive, cloud relay, or remote-support sessions. Current topology and active task inputs remain in memory, so
nodes automatically re-register after a Control restart and an interrupted change requires a new preview. Redacted
operation history, audit metadata, and configuration snapshots persist without making an ambiguous write resumable.
Manual installation remains supported; VotingPlugin may also opt in to verified download and child-process hosting.
