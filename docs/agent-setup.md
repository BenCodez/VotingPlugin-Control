# Configure a vote site with an MCP agent

VotingPlugin Control can provide a local stdio MCP connection to Codex CLI and Claude Code. The agent can discover authorized enrolled nodes, read one named vote site on a Bukkit backend, preview a change and track the result. The owner reviews and applies the preview in Control. The backend uses existing managed-file and quick-setup capabilities over the current task protocol.

## Connect a client

1. Install VotingPlugin and enroll the backend as an administrator. Check that Control reports the backend online with both accepted capabilities, `config.files.v1` and `config.quick-setup.v1`.
2. Open **Access → Agent access** in Control. Enter the exact node IDs and choose the actions and lifetime. Issue the credential and copy it when displayed. Control keeps only its verifier in memory. Grants expire after the chosen lifetime (up to 24 hours), can be revoked immediately, and are invalidated when Control restarts. Revocation prevents new requests and prevents approval of pending previews. A write the owner already approved can still complete.
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
| `list_nodes` | Empty object. Returns up to the 50 nodes on the grant. |
| `read_setup` | `nodeId`, `name`. |
| `preview_setup` | `nodeId`, `readOperationId`, `options`. Options require `name`, `serviceSite` and `voteUrl` and may include `enabled`, `displayName`, `priority`, `hidden`, `voteDelay` and `material`. |
| `get_operation` | `operationId`. Only operations from the current grant are visible. |

The adapter can prepare work for a single enrolled backend per operation. Deployment, enrollment, restarts and raw file editing use their existing administrator workflows.
