# VotingPlugin Control

VotingPlugin Control is a separate, local-first administration service for a VotingPlugin network. The current milestone
provides authenticated, read-only discovery of multiple BungeeCord and Velocity proxies and the backend servers each
proxy observes. It does not process votes, and VotingPlugin does not depend on it for startup, joins, routing, or shutdown.

## Trust and deployment boundary

One Control process can observe an entire network:

```text
Browser (future local UI) -> Control <- Proxy A / Proxy B / Proxy C <- backend servers
```

Proxy connectors initiate outbound HTTP(S) requests. Bukkit backend nodes do not connect directly. Control works without
Internet or a cloud account. Node calls require explicitly created, node-bound bearer credentials; management reads
require a separate admin credential. Raw credentials are never stored by Control: `data/credentials.json` contains only
SHA-256 verifiers for 256-bit random tokens.

Control binds to `127.0.0.1` by default. A non-loopback bind is refused until an admin credential exists. Authentication
does not encrypt traffic: use HTTPS or a trusted private tunnel/network when traffic can cross an untrusted network. Do
not publish the HTTP service directly to the Internet.

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
| `CONTROL_HOST` | `127.0.0.1` | Valid bind host; non-loopback requires an admin credential |
| `CONTROL_PORT` | `8080` | `0`–`65535`; `0` selects a free port |
| `CONTROL_DATA_DIR` | `data` | Writable directory for identity and credential verifiers |
| `CONTROL_OFFLINE_TIMEOUT_SECONDS` | `90` | `1`–`3600` |
| `CONTROL_REQUEST_TIMEOUT_SECONDS` | `10` | `1`–`60`; bounds stalled JDK HTTP exchanges |

The server also uses a bounded HTTP executor (8 active requests and a 32-request queue), a 64 KiB request limit, bounded
JSON depth/string/number sizes, and a global authentication-failure limit. Shutdown stops the server and its daemon request
workers without waiting indefinitely.

## Enrollment

Owner commands modify the configured data directory and print a new secret once. Stop shell history capture or otherwise
handle the output as a password.

```shell
# Create or rotate a credential bound to proxy-a
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar enroll proxy-a data

# Immediately revoke it
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar revoke proxy-a data

# Create or rotate the credential used for management GET endpoints
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar admin-token data
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
| `GET` | `/api/v1/health` | none | Health plus Control identity/application/protocol version |
| `POST` | `/api/v1/nodes/register` | node | Idempotently create/replace one enrolled proxy session |
| `PUT` | `/api/v1/nodes/{nodeId}/heartbeat` | matching node | Refresh liveness and replace capability advertisement |
| `PUT` | `/api/v1/nodes/{nodeId}/presence` | matching node | Replace that session's backend snapshot |
| `GET` | `/api/v1/nodes?offset=0&limit=50` | admin | Stable node-ID ordering; limit `1`–`100` |

Routes are exact. Child suffixes do not inherit a handler, every known endpoint has an intentional method/structured 405,
and all unknown endpoints return a structured 404.

Registration includes a stable node ID and a random process session ID. A new session replaces the old registration and
clears its old topology. Presence snapshots are full replacements, contain at most 4096 unique backend IDs, and use a
monotonic sequence within the registered session. Replayed or out-of-order sequence numbers are idempotently ignored.
Control records its own observation time; it does not trust remote wall-clock time for online/offline decisions.

A node is online only while `lastSeen + offlineTimeout` is strictly after Control's current time. The exact timeout boundary
is offline. Backend entries distinguish whether authoritative backend presence is known from the existing VotingPlugin
presence protocol; server addresses and all unrelated configuration are excluded.

The immutable correlated request/response envelope DTOs are reserved for later typed node operations and relay transports.
They are not misleadingly used by these simple HTTP resource endpoints.

## Troubleshooting

- `401 UNAUTHORIZED`: the credential is missing, invalid, revoked, rotated, or enrolled for another node ID.
- `409 SESSION_MISMATCH`: re-register; Control has a newer process session for that stable node.
- `409 UNSUPPORTED_PROTOCOL` / `INCOMPATIBLE_CAPABILITIES`: upgrade the older side before retrying.
- Node stays offline: verify the proxy connector is enabled, the node ID matches enrollment, and heartbeat timeouts permit
  the configured interval.
- LAN startup is refused: create the admin token first and protect transport with HTTPS/private tunneling.
- Corrupt `instance-id` or credential data fails closed; restore a known backup or deliberately recreate the affected file
  and re-enroll nodes.

## Intentionally out of scope

This milestone has no configuration reads or writes, WebUI, topology persistence/history, audit log, diagnostics bundle,
automatic distribution, cloud relay, or remote-support sessions. Node registry state is currently in memory, so proxies
automatically re-register after a Control restart. Manual installation remains supported.
