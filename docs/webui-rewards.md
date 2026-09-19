# Rewards workspace

This inventory is based on the current VotingPlugin `VoteSites.yml`, `Config.yml`,
`SpecialRewards.yml`, the current reward registrars, and AdvancedCore's reward
loader. The screenshots are visual references, not a schema. The companion connector PR adds
`config.reward-files.v1` for strictly named `.yml` files directly under `Rewards/`;
older connectors still support the top-level files and show named files as unsupported.

| Source | Reward path or family | Control support |
|---|---|---|
| `VoteSites.yml` | `VoteSites.<site key>.Rewards` | Visually readable; bounded command and scalar editing, explicit create/remove |
| `VoteSites.yml` | `EverySiteReward` | Read-only inventory / Full YAML |
| `VoteSites.yml` | `VoteSites.<site>.WaitUntilVoteDelayRewards`, `CoolDownEndRewards` | Advanced-only / Full YAML |
| `Config.yml` | `VoteReminders.<id>.Rewards` and reminder defaults | Read-only inventory / Full YAML |
| `SpecialRewards.yml` | `VoteParty.Rewards`, `VoteMilestones.<id>.Rewards` | Read-only inventory / Full YAML |
| `SpecialRewards.yml` | `AnySiteRewards` | Read-only inventory / Full YAML |
| `SpecialRewards.yml` | `VoteStreaks` progress-group milestones and `LostRewards` | Read-only inventory / Full YAML |
| `SpecialRewards.yml` | `MonthlyAwards`, `WeeklyAwards`, `DailyAwards` rank rewards | Read-only inventory / Full YAML |
| `SpecialRewards.yml` | `NameMCLikeReward.Rewards`, legacy reward sections | Read-only inventory / Full YAML |
| `Rewards/<name>.yml` | Named AdvancedCore reward definitions | Bounded inventory/READ and existing-root visual edits with source-preserving PREVIEW/APPLY; no file creation/deletion |

The current defaults were successfully parsed as 3 VoteSites scopes, 6 Config
scopes, and 24 SpecialRewards scopes. The exact dynamic families are:

- `VoteSites.<site key>.Rewards` and, when configured,
  `VoteSites.<site key>.WaitUntilVoteDelayRewards` and
  `VoteSites.<site key>.CoolDownEndRewards`; plus `EverySiteReward`.
- `VoteReminderOptions.Defaults.Rewards` and
  `VoteReminders.<reminder id>.Rewards` in `Config.yml`.
- `AnySiteRewards`, `VoteParty.Rewards`, `VoteMilestones.<milestone id>.Rewards`,
  `VoteStreaks.<streak id>.Rewards`,
  `VoteStreaks.ProgressGroups.<group id>.LostRewards`, and
  `VoteStreaks.ProgressGroups.<group id>.Milestones.<milestone id>.Rewards`.
- `NameMCLikeReward.Rewards`; legacy `Cumulative.<count>.Rewards`,
  `MileStones.<count>.Rewards`, and `VoteStreak.<period>.<count>.Rewards`;
  `MonthlyAwards.<rank>.Rewards`, `WeeklyAwards.<rank>.Rewards`, and
  `DailyAwards.<rank>.Rewards` in `SpecialRewards.yml`.

The AdvancedCore reward engine additionally has nested map structures such as
`AdvancedPriority`, `Choices`, `SpecialChance`, and `Rewards` sub-rewards.
Names under these maps are identities and order may affect execution. The
read-only structural tree exposes bounded path names, but Simple mode does not
rewrite or normalize those nested maps.

The advanced inventory reports reward scope presence and known primitive fields,
plus names of unhandled keys. It does not turn an advanced-only scope into a
visual write path. Named reward files are separate scopes, never inlined into a
referring site. References may be visible in Advanced structure, but this editor
does not resolve or rewrite them. Creation/deletion of named files remains outside
the bounded visual API.

The Simple-mode subset applies to **existing inline Vote Site rewards** identified
by YAML site key, never by `Name` or `ServiceSite`, and to the root of existing
named `Rewards/<name>.yml` files where the new capability is negotiated:

| Operation | Exact path | Meaning |
|---|---|---|
| Append / remove one exact command | `VoteSites.<key>.Rewards.Commands` | Preserve each target's distinct existing list; duplicate-match removal fails closed |
| Explicitly replace command list | `VoteSites.<key>.Rewards.Commands` | Destructive, requires confirmation; fails closed if comments would be lost |
| Set player or broadcast message | `VoteSites.<key>.Rewards.Messages.Player` / `.Broadcast` | Edit only an existing scalar, preserving sibling message keys |
| Set numeric amount/chance | `VoteSites.<key>.Rewards.Money` / `.Chance` | Edit only an existing numeric scalar; no claim that an economy plugin is active or chance is guaranteed |
| Set existing item material/amount | `VoteSites.<key>.Rewards.Items.<item key>.Material` / `.Amount` or the equivalent named-file root path | Change only that scalar source span; preserve item display metadata, enchantments, unknown keys and sibling items. Material is an exact runtime identifier, not a stale browser material list. Amount is 1–64. |
| Create inline reward | `VoteSites.<key>.Rewards` | Explicit new section with one console command only, and only if absent |
| Remove inline reward | `VoteSites.<key>.Rewards` | Exact subtree removal; the site and all named files remain |

`Commands` is an ordered list in the current defaults. The AdvancedCore loader
also accepts `Commands.Console` and `Commands.Player`; the current read model
marks those map shapes advanced-only and does not normalize them. `Messages.Player`
and `.Broadcast` are separate. `Money` may have a random-range mapping, which
is advanced-only. Item metadata, nested `AdvancedPriority`, `Choices`,
`SpecialChance`, conditions, references, and custom/newer fields are visible as
advanced keys and preserved by path-local edits. Direct item Material/Amount
leaves are editable only when their exact existing source shape is safe; no
item map or metadata is synthesized. Nested `AdvancedPriority`, `Choices` and
similar child rewards remain read-only in the structure tree: their identity,
order and execution semantics cannot safely be generalized into a path edit.
Current `SpecialRewards.yml` actively uses `VoteMilestones`, Vote Party,
streak/progress groups, lost rewards, rank awards and other structures. These
are inventoried/read-only in the visual page with Full YAML as the edit fallback;
they are not called deprecated or silently flattened. The legacy reward builder
remains reachable and explicitly warns that it replaces a selected subtree;
it is not the existing-reward editor.

Named files are inventoried by names only, at most 100 direct `.yml` files of
at most 512 KiB each. Logical basenames are ASCII letters/digits followed by
letters/digits/underscore/hyphen (100 characters maximum). Traversal,
separators, absolute paths, symlinks, case-ambiguous names and other extensions
are rejected or excluded. The browser lazily reads only a selected named file,
and every edit uses its own revision-bound retained READ and exact approval.
The capable connector uses descriptor-relative secure directory operations for
named-file reads and publication; on a filesystem provider without that facility,
named-file operations fail closed. Staged file contents and pinned directory
entries are forced on supported providers. Java does not provide that directory
guarantee portably across every filesystem provider; unsupported providers do
not advertise this optional capability.
Old connectors cannot advertise this capability, so they cannot be made to
serve named files by a browser request.

Every target is READ independently. A visual edit is an explicit operation,
not a merged form value. PREVIEW is created from each target's own retained
source and expected revision, through the existing operation journal. A changed
target or field invalidates its approval; abandoned previews use the existing
disposal path. APPLY requires exact preview approval, then a confirmed fresh
READ. A failed reload remains a rollback/failure, never a success. Navigation
does not write. Visual batches retain the eight-changed-target limit. Source
parsing rejects duplicate/ambiguous keys, anchors/aliases, malformed and
multi-document YAML, excessive nesting and oversized documents; unsafe
operations fail closed instead of normalizing the entire document.

The inventory's `advancedKeys` are names only. The typed endpoint is
authenticated and CSRF-protected, bounded to retained managed-file READs, and
does not return raw YAML, approval tokens, or unrelated file contents.

The disposable Paper 26.2 connector gate exercised inline reward create/append/
scalar edits and named-file inventory, READ, append, Money edit, reload,
confirmed READ, stale revision rejection and path rejection through actual
Control HTTP and VotingPlugin filesystem services. The server and configuration
were disposable; no production apply was performed.
