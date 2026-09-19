# Vote Sites visual editor (Phase 3)

The Vote Sites editor is scoped to the selected Bukkit workspace, never the inspected server. A single server and multiple servers use the same model. The source of truth for this contract is VotingPlugin `origin/master` at `11f20c39f551c40c55923c6a686b1b6c2a260f83` and its default `VoteSites.yml`; that source SHA is review evidence, not a dependency pin. The visual mockups and historical VotingPluginEditor behavior do not define YAML names or defaults.

## Exposed paths

All paths are under `VoteSites.yml` → `VoteSites.<exact-site-key>`:

| Path suffix | Type / absent behavior | Editing rule |
| --- | --- | --- |
| `Enabled` | Boolean; absent sites default disabled | Typed boolean |
| `Name` | Display name; absent uses site key | Bounded string |
| `ServiceSite` | Service matcher; missing or invalid site may not match votes | Bounded nonempty string |
| `VoteURL` | Vote link/text, possibly VotingPlugin link formatting | Bounded nonempty string; no external URL check |
| `VoteDelay` | Duration string such as `24h`; legacy numeric delay has separate semantics | Bounded nonempty string; numeric legacy value is read as unsupported, not normalized |
| `Priority` | Integer; higher priority sorts first | Signed 32-bit integer |
| `Hidden` | Boolean | Typed boolean |
| `DisplayItem.Material` | Material identifier string, interpreted by the installed Minecraft/VP runtime | Bounded text; unknown/current-version materials are retained unmodified |
| `DisplayItem.Amount` | Integer 1–64 for visual edits | Typed integer |

The site key is the YAML mapping key, not `Name` or `ServiceSite`. New keys are restricted to 1–64 letters, digits, underscores and hyphens; case-insensitive conflicts are rejected. Arbitrary properties, inline rewards, comments, target-local site differences, and newer-version properties are not reserialized when editing an unrelated field. An explicit site removal removes that site's own inline rewards too; it never touches another site or a separate reward file. The UI confirms removal, then still requires preview and approval.

## Safe per-target workflow

The editor calls existing `config.files.v1` READ for `VoteSites.yml` on each eligible selected backend. The additive authenticated typed state endpoint reads only the retained successful result for that node and returns the exact session/revision, site key, nine allowlisted field states, editable flag and a reward-presence boolean. Both editors share the generic SAME/MIXED/MISSING/UNSUPPORTED/ERROR aggregator in `configuration-state.js`; the site-specific model adds key presence and add/remove policies, which the Phase 2 boolean-only `MultiTargetState` cannot represent. A matching supported subset is not presented as the entire workspace's state. It tracks only explicit field edits. Add creation on missing targets and editing existing sites require an explicit policy if presence is partial. No missing site is created by opening its editor. Offline and excluded targets stay visible and require acknowledgement before an otherwise-ready apply.

Each typed preview is a single-target `ADD`, `EDIT`, or `REMOVE` with the exact retained READ ID, target ID, key and allowlisted fields. Control patches that target's own source spans and sends an ordinary revision-bound `VoteSites.yml` FILE preview through existing connector/journal logic. It never copies a source server's document to another target, exposes a generic YAML patch API, or silently writes during navigation. The pending approval is disposed on re-preview or changed draft/scope. A fresh read of the exact workspace occurs immediately before applying the explicit per-target approval tokens, followed by cache invalidation and fresh confirmation reads. The connector's write/reload/rollback behavior and durable operation entries remain authoritative; mixed success is reported per target and a successful write alone does not establish confirmed runtime state. At most eight **changed** targets can be previewed per batch; nothing is silently dropped. No automatic APPLY retry exists.

The READ/preview/apply endpoints reuse admin authentication, session CSRF, bounded request JSON, connector capability checks, file redaction and operation journal. Malformed YAML, duplicate or case-ambiguous site/property keys, aliases/merges and unsupported field structures fail closed. A noncanonical casing such as `enabled` is shown as unsupported instead of being treated as an absent canonical `Enabled` field. Existing synchronization remains in Quick Setup as a separate source-to-destinations operation with its own semantics; normal visual editing never invokes it. Detected service names and vote-site health come from Control's read-only `data.inspect.v1` observations, not website uptime/ping checks.

## Deliberate boundaries

Current VotingPlugin loads sites from the main `VoteSites.yml`; separately stored site files in its managed configuration system are not loaded as part of this runtime site registry. The visual editor is limited to the main file and does not broaden filesystem access. An unsupported material is shown as its source value and is never coerced; the browser does not ship a stale cross-version material list. Inline rewards are preserved when editing ordinary site fields; the separate [Rewards workspace](webui-rewards.md) provides bounded visual reward edits and a Full YAML fallback for complex structures. One target's failed reload does not roll back successful changes on another target (no distributed atomicity).
