# Approved mockups: capability map

This document compares the nine approved Control mockups with the current management protocol and WebUI. The mockups
define the visual direction. Current Control and VotingPlugin contracts define which labels and actions are truthful.

The classifications are:

- **ALREADY REAL**: current Control and connector capabilities provide the data or operation.
- **IMPLEMENTABLE**: current bounded data and APIs are sufficient, but the WebUI does not yet present the complete flow.
- **FUTURE / UNSUPPORTED**: a new additive, negotiated connector capability or a new bounded data model is required.
- **MOCKUP-ONLY / INVALID**: the mockup implies evidence or a VotingPlugin setting that does not exist. It must be
  removed or replaced with a truthful observation.

Unknown or unavailable evidence must never be rendered as healthy. Every mutation continues to use the authenticated,
revision-aware `READ -> PREVIEW -> APPROVE -> APPLY -> CONFIRMED READ` workflow.

## Network Health

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Connected nodes, roles, versions, capabilities | Registration and heartbeat expose these values | ALREADY REAL | Keep the node table and summary cards |
| Online/offline state | Control observes authenticated heartbeat liveness | ALREADY REAL | Label as Control connectivity, separate from Minecraft presence |
| Proxy/backend topology and presence | Proxy presence snapshots expose bounded backend state | ALREADY REAL | Show the reporting proxy and label stale/unknown evidence |
| Configuration and operation alerts | Redacted durable operation history and overview inspection exist | ALREADY REAL | Link alerts to the relevant safe workflow |
| Vote Site state and unmatched services | `vote-site-health` inspection reports configuration and retained observations | ALREADY REAL | Describe this as configuration/log evidence, never website uptime |
| Vote activity | Bounded VoteLog summaries exist when VoteLog is enabled and readable | ALREADY REAL | Label values as retained logged events and show unavailable explicitly |
| CPU/RAM, internet, vote-site website uptime | No current measurement exists | MOCKUP-ONLY / INVALID | Omit |
| Reward execution health or end-to-end vote health | No complete delivery trace exists | MOCKUP-ONLY / INVALID | Replace with configuration state and bounded logged-event evidence |

## Setup Doctor

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Enrollment, connector, version, capability and topology checks | Node registry and diagnostics inspection expose these values | ALREADY REAL | Keep evidence and source with PASS/WARNING/FAIL/UNKNOWN |
| Vote Site, logging, storage mode and proxy configuration checks | Bounded diagnostics/overview fields exist | ALREADY REAL | Navigate to configuration; never mutate silently |
| Proxy communication test | `config.transport-test.v1` performs a correlated status request without a vote | ALREADY REAL | Keep as an explicit diagnostic action |
| Safe recommended actions | Existing pages can be opened with the affected target selected | IMPLEMENTABLE | Expand navigation links as typed editors grow |
| Vote-site website responding | No reliable remote probe exists | MOCKUP-ONLY / INVALID | Replace with configured/enabled and observed ServiceSite evidence |
| Votifier port reachability | Votifier detection does not prove external reachability | MOCKUP-ONLY / INVALID | Show detection only; do not claim reachability |
| Database health | Current overview can report configured storage/readability, not general database health | MOCKUP-ONLY / INVALID | Use narrow storage/VoteLog evidence and UNKNOWN otherwise |

## Configuration Workspace

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Target chips and SAME/MIXED/MISSING/UNSUPPORTED/ERROR | Workspace state already reads each selected target independently | ALREADY REAL | Preserve per-target source and dirty-only edits |
| General settings | Typed `Config.yml` paths are source-preserving | ALREADY REAL | Expand only from verified current configuration paths |
| Vote Sites | Typed VoteSite property editing is source-preserving | ALREADY REAL | Keep rewards and unknown fields untouched |
| Rewards | Bounded reward inventory and simple edits exist | ALREADY REAL | Keep Full YAML available for advanced structures |
| Vote Party, vote logging, proxy setup | Guided capability-gated presets exist | ALREADY REAL | Move into concept navigation without changing their contracts |
| GUI, Shop, reminders, streaks, milestones, storage | Full YAML supports them, but typed coverage is incomplete | IMPLEMENTABLE | Add typed sections incrementally from current defaults/loaders |
| Full YAML for managed files and existing named reward files | Revisioned redacted file workflow exists | ALREADY REAL | Improve editor ergonomics; retain single-source identity |
| Comment/format fidelity | Connector support is negotiated and full-file apply may normalize YAML | FUTURE / UNSUPPORTED | Report capability accurately and fail closed where fidelity is unavailable |
| Mockup labels that resemble settings | Some do not map to real paths | MOCKUP-ONLY / INVALID | Derive every field from current defaults and loaders |

## Rewards Designer

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Reward scopes and existing named reward files | Current connector can inventory supported scopes and files | ALREADY REAL | Keep per-target presence and advanced fallback |
| Commands, messages, broadcast, money and basic item leaves | Current bounded reward proposal and parser understand a safe subset | ALREADY REAL | Present only supported operations |
| No-side-effect validation/simulation | `reward-simulation` inspection exists | ALREADY REAL | Call it validation/simulation and never claim execution |
| Nested rewards, chance, choices, priority and conditions | Real reward structures exist, but complete visual editing is not implemented | IMPLEMENTABLE | Add one verified structure at a time without flattening YAML |
| Universal execution preview or guaranteed result | Simulation cannot promise external command/economy outcomes | MOCKUP-ONLY / INVALID | Show parsed actions and warnings only |

## Updates & Deployments

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Uploaded artifact validation and per-node staging | `plugin.deploy.v1` validates JAR identity/SHA-256 and stages without restart | ALREADY REAL | Keep restart-required results explicit |
| Current installed versions | Node registration provides the running plugin version | ALREADY REAL | Display per target |
| Re-stage a retained verified artifact | Artifacts and deployment history are retained with bounds | IMPLEMENTABLE | Add selection only when retention still contains the artifact |
| Stable/development build discovery from bencodez.com | No verified server-defined source is implemented | FUTURE / UNSUPPORTED | First discover and document the real allowlisted source and metadata |
| Automatic restart/reload or arbitrary URL download | No safe capability exists | MOCKUP-ONLY / INVALID | Omit; never accept a browser-supplied download URL |

## Configuration Drift & Sync

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Exact redacted file comparison and revisions | Current compare view reads selected capable nodes | ALREADY REAL | Keep bounded line comparison |
| VoteSites reward-safe source synchronization | `config.vote-sites-sync.v1` exists | ALREADY REAL | Keep explicit source and destination selection |
| Semantic property differences for typed fields | Typed General/Vote Sites/Rewards models already retain per-target values | IMPLEMENTABLE | Build property-level views from those models |
| Selective source-to-target property copy | Current typed preview APIs can support explicit edits | IMPLEMENTABLE | Generate ordinary per-target previews, never hidden writes |
| Copying secrets between nodes | Redacted values cannot safely be reconstructed | MOCKUP-ONLY / INVALID | Preserve each destination secret; require a separate future migration design |

## Backups & Restore

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Named redacted configuration snapshots | Durable bounded snapshots exist | ALREADY REAL | Keep restore as proposed content requiring a fresh preview |
| Node-local rollback during apply | Connectors already create a local backup and roll back failed reloads | ALREADY REAL | Surface the result; do not present it as a downloadable backup |
| Multi-file configuration backup metadata/download | Current snapshots are operation-derived documents, not a full backup set | IMPLEMENTABLE | Extend with a typed bounded configuration-backup capability |
| SQLite/local user-data backup | No connector capability exists | FUTURE / UNSUPPORTED | Add a narrowly scoped application-owned export capability first |
| MySQL user-data backup/restore | No application-aware export/restore capability exists | FUTURE / UNSUPPORTED | Keep database credentials on the node; never add arbitrary SQL |
| Automated schedules and retention policies | No scheduler contract exists | FUTURE / UNSUPPORTED | Design after manual backups are safe |

## Vote Analytics

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Bounded vote totals and top services/servers over a date range | VoteLog summaries expose retained logged events | ALREADY REAL | State the date range and logging/readability boundary |
| Exact player lookup and top voters | Exact player lookup exists; arbitrary player enumeration does not | FUTURE / UNSUPPORTED | Add only bounded indexed aggregate queries through a new inspection schema |
| Unique voters and trends | Derivable only when retained VoteLog data is complete enough | IMPLEMENTABLE | Add bounded server-side aggregates with explicit coverage labels |
| Backend attribution | Available only when the retained event contains a trustworthy server field | IMPLEMENTABLE | Display UNKNOWN when attribution is absent |
| Vote/reward failure rate and reward execution statistics | No authoritative persisted evidence exists | MOCKUP-ONLY / INVALID | Omit rather than infer from missing log rows |

## Network Report

| Mockup element | Reality | Classification | Action |
| --- | --- | --- | --- |
| Version, platform, capabilities and topology | Current registry exposes them | ALREADY REAL | Include selected nodes and evidence timestamps |
| Diagnostics, Doctor and operation summaries | Redacted bounded results exist | ALREADY REAL | Include only safe retained summaries |
| Configuration and Vote Site excerpts | Redacted reads exist for a clearly identified source | IMPLEMENTABLE | Add category selection and preserve per-node identity |
| Player privacy controls | Exact player inspection is separate and need not be included | IMPLEMENTABLE | Default to exclusion; redact name/UUID when explicitly included later |
| Human summary, JSON and ZIP bundle | Only the current diagnostics JSON download exists | FUTURE / UNSUPPORTED | Add a bounded report capability/store before advertising a support bundle |
| Raw logs, credentials, private keys or automatic upload | Prohibited by the Control trust boundary | MOCKUP-ONLY / INVALID | Never include or upload them |

## Phased PR plan

1. Improve the Full YAML workbench and continue splitting focused frontend modules. Add visible source/revision context,
   line navigation/search, line numbers, and a reliable unsaved state without changing connector semantics.
2. **Implemented:** align Network Health and Setup Doctor navigation/layout with the approved cards and tables. The
   Doctor renders PASS/WARNING/FAIL/UNKNOWN checks with evidence sources and safe navigation while retaining the current
   observed/unknown evidence model.
3. Expand typed configuration in small groups sourced from current defaults and loaders. Prioritize reminders, Vote Party,
   vote logging, proxy/storage, GUI, and Shop. Full YAML remains available for every managed file.
4. Expand the Rewards Designer one understood reward structure at a time and retain the no-side-effect simulator boundary.
5. Investigate the real bencodez.com source, then add allowlisted server-side build discovery and verified staging.
6. Add semantic Drift & Sync over typed models.
7. Add manual typed configuration/user-data backup capabilities before scheduling or database restore.
8. Add bounded analytics aggregates and a redacted network-report bundle.

Native vote tracing/debugger work remains deferred.
