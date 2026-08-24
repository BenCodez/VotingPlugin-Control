# VotingPlugin Control

VotingPlugin Control is the local-first administration service for a VotingPlugin network. This repository currently
contains the first runnable protocol and service foundation—not a VotingPlugin integration or a finished management UI.

## Architecture and boundaries

Control is one local application intended to observe and, in later milestones, coordinate multiple BungeeCord and
Velocity proxies and their backend servers. VotingPlugin nodes will connect to Control; Control is never in the vote
processing path. A stopped or unreachable Control instance must therefore have no effect on voting.

The protocol/domain package has no HTTP, Bukkit, BungeeCord, Velocity, or cloud dependencies. HTTP is the first transport
adapter. The immutable request/response envelope DTOs provide the correlation and version fields needed by future
transports. An optional cloud relay can later carry the same versioned API over an outbound authenticated WebSocket.
Control remains authoritative and local access continues without cloud connectivity.

The server binds to `127.0.0.1` by default. **There is no authentication in this milestone; do not bind it to an
untrusted interface.** Registration is discovery data, not an authenticated identity proof. The API accepts unknown JSON
fields so additive fields from newer peers can be ignored, but requires protocol version `1` and validates all understood
fields. Bodies are limited to 16 KiB and node listings are paginated (maximum 100 items).

## Build and run

Requirements: JDK 17+ and Maven 3.9+.

```shell
mvn clean verify
java -jar target/votingplugin-control-0.1.0-SNAPSHOT-all.jar
```

The service stores only its stable Control instance UUID under `./data`; node registrations are currently in memory.
Configuration is available through environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `CONTROL_HOST` | `127.0.0.1` | Listen address |
| `CONTROL_PORT` | `8080` | Listen port (`0` selects a free port) |
| `CONTROL_DATA_DIR` | `data` | Local identity/data directory |

Example health check: `curl http://127.0.0.1:8080/api/v1/health`.

## API v1

All bodies and responses use JSON. Errors have the form
`{"error":{"code":"VALIDATION_ERROR","message":"...","details":["..."]}}`.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `GET` | `/api/v1/health` | Health, application/protocol version, and Control identity |
| `POST` | `/api/v1/nodes/register` | Register a node, or replace discovery metadata for the same stable node ID |
| `PUT` | `/api/v1/nodes/{nodeId}/heartbeat` | Refresh last-seen and optionally negotiated capabilities |
| `GET` | `/api/v1/nodes?offset=0&limit=50` | List nodes with computed online state |

Registration fields are `nodeId`, `displayName`, `platform` (`BUNGEECORD`, `VELOCITY`, `BUKKIT`, or `OTHER`),
`pluginVersion`, `protocolVersion`, and `capabilities`. Capability identifiers are bounded lowercase tokens such as
`status.read` or `servers.list`. A node is offline when it has not registered or sent a heartbeat for 90 seconds.

## Planned security and management model

Configuration management will start with a narrow typed domain—not unrestricted YAML—and use revision reads,
per-target validation, preview, compare-and-set application, backup, atomic writes, reload-after-persist, per-node results,
and local audit records. APIs will redact credentials, database passwords, encryption keys, webhook secrets, and device
credentials.

Remote support will use expiring, revocable, permission-scoped owner invitations. Typed operations (status, redacted
configuration, diagnostics, proposing changes, and owner-approved application) will be audited locally. It will not expose
a shell, arbitrary files, raw databases, or unrestricted console commands.

Future distribution will support explicit opt-in, pinned compatible versions, signed metadata and SHA-256 verification,
staging, atomic activation, rollback retention, health checks, manual installs, disabling updates, and an update lock while
configuration work is active.

## Intentionally out of scope

This milestone does not include a WebUI, persistence of node registrations, TLS/authenticated node enrollment,
VotingPlugin-side code, server-presence ingestion, configuration mutation, audit storage, release downloading, automatic
updates, cloud service, relay connection, or remote-support sessions. VotingPlugin integration details must be based on
its actual implementations before a connector is added; that source was not available in this development environment.
