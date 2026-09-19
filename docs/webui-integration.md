# Disposable connector and browser validation

The WebUI visual editors use the existing authenticated Control HTTP API and
VotingPlugin connector. A local integration gate was run with one disposable
Paper 26.2 server (Java 25), current VotingPlugin 7.1.2-SNAPSHOT, and the
Control shaded JAR on loopback addresses. It did not use a production node or
configuration. The test server used offline mode solely for this isolated gate;
never expose that setup to a network.

## Running connector path

The external test harness drove real API requests through Control, the HTTP
connector, the Paper plugin, its configuration files, and VotingPlugin reload.
It exercised:

- General Settings `Config.yml` READ → typed state → preview → approval/APPLY →
  reload → confirmed READ.
- Vote Site add, edit, remove, and confirmed READ, including an inline reward
  left intact by an unrelated site field edit.
- Inline reward create, command append, Money edit, and confirmed READ.
- Named reward-file names-only inventory, READ, command append, Money and item
  Amount edits, reload and confirmed READ. The disposable file retained its
  custom item metadata and unknown field.
- A named reward deliberately made inactive at reload was reported as
  `RELOAD_FAILED`; Control restored the previous file and a fresh READ
  confirmed the rollback. No player reward was executed.
- An old expected revision rejected after a later write; an old connector
  session's retained READ rejected after a Paper restart (`TARGET_CHANGED`).
- A traversal filename rejected by Control before it could reach the node.

The first running `Config.yml` READ revealed a null comment-metadata entry in
the Bukkit YAML loader. The connector now preserves that blank-line marker
while redacting comments and returns a sanitized structured read error if a genuine I/O failure remains.
The disposable test reproduced the failure before the fix and completed the
above read/apply path after the fix.

This gate used disposable files and no real votes or player rewards. It did not
exercise a running two-backend partial apply, a full proxy, or a failure of the
general VotingPlugin reload routine itself. The named-reward post-reload failure
above and service-layer tests cover restoration; they are not evidence for every
possible plugin-reload failure. These gaps are not described as successful live
integration results.

The local evidence, artifacts, and gate scripts were kept **outside** the Git
repositories and are not part of this change. Credential and admin-token files
were private (mode 0600); never copy their contents into logs, URLs, commits,
or bug reports. The scripts assumed a loopback disposable Paper/Control pair
and must not be pointed at a live server.

## Browser and package gate

The actual `web/*` assets extracted from the clean-verified Control shaded JAR
were served to Chromium behind controlled API fixtures. Separate browser gates
covered Home/server selection, single/multi/global scope, General Settings
mixed/partial preview and stale approval, Vote Sites add/edit/remove and
partial rollback presentation, Rewards mixed commands/append and named item
preview. Desktop widths 1440, 1280, 1024 and 768 px plus mobile 390 px were
checked for page errors, horizontal overflow, visible focus, and usable
navigation. A browser fixture is not substituted for the running connector
gate above.

For this candidate, JavaScript syntax checks, the complete frontend suite,
`mvn -B test`, `mvn -B clean verify`, `git diff --check`, and JAR ZIP
integrity/content checks were run alongside the disposable connector/browser
gates. Keep Maven builds sequential. Visual editors must still require exact
preview and approval; an automatic READ or navigation action must never APPLY.
