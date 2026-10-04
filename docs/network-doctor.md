# Network Doctor

Network Doctor is a read-only, bounded setup validator. It never sends a vote, changes configuration, executes a command,
scans a network, reads an arbitrary path, or becomes a dependency of vote processing. A successful check proves only the
specific reported observation. It does not certify voting-site uptime or end-to-end reward execution.

## Results and evidence

Results contain stable `id`, `category`, `status`, `title`, `explanation`, `affectedNodes`, `evidence`, `nextAction`, and
`restartRequired` fields. Statuses are `PASS`, `WARNING`, `FAIL`, `UNKNOWN`, and `INFO`. The default view groups results by
category and emphasizes FAIL, WARNING, and UNKNOWN; PASS/INFO are collapsed. The download contains the same structured
checks, including collapsed results. Unknown evidence is never replaced by a successful Boolean default.

A run requests the empty-filter `network-health` inspection from up to 100 online nodes that negotiate **both**
`data.inspect.v1` and `data.network-health.v1`. At most three browser requests are in progress concurrently. Offline,
older, timed-out, incompatible, malformed, or reconnected nodes provide no usable evidence and therefore produce UNKNOWN.
The server uses only validated results from the current node session, with a five-minute maximum age measured from its
own request creation time. A newer pending, rejected or failed observation invalidates an earlier success. Evidence is short-lived in memory,
not added to configuration history. Peer timestamps cannot extend freshness.

`GET /api/v1/network-doctor` requires the same administrator/browser authentication as node inspection views. It does not
accept user-supplied evidence. The server evaluates its current registry plus eligible inspection results. The report is
limited to 100 nodes and 500 checks; limits are explicitly reported as UNKNOWN/truncated. Missing nodes or omitted checks
have not passed. Re-run checks for the relevant network when evidence expires.

## Checks

- **Control & Topology:** offline/stale/unsupported evidence, proxy/backend relationships, reported backend enrollment,
  missing presence, bounded/incomplete inventories. Enrollment alone does not establish a shared network.
- **Proxy Setup:** blank/PleaseSet backend identity, duplicate/case-only identity and dash-to-underscore storage collisions,
  proxy mode, configured transport validity and mismatches, unsupported BungeeManageTotals:false, dedicated proxy with
  plugin messaging, OnlineMode/Bedrock prefix/time offset/milestone and month-total consistency where reported.
- **Transport:** initialization, configured versus active transport drift/restart, channel and encryption mismatch,
  authenticated transport mode/key fingerprint mismatch, Redis SSL and Redis/MQTT namespace mismatch, MQTT ClientID
  duplication, HTTP endpoint/enrollment/identity/stale connection code, MYSQL recommendation, reported status probes.
  Zero carrier players under PLUGINMESSAGING is UNKNOWN/waiting, never a broken-network FAIL.
- **Votifier:** required provider/listener where evidence proves ingress, synthetic-event provider compatibility,
  competing implementations, enabled VotifierPlus socket forwarding alongside VotingPlugin delivery, optional-provider
  diagnostics and external destination uncertainty.
- **Routing:** unknown backend references, blocked/whitelist overlap, no eligible destinations, intentional exclusions,
  allow-unjoined prerequisites and wait-for-online combinations. Invalid references are FAIL only with complete topology
  and evidence that the routing list participates in the configured feature. Inactive broadcast/VoteParty examples do not fail.
- **Database & Storage:** unavailable selected storage/driver, enabled but unreadable VoteLog, database-dependent cache
  and GlobalData settings, GlobalData/prefix/shared storage identity inconsistencies, nonempty backend isolation prefixes.
- **Vote Sites:** configured/enabled inventory, blank/placeholder/duplicate ServiceSite, case-only conflicts, unfinished
  URLs or auto-created sites, invalid/unusual delay, unmatched detected/logged services, auto-create recommendation,
  proxy delay service mapping and low daily limits. No reward is INFO: totals-only sites are valid.
- **Rewards:** processing disabled, missing/unparseable named files or safely inferred dependencies when a complete
  inventory is reported, per-server identity problems, paired extra-all-sites settings.
- **VoteParty:** nonpositive enabled threshold, absent effective rewards, and server-list routing references.
- **Multi-Proxy:** transport-specific declared group identities/primary count, method/auth/encryption consistency, incompatible global reward
  plus send-to-all settings, missing related peer evidence, and valid secondary ingress omission.
- **Runtime:** reported queue/parked counts, oldest age, authentication counters, and restart state. A retained count alone
  does not prove a vote is lost. No arbitrary SQL or transport scan is used to obtain missing evidence.

Checks are evaluated only within an observed proxy/backend relationship or declared multi-proxy group. Unrelated standalone
servers are not compared. Offline proxy snapshots cannot force an online standalone backend into proxy-mode checks.
Socket multi-proxy peers come from `socketProxyServers`; Redis peers come from `proxyServers`. Redis authentication settings
are compared for Redis multi-proxy traffic, never borrowed from unrelated socket-connected backend networks.
Backend identity mapping differences are recommendations to verify the mapping, because a Control
node ID may be explicitly customized. Duplicate Control identities remain governed by registry/session/credential validation;
Network Doctor does not override session ownership or infer two physical machines from a reconnect.

## Votifier paths

A. `Voting site → proxy Votifier → VotingPlugin proxy → backend VotingPlugin` is normal proxy ingress.

B. `A → synthetic backend VotifierEvent` is valid when `TriggerVotifierEvent:true` has a compatible backend provider.
Having Votifier installed on both sides is not itself a duplicate path. With the trigger disabled, backend Votifier presence
is informational. Enabling the trigger without a compatible provider is FAIL.

C. Running A alongside `proxy VotifierPlus Forwarding → backend Votifier` creates two possible deliveries and is FAIL.
A forwarding destination name matching an enrolled backend with a provider makes the explanation explicit. Destination
names alone cannot prove the remote host identity. Synthetic event compatibility does not make socket forwarding safe.

## Compatibility and security

The registration protocol stays version 1. New diagnostics require the exact additive capability. New Control does not
send this kind to old peers, and old Control leaves the new capability unaccepted. Normal voting continues in either
upgrade order and when Control is absent. Proxy plugin names use the existing bounded `detectedPlugins` registration field.

The diagnostic result is a strict allow-list: known Boolean facts; supported transport/authentication enums; bounded integer
counts; bounded non-control text; at most 100 unique names per list; and at most 100 typed Vote Site rows. Unknown fields,
wrong types/enums, duplicates, malformed fingerprints, and oversized lists are rejected. Omit an unsupported field rather
than emitting a default. Full inspection envelopes remain capped at 512 KiB. Result data contains no raw YAML, logs,
player records, database addresses/credentials, Votifier keys/tokens, HTTP certificates/private keys, or unrestricted paths.
Key/storage equality, when reported, uses 64-character non-reversible fingerprints rather than secret material.

VotifierPlus optionally supplies an immutable diagnostic snapshot containing provider presence, listener initialization,
forwarding availability, and enabled forwarding **entry names** only. VotingPlugin consumes it without a mandatory upgrade
or dependency. Older VotifierPlus, NuVotifier, and other providers lacking that API cannot prove forwarding/listener state:
those checks are UNKNOWN. Oversized/incomplete forwarding evidence must not pass as an empty configuration.

## Evidence that may remain UNKNOWN

Runtime support varies by platform/transport/version. No listener or connection is inferred solely from installed plugin
names or persisted YAML. Absent runtime initialization/active method/status probe, HTTP client identity state, socket peer
identity/bind state, database driver/pool/schema readiness, cache counts/oldest age/authentication counters, shared storage
identity, reward dependency/inventory completeness, or forwarding APIs remain UNKNOWN. External voting-site destination and
uptime are always UNKNOWN. A diagnostic does not trigger an automatic correlated probe: use the existing non-vote
communication-test workflow when desired (and when a plugin-message carrier is available).

Intentionally deferred: arbitrary endpoint reachability, remote voting-site checks, raw log inference, command dependency
heuristics that cannot prove a plugin requirement, and reward execution validation. They would require unsafe access,
change behavior, or create false positives. Complete topology is required before declaring missing routing destinations
invalid; a disconnected peer is not treated as proof that a configured destination does not exist.
