package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkDoctorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void offlineOrMissingEvidenceProducesUnknownAndStandaloneDoesNotCompare() {
        NodeStatus standalone = node("standalone", "BUKKIT", false, List.of());
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(standalone), Map.of(), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(report, "evidence.proxy-setup"));
        assertFalse(report.checks().stream().anyMatch(c -> c.id().equals("proxy.method.mismatch")));
    }

    @Test
    void validBackendVotifierTriggerIsInfoAndNoCarrierIsUnknown() {
        NodeStatus backend = node("backend-a", "BUKKIT", true, List.of());
        ObjectNode evidence = base().put("proxyMode", false).put("configurationHealthy", true)
                .put("triggerVotifierEvent", true).put("votifierProviderPresent", true)
                .put("voteSitesComplete", true).put("rewardsComplete", true)
                .put("transportProbeState", "WAITING").put("carrierPlayers", 0)
                .put("processRewards", true);
        NetworkDoctor.Report report = evaluate(List.of(backend), evidence);
        assertEquals(NetworkDoctor.Status.INFO, status(report, "votifier.trigger-event.provider-missing"));
        assertFalse(report.checks().stream().anyMatch(c -> c.status() == NetworkDoctor.Status.FAIL
                && c.id().startsWith("votifier.")));
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(report, "votifier.external-destination"));
    }

    @Test
    void proxyForwardingToKnownBackendWithVotifierIsFail() {
        BackendServerIdentity relation = new BackendServerIdentity("backend-a", "backend-a", true, true, 1);
        NodeStatus proxy = node("proxy", "BUNGEE", true, List.of(relation));
        NodeStatus backend = node("backend-a", "BUKKIT", true, List.of());
        ObjectNode p = proxyEvidence().put("votifierProviderPresent", true).put("votifierListenerInitialized", true)
                .put("votifierForwardingKnown", true);
        p.putArray("forwardingDestinations").add("backend-a");
        ObjectNode b = base().put("proxyMode", true).put("votifierProviderPresent", true)
                .put("triggerVotifierEvent", true).put("configurationHealthy", true);
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, backend),
                Map.of("proxy", p, "backend-a", b), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "votifier.forwarding.duplicate-path"));
        assertTrue(report.checks().stream().filter(c -> c.id().equals("votifier.forwarding.duplicate-path"))
                .anyMatch(c -> c.explanation().contains("matches an enrolled VotingPlugin backend with a Votifier provider")));
        b.put("votifierProviderPresent", false);
        assertEquals(NetworkDoctor.Status.FAIL, status(NetworkDoctor.evaluate(List.of(proxy, backend),
                Map.of("proxy", p, "backend-a", b), Instant.EPOCH, false), "votifier.forwarding.duplicate-path"));
    }

    @Test
    void unknownForwardingEvidenceDoesNotBecomeDuplicatePathFail() {
        NodeStatus proxy = node("proxy", "VELOCITY", true, List.of());
        ObjectNode p = proxyEvidence().put("votifierProviderPresent", true).put("votifierListenerInitialized", true);
        NetworkDoctor.Report report = evaluate(List.of(proxy), p);
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(report, "votifier.forwarding.duplicate-path"));
    }

    @Test
    void triggerWithoutProviderFailsAndBackendTriggerFalseIsNotFalsePositive() {
        NodeStatus missing = node("missing", "BUKKIT", true, List.of());
        ObjectNode fail = base().put("proxyMode", false).put("triggerVotifierEvent", true)
                .put("votifierProviderPresent", false);
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(missing), fail), "votifier.trigger-event.provider-missing"));

        NodeStatus ordinary = node("ordinary", "BUKKIT", true, List.of());
        ObjectNode info = base().put("proxyMode", false).put("triggerVotifierEvent", false)
                .put("votifierProviderPresent", true);
        NetworkDoctor.Report report = evaluate(List.of(ordinary), info);
        assertFalse(report.checks().stream().anyMatch(c -> c.id().equals("votifier.trigger-event.provider-missing")
                && c.status() == NetworkDoctor.Status.FAIL));
    }

    @Test
    void healthyStandaloneAndProxyNetworksHaveNoFailChecks() {
        NodeStatus standalone = node("standalone", "BUKKIT", true, List.of());
        ObjectNode standaloneEvidence = base().put("proxyMode", false).put("configurationHealthy", true)
                .put("votifierProviderPresent", true).put("triggerVotifierEvent", false)
                .put("databaseInitialized", true).put("jdbcDriverAvailable", true).put("processRewards", true)
                .put("voteSitesComplete", true).put("rewardsComplete", true);
        standaloneEvidence.putArray("voteSites");
        assertFalse(evaluate(List.of(standalone), standaloneEvidence).checks().stream()
                .anyMatch(c -> c.status() == NetworkDoctor.Status.FAIL));

        BackendServerIdentity relation = new BackendServerIdentity("backend-a", "backend-a", true, true, 1);
        NodeStatus proxy = node("proxy", "BUNGEE", true, List.of(relation));
        NodeStatus backend = node("backend-a", "BUKKIT", true, List.of());
        ObjectNode p = proxyEvidence().put("votifierProviderPresent", true).put("votifierListenerInitialized", true)
                .put("votifierForwardingKnown", true).put("databaseInitialized", true).put("jdbcDriverAvailable", true)
                .put("proxyMode", true).put("topologyComplete", true);
        p.putArray("forwardingDestinations");
        ObjectNode b = base().put("proxyMode", true).put("configurationHealthy", true)
                .put("configuredMethod", "PLUGINMESSAGING").put("activeMethod", "PLUGINMESSAGING")
                .put("transportInitialized", true).put("pluginMessageChannel", "VotingPlugin")
                .put("encryption", false).put("votifierProviderPresent", true).put("triggerVotifierEvent", true)
                .put("databaseInitialized", true).put("jdbcDriverAvailable", true).put("processRewards", true)
                .put("voteSitesComplete", true).put("rewardsComplete", true);
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, backend), Map.of("proxy", p, "backend-a", b), Instant.EPOCH, false);
        assertFalse(report.checks().stream().anyMatch(c -> c.status() == NetworkDoctor.Status.FAIL));
    }

    @Test
    void duplicateCaseAndDashUnderscoreBackendIdentitiesFail() {
        BackendServerIdentity a = new BackendServerIdentity("a", "a", true, true, 0);
        BackendServerIdentity b = new BackendServerIdentity("b", "b", true, true, 0);
        NodeStatus proxy = node("proxy", "BUNGEE", true, List.of(a, b));
        ObjectNode p = proxyEvidence().put("topologyComplete", true);
        ObjectNode ea = base().put("proxyMode", true).put("serverName", "Server-A");
        ObjectNode eb = base().put("proxyMode", true).put("serverName", "server_a");
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, node("a", "BUKKIT", true, List.of()), node("b", "BUKKIT", true, List.of())),
                Map.of("proxy", p, "a", ea, "b", eb), Instant.EPOCH, false);
        assertTrue(report.checks().stream().anyMatch(c -> c.id().equals("backend.server-name.normalization-collision")
                || c.id().equals("backend.server-name.duplicate")));
    }

    @Test
    void boundedHundredNodeReportDoesNotGrowWithoutLimit() {
        List<NodeStatus> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) nodes.add(node("n" + i, "BUKKIT", false, List.of()));
        NetworkDoctor.Report report = NetworkDoctor.evaluate(nodes, Map.of(), Instant.EPOCH, false);
        assertTrue(report.checks().size() <= 500);
        assertTrue(report.truncated());
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(report, "control.evidence.truncated"));
    }

    @Test
    void healthyProxyWithTwoBackendsHasNoFailChecks() {
        BackendServerIdentity a = new BackendServerIdentity("a", "a", true, true, 1);
        BackendServerIdentity b = new BackendServerIdentity("b", "b", true, true, 1);
        NodeStatus proxy = node("proxy2", "BUNGEECORD", true, List.of(a, b));
        NodeStatus na = node("a", "BUKKIT", true, List.of());
        NodeStatus nb = node("b", "BUKKIT", true, List.of());
        ObjectNode p = proxyEvidence().put("votifierProviderPresent", true).put("votifierListenerInitialized", true)
                .put("votifierForwardingKnown", true).put("topologyComplete", true).put("databaseInitialized", true)
                .put("jdbcDriverAvailable", true);
        p.putArray("forwardingDestinations");
        ObjectNode ea = backendHealthy("a");
        ObjectNode eb = backendHealthy("b");
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, na, nb), Map.of("proxy2", p, "a", ea, "b", eb), Instant.EPOCH, false);
        assertFalse(report.checks().stream().anyMatch(c -> c.status() == NetworkDoctor.Status.FAIL));
    }

    @Test
    void reportedBackendWithProxyModeDisabledFails() {
        BackendServerIdentity relation = new BackendServerIdentity("backend", "backend", true, true, 0);
        NodeStatus proxy = node("proxy", "BUNGEECORD", true, List.of(relation));
        ObjectNode p = proxyEvidence().put("topologyComplete", true);
        ObjectNode b = base().put("proxyMode", false).put("serverName", "backend");
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, node("backend", "BUKKIT", true, List.of())), Map.of("proxy", p, "backend", b), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "backend.proxy-mode.disabled"));
    }

    @Test
    void noCarrierPluginMessagingIsUnknownInsteadOfFail() {
        NodeStatus proxy = node("proxy", "BUNGEECORD", true, List.of());
        ObjectNode p = proxyEvidence().put("carrierPlayers", 0).put("transportProbeState", "WAITING");
        NetworkDoctor.Report report = evaluate(List.of(proxy), p);
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(report, "transport.communication"));
    }

    @Test
    void requiredSharedAuthMismatchedFingerprintsFails() {
        BackendServerIdentity relation = new BackendServerIdentity("backend", "backend", true, true, 0);
        NodeStatus proxy = node("proxy", "BUNGEECORD", true, List.of(relation));
        ObjectNode p = proxyEvidence().put("configuredMethod", "REDIS").put("activeMethod", "REDIS")
                .put("sharedAuthentication", "REQUIRED").put("sharedKeyFingerprint", "a".repeat(64));
        ObjectNode b = base().put("proxyMode", true).put("configuredMethod", "REDIS").put("activeMethod", "REDIS")
                .put("sharedAuthentication", "REQUIRED").put("sharedKeyFingerprint", "b".repeat(64));
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, node("backend", "BUKKIT", true, List.of())), Map.of("proxy", p, "backend", b), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "transport.authentication.key-mismatch"));
    }

    @Test
    void missingJdbcDriverFails() {
        NodeStatus backend = node("db", "BUKKIT", true, List.of());
        ObjectNode e = base().put("proxyMode", false).put("jdbcDriverAvailable", false).put("databaseInitialized", true);
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(backend), e), "storage.jdbc-driver.missing"));
    }

    @Test
    void enabledVoteLogWithoutReadableLogFails() {
        NodeStatus backend = node("log", "BUKKIT", true, List.of());
        ObjectNode e = base().put("proxyMode", false).put("voteLoggingEnabled", true).put("voteLogReadable", false);
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(backend), e), "storage.votelog.unreadable"));
    }

    @Test
    void duplicateAndMissingVoteSiteServiceIdsAreReported() {
        NodeStatus backend = node("sites", "BUKKIT", true, List.of());
        ObjectNode e = base().put("proxyMode", false).put("voteSitesComplete", true);
        e.putArray("voteSites").addObject().put("name", "one").put("serviceSite", "Service")
                .put("enabled", true).put("delayValid", true).put("voteUrlState", "VALID");
        e.withArray("voteSites").addObject().put("name", "two").put("serviceSite", "Service")
                .put("enabled", true).put("delayValid", true).put("voteUrlState", "VALID");
        NetworkDoctor.Report report = evaluate(List.of(backend), e);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "votesite.service-site.duplicate"));
        e.withArray("voteSites").addObject().put("name", "three").put("enabled", true).put("serviceSite", "");
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(backend), e), "votesite.service-site.missing"));
    }

    @Test
    void autoCreateVoteSitesIsWarning() {
        NodeStatus backend = node("auto", "BUKKIT", true, List.of());
        ObjectNode e = base().put("proxyMode", false).put("voteSitesComplete", true).put("autoCreateVoteSites", true);
        e.putArray("voteSites").addObject().put("name", "site").put("serviceSite", "svc")
                .put("enabled", true).put("delayValid", true).put("voteUrlState", "VALID");
        assertEquals(NetworkDoctor.Status.WARNING, status(evaluate(List.of(backend), e), "votesite.auto-create.enabled"));
    }

    @Test
    void unknownWhitelistedServerFailsWithCompleteTopology() {
        NodeStatus proxy = node("whitelist", "BUNGEECORD", true, List.of());
        ObjectNode e = proxyEvidence().put("topologyComplete", true);
        e.putArray("blockedServers");
        e.putArray("whitelistedServers").add("missing");
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(proxy), e), "routing.unknown-server"));
    }

    @Test
    void duplicateMultiProxyPrimaryFails() {
        NodeStatus a = node("pa", "BUNGEECORD", true, List.of());
        NodeStatus b = node("pb", "BUNGEECORD", true, List.of());
        ObjectNode ea = proxyEvidence().put("multiProxySupport", true).put("multiProxyMethod", "REDIS").put("primaryServer", true).put("proxyServerName", "pa").put("topologyComplete", true);
        ea.putArray("proxyServers").add("pb");
        ObjectNode eb = proxyEvidence().put("multiProxySupport", true).put("multiProxyMethod", "REDIS").put("primaryServer", true).put("proxyServerName", "pb").put("topologyComplete", true);
        eb.putArray("proxyServers").add("pa");
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(a, b), Map.of("pa", ea, "pb", eb), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "multiproxy.primary.duplicate"));
    }

    @Test
    void backendIdentityAndPerServerChecksCatchPlaceholders() {
        NodeStatus backend = node("backend-a", "BUKKIT", true, List.of());
        ObjectNode evidence = base().put("proxyMode", true).put("configurationHealthy", true)
                .put("serverName", "PleaseSet").put("perServerRewards", true).put("processRewards", false)
                .put("voteSitesComplete", true).put("rewardsComplete", true);
        NetworkDoctor.Report report = evaluate(List.of(backend), evidence);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "backend.server-name.unset"));
        assertEquals(NetworkDoctor.Status.WARNING, status(report, "rewards.processing.disabled"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "rewards.per-server.identity"));
    }

    @Test
    void proxyBackendCatchesMethodChannelEncryptionAndRoutingErrors() {
        BackendServerIdentity relation = new BackendServerIdentity("backend-a", "backend-a", true, true, 1);
        NodeStatus proxy = node("proxy", "BUNGEE", true, List.of(relation));
        NodeStatus backend = node("backend-a", "BUKKIT", true, List.of());
        ObjectNode p = base().put("configuredMethod", "PLUGINMESSAGING").put("activeMethod", "PLUGINMESSAGING")
                .put("transportInitialized", true).put("bungeeManageTotals", true).put("topologyComplete", true)
                .put("pluginMessageChannel", "A").put("encryption", true).put("proxyMode", true);
        p.putArray("blockedServers").add("unknown");
        ObjectNode b = base().put("configuredMethod", "HTTP").put("activeMethod", "HTTP")
                .put("transportInitialized", true).put("pluginMessageChannel", "B").put("encryption", false)
                .put("proxyMode", true).put("serverName", "backend-a").put("configurationHealthy", true);
        NetworkDoctor.Report report = NetworkDoctor.evaluate(List.of(proxy, backend), Map.of("proxy", p, "backend-a", b), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "proxy.method.mismatch"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "transport.pluginmessaging.channel-mismatch"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "transport.encryption.mismatch"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "routing.unknown-server"));
    }

    @Test
    void routingOverlapAndNoEligibleBackendAreDistinctWarningsAndFailures() {
        BackendServerIdentity relation = new BackendServerIdentity("backend-a", "backend-a", true, true, 0);
        NodeStatus proxy = node("proxy", "VELOCITY", true, List.of(relation));
        ObjectNode p = base().put("configuredMethod", "HTTP").put("activeMethod", "HTTP")
                .put("transportInitialized", true).put("bungeeManageTotals", true).put("topologyComplete", true);
        p.putArray("blockedServers").add("backend-a");
        p.putArray("whitelistedServers").add("backend-a");
        NetworkDoctor.Report report = evaluate(List.of(proxy), p);
        assertEquals(NetworkDoctor.Status.WARNING, status(report, "routing.block-whitelist.overlap"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "routing.no-eligible-backends"));
    }

    @Test
    void voteSitesRewardsVotePartyAndMultiProxyFailuresAreSpecific() {
        NodeStatus proxy = node("proxy", "BUNGEE", true, List.of());
        ObjectNode p = base().put("configuredMethod", "HTTP").put("activeMethod", "HTTP")
                .put("transportInitialized", true).put("bungeeManageTotals", true).put("topologyComplete", true)
                .put("multiProxySupport", true).put("multiProxyMethod", "REDIS").put("proxyServerName", "proxy")
                .put("multiProxyOneGlobalReward", true).put("sendVotesToAllServers", true)
                .put("votePartyEnabled", true).put("votePartyVotesRequired", 0);
        p.putArray("proxyServers");
        NetworkDoctor.Report report = evaluate(List.of(proxy), p);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "multiproxy.identity.unset"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "multiproxy.global-reward.send-all"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "voteparty.votes-required.invalid"));
    }

    @Test
    void runtimeDriftAndTruncationAreVisible() {
        NodeStatus proxy = node("proxy", "BUNGEE", true, List.of());
        ObjectNode p = base().put("configuredMethod", "HTTP").put("activeMethod", "PLUGINMESSAGING")
                .put("transportInitialized", true).put("bungeeManageTotals", true);
        NetworkDoctor.Report report = evaluate(List.of(proxy), p, true);
        assertEquals(NetworkDoctor.Status.WARNING, status(report, "transport.runtime.drift"));
        assertTrue(report.truncated());
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(report, "control.evidence.truncated"));
    }

    @Test void mqttProxyClientIdentityAndBackendGlobalPrefixAreComparedWithinNetwork() {
        NodeStatus proxy = node("proxy", "VELOCITY", true, List.of(
                new BackendServerIdentity("a", "a", true, true, 1),
                new BackendServerIdentity("b", "b", true, true, 1)));
        ObjectNode p = proxyEvidence().put("configuredMethod", "MQTT").put("mqttClientId", "same");
        ObjectNode a = backendHealthy("a").put("configuredMethod", "MQTT").put("mqttClientId", "same")
                .put("globalDataEnabled", true).put("globalDataPrefix", "one");
        ObjectNode b = backendHealthy("b").put("configuredMethod", "MQTT").put("mqttClientId", "other")
                .put("globalDataEnabled", true).put("globalDataPrefix", "two");
        var report = NetworkDoctor.evaluate(List.of(proxy, node("a", "BUKKIT", true, List.of()), node("b", "BUKKIT", true, List.of())),
                Map.of("proxy", p, "a", a, "b", b), Instant.EPOCH, false);
        assertTrue(report.checks().stream().anyMatch(c -> c.id().equals("transport.mqtt.client-id.duplicate") && c.status() == NetworkDoctor.Status.FAIL));
        assertTrue(report.checks().stream().anyMatch(c -> c.id().equals("storage.consistency.globalDataPrefix") && c.status() == NetworkDoctor.Status.WARNING));
    }

    @Test void separateCacheDatabaseDoesNotBorrowMainPoolReadiness() {
        NodeStatus proxy = node("proxy", "VELOCITY", true, List.of());
        ObjectNode p = proxyEvidence().put("databaseInitialized", true).put("voteCacheMysql", true).put("voteCacheMainMysql", false);
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(evaluate(List.of(proxy), p), "storage.voteCacheMysql.unavailable"));
        p.put("voteCacheDatabaseInitialized", false);
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(proxy), p), "storage.voteCacheMysql.unavailable"));
        p.put("voteCacheMainMysql", true);
        assertEquals(NetworkDoctor.Status.PASS, status(evaluate(List.of(proxy), p), "storage.voteCacheMysql.unavailable"));
    }

    @Test void socketMultiProxyUsesSocketPeersAndDoesNotCompareRedisPolicies() {
        var primary = node("one", "VELOCITY", true, List.of()); var secondary = node("two", "VELOCITY", true, List.of());
        var a = proxyEvidence().put("multiProxySupport", true).put("multiProxyMethod", "SOCKET").put("primaryServer", true)
                .put("proxyServerName", "one").put("sharedAuthentication", "REQUIRED");
        var b = proxyEvidence().put("multiProxySupport", true).put("multiProxyMethod", "SOCKET").put("primaryServer", false)
                .put("proxyServerName", "two").put("sharedAuthentication", "COMPATIBILITY");
        a.putArray("proxyServers"); b.putArray("proxyServers");
        a.putArray("socketProxyServers").add("two"); b.putArray("socketProxyServers").add("one");
        var report = NetworkDoctor.evaluate(List.of(primary, secondary), Map.of("one", a, "two", b), Instant.EPOCH, false);
        assertTrue(report.checks().stream().filter(c -> c.id().equals("multiproxy.primary.duplicate")).allMatch(c -> c.status() == NetworkDoctor.Status.PASS));
        assertFalse(report.checks().stream().anyMatch(c -> c.id().equals("multiproxy.authentication.mismatch") || c.id().equals("multiproxy.key.mismatch")));
        b.remove("socketProxyServers");
        assertTrue(NetworkDoctor.evaluate(List.of(primary, secondary), Map.of("one", a, "two", b), Instant.EPOCH, false).checks().stream()
                .anyMatch(c -> c.id().equals("multiproxy.relationships") && c.status() == NetworkDoctor.Status.UNKNOWN));
    }
    @Test void inactiveRoutingListsCannotFailHealthyTopology() {
        var proxy = node("proxy", "VELOCITY", true, List.of());
        var p = proxyEvidence().put("broadcastServersApplicable", false).put("offlineForwardServersApplicable", false).put("votePartyEnabled", false);
        p.putArray("backendNames").add("backend-a"); p.putArray("blockedServers"); p.putArray("whitelistedServers");
        p.putArray("broadcastServers").add("unused"); p.putArray("offlineForwardServers").add("lobby"); p.putArray("votePartyServers").add("old");
        assertFalse(evaluate(List.of(proxy), p).checks().stream().anyMatch(c -> c.id().equals("routing.unknown-server")));
        p.put("broadcastServersApplicable", true);
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(proxy), p), "routing.unknown-server"));
    }
    @Test void offlineProxyTopologyCannotForceOnlineStandaloneIntoProxyMode() {
        var stale = node("proxy", "VELOCITY", false, List.of(new BackendServerIdentity("backend-a", "backend-a", true, true, 1)));
        var standalone = node("backend-a", "BUKKIT", true, List.of());
        var b = backendHealthy("backend-a").put("proxyMode", false);
        var report = NetworkDoctor.evaluate(List.of(stale, standalone), Map.of("backend-a", b), Instant.EPOCH, false);
        assertFalse(report.checks().stream().anyMatch(c -> c.id().equals("backend.proxy-mode.disabled")));
    }

    @Test void caseOnlyBackendNamesCollideInOneNetwork() {
        var proxy = node("proxy", "VELOCITY", true, List.of(new BackendServerIdentity("a", "a", true, true, 1), new BackendServerIdentity("b", "b", true, true, 1)));
        var a = node("a", "BUKKIT", true, List.of()); var b = node("b", "BUKKIT", true, List.of());
        var report = NetworkDoctor.evaluate(List.of(proxy, a, b), Map.of("proxy", proxyEvidence(), "a", backendHealthy("Lobby"), "b", backendHealthy("lobby")), Instant.EPOCH, false);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "backend.server-name.duplicate"));
    }
    @Test void completeRewardFailureEvidenceAndInvalidSiteDelayFailButIncompleteRewardEvidenceDoesNot() {
        var node = node("backend", "BUKKIT", true, List.of());
        var data = backendHealthy("backend").put("rewardsComplete", true);
        data.putArray("missingRewardFiles").add("daily.yml"); data.putArray("invalidRewardFiles").add("broken.yml");
        data.putArray("missingRewardDependencies").add("PlaceholderAPI");
        data.putArray("voteSites").addObject().put("name", "site").put("enabled", true).put("serviceSite", "svc").put("delayValid", false);
        var report = evaluate(List.of(node), data);
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "rewards.missingRewardFiles"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "rewards.invalidRewardFiles"));
        assertEquals(NetworkDoctor.Status.WARNING, status(report, "rewards.missingRewardDependencies"));
        assertEquals(NetworkDoctor.Status.FAIL, status(report, "votesite.delay.invalid"));
        data.put("rewardsComplete", false);
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(evaluate(List.of(node), data), "rewards.missingRewardFiles"));
    }

    @Test void votePartySendAllMakesItsExplicitListInactive() {
        var proxy = node("proxy", "VELOCITY", true, List.of());
        var data = proxyEvidence().put("votePartyEnabled", true).put("votePartyServersApplicable", false).put("votePartyVotesRequired", 100);
        data.putArray("backendNames").add("backend-a"); data.putArray("votePartyServers").add("retired");
        assertFalse(evaluate(List.of(proxy), data).checks().stream().anyMatch(c -> c.id().equals("routing.unknown-server")));
        data.put("votePartyServersApplicable", true);
        assertEquals(NetworkDoctor.Status.FAIL, status(evaluate(List.of(proxy), data), "routing.unknown-server"));
        data.remove("votePartyServersApplicable");
        assertEquals(NetworkDoctor.Status.UNKNOWN, status(evaluate(List.of(proxy), data), "routing.votePartyServers.unknown"));
    }

    private static NetworkDoctor.Report evaluate(List<NodeStatus> nodes, ObjectNode evidence) {
        return NetworkDoctor.evaluate(nodes, Map.of(nodes.get(0).nodeId(), evidence), Instant.EPOCH, false);
    }

    private static NetworkDoctor.Report evaluate(List<NodeStatus> nodes, ObjectNode evidence, boolean truncated) {
        return NetworkDoctor.evaluate(nodes, Map.of(nodes.get(0).nodeId(), evidence), Instant.EPOCH, truncated);
    }

    private static ObjectNode base() {
        return JSON.createObjectNode().put("schemaVersion", 1).put("role", "BACKEND");
    }

    private static ObjectNode proxyEvidence() {
        return base().put("proxyMode", true).put("configurationHealthy", true)
                .put("configuredMethod", "PLUGINMESSAGING").put("activeMethod", "PLUGINMESSAGING")
                .put("transportInitialized", true).put("bungeeManageTotals", true)
                .put("topologyComplete", true).put("pluginMessageChannel", "VotingPlugin")
                .put("encryption", false).put("carrierPlayers", 1)
                .put("transportProbeState", "SUCCESS");
    }

    private static ObjectNode backendHealthy(String name) {
        return base().put("proxyMode", true).put("serverName", name).put("configurationHealthy", true)
                .put("configuredMethod", "PLUGINMESSAGING").put("activeMethod", "PLUGINMESSAGING")
                .put("transportInitialized", true).put("pluginMessageChannel", "VotingPlugin")
                .put("encryption", false).put("votifierProviderPresent", true).put("triggerVotifierEvent", true)
                .put("databaseInitialized", true).put("jdbcDriverAvailable", true).put("processRewards", true)
                .put("voteSitesComplete", true).put("rewardsComplete", true);
    }

    private static NodeStatus node(String id, String platform, boolean online, List<BackendServerIdentity> backends) {
        return new NodeStatus(id, UUID.nameUUIDFromBytes(id.getBytes()), id, platform, "test", 1,
                Set.of(NetworkHealthEvidence.CAPABILITY), Set.of(NetworkHealthEvidence.CAPABILITY), backends,
                1, Instant.EPOCH, Instant.EPOCH, online);
    }

    private static NetworkDoctor.Status status(NetworkDoctor.Report report, String id) {
        return report.checks().stream().filter(check -> check.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("missing check " + id)).status();
    }
}
