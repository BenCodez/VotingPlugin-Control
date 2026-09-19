# General Settings: independent workspace proposals

General Settings uses `selectedTargetIds`, not the inspected server or the legacy tool source. One target uses the same
state model as many targets. Global mode is separate and cannot open this backend editor. Other guided presets, Rewards,
Full YAML, Compare, snapshots and network workflows retain their existing boundaries.

## Source-verified curated settings

All entries are top-level booleans in VotingPlugin `Config.yml`. Source verification used VotingPlugin `origin/master`
at `11f20c39f551c40c55923c6a686b1b6c2a260f83`, its default Config.yml, configuration reader, backend connector and
`VotingPluginMain.reloadFromControl`. This is evidence, not a pinned development dependency.

| YAML path | Documented default | Effect | Runtime action |
| --- | --- | --- | --- |
| `ProcessRewards` | true | Process vote rewards (offline processing still follows VotingPlugin behavior) | Configuration reload |
| `AutoCreateVoteSites` | true | Create sites for newly received services | Configuration reload |
| `ExtraAllSitesCheck` | false | Additional duplicate all-sites reward check | Configuration reload |
| `CountFakeVotes` | true | Include fake votes in points/totals | Configuration reload |
| `DisableNoServiceSiteMessage` | false | Suppress missing service-site and related site warnings | Configuration reload |
| `DisableUpdateChecking` | false | Disable update checks | Configuration reload plus backend restart to reconcile scheduler lifecycle |
| `UseVoteGUIMainCommand` | false | Open the vote GUI from the main vote command | Configuration reload |
| `CloseInventoryOnVote` | true | Close the vote inventory when voting | Configuration reload |
| `ExtraVoteShopCheck` | true | Additional vote shop purchase check | Configuration reload |

The update-check timer is started by `CheckUpdate.startUp` at plugin startup, not by the configuration reload path.
The editor does not restart a backend. A confirmed READ proves persisted configuration, not completion of a restart.
Vote Logging and Vote Party remain accessible through their existing typed/capability-gated tools; they are deliberately
not added to this first visual group. Missing values are never synthesized from defaults or inserted automatically.

## State and capability contract

`configuration-state.js` provides per-target snapshots and explicit dirty overrides. Fields distinguish SAME, MIXED,
MISSING, UNSUPPORTED and ERROR, with supported-subset values and each excluded target visible separately. Mixed controls
have a placeholder, not an implicit true/false choice. Focusing a control is not an edit. A reset removes its override.

The required connector contract is the existing `config.files.v1` on an online Bukkit node. Actual parsed file presence
and boolean type gate each field, instead of guessing support from a version string. Missing keys, wrong types and
anchored/aliased edited values are not writable. Malformed/ambiguous YAML fails closed with sanitized errors. Offline,
unsupported and failed targets remain visible. Applying only a supported subset requires explicit acknowledgement.

## Additive Control API (no connector/protocol change)

1. Existing `/api/v1/configuration/read` reads Config.yml from every eligible selected target in a bounded batch.
2. POST `/api/v1/configuration/general-settings/state` with `readOperationId` and `nodeId` returns nine typed fields,
   that target's session and revision. It requires a retained successful READ of that node's Config.yml.
3. POST `/api/v1/configuration/general-settings/preview` adds `overrides` (one to nine allowlisted JSON booleans).
   Control patches only changed scalar source spans in that target's retained redacted document and creates an ordinary
   FILE PREVIEW bound to the READ revision. Comments, unknown keys, unrelated values and redacted placeholders are not
   regenerated. Each target has its own proposal and approval token; no common document is distributed.
4. Existing `/api/v1/configuration/apply` consumes that exact preview/token once. The connector verifies its revision,
   writes/reloads and retains its existing rollback semantics. No network-wide atomicity is claimed.

The additive POST routes use existing admin authentication and session CSRF protection, request-size/JSON constraints,
and strict known request fields. The typed view does not expose document content or secrets. Operation history, audit
and journal continue to omit file proposal contents. Existing node file redaction/restoration remains unchanged.

## Approval, retention and confirmation

Target IDs/sessions, every relevant READ revision/state and explicit overrides form the preview signature. Edits, resets,
scope changes and changed revisions invalidate approval. Before APPLY a fresh scope READ checks that signature again;
the connector revision guard closes the remaining race. Navigation only reads and never previews, applies or reloads.

At most eight changed targets can be previewed in one visual batch, within the existing bounded FILE-operation retention.
All workspace targets are still read/displayed. Larger changed sets must be reduced explicitly, not silently truncated.
Successful revision-bound pending previews are protected from ordinary FILE-retention eviction for fifteen minutes,
or until consumed/discarded. `/api/v1/configuration/general-settings/discard-preview` releases the exact abandoned
approval without scheduling a task or modifying a node. The browser disposes approvals on edits/scope changes/re-preview;
disposal is best-effort during connection loss, with bounded retention as the fallback. A stale visual PREVIEW cannot
be retried from Activity with its old document; it requires a fresh READ and dirty-field preview.
There is no unlimited retention or additional durable proposal store. Expired/evicted READ snapshots require a fresh READ.

Per-target previews show exact changes, unchanged targets and exclusions. Apply results retain operation IDs, success,
structured failures, reload and rollback flags. Affected caches are invalidated and fresh confirmed READs rebuild mixed
state after the batch. Failed targets/confirmations remain visible; overrides clear only when confirmed resolved. Retry
is read-only, never an automatic write retry. Workspace/configuration cache is session-bound and not persisted to storage.

## Verification

```shell
node --check src/main/resources/web/app.js
node --check src/main/resources/web/workspace.js
node --check src/main/resources/web/configuration-state.js
node --check src/main/resources/web/general-settings.js
node --test src/test/web/*.test.cjs
mvn -B test
mvn -B clean verify
git diff --check
```

Deterministic tests cover source-preserving per-target proposals, allowlists/capabilities, stale revision/approval,
single-flight and stale-result guards, mixed/partial reads, dirty fields and confirmed partial apply results. Browser
smoke evidence uses actual static assets against controlled API fixtures. A disposable Paper 26.2 server with the current
VotingPlugin connector also exercised the real HTTP connector, Config.yml READ/PREVIEW/APPLY/reload/confirmed READ,
and stale revision rejection. That local gate never points at production configuration.
