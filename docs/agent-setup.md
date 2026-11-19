# Configure a vote site with an MCP agent

VotingPlugin Control can provide a local stdio MCP connection to Codex CLI and Claude Code. The agent can discover authorized enrolled nodes, read one named vote site on a Bukkit backend, preview a change and track the result. The owner reviews and applies the preview in Control. The backend uses existing managed-file and quick-setup capabilities over the current task protocol.

## Enable MCP management

Control disables the agent API by default. Set `CONTROL_MCP_ENABLED=true` on the Control server and restart that service to enable it. The client adapter cannot enable server access. Remove the setting or set it to `false` and restart Control to disable it. See the [management coverage matrix](mcp-network-coverage.md) for supported tools.

## Connect a client

1. Install VotingPlugin and enroll the backend as an administrator. Check that Control reports the backend online with both accepted capabilities, `config.files.v1` and `config.quick-setup.v1`.
2. Open **Access → Agent access** in Control. Enter exact node IDs or enter one online proxy ID to grant its enrolled network. Choose the actions and lifetime. Issue the credential and copy it when displayed. Control keeps only its verifier in memory. Grants expire after the chosen lifetime (up to 24 hours), can be revoked immediately, and are invalidated when Control restarts. Revocation prevents new requests and prevents approval of pending previews. A write the owner already approved can still complete. A proxy grant covers the proxy and the enrolled Bukkit nodes in its reported snapshot at issuance. Removing a member or disconnecting the proxy drops current access. A new proxy session or newly reported backend requires a fresh grant. The grant list shows the selected proxy and node count. Control uses the latest reported snapshot for that live proxy session. A heartbeat does not certify the age or accuracy of its topology.
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

Use `/mcp` to check the connection. [Claude Code MCP configuration](https://code.claude.com/docs/en/mcp)

## Review and apply

Ask the agent to choose an authorized node, read the exact site name and prepare a preview. The agent returns a review path on the configured Control origin. You can also open **Activity → Review agent preview**. The review lists the agent grant, target, changed configuration paths and effective proposed values. Control reads the redacted main `VoteSites.yml` through its managed-file service and extracts only the named site's typed values. It discards the full document after extraction. The review shows the preset's proposed display item amount of one and the prior value when present. Control blocks approval if the file has an ambiguous structure or the node's changed paths do not match the revision-bound typed diff. Split VoteSites files are outside this setup flow. The URL must use HTTP(S) and omit userinfo, query and fragment. Only put public vote-site information in the guided fields.

Approve in the browser to queue the exact preview. The agent can poll the preview operation for the resulting apply ID and then track the apply. A changed file revision, changed session, lost capability, expired grant or incomplete diff requires a new eligible preview. The MCP server exposes four tools:

| Tool | Arguments |
|---|---|
| `list_nodes` | Optional `offset`, starting at zero. Returns up to 50 authorized nodes and `nextOffset` when there are more positions to check. |
| `read_setup` | `nodeId`, `name`. |
| `preview_setup` | `nodeId`, `readOperationId`, `options`. Options require `name`, `serviceSite` and `voteUrl` and may include `enabled`, `displayName`, `priority`, `hidden`, `voteDelay` and `material`. |
| `get_operation` | `operationId`. Only operations from the current grant are visible. |

The adapter can prepare work for a single enrolled backend per operation. Deployment, enrollment, restarts and raw file editing use their existing administrator workflows.

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
