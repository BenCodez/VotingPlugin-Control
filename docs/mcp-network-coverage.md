# MCP coverage for an enrolled proxy network

A proxy-network grant limits where an agent may use MCP. The negotiated node capability and available typed tools still determine what the agent can do.

| Existing Control management | Current MCP coverage | Increment after network scoping |
| --- | --- | --- |
| Enrolled identity, liveness, accepted capabilities, proxy/backend presence and topology | Bounded discovery and topology, with proxy snapshot freshness and truncation indicators | Extend large-network link pagination |
| Network Doctor, overview, diagnostics, vote-site health, vote-log summary/search/trace, exact player lookup, site resolution, reward simulation | Network Doctor status and read-only observation refresh with a separate health action | Further diagnostic projections. Player and log data need separate projections and access decisions |
| Bukkit managed YAML, split VoteSites, named reward files, supported file comments, General Settings, typed Vote Sites and Rewards | Redacted main VoteSites read projected to one named site and one guided preview on one eligible Bukkit target | Add typed editor tools using their existing validators, per-target revisions, and bounded results |
| Quick setup v1/v2, standalone/proxy setup, common settings, auto-create, vote logging, Vote Party, rewards and VoteSites sync | Named vote-site preset only | Add preset-specific schemas and their exact capability and role checks |
| Proxy routing, fixed proxy YAML, coordinated proxy-method v1/v2 and transport tests | One typed routing read and preview with browser approval | Add fixed proxy YAML projections and safe transport checks. Proxy-method changes need the complete reported target set and existing rollback and restart reporting |
| Configuration review, apply, status, retries, durable operation history, snapshots and drift | Token-free status for operations from the same grant. Apply goes through Control browser approval | Extend the same review flow to exposed typed proposals. Define partial failure, offline and stale targets for bounded multi-node workflows |
| Plugin staging, artifact source, admin enrollment, credentials and restart coordination | Owner-only workflows | Keep separate owner gates |

Control already has typed management services in both roles. MCP coverage is currently narrower than those services. Control has no general command, SQL, or filesystem API, and cannot restart a remote process through these tools. An enrolled server on another proxy is outside a grant for the selected proxy unless the administrator explicitly grants that node or the selected proxy reports it as a member at issue time.

MCP uses an opt-in server boundary, proxy-network grants, bounded discovery and a separately scoped read-only health projection. It makes no connector protocol change. A future multi-node preview should keep a bounded explicit target list and immutable revisions, show offline or unsupported targets, report partial failure, and preserve the existing one-time owner approval and audit behavior.
