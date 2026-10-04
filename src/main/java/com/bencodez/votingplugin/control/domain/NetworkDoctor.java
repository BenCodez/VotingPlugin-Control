package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.*;

/** Pure read-only checks over bounded, validated, current-session evidence and observed topology. */
public final class NetworkDoctor {
    public enum Status { PASS, WARNING, FAIL, UNKNOWN, INFO }
    public record Check(String id, String category, Status status, String title, String explanation,
                        List<String> affectedNodes, String evidence, String nextAction, boolean restartRequired) { }
    public record Report(int schemaVersion, Instant generatedAt, List<Check> checks, boolean truncated) { }
    private static final int MAX_CHECKS = 500;
    private final List<Check> checks = new ArrayList<>();
    private final List<NodeStatus> nodes;
    private final Map<String, JsonNode> evidence;
    private boolean truncated;
    private NetworkDoctor(List<NodeStatus> nodes, Map<String, JsonNode> evidence) {
        this.nodes = List.copyOf(nodes); this.evidence = Map.copyOf(evidence);
    }
    public static Report evaluate(List<NodeStatus> nodes, Map<String, JsonNode> evidence, Instant now, boolean truncated) {
        if (nodes.size() > 100) throw new IllegalArgumentException("Network Doctor is limited to 100 nodes");
        evidence.values().forEach(value -> { if (!NetworkHealthEvidence.valid(value)) throw new IllegalArgumentException("invalid evidence"); });
        var doctor = new NetworkDoctor(nodes, evidence); doctor.truncated = truncated;
        for (var node : nodes) { if (doctor.checks.size() >= MAX_CHECKS - 1) { doctor.truncated = true; break; } doctor.node(node); }
        for (var proxy : nodes) { if (doctor.checks.size() >= MAX_CHECKS - 1) { doctor.truncated = true; break; } if (proxy(proxy)) doctor.network(proxy); }
        doctor.multiProxy();
        if (doctor.truncated) doctor.check("control.evidence.truncated", "Control & Topology", Status.UNKNOWN,
                "Evidence limit reached", "The bounded report is incomplete; omitted checks have not passed.", List.of(), "limits", "Inspect smaller networks or individual nodes.", false);
        return new Report(1, now, List.copyOf(doctor.checks), doctor.truncated);
    }
    private JsonNode data(NodeStatus n) { return evidence.getOrDefault(n.nodeId(), JsonNodeFactory.instance.objectNode()); }
    private static boolean proxy(NodeStatus n) { return !"BUKKIT".equals(n.platform()); }
    private Boolean bool(NodeStatus n, String key) { var v = data(n).get(key); return v == null ? null : v.booleanValue(); }
    private String str(NodeStatus n, String key) { var v = data(n).get(key); return v == null ? null : v.asText(); }
    private Integer num(NodeStatus n, String key) { var v = data(n).get(key); return v == null ? null : v.intValue(); }
    private List<String> list(NodeStatus n, String key) {
        var v = data(n).get(key); if (v == null) return null;
        List<String> values = new ArrayList<>(); v.forEach(item -> values.add(item.asText())); return values;
    }
    private static boolean yes(Boolean v) { return Boolean.TRUE.equals(v); }
    private static boolean no(Boolean v) { return Boolean.FALSE.equals(v); }
    private static boolean invalidName(String s) { return s == null || s.isBlank() || Set.of("pleaseset", "sitename").contains(s.toLowerCase(Locale.ROOT)); }
    private static String normalized(String s) { return s.replace('-', '_').toLowerCase(Locale.ROOT); }
    private void check(String id, String category, Status status, String title, String text, List<String> affected,
                       String source, String action, boolean restart) {
        if (checks.size() >= MAX_CHECKS - 1 && !"control.evidence.truncated".equals(id)) { truncated = true; return; }
        checks.add(new Check(id, category, status, title, text, List.copyOf(affected), source, action, restart));
    }
    private void emit(NodeStatus n, String id, String category, Status status, String text, String source, String action) {
        check(id, category, status, Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('.', ' ').replace('-', ' '), text, List.of(n.nodeId()), source, action, false);
    }
    private void requirement(NodeStatus n, String id, String category, String field, String failure) {
        Boolean v = bool(n, field);
        emit(n, id, category, v == null ? Status.UNKNOWN : v ? Status.PASS : Status.FAIL,
                v == null ? "This peer did not report the required evidence." : v ? "The reported requirement is satisfied." : failure,
                field, v == null ? "Upgrade or reconnect the peer and run checks again." : v ? "No action required." : "Review the node configuration and initialization state.");
    }
    private void node(NodeStatus n) {
        emit(n, "control.node.online", "Control & Topology", n.online() ? Status.PASS : Status.UNKNOWN,
                n.online() ? "Control reports a connected node; this does not prove vote delivery." : "The node is offline; runtime and configuration cannot be verified.",
                "registry", n.online() ? "No action required." : "Reconnect the node before checking it.");
        if (!n.online() || !evidence.containsKey(n.nodeId())) {
            for (String category : List.of("Proxy Setup", "Transport", "Votifier", "Routing", "Database & Storage", "Vote Sites", "Rewards", "VoteParty", "Multi-Proxy", "Runtime"))
                emit(n, "evidence." + category.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "-"), category, Status.UNKNOWN,
                        "No fresh capability-negotiated evidence from this node session.", "data.network-health.v1", "Upgrade, reconnect or run the read-only health inspection.");
            return;
        }
        requirement(n, "configuration.parse", "Proxy Setup", "configurationHealthy", "Managed configuration could not be parsed or validated.");
        List<String> invalidFields = list(n, "invalidConfigurationFields");
        if (invalidFields != null && !invalidFields.isEmpty()) emit(n, "configuration.type.invalid", "Proxy Setup", Status.FAIL,
                "Known configuration fields have invalid types or out-of-range values.", String.join(", ", invalidFields).substring(0, Math.min(160, String.join(", ", invalidFields).length())),
                "Correct the reported managed settings; the checker does not repair them.");
        boolean managed = proxy(n) || yes(bool(n, "proxyMode")) || nodes.stream().anyMatch(p -> proxy(p) && p.backends().stream().anyMatch(b -> b.backendId().equals(n.nodeId())));
        if (!proxy(n) && managed) {
            requirement(n, "backend.proxy-mode.disabled", "Proxy Setup", "proxyMode", "This proxy-managed backend has UseBungeecord disabled.");
            String name = str(n, "serverName");
            emit(n, "backend.server-name.unset", "Proxy Setup", name == null ? Status.UNKNOWN : invalidName(name) ? Status.FAIL : Status.PASS,
                    name == null ? "Backend identity was not reported." : invalidName(name) ? "Server must be nonblank and must not be PleaseSet or SITENAME." : "A backend storage identity is configured.",
                    "serverName", "Set a unique Server name matching the intended proxy identity.");
            boolean linked = nodes.stream().anyMatch(p -> p.online() && proxy(p) && p.backends().stream().anyMatch(b -> b.backendId().equals(n.nodeId())));
            emit(n, "backend.proxy.visible", "Control & Topology", linked ? Status.PASS : Status.UNKNOWN,
                    linked ? "An online proxy reports this enrolled backend." : "No current proxy relationship is reported; enrollment alone does not establish a network.",
                    "registry.backends", "Check proxy presence reporting and node identity.");
        }
        if (managed) transport(n);
        if (!proxy(n) && no(bool(n, "proxyMode"))) {
            requirement(n, "votifier.standalone.provider", "Votifier", "votifierProviderPresent", "Standalone vote ingress has no compatible Votifier provider.");
            requirement(n, "votifier.standalone.listener", "Votifier", "votifierListenerInitialized", "Standalone Votifier ingress failed to initialize.");
        }
        if (yes(bool(n, "restartRequired"))) check("runtime.restart-required", "Runtime", Status.WARNING,
                "Restart required", "The node reports persisted settings requiring a restart.", List.of(n.nodeId()),
                "restartRequired", "Restart through the normal administrator workflow, then rerun health checks.", true);
        votifier(n, managed);
        storage(n);
        if (!proxy(n)) sites(n);
        rewards(n, managed);
        if (yes(bool(n, "votePartyEnabled"))) {
            Integer votes = num(n, "votePartyVotesRequired");
            emit(n, "voteparty.votes-required.invalid", "VoteParty", votes == null ? Status.UNKNOWN : votes <= 0 ? Status.FAIL : Status.PASS,
                    "An enabled VoteParty requires a positive vote threshold.", "votePartyVotesRequired", "Review VoteParty.VotesRequired.");
            Boolean effective = bool(n, "votePartyEffectiveReward");
            emit(n, "voteparty.reward.empty", "VoteParty", effective == null ? Status.UNKNOWN : effective ? Status.PASS : Status.WARNING,
                    "VoteParty effectiveness is based on configured rewards/commands, not execution.", "votePartyEffectiveReward", "Confirm the party has an intended effective reward or command.");
        } else if (bool(n, "votePartyEnabled") == null) unknown(n, "voteparty.state", "VoteParty", "votePartyEnabled");
        for (String field : List.of("queuedVotes", "parkedVotes", "oldestPendingSeconds", "authenticationFailures")) {
            Integer count = num(n, field);
            emit(n, "runtime." + field, "Runtime", count == null ? Status.UNKNOWN : count > 0 ? Status.INFO : Status.PASS,
                    count == null ? "The runtime does not expose this bounded observation." : "Reported " + field + ": " + count + ". This observation does not prove delivery failure.",
                    field, "Use existing runtime status tools if investigation is needed.");
        }
        emit(n, "votifier.external-destination", "Votifier", Status.UNKNOWN,
                "Control cannot prove the external voting site's configured destination or uptime.", "not observed", "Verify each voting site's destination independently.");
    }
    private void unknown(NodeStatus n, String id, String category, String source) {
        emit(n, id, category, Status.UNKNOWN, "The peer did not report sufficient evidence for this check.", source, "Obtain supported diagnostic evidence; do not assume success.");
    }
    private void transport(NodeStatus n) {
        String method = str(n, "configuredMethod"), active = str(n, "activeMethod");
        emit(n, "proxy.method.valid", "Transport", method == null ? Status.UNKNOWN : "INVALID".equals(method) ? Status.FAIL : Status.PASS,
                "Configured transport must be a supported method.", "configuredMethod", "Review BungeeMethod.");
        if (method != null && active != null && !method.equals(active)) {
            check("transport.runtime.drift", "Runtime", Status.WARNING, "Transport restart required",
                    "Persisted and active transport differ. Configuration alone does not prove the new method is running.",
                    List.of(n.nodeId()), "configuredMethod / activeMethod", "Apply the documented reload or restart and rerun checks.", true);
        } else if (active == null || method == null) unknown(n, "transport.runtime.drift", "Runtime", "activeMethod");
        else emit(n, "transport.runtime.drift", "Runtime", Status.PASS, "Configured and active methods agree.", "configuredMethod / activeMethod", "No action required.");
        requirement(n, "transport.initialized", "Transport", "transportInitialized", "The required active transport failed to initialize.");
        if ("INVALID".equals(str(n, "sharedAuthentication"))) emit(n, "transport.authentication.invalid", "Transport", Status.FAIL,
                "SharedTransportAuthentication is not a supported mode.", "sharedAuthentication", "Choose a supported authentication policy.");
        String probe = str(n, "transportProbeState"); Integer players = num(n, "carrierPlayers");
        boolean waiting = "PLUGINMESSAGING".equals(method) && (players != null && players == 0 || "WAITING".equals(probe));
        emit(n, "transport.communication", "Transport", waiting || probe == null || "UNKNOWN".equals(probe) ? Status.UNKNOWN
                        : "SUCCESS".equals(probe) ? Status.PASS : "FAILED".equals(probe) ? Status.WARNING : Status.UNKNOWN,
                waiting ? "Plugin messaging is waiting for a carrier player; zero online players is not a broken network."
                        : "SUCCESS".equals(probe) ? "A reported correlated status probe succeeded; no vote was sent." : "No successful current correlated status probe proves communication.",
                "transportProbeState / carrierPlayers", "Use the existing communication test when a carrier is available.");
        if (proxy(n)) {
            requirement(n, "proxy.manage-totals.unsupported", "Proxy Setup", "bungeeManageTotals", "BungeeManageTotals:false is documented as unsupported.");
            if ("PLUGINMESSAGING".equals(method) && yes(bool(n, "dedicatedVotingProxy"))) emit(n, "proxy.dedicated.pluginmessaging", "Proxy Setup", Status.FAIL,
                    "DedicatedVotingProxy cannot deliver through PLUGINMESSAGING.", "dedicatedVotingProxy / configuredMethod", "Choose a supported dedicated-proxy transport.");
        }
        if ("HTTP".equals(method)) {
            requirement(n, proxy(n) ? "transport.http.endpoint" : "transport.http.enrollment", "Transport",
                    proxy(n) ? "httpPublicEndpointConfigured" : "httpEnrolled", "HTTP endpoint or enrollment is unavailable.");
            if (!proxy(n)) {
                requirement(n, "transport.http.identity", "Transport", "httpIdentityValid", "HTTP client identity is invalid.");
                if (yes(bool(n, "httpEnrolled")) && yes(bool(n, "httpConnectionCodePresent"))) emit(n, "transport.http.connection-code.stale", "Transport", Status.WARNING,
                        "A connection code remains after successful enrollment.", "httpConnectionCodePresent", "Remove the stale enrollment code according to the HTTP setup guide.");
            }
        }
        if ("MYSQL".equals(method)) {
            requirement(n, "transport.mysql.database", "Transport", "databaseInitialized", "Database proxy transport is unavailable.");
            emit(n, "transport.mysql.recommendation", "Transport", Status.INFO, "MYSQL proxy transport is not the preferred network transport.", "documented transport guidance", "Consider HTTP or another supported transport for this network.");
        }
        if ("SOCKETS".equals(method)) unknown(n, "transport.sockets.peer-identity", "Transport", "peer endpoint / identity not exposed");
    }
    private void votifier(NodeStatus n, boolean managed) {
        Boolean provider = bool(n, "votifierProviderPresent");
        if (!proxy(n)) {
            Boolean trigger = bool(n, "triggerVotifierEvent");
            if (yes(trigger)) emit(n, "votifier.trigger-event.provider-missing", "Votifier", provider == null ? Status.UNKNOWN : provider ? Status.INFO : Status.FAIL,
                    provider == null ? "Synthetic event compatibility cannot be verified." : provider ? "Backend Votifier is valid here: VotingPlugin emits a synthetic VotifierEvent for proxy-delivered votes. This is separate from socket forwarding."
                            : "TriggerVotifierEvent is enabled without a compatible backend provider/API.", "triggerVotifierEvent / votifierProviderPresent", "Install a compatible provider or disable the synthetic-event option.");
            else emit(n, "votifier.backend.provider", "Votifier", trigger == null || provider == null ? Status.UNKNOWN : Status.INFO,
                    "A backend Votifier installation does not establish a duplicate forwarding path.", "triggerVotifierEvent / votifierProviderPresent", "Verify intended ingress; no change is required solely for plugin presence.");
            return;
        }
        boolean secondary = yes(bool(n, "multiProxySupport")) && no(bool(n, "primaryServer"));
        if (secondary) emit(n, "votifier.secondary.ingress", "Votifier", Status.INFO, "A secondary proxy may use the primary for vote ingress and does not require its own Votifier.", "multiProxySupport / primaryServer", "Verify the primary ingress.");
        else {
            requirement(n, "votifier.proxy.provider", "Votifier", "votifierProviderPresent", "The expected proxy ingress has no compatible Votifier provider.");
            requirement(n, "votifier.proxy.listener", "Votifier", "votifierListenerInitialized", "The expected Votifier listener failed to initialize.");
        }
        List<String> plugins = list(n, "detectedPlugins");
        if (plugins != null && plugins.stream().filter(p -> Set.of("votifier", "votifierplus", "nuvotifier").contains(p.toLowerCase(Locale.ROOT))).count() > 1)
            emit(n, "votifier.providers.competing", "Votifier", Status.WARNING, "Multiple Votifier implementations may compete for proxy ingress.", "detectedPlugins", "Keep one intended listener per ingress role.");
        List<String> destinations = list(n, "forwardingDestinations");
        if (!yes(bool(n, "votifierForwardingKnown")) || destinations == null) unknown(n, "votifier.forwarding.duplicate-path", "Votifier", "optional VotifierPlus diagnostics");
        else if (destinations.isEmpty()) emit(n, "votifier.forwarding.duplicate-path", "Votifier", Status.PASS, "The provider reports no enabled socket forwarding destinations.", "forwardingDestinations", "No action required.");
        else if (managed) {
            boolean proven = nodes.stream().anyMatch(b -> !proxy(b) && yes(bool(b, "votifierProviderPresent"))
                    && destinations.stream().anyMatch(d -> d.equals(b.nodeId()) || d.equals(str(b, "serverName")))
                    && n.backends().stream().anyMatch(r -> r.backendId().equals(b.nodeId())));
            emit(n, "votifier.forwarding.duplicate-path", "Votifier", Status.FAIL,
                    proven ? "An enabled proxy VotifierPlus destination matches an enrolled VotingPlugin backend with a Votifier provider: socket forwarding and VotingPlugin delivery can process the same vote twice."
                            : "Proxy VotifierPlus socket forwarding is enabled alongside VotingPlugin proxy delivery, creating a second possible path. Destination names do not prove remote host identity.",
                    "forwardingDestinations / registry.backends", "Disable unintended VotifierPlus socket forwarding. TriggerVotifierEvent does not make socket forwarding safe.");
        }
    }
    private void storage(NodeStatus n) {
        requirement(n, "storage.database.unavailable", "Database & Storage", "databaseInitialized", "The selected storage/database is unavailable.");
        requirement(n, "storage.jdbc-driver.missing", "Database & Storage", "jdbcDriverAvailable", "The selected database has no suitable JDBC driver.");
        if (yes(bool(n, "voteLoggingEnabled"))) requirement(n, "storage.votelog.unreadable", "Database & Storage", "voteLogReadable", "VoteLogging is enabled but the retained logged-event table is unavailable/unreadable.");
        else if (bool(n, "voteLoggingEnabled") == null) unknown(n, "storage.votelog.unreadable", "Database & Storage", "voteLoggingEnabled");
        for (String field : List.of("voteCache", "nonVotedCache")) if (yes(bool(n, field + "Mysql"))) {
            Boolean main = bool(n, field + "MainMysql");
            if (main == null) unknown(n, "storage." + field + "Mysql.unavailable", "Database & Storage", field + "MainMysql");
            else requirement(n, "storage." + field + "Mysql.unavailable", "Database & Storage", main ? "databaseInitialized" : field + "DatabaseInitialized",
                    field + " requests MySQL but its selected database is unavailable.");
        }
        if (yes(bool(n, "globalDataEnabled")) && yes(bool(n, "globalDataUseMainMysql")))
            requirement(n, "storage.globalDataUseMainMysql.unavailable", "Database & Storage", "databaseInitialized", "Enabled GlobalData requires its selected main database.");
        if (!proxy(n) && yes(bool(n, "proxyMode"))) {
            String prefix = str(n, "databasePrefix");
            if (prefix == null) unknown(n, "storage.backend-prefix.isolation", "Database & Storage", "databasePrefix");
            else if (!prefix.isEmpty()) emit(n, "storage.backend-prefix.isolation", "Database & Storage", Status.WARNING,
                    "A per-backend database prefix may isolate data intended to be shared.", "databasePrefix", "Confirm this prefix is intentional for shared/global storage.");
        }
    }
    private void sites(NodeStatus n) {
        JsonNode sites = data(n).get("voteSites");
        if (sites == null) { unknown(n, "votesite.inventory", "Vote Sites", "voteSites"); return; }
        boolean complete = yes(bool(n, "voteSitesComplete"));
        emit(n, "votesite.configured", "Vote Sites", sites.isEmpty() && complete ? Status.WARNING : complete ? Status.PASS : Status.UNKNOWN,
                sites.isEmpty() ? "No configured Vote Sites were reported." : "Vote Site inventory is a bounded configuration observation.", "voteSites / voteSitesComplete", "Configure intended sites and complete the inventory.");
        long enabled = 0; Map<String, String> services = new HashMap<>();
        for (JsonNode site : sites) {
            if (!site.path("enabled").isBoolean()) { unknown(n, "votesite.enabled", "Vote Sites", "voteSites.enabled"); continue; }
            if (!site.path("enabled").booleanValue()) continue;
            enabled++;
            String service = site.has("serviceSite") ? site.path("serviceSite").asText() : null;
            String scope = "Site " + site.path("name").asText() + ": ";
            if (service == null) unknown(n, "votesite.service-site.missing", "Vote Sites", "voteSites.serviceSite");
            else if (service.isBlank() || service.equalsIgnoreCase("SERVICESITE")) emit(n, "votesite.service-site.missing", "Vote Sites", Status.FAIL, scope + "ServiceSite is blank or a placeholder.", "voteSites.serviceSite", "Set the service identifier supplied by the voting site.");
            else {
                String old = services.putIfAbsent(service.toLowerCase(Locale.ROOT), service);
                if (old != null) emit(n, "votesite.service-site.duplicate", "Vote Sites", old.equals(service) ? Status.FAIL : Status.WARNING,
                        scope + "Another enabled site has the same case-normalized ServiceSite.", "voteSites.serviceSite", "Use one unambiguous enabled site per service identifier.");
            }
            if (site.has("delayValid") && !site.path("delayValid").booleanValue()) emit(n, "votesite.delay.invalid", "Vote Sites", Status.FAIL, scope + "VoteDelay cannot be parsed.", "voteSites.delayValid", "Correct the VoteDelay format.");
            else if (!site.has("delayValid")) unknown(n, "votesite.delay.invalid", "Vote Sites", "voteSites.delayValid");
            if (site.has("delayHours") && (site.path("delayHours").asInt() == 0 || site.path("delayHours").asInt() > 8760)) emit(n, "votesite.delay.unusual", "Vote Sites", Status.WARNING, scope + "Vote delay is unusually short or long.", "voteSites.delayHours", "Confirm the voting site's intended delay.");
            if (site.has("voteUrlState") && !"VALID".equals(site.path("voteUrlState").asText())) emit(n, "votesite.url.unfinished", "Vote Sites", Status.WARNING, scope + "VoteURL is blank, an example or invalid.", "voteSites.voteUrlState", "Set the intended public voting URL; remote uptime is not observed.");
            if (site.path("autoCreated").asBoolean(false)) emit(n, "votesite.auto-created.unfinished", "Vote Sites", Status.WARNING, scope + "Review the automatically created site.", "voteSites.autoCreated", "Complete or remove the auto-created configuration.");
            if (site.has("hasRewards") && !site.path("hasRewards").booleanValue()) emit(n, "votesite.rewards.none", "Vote Sites", Status.INFO, scope + "No rewards are configured; totals-only sites can be intentional.", "voteSites.hasRewards", "No action required for an intentional totals-only site.");
        }
        if (enabled == 0) emit(n, "votesite.enabled.none", "Vote Sites", complete ? Status.WARNING : Status.UNKNOWN, "No enabled sites are present in the reported inventory.", "voteSites", "Check enabled Vote Sites.");
        if (yes(bool(n, "autoCreateVoteSites")) && enabled > 0) emit(n, "votesite.auto-create.enabled", "Vote Sites", Status.WARNING, "AutoCreateVoteSites remains enabled after sites have been configured.", "autoCreateVoteSites", "Consider disabling automatic creation after setup.");
        for (String field : List.of("detectedServices", "loggedServices")) {
            List<String> observed = list(n, field);
            if (observed == null || !complete) unknown(n, "votesite." + field + ".unmatched", "Vote Sites", field);
            else if (observed.stream().anyMatch(s -> !services.containsKey(s.toLowerCase(Locale.ROOT)))) emit(n, "votesite." + field + ".unmatched", "Vote Sites", Status.WARNING,
                    "An observed service has no matching enabled Vote Site.", field, "Review observed identifiers without auto-creating sites.");
        }
        Integer max = num(n, "maxVotesPerDay");
        if (max != null && max > 0 && max < enabled) emit(n, "votesite.daily-limit.conflict", "Vote Sites", Status.WARNING, "MaxAmountOfVotesPerDay is lower than the enabled site count.", "maxVotesPerDay / voteSites", "Confirm the intended daily limit.");
    }
    private void rewards(NodeStatus n, boolean managed) {
        Boolean process = bool(n, "processRewards");
        if (process == null) unknown(n, "rewards.processing.disabled", "Rewards", "processRewards");
        else if (!process) emit(n, "rewards.processing.disabled", "Rewards", Status.WARNING, "ProcessRewards is disabled.", "processRewards", "Confirm this node intentionally processes no rewards.");
        for (String field : List.of("missingRewardFiles", "invalidRewardFiles", "missingRewardDependencies")) {
            List<String> values = list(n, field);
            if (values == null || !yes(bool(n, "rewardsComplete"))) unknown(n, "rewards." + field, "Rewards", field);
            else if (!values.isEmpty()) emit(n, "rewards." + field, "Rewards", field.equals("missingRewardDependencies") ? Status.WARNING : Status.FAIL,
                    "The node reports " + values.size() + " missing/invalid reward references or dependencies.", field, "Review the named reward inventory and required plugins.");
        }
        if (managed && (yes(bool(n, "perServerRewards")) || yes(bool(n, "perServerPoints")))) {
            String name = str(n, "serverName");
            emit(n, "rewards.per-server.identity", "Rewards", name == null ? Status.UNKNOWN : invalidName(name) ? Status.FAIL : Status.INFO,
                    "PerServerRewards/PerServerPoints require a valid unique storage identity.", "serverName / perServerRewards / perServerPoints", "Correct invalid or colliding server identities.");
        }
        if (yes(bool(n, "giveExtraAllSitesRewards"))) {
            String method = str(n, "configuredMethod");
            if (method == null) unknown(n, "rewards.extra-all-sites.method", "Rewards", "configuredMethod");
            // Applicability is producer/documentation-specific; do not infer an unsupported method from its label.
            if (no(bool(n, "extraAllSitesCheck"))) emit(n, "rewards.extra-all-sites.check", "Rewards", Status.WARNING, "GiveExtraAllSitesRewards is enabled without ExtraAllSitesCheck.", "giveExtraAllSitesRewards / extraAllSitesCheck", "Review the documented paired options.");
        }
    }
    private void compare(NodeStatus a, NodeStatus b, String field, String id, String category, Status mismatch) {
        JsonNode left = data(a).get(field), right = data(b).get(field);
        Status status = left == null || right == null ? Status.UNKNOWN : left.equals(right) ? Status.PASS : mismatch;
        check(id, category, status, id,
                status == Status.UNKNOWN ? "One or both participating peers did not report this setting."
                        : status == Status.PASS ? "Reported settings agree for this observed network relationship." : "Reported settings differ within this proxy/backend network.",
                List.of(a.nodeId(), b.nodeId()), field, status == Status.PASS ? "No action required." : "Review the intended shared network settings on both nodes.", false);
    }
    private void network(NodeStatus p) {
        if (!p.online() || !evidence.containsKey(p.nodeId())) return;
        List<NodeStatus> members = nodes.stream().filter(b -> !proxy(b) && p.backends().stream().anyMatch(r -> r.backendId().equals(b.nodeId()))).toList();
        boolean complete = yes(bool(p, "topologyComplete")) && !truncated;
        List<String> reportedNames = list(p, "backendNames");
        Set<String> known = reportedNames == null ? new HashSet<>() : new HashSet<>(reportedNames);
        if (reportedNames == null) p.backends().forEach(b -> known.add(b.backendId()));
        for (var backend : p.backends()) {
            if (nodes.stream().noneMatch(n -> !proxy(n) && n.nodeId().equals(backend.backendId()))) emit(p, "topology.backend.unenrolled", "Control & Topology", Status.WARNING,
                    "A proxy-reported backend is not enrolled in this bounded Control view.", "registry.backends", "Enroll the intended backend or verify the reported identity.");
            if (!backend.presenceKnown()) emit(p, "routing.presence.unknown", "Routing", Status.UNKNOWN,
                    "Backend presence is not known; presence-dependent routing cannot be verified.", "registry.backends.presenceKnown", "Check presence reporting without sending a vote.");
        }
        if (members.isEmpty()) emit(p, "topology.backends.missing", "Control & Topology", complete ? Status.WARNING : Status.UNKNOWN,
                "No enrolled backend relationship is available for this proxy.", "registry.backends", "Verify backend enrollment and topology reporting.");
        if (!members.isEmpty() && members.stream().allMatch(b -> no(bool(b, "proxyMode")))) emit(p, "proxy.backends.mode-disabled", "Proxy Setup", Status.WARNING,
                "All reported enrolled backends have proxy mode disabled.", "proxyMode / registry.backends", "Enable proxy mode on intended VotingPlugin-managed backends.");
        Map<String, NodeStatus> normalized = new HashMap<>();
        for (NodeStatus b : members) {
            if (checks.size() >= MAX_CHECKS - 1) { truncated = true; return; }
            if (!b.online() || !evidence.containsKey(b.nodeId())) { unknown(b, "proxy.method.mismatch", "Transport", "current-session evidence"); continue; }
            String name = str(b, "serverName");
            if (name != null && !invalidName(name)) {
                NodeStatus old = normalized.putIfAbsent(normalized(name), b);
                if (old != null) {
                    String previous = str(old, "serverName");
                    String id = previous.equalsIgnoreCase(name) ? "backend.server-name.duplicate" : "backend.server-name.normalization-collision";
                    if (yes(bool(old, "perServerRewards")) || yes(bool(old, "perServerPoints"))
                            || yes(bool(b, "perServerRewards")) || yes(bool(b, "perServerPoints"))) {
                        check("rewards.per-server.identity", "Rewards", Status.FAIL, "Per-server reward identities collide",
                                "PerServerRewards/PerServerPoints cannot separate these normalized backend identities.",
                                List.of(old.nodeId(), b.nodeId()), "serverName / per-server options", "Assign distinct normalized identities.", true);
                    }
                    check(id, "Proxy Setup", Status.FAIL, "Backend storage identities collide",
                            "Two backends in this network have duplicate, case-only or dash-to-underscore normalized identities. Per-server storage may collide.",
                            List.of(old.nodeId(), b.nodeId()), "serverName / observed topology", "Assign distinct backend identities and review per-server storage before restarting.", true);
                }
                if (!name.equals(b.nodeId())) emit(b, "backend.server-name.proxy-mismatch", "Proxy Setup", Status.WARNING,
                        "Configured Server differs from the proxy-reported backend identity. Control node IDs can be customized; verify the intended mapping.",
                        "serverName / registry.backends.backendId", "Confirm the proxy backend and VotingPlugin Server mapping.");
            }
            compare(p, b, "configuredMethod", "proxy.method.mismatch", "Transport", Status.FAIL);
            for (String field : List.of("onlineMode", "bedrockPlayerPrefix", "timeHourOffset", "resetMilestonesMonthly", "monthDateTotals"))
                compare(p, b, field, "network.consistency." + field, "Proxy Setup", Status.WARNING);
            String method = str(p, "configuredMethod");
            if ("PLUGINMESSAGING".equals(method)) {
                compare(p, b, "pluginMessageChannel", "transport.pluginmessaging.channel-mismatch", "Transport", Status.FAIL);
                compare(p, b, "encryption", "transport.encryption.mismatch", "Transport", Status.FAIL);
            }
            if (Set.of("REDIS", "MQTT", "SOCKETS").contains(method == null ? "" : method)) {
                compare(p, b, "sharedAuthentication", "transport.authentication.mode-mismatch", "Transport", Status.FAIL);
                if (yesRequired(p) || yesRequired(b)) compare(p, b, "sharedKeyFingerprint", "transport.authentication.key-mismatch", "Transport", Status.FAIL);
                if ("COMPATIBILITY".equals(str(p, "sharedAuthentication")) && data(b).has("sharedAuthentication")) emit(p, "transport.authentication.compatibility", "Transport", Status.WARNING,
                        "Compatibility authentication supports rolling upgrades. Consider REQUIRED only after every intended peer supports authenticated transport.", "sharedAuthentication", "Verify all peers before changing authentication mode.");
            }
            if (Set.of("REDIS", "MQTT").contains(method == null ? "" : method)) compare(p, b, "transportNamespace", "transport.namespace.mismatch", "Transport", Status.FAIL);
            if ("REDIS".equals(method)) compare(p, b, "redisSsl", "transport.redis.ssl-mismatch", "Transport", Status.FAIL);
            if ("MQTT".equals(method)) mqttIdentity(p, b);
            if (yes(bool(p, "allowUnjoined"))) requirement(b, "routing.allow-unjoined.prerequisite", "Routing", "allowUnjoined", "Proxy accepts unjoined players but backend AllowUnjoined is disabled.");
            if (yes(bool(p, "waitForUserOnline")) && no(bool(b, "waitForUserOnline"))) emit(b, "routing.wait-online.combination", "Routing", Status.WARNING,
                    "Proxy and backend waiting behavior differ; verify intended offline reward routing.", "waitForUserOnline", "Review the documented wait-for-online reward path.");
            for (String field : List.of("globalDataEnabled", "globalDataPrefix")) compare(p, b, field, "storage.consistency." + field, "Database & Storage", Status.WARNING);
            List<String> waiting = list(p, "waitUntilVoteDelayServices");
            if (waiting != null && yes(bool(b, "voteSitesComplete")) && data(b).has("voteSites")) {
                Set<String> services = new HashSet<>(); data(b).path("voteSites").forEach(s -> { if (s.has("serviceSite")) services.add(s.path("serviceSite").asText().toLowerCase(Locale.ROOT)); });
                if (waiting.stream().anyMatch(s -> !services.contains(s.toLowerCase(Locale.ROOT)))) emit(b, "votesite.proxy-delay.unmatched", "Vote Sites", Status.WARNING,
                        "Proxy WaitUntilVoteDelay references a service not configured on this backend.", "waitUntilVoteDelayServices / voteSites", "Align intended delay service identifiers.");
            }
        }
        for (int i = 0; i < members.size(); i++) for (int j = i + 1; j < members.size(); j++) {
            if (checks.size() >= MAX_CHECKS - 1) { truncated = true; return; }
            var a = members.get(i); var b = members.get(j);
            if (!a.online() || !b.online() || !evidence.containsKey(a.nodeId()) || !evidence.containsKey(b.nodeId())) continue;
            compare(a, b, "globalDataEnabled", "storage.consistency.globalDataEnabled", "Database & Storage", Status.WARNING);
            if (yes(bool(a, "globalDataEnabled")) || yes(bool(b, "globalDataEnabled"))) {
                compare(a, b, "globalDataPrefix", "storage.consistency.globalDataPrefix", "Database & Storage", Status.WARNING);
                compare(a, b, "storageFingerprint", "storage.shared-identity.mismatch", "Database & Storage", Status.WARNING);
                compare(a, b, "databasePrefix", "storage.prefix.mismatch", "Database & Storage", Status.WARNING);
            }
            if ("MQTT".equals(str(p, "configuredMethod"))) {
                mqttIdentity(a, b);
            }
        }
        routing(p, known, complete);
    }
    private void mqttIdentity(NodeStatus a, NodeStatus b) {
        String left = str(a, "mqttClientId"), right = str(b, "mqttClientId");
        if (left == null || right == null) unknown(a, "transport.mqtt.client-id.duplicate", "Transport", "mqttClientId");
        else if (!left.isBlank() && left.equals(right)) check("transport.mqtt.client-id.duplicate", "Transport", Status.FAIL,
                "Duplicate MQTT ClientIDs", "Participating clients can evict one another from the broker.", List.of(a.nodeId(), b.nodeId()), "mqttClientId", "Set distinct MQTT ClientIDs.", true);
    }
    private boolean yesRequired(NodeStatus n) { return "REQUIRED".equals(str(n, "sharedAuthentication")); }
    private void routing(NodeStatus p, Set<String> known, boolean complete) {
        for (String field : List.of("blockedServers", "whitelistedServers", "broadcastServers", "offlineForwardServers", "votePartyServers")) {
            List<String> entries = list(p, field);
            if (entries == null) { unknown(p, "routing." + field + ".unknown", "Routing", field); continue; }
            for (String entry : entries) if (!known.contains(entry)) emit(p, "routing.unknown-server", "Routing", complete ? Status.FAIL : Status.UNKNOWN,
                    complete ? "A routing list references an unknown backend identity." : "Topology is incomplete; the routing destination cannot be verified.", field, "Review routing destinations against the proxy's backend inventory.");
        }
        List<String> blocked = list(p, "blockedServers"), white = list(p, "whitelistedServers");
        if (blocked == null || white == null || !complete) { unknown(p, "routing.no-eligible-backends", "Routing", "routing / topologyComplete"); return; }
        if (white.stream().anyMatch(blocked::contains)) emit(p, "routing.block-whitelist.overlap", "Routing", Status.WARNING,
                "BlockedServers and WhiteListedServers overlap; this is confusing even when block precedence is intended.", "blockedServers / whitelistedServers", "Remove unintended overlapping rules.");
        long eligible = known.stream().filter(s -> !blocked.contains(s) && (white.isEmpty() || white.contains(s))).count();
        emit(p, "routing.no-eligible-backends", "Routing", eligible == 0 ? Status.FAIL : Status.PASS,
                eligible == 0 ? "Routing rules leave no possible backend destination." : "Routing rules leave at least one configured backend destination; runtime delivery is checked separately.",
                "blockedServers / whitelistedServers / topology", "Review whitelist and blocked rules.");
        if (known.stream().anyMatch(s -> blocked.contains(s) || !white.isEmpty() && !white.contains(s))) emit(p, "routing.backend.excluded", "Routing", Status.WARNING,
                "At least one known backend is excluded from normal vote routing.", "blockedServers / whitelistedServers", "Confirm intentional exclusions.");
    }
    private void multiProxy() {
        List<NodeStatus> proxies = nodes.stream().filter(NetworkDoctor::proxy).filter(p -> p.online() && yes(bool(p, "multiProxySupport"))).toList();
        for (NodeStatus p : proxies) {
            if (checks.size() >= MAX_CHECKS - 1) { truncated = true; return; }
            String method = str(p, "multiProxyMethod");
            emit(p, "multiproxy.method.valid", "Multi-Proxy", method == null ? Status.UNKNOWN : "INVALID".equals(method) ? Status.FAIL : Status.PASS,
                    "MultiProxyMethod must be a supported method.", "multiProxyMethod", "Review MultiProxyMethod.");
            String name = str(p, "proxyServerName");
            emit(p, "multiproxy.identity.unset", "Multi-Proxy", name == null ? Status.UNKNOWN : invalidName(name) || name.equalsIgnoreCase("proxy") ? Status.FAIL : Status.PASS,
                    "Multi-proxy requires a nonblank, nondefault unique identity.", "proxyServerName", "Set a unique ProxyServerName.");
            if (yes(bool(p, "multiProxyOneGlobalReward")) && yes(bool(p, "sendVotesToAllServers"))) emit(p, "multiproxy.global-reward.send-all", "Multi-Proxy", Status.FAIL,
                    "MultiProxyOneGlobalReward requires SendVotesToAllServers to be disabled.", "multiProxyOneGlobalReward / sendVotesToAllServers", "Correct the incompatible options.");
            List<String> declared = list(p, "proxyServers");
            if (declared == null) { unknown(p, "multiproxy.relationships", "Multi-Proxy", "proxyServers"); continue; }
            List<NodeStatus> related = proxies.stream().filter(other -> other.nodeId().equals(p.nodeId()) || declared.contains(other.nodeId()) || declared.contains(str(other, "proxyServerName"))).toList();
            boolean complete = yes(bool(p, "topologyComplete")) && declared.stream().allMatch(id -> related.stream().anyMatch(other -> id.equals(other.nodeId()) || id.equals(str(other, "proxyServerName"))));
            long primary = related.stream().filter(other -> yes(bool(other, "primaryServer"))).count();
            Status state = primary > 1 ? Status.FAIL : !complete || related.stream().anyMatch(other -> bool(other, "primaryServer") == null) ? Status.UNKNOWN : primary == 1 ? Status.PASS : Status.FAIL;
            emit(p, "multiproxy.primary.duplicate", "Multi-Proxy", state,
                    "The declared multi-proxy group requires exactly one primary; incomplete or offline evidence cannot prove that count.", "primaryServer / proxyServers", "Verify all intended peers and configure one primary.");
            for (String reference : declared) if (related.stream().noneMatch(other -> reference.equals(other.nodeId()) || reference.equals(str(other, "proxyServerName")))) unknown(p, "multiproxy.peer.missing", "Multi-Proxy", "proxyServers");
            for (NodeStatus other : related) if (!other.nodeId().equals(p.nodeId())) {
                if (name != null && name.equalsIgnoreCase(str(other, "proxyServerName"))) check("multiproxy.identity.duplicate", "Multi-Proxy", Status.FAIL,
                        "Duplicate proxy identity", "Related proxies share a case-normalized ProxyServerName.", List.of(p.nodeId(), other.nodeId()), "proxyServerName", "Assign distinct proxy identities.", true);
                compare(p, other, "multiProxyMethod", "multiproxy.method.mismatch", "Multi-Proxy", Status.FAIL);
                compare(p, other, "sharedAuthentication", "multiproxy.authentication.mismatch", "Multi-Proxy", Status.FAIL);
                if (yesRequired(p) || yesRequired(other)) compare(p, other, "multiProxyKeyFingerprint", "multiproxy.key.mismatch", "Multi-Proxy", Status.FAIL);
                compare(p, other, "encryption", "multiproxy.encryption.mismatch", "Multi-Proxy", Status.FAIL);
            }
        }
    }
}
