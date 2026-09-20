---
name: architecture
description: Control and node responsibility boundaries.
triggers: [architecture, connector, enrollment, hosted]
last_updated: 2026-09-20
mex:
  id: mx_01M3074CECX8T3QW4PR0QH6THT
  type: architecture
  status: promoted
  revision: 1
  title: architecture
---

# Control boundaries

Control is a separate service: browsers and automation call its HTTP API, while node connectors initiate outbound requests and pull tasks. Capability acceptance, node session, and task attempt form the compatibility boundary; an older or offline node cannot be treated as a successful participant. VotingPlugin remains usable when Control is absent. Source: root `AGENTS.md`, `docs/control-management.md`, `src/main/java/com/bencodez/votingplugin/control/domain/ConfigurationOperations.java`.

The WebUI is a dependency-free JavaScript client over the same API. Its graph symbols may help locate client state, but server authorization and durable operation rules live in Java and formal contracts. Hosted Control is supervised by VotingPlugin, but a child process health result does not make Control part of the vote path. Source: `README.md`, `src/main/resources/web/app.js`, root `AGENTS.md`.
