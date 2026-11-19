# VotingPlugin configuration editing coverage

Use **Configuration → Full YAML → Edit typed fields** after reading the selected file. It shows the present fields
supported by the typed editor and their current values. Edit one value using JSON, review its old and new values,
then approve. MCP can list its own field catalog and preview up to eight typed changes from one scoped read. Every
typed preview checks the same node session, file revision, masked source and exact connector change paths.

| Domain | Typed browser and scoped MCP coverage | Other supported browser editing |
| --- | --- | --- |
| `Config.yml` | General Settings and guided presets, plus reminder flags, time changes, vote limits, broadcast limits, reward-world lists and other catalog fields | Full YAML and database pool size |
| `VoteSites.yml` | Named sites, editable site fields, and scalar inline reward fields through existing tools | Full YAML, split site files where supported, and existing typed reward command list editing |
| `SpecialRewards.yml` | Existing Vote Party v2 presets plus reset, eligibility and cumulative flags and values | Full YAML and typed Vote Party global, reminder and reward command lists |
| `Rewards/*.yml` | Existing named reward scalar tools on capable backends | Existing named reward and command list editors and Full YAML |
| `BungeeSettings.yml` | Existing coordinated method tooling plus per-server points and rewards, event forwarding, extra rewards and Redis TLS flags | Full YAML, global data pool size and global party command list |
| `bungeeconfig.yml` | Routing and coordinated method tooling plus broadcast scope and forwarding, wait and cache settings, scheduling and party target lists | Full YAML, pool sizes and party command lists. File changes can require a separate proxy restart |
| `GUI.yml` and `Shop.yml` | Known GUI item/URL and vote-shop appearance or behavior fields | Full YAML and selected lore lists. Named shop entries and custom layouts use Full YAML |

The catalog specifies exact fields and bounds. The typed editor works on present supported values. It rejects ambiguous
mappings and skips unsupported representations. It retains unknown fields and comments outside the replaced YAML span.
A list with comments inside its edited span stays in Full YAML. Connector comment preservation still depends on the
negotiated file contract.

The editor saves command strings as configuration for VotingPlugin's normal reward events. It has no command or reward
execution action. Review the complete command list in the browser before saving. Avoid placing credentials in command
arguments, messages or other free-text fields.

Use the existing masked browser workflow for connection settings. Typed connection tuning is limited to pool sizes.
Passwords, tokens, connection codes and keys are absent from both typed catalogs. Leave existing redaction markers
untouched to preserve secrets in the Full YAML workflow. The MCP read returns typed fields without the YAML document,
command values, approval material or connection settings.

This covers VotingPlugin configuration files already supported by the enrolled connector. It does not edit player data,
balances, vote totals, SQL tables, generated runtime files, other plugins, or host settings. Existing capability, reload,
rollback and restart reporting applies. Neither editor can trigger a process restart or deployment.
