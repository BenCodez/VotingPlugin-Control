# MCP coverage for an enrolled proxy network

A proxy-network grant limits where an agent may use MCP. The negotiated node capability and available typed tools still determine what the agent can do.

| Existing Control management | Current MCP coverage | Increment after network scoping |
| --- | --- | --- |
| Enrolled identity, liveness, accepted capabilities, proxy/backend presence and topology | Bounded discovery and topology, with proxy snapshot freshness and truncation indicators | Extend large-network link pagination |
| Network Doctor, overview, diagnostics, vote-site health, vote-log summary/search/trace, exact player lookup, site resolution, reward simulation | Network Doctor status and read-only observation refresh with a separate health action | Further diagnostic projections. Player and log data need separate projections and access decisions |
| Bukkit managed YAML, split VoteSites, named reward files, supported file comments, General Settings, typed Vote Sites and Rewards | Typed backend and proxy General Settings, plus a named-site guided preview and typed Vote Sites and scalar Rewards editors | Add typed editor tools using their existing validators, per-target revisions, and bounded results |
| Quick setup v1/v2, standalone/proxy setup, common settings, auto-create, vote logging, Vote Party, rewards and VoteSites sync | Named-site, automatic site creation, common settings, VoteLog and Vote Party v2 | Standalone and proxy-backend setup remain in the browser |
| Proxy routing, fixed proxy YAML, coordinated proxy-method v1/v2 and transport tests | Typed routing and coordinated method proposals with browser approval, plus a non-vote transport check | Add further curated proxy projections |
| Configuration review, apply, status, retries, durable operation history, snapshots and drift | Token-free, per-target status for operations from the same grant. Browser-only Apply. Automatic site policy supports 1–10 explicit backends with immutable revisions | Extend bounded multi-node review and partial-failure reporting to other safe typed operations |
| Plugin staging, artifact source, admin enrollment, credentials and restart coordination | Owner-only workflows | Keep separate owner gates |

Control already has typed management services in both roles. MCP coverage is currently narrower than those services. Control has no general command, SQL, or filesystem API, and cannot restart a remote process through these tools. An enrolled server on another proxy is outside a grant for the selected proxy unless the administrator explicitly grants that node or the selected proxy reports it as a member at issue time.

MCP uses an opt-in server boundary, proxy-network grants, bounded discovery and a separately scoped read-only health projection. It makes no connector protocol change. Multi-node previews use bounded explicit targets and immutable revisions, reject unavailable peers, preserve one-time browser approval, and report results per target.

## Acceptance checklist

| Surface | State | Acceptance evidence or remaining work |
| --- | --- | --- |
| Server gate and exact or proxy grants | Implemented and tested | Disabled API denial, grant expiry/revoke, proxy session replacement, membership removal and isolated discovery |
| Scoped topology and health | Implemented and tested | MCP frames, aged topology independent of heartbeat, capability gating, bounded inspection admission and projection |
| Single named vote site, proxy routing, automatic site policy | Implemented and tested | Typed prior reads, scoped preview, exact diff and revision, browser-only approval, token-free status |
| Existing non-vote transport probe | Implemented and tested | Both peers in grant; fresh reported relation and current sessions required |
| Batch site policy | Implemented and tested | Explicit unique targets, separate batch permission, revision per backend, all previews successful before approval, per-target terminal results |
| Typed General Settings | Implemented and tested | Both node roles use the curated editor, scoped reads, exact revision and diff, and browser approval. Proxy applies report restart required |
| Typed Vote Sites editor | Implemented and tested | Exact-site read, typed add/edit, and removal for reviewable sites without inline Rewards. Revision, masked source and leaf changes checked before browser approval |
| Typed Rewards editor | Implemented and tested for supported scalar fields | Inline and named-file reward reads, exact same-scope scalar previews and browser approval. Command payloads and whole-scope edits stay in administrator workflows |
| Coordinated proxy method | Implemented and tested | Full fresh topology, exact method capability, per-peer revision, backend-first apply and explicit partial persistence. Runtime readiness and other proxy transport settings are outside the revision contract |
| VoteSites sync | Browser only pending complete diff proof | The connector can replace a destination scalar or list with a mapping while omitting the removed value from its change paths. Agent approval needs revision-matched source and destination shape evidence and a typed source projection |
| Guided common settings, VoteLog and Vote Party v2 | Implemented and tested | Both same-session READs agree on revision, key presence is reviewed, and approval checks every target's exact diff. Up to four explicit targets. Vote Party v1 and database selection stay in the browser |
| Remaining guided presets and fixed proxy-file projections | Browser workflows | Standalone, proxy-backend connection setup and advanced file settings require separately reviewable projections. Proxy-backend HTTP depends on its connector contract |
| Other multi-node proposals, typed retries and snapshot or drift projections | Remaining | Add bounded per-target reads, target-specific proposal data and clear validation/partial failure handling |
| Player and vote-log inspections | Separately gated | Require distinct grants and purpose-specific redacted results before exposure |
| Enrollment, credential administration, artifact staging and restart | Owner-only | Maintain established non-MCP gates |
| Arbitrary command, SQL, filesystem and new connector features | Outside supported scope | Any new protocol requires its own design and decision |
| Client verification | Packaged protocol and Codex App Server exercised | A disposable two-node fixture covered discovery, reads, preview, browser-session review and approval, and mixed terminal apply status. Codex CLI 0.169.0 mediated the MCP calls through App Server with process-local settings. Model-driven interaction, browser rendering and Claude Code runtime remain unverified |

Completion means supported typed rows are implemented and have permission, revision, failure and client evidence, or have a documented product decision to remain gated. Check the browser and agent boundaries for every write operation.
