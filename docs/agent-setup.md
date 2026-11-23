# Configure a vote site with an MCP agent

VotingPlugin Control provides a local stdio MCP connection. The packaged adapter has been exercised through Codex CLI App Server. Claude Code configuration is documented below, and its runtime compatibility remains unverified. The agent can discover authorized enrolled nodes, read one named vote site on a Bukkit backend, preview a change and track the result. The owner reviews and applies the preview in Control. The backend uses existing managed-file and quick-setup capabilities over the current task protocol.

## Enable MCP management

Control disables the agent API by default. Sign in as the owner and open **Access → Agent access**. Select **Enable MCP management** and save. The page shows the current server state and the number of active grants. Changes take effect without restarting Control or the proxy.

The saved setting in the Control data directory takes precedence over `CONTROL_MCP_ENABLED`. With no saved preference, `CONTROL_MCP_ENABLED=true` preserves startup opt-in behavior. Hosted Control uses the same persistent data directory and needs no environment forwarding for the WebUI switch. Keep that directory across upgrades. If Control cannot validate or save the preference, the page reports the failure and the instance fails closed on an unreadable preference at startup. A failed disable save still disables MCP and revokes grants for the current process. Confirm a successful save before restarting; otherwise the previous preference may return. Access changes and bulk revocation are recorded as metadata.

Disabling MCP revokes all active grants and prevents approval of their pending previews. Enabling again requires fresh grants. Operations already approved keep their normal lifecycle. The client adapter cannot enable server access. See the [management coverage matrix](mcp-network-coverage.md) for supported tools.

## Connect a client

1. Install VotingPlugin and enroll the backend as an administrator. Check that Control reports the backend online with both accepted capabilities, `config.files.v1` and `config.quick-setup.v1`.
2. Open **Access → Agent access** in Control. Enter exact node IDs or enter one online proxy ID to grant its enrolled network. Choose the actions and lifetime. Issue the credential and copy it when displayed. Control keeps only its verifier in memory. The WebUI defaults to 60 minutes and accepts a whole number of minutes, hours or days up to 90 days. It shows the chosen lifetime and the exact expiry after issuing. API clients continue to send a whole integer number of minutes (1–129600). Grants can be revoked immediately and are invalidated when Control restarts, even if their expiry is later. Revocation prevents new requests and prevents approval of pending previews. A write the owner already approved can still complete. A proxy grant covers the proxy and the enrolled Bukkit nodes in its reported snapshot at issuance. Removing a member or disconnecting the proxy drops current access. A new proxy session or newly reported backend requires a fresh grant. The grant list shows the selected proxy and node count. Control uses the latest reported snapshot for that live proxy session. A heartbeat does not certify the age or accuracy of its topology.
3. Supply `VP_CONTROL_MCP_ORIGIN` and `VP_CONTROL_MCP_TOKEN` in the local client environment. Use a verified HTTPS origin or literal loopback HTTP such as `http://127.0.0.1:8080`. Use the actual Control port. Keep the token out of tool arguments, command-line arguments, agent prompts and checked-in files.
4. Configure the client to launch the Control all-in-one JAR with its `mcp` subcommand. Use an absolute path to the JAR.

### Codex CLI

Add the following to your local Codex configuration:

```toml
[mcp_servers.votingplugin-control]
command = "java"
args = ["-jar", "/absolute/path/votingplugin-control-all.jar", "mcp"]
env_vars = ["VP_CONTROL_MCP_ORIGIN", "VP_CONTROL_MCP_TOKEN"]
```

Verify that the server is configured with `codex mcp list`. [Codex MCP configuration](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)

### Claude Code

Launch Claude Code with the two environment variables available to the process. Register the stdio command in local scope:

```sh
claude mcp add --transport stdio --scope local votingplugin-control -- java -jar /absolute/path/votingplugin-control-all.jar mcp
```

Check the connection in Claude Code with `/mcp`. [Claude Code MCP configuration](https://code.claude.com/docs/en/mcp)

## Review and apply

Ask the agent to choose an authorized node, read the exact site name and prepare a preview. The agent returns a review path on the configured Control origin. You can also open **Activity → Review agent preview**. The review lists the agent grant, target, changed configuration paths and effective proposed values. Control reads the redacted main `VoteSites.yml` through its managed-file service and extracts only the named site's typed values. It discards the full document after extraction. The review shows the preset's proposed display item amount of one and the prior value when present. Control blocks approval if the file has an ambiguous structure or the node's changed paths do not match the revision-bound typed diff. Split VoteSites files are outside this setup flow. The URL must use HTTP(S) and omit userinfo, query and fragment. Only put public vote-site information in the guided fields.

Approve in the browser to queue the exact preview. The agent can poll the preview operation for the resulting apply ID and then track the apply. A changed file revision, changed session, lost capability, expired grant or incomplete diff requires a new eligible preview. Use these tools for the guided named-site workflow:

| Tool | Arguments |
|---|---|
| `list_nodes` | Optional `offset`, starting at zero. Returns up to 50 authorized nodes and `nextOffset` when there are more positions to check. |
| `read_setup` | `nodeId`, `name`. |
| `preview_setup` | `nodeId`, `readOperationId`, `options`. Options require `name`, `serviceSite` and `voteUrl` and may include `enabled`, `displayName`, `priority`, `hidden`, `voteDelay` and `material`. |
| `get_operation` | `operationId`. Only operations from the current grant are visible. |

The guided named-site workflow targets one enrolled backend per operation. Deployment, enrollment, restarts and raw file editing use their existing administrator workflows.

## Optional topology and diagnostics

Select **Topology and diagnostics** when issuing a grant to enable `get_topology`, `get_network_health` and
`refresh_node_health`. The other actions do not enable these tools.

- `get_topology` accepts an optional page offset. It reports only authorized nodes and links. Each proxy row shows when
  Control received its last topology snapshot and whether it is less than five minutes old. Links may be truncated.
- `refresh_node_health` queues the existing read-only Network Doctor inspection for one authorized, capable node.
  Wait at least two seconds between requests on the same grant. Control coalesces queued or recent observations
  and reserves shared inspection capacity for browser and administrator requests.
- `get_network_health` accepts 1–25 exact authorized node IDs. It returns bounded check IDs, statuses, affected node IDs
  and restart-required flags. Missing, stale or unsupported evidence remains unknown. A truncated report is incomplete.

These tools do not expose raw inspection data, player lookup, vote logs, health evidence values, or restart controls.

## Optional proxy routing

Select **Read and preview proxy routing** to enable `read_proxy_routing` and `preview_proxy_routing`. The grant
also needs **Track operations** to retrieve READ results and apply status. Routing targets one enrolled proxy with
`config.proxy-routing.v1`. Preview takes the current routing READ ID, an explicit `sendVotesToAllServers` Boolean
and the complete `blockedServers` list. The proxy must report the same revision and exact changes. Apply uses the
browser review and the existing proxy reload and rollback workflow. Long diffs and server identifiers outside
the bounded MCP schema require the Control browser editor.

## Optional transport check

Select **Run non-vote transport checks** and **Track operations** to run `check_transport` and retrieve its result.
The request names one authorized proxy and one authorized enrolled backend on a fresh reported link. Both must be
online, and the proxy must negotiate `config.transport-test.v1`. The existing status probe sends no vote and changes
no configuration. Results are scoped to the two node sessions and contain status codes rather than raw probe messages.

## Optional automatic vote-site creation policy

Select **Read and preview automatic vote-site creation** and **Track operations** to use `read_site_policy` and
`preview_site_policy` on an authorized Bukkit backend with `config.quick-setup.v1`. Preview takes the operation ID
from the current policy read and an explicit Boolean `enabled`. Control requires the same file revision and a diff
limited to `AutoCreateVoteSites`. The prior value is the backend’s effective value, including its default where applicable.
The operator reviews and applies the change in the browser.

### Batch proposal

Grant **Preview automatic vote-site creation across multiple backends** separately to use `preview_site_policy_batch`.
Obtain a current policy read for each target with the same grant. Pass one Boolean `enabled` and 1–10 explicit
`targets`, each with `nodeId` and `readOperationId`. Offline, unsupported, unchanged, or missing targets prevent
queueing the batch. Any failed or stale preview blocks approval for the entire batch. The browser lists each prior
value and revision. An approved apply can still fail on individual nodes. Inspect each node's outcome and rollback
indicator before taking further action.

## Optional typed General Settings

Grant **Read and preview typed General Settings** and **Track operations**. Use `read_general_settings` on one
authorized backend or proxy, then pass its current read ID and typed `overrides` to `preview_general_settings`.
The tool selects the fixed file for the target role and uses Control's curated General Settings schema. It returns
available typed fields, while redacted documents stay within Control's existing bounded retention. The node must
return the same current revision, redacted document, and expected changed paths before browser approval. A successful
proxy-file apply reports `restartRequired`; the operator restarts through the existing owner workflow.


## Optional typed Vote Sites editor

Grant **Read and preview typed Vote Sites edits** and **Track operations** to use `read_vote_site_editor`
and `preview_vote_site_editor` on one Bukkit backend with `config.files.v1`. Read an exact `siteKey`, then preview
`ADD`, `EDIT`, or `REMOVE` with that read operation ID. Add requires all nine schema fields; edit requires one or
more changed fields. Remove takes no fields. A site with inline Rewards or unreviewable fields needs the browser editor.
The proposal preserves unrelated sites and sections. Control checks the current redacted source and complete changed
paths against the reviewed proposal before making browser approval available. MCP returns typed fields and a Rewards
presence indicator, and omits raw documents and approval material.


## Optional typed Rewards editor

Grant **Read and preview typed Reward fields** and **Track operations** to use `read_reward_field`
and `preview_reward_field` on one Bukkit backend. Select an exact `siteKey` for an inline reward with
`config.files.v1`, or an exact `rewardName` for an enrolled named reward file with `config.reward-files.v1`.
Select an existing money, chance, player or broadcast message, or named item material or amount field.
Preview uses that read operation ID and one replacement scalar. Control checks the source, revision and exact
changed path before browser approval. Command payloads, reward creation or removal, raw files, and execution
are outside this tool surface.


## Coordinated network proposals

**Read and preview coordinated proxy method changes** and **Track operations** allow
`read_proxy_method` and `preview_proxy_method`. Read each peer using the intended method to select its capability,
then submit the exact proxy and every backend in its fresh reported topology, with one read ID per peer.
The total limit is ten nodes. HTTP requires `config.proxy-method.v2`; legacy methods require
`config.proxy-method.v1`. Backends also require `config.quick-setup.v1`.
Control binds the preview to the exact participants, node sessions and per-target revisions.
The proxy revision covers the method enum. Changes to other proxy transport settings are outside that revision.
Control applies backends first and waits for their durable success before issuing work to the proxy.
Per-peer status distinguishes reported persistence, rollback, and unconfirmed persistence.
Proxy acknowledgement can trigger the existing connector’s runtime replacement, but runtime readiness remains unknown.
A partial rollout requires a fresh review and can leave some backends changed.


## Guided common settings, VoteLog, and Vote Party

Grant the individual preset action and **Track operations** to use `read_guided_settings` and
`preview_guided_settings`. On each explicit backend, read the preset twice with `PRESET` and `PRESENCE`
as the evidence selector. The presence read is an internal masked managed-file read. Its raw document stays in Control.
Preview requires both successful read IDs from the same grant, session and file revision. A mismatched revision requires
both reads again. This matches file revisions from two independent reads.

A proposal accepts up to four explicit targets. Each must accept the same typed proposal, including the values
retained from its preset read. If the targets need different proposals, preview them separately. An absent key is
shown with its effective default. Approval stays unavailable if an unselected key would be materialized or a node
cannot return the complete expected diff. Each node applies its own revision and reports its result separately.

- **Common settings** edits reward processing, the extra all-sites check, and the missing-service message setting.
  Automatic site creation, fake-vote counting and update checking keep their existing tools.
- **VoteLog** edits enablement and purge retention. Retention is `-1` to disable automatic purging or 1–3650 days.
  Database selection and connection settings remain administrator-managed in the browser. A change to logging
  enablement requires a VotingPlugin restart, which this tool does not initiate.
- **Vote Party** requires both quick-setup versions and a typed v2 READ with an explicit enabled value.
  It edits enablement, threshold, recipient flags and a nonblank broadcast. Legacy v1-only connectors and
  inconsistent READ replies are unavailable for this agent workflow. Reward commands remain browser-managed.


## Typed managed-file fields

Grant **Read and preview typed managed-file fields** and **Track operations**. Use
`list_configuration_fields` to obtain the exact field catalog for a managed file,
`read_configuration_fields` on one authorized node, and `preview_configuration_fields` with
that read operation ID and up to eight explicit typed overrides. The status result returns only
present agent-visible values. List values are rendered as quoted lists and edits take JSON arrays.

The connector must confirm the same source revision, masked document and exact leaf paths before
browser approval. The proposal preserves all bytes outside selected values. Missing keys, ambiguous
mappings and lists with internal comments require the existing browser workflow. Commands, free-text
lists and database fields are absent from the MCP catalog. See [configuration coverage](configuration-coverage.md).
