# VotingPlugin Control

VotingPlugin Control is a separate, local-first administration service for a VotingPlugin network. The current milestone
provides authenticated discovery of multiple BungeeCord and Velocity proxies, direct Bukkit backend enrollment, full
VotingPlugin YAML configuration control, and common quick-setup workflows. It includes a local WebUI over the same
versioned API. It does not process votes, and VotingPlugin
does not depend on it for startup, joins, routing, or shutdown.

## Trust and deployment boundary

One Control process can observe an entire network:

```text
Browser -> Control WebUI/API <- Proxy A / Proxy B / Proxy C
                          ^--- Bukkit backend A / B / C
```

Proxy and Bukkit connectors initiate outbound HTTP(S) requests. Control works without
Internet or a cloud account. Node calls require explicitly created, node-bound bearer credentials; management reads
require a separate admin credential. Browser management uses a distinct owner-chosen password. Raw credentials are never
stored by Control: `data/credentials.json` contains SHA-256 verifiers for 256-bit random tokens and a salted,
600,000-iteration PBKDF2-HMAC-SHA256 WebUI password verifier.

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

Claimed configuration tasks include an `attemptId`. Nodes must echo it in the result so an expired and reissued lease
cannot be completed by a stale execution from the same node session.

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

```shell
# Create or rotate a credential bound to proxy-a
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar enroll proxy-a data

# Enroll every Bukkit backend separately when full file control is wanted
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar enroll backend-lobby data

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
| `GET` | `/api/v1/operations/{operationId}` | admin or WebUI session | Read overall and per-node operation status |

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
proxy-backend, vote-site, easy-reward, common-settings, and vote-party presets. Bukkit registration also reports a bounded
set of installed plugin names (at most 16384 entries across the registry) so the WebUI can offer editable Minecraft,
Essentials, CMI, and LuckPerms command suggestions;
these are suggestions, not executed commands, and follow the normal preview/approval path. Password, secret, token, API-key, authorization, and webhook-secret values are masked
on every read; leaving the redaction marker in a proposal preserves the current value. A newly entered secret is accepted
only in the authenticated proposal and is not returned in results or written to the audit log.

Preview parses YAML and calculates path-level changes without writing. Apply consumes a
single-use random approval token, carries each previewed revision to that node, and reports partial failures instead of a
network-wide success. Proxies and Bukkit nodes create local backups, require atomic replacement, reload, and restore the
backup if reload fails. Bukkit reads normalize YAML; administrators should expect comments and formatting to be normalized
when applying through the full editor.

`configuration-audit.jsonl` records bounded, append-only, hash-chained operation metadata and rotates once at 5 MiB. A
cross-process lifetime lock prevents two Control processes from forking the same audit chain. Both
the active and retained segment are verified against a durable atomic tail/count checkpoint before startup accepts new
operations, so record-boundary truncation also fails closed. A durable pending-append record makes audit and checkpoint
publication recoverable when the process stops between their writes. It does not contain configuration
values, credentials, or approval tokens. Operation queues are bounded; abandoned operations expire after 15 minutes and
completed operations after 24 hours. Active operations are intentionally lost on Control restart and must be previewed
again.

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
- Node stays offline: verify its connector is enabled, the node ID matches enrollment, and heartbeat timeouts permit
  the configured interval.
- Container/LAN access: bind `0.0.0.0` inside the container rather than the VM's address, allocate the port, and place it
  behind trusted HTTPS/private routing. Complete the one-time browser setup using the code file before normal sign-in.
- Corrupt `instance-id` or credential data fails closed; restore a known backup or deliberately recreate the affected file
  and re-enroll nodes.

## Intentionally out of scope

This milestone has no arbitrary command execution, manual rollback endpoint, topology persistence/history, diagnostics
bundle, cloud relay, or remote-support sessions. Node and operation state is currently in memory, so nodes automatically
re-register after a Control restart and an interrupted change requires a new preview. Manual installation
remains supported; the companion VotingPlugin development PR can also opt in to verified download and child-process hosting.
