# General Settings: independent workspace proposals

General Settings uses `selectedTargetIds`, not the inspected server or the legacy tool source. One target uses the same
state model as many targets. Global mode is separate and cannot open this backend editor. Other guided presets, Rewards,
Full YAML, Compare, snapshots and network workflows retain their existing boundaries.

## Source-verified curated settings

Entries are bounded top-level booleans, enums, or strings in VotingPlugin `Config.yml` and `bungeeconfig.yml`.
Source verification used VotingPlugin `origin/master` at `d22a611202aec5a7df99979390ecfbaa1d8f1e0c`, its default files,
configuration readers, backend/proxy connectors, and
`VotingPluginMain.reloadFromControl`. This is evidence, not a pinned development dependency.

| YAML path / control | Documented default | Effect | Runtime action |
| --- | --- | --- | --- |
| `DebugLevel` / proxy `Debug` | NONE | Backend diagnostic level; proxy offers Off/Info (Extra maps to Info) | Configuration reload |
| `OnlineMode` | true | Keep online identity handling aligned across applicable backend and proxy targets | Network configuration reload |
| `AutoCreateVoteSites` | true | Create sites for newly received services | Configuration reload |
| `CountFakeVotes` | true | Include fake votes in points/totals | Configuration reload |
| `AllowUnjoined` / proxy `AllowUnJoined` | false | Effective proxy value governs proxy-managed networks; backend requests true in that mode | Configuration reload |
| `UseVoteGUIMainCommand` | false | Open the vote GUI from the main vote command | Configuration reload |
| `GiveDefaultPermission` | true | Grant default permissions during startup | Configuration reload plus backend restart |
| `LoadCommandAliases` | true | Load command aliases during startup | Configuration reload plus backend restart |
| `CaseInsensitiveYMLFiles` | true | Use case-insensitive YAML file lookups | Configuration reload plus backend restart |
| `BedrockPlayerPrefix` | `.` | Share Bedrock player name handling across applicable backend and proxy targets | Network configuration reload |
| `PerSiteCoolDownEvents` | false | Emit cooldown events per site | Configuration reload plus backend restart |
| `CheckForUpdates` (inverted `DisableUpdateChecking`) | true | Enable update checks | Configuration reload plus backend restart to reconcile scheduler lifecycle |
| `CloseInventoryOnVote` | true | Close the vote inventory when voting | Configuration reload |

The visual group deliberately omits older primary controls such as `ProcessRewards`, `ExtraAllSitesCheck`,
`DisableNoServiceSiteMessage`, and `ExtraVoteShopCheck`; their existing typed or guided workflows remain available where
supported. Backend `Config.yml` and proxy `bungeeconfig.yml` are read in grouped batches. A proxy is included when it
reports one of the selected backend targets, and its effective network values are shown alongside backend values.
Missing or unsupported values remain visible and are never replaced by documented defaults. Control recommends disabling
auto-create after intended vote sites are configured, then reviewing detected service sites before creating new entries.

The update-check timer is started by `CheckUpdate.startUp` at plugin startup, not by the configuration reload path.
The editor does not restart a backend or proxy. Proxy `bungeeconfig.yml` writes always remain restart-required; a
confirmed READ proves persisted configuration, not completion of a restart.
Vote Logging and Vote Party remain accessible through their existing typed/capability-gated tools; they are deliberately
not added to this first visual group. Missing values are never synthesized from defaults or inserted automatically.

## State and capability contract

`configuration-state.js` provides per-target snapshots and explicit dirty overrides. Fields distinguish SAME, MIXED,
MISSING, UNSUPPORTED and ERROR, with supported-subset values and each excluded target visible separately. Mixed controls
have a placeholder, not an implicit true/false choice. Focusing a control is not an edit. A reset removes its override.

The connector contracts remain `config.files.v1` for Bukkit and `config.proxy-files.v1` for proxies. Actual parsed file
presence and expected scalar type gate each field, instead of guessing support from a version string. Missing keys, wrong types and
anchored/aliased edited values are not writable. Malformed/ambiguous YAML fails closed with sanitized errors. Offline,
unsupported and failed targets remain visible. Applying only a supported subset requires explicit acknowledgement.

## Additive Control API (no connector/protocol change)

1. Existing `/api/v1/configuration/read` reads `Config.yml` and `bungeeconfig.yml` in separate bounded target batches.
2. POST `/api/v1/configuration/general-settings/state` with `readOperationId` and `nodeId` returns the profile's typed
   fields, target session, revision, and backend/proxy profile. It requires a retained successful read of the matching file.
3. POST `/api/v1/configuration/general-settings/preview` adds one or more allowlisted typed overrides.
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

The changed-target preview limit is calculated from the actual number of bounded 100-target backend/proxy read batches.
The coordinator retains at most sixteen FILE operations, so the editor reserves one slot per required fresh read batch
before APPLY instead of allowing an approval set that cannot be revalidated.
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

### Configuration health outside the editor

Dashboard refresh (including the network workspace) and Network Doctor read the same allowlisted,
revision-bound managed configuration directly. Visiting General Settings first is not required.
Health reads use a separate read-only state so refreshing health never discards an editor draft or
approval. Reads are coalesced and cached for 30 seconds; configuration changes invalidate the cache.
Offline, unsupported, missing, or incomplete-topology observations are reported as UNKNOWN rather
than passing. Identity comparisons apply within reported proxy/backend groups, not between unrelated
standalone servers. Network Doctor exports these observations as `configurationChecks`; no settings
are written by a health check.
