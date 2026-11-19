package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.InspectionQuery;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
import com.bencodez.votingplugin.control.protocol.ProxyRoutingConfiguration;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentOperationsTest {
    @TempDir Path directory;

    @Test void grantEnforcesActionsExpiryAndProcessLifetime() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        try (var operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of("backend-a"), List.of("discover"), 1, null));
            agents.discover(grant.token(), new AgentOperations.Discovery(null));
            assertThrows(SecurityException.class, () -> agents.read(grant.token(),
                    new AgentOperations.Read("backend-a", "example")));
            var restarted = new AgentOperations(registry, operations, clock);
            assertThrows(SecurityException.class, () -> restarted.discover(grant.token(), new AgentOperations.Discovery(null)));

            clock.now = grant.expiresAt();
            assertThrows(SecurityException.class, () -> agents.discover(grant.token(), new AgentOperations.Discovery(null)));
            assertEquals(List.of(), agents.list());
        }
    }

    @Test void namedSiteReadRejectsNonMappingsAndShowsExistingItemAmount() {
        assertThrows(IllegalArgumentException.class, () -> AgentSetup.readSite("example",
                ManagedConfiguration.file("VoteSites.yml", "VoteSites: old-value\n")));
        assertThrows(IllegalArgumentException.class, () -> AgentSetup.readSite("example",
                ManagedConfiguration.file("VoteSites.yml", "VoteSites:\n  example: [old-value]\n")));
        var prior = AgentSetup.readSite("example", ManagedConfiguration.file("VoteSites.yml", """
                VoteSites:
                  example:
                    Name: Example
                    DisplayItem:
                      Amount: 7
                  other:
                    Name: Another site
                """));
        assertEquals(Map.of("name", "example", "exists", "true", "displayName", "Example",
                "displayItem.amount", "7"), prior);
    }

    @Test void networkGrantIsBoundToOneProxySnapshotAndNeverExpands() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID proxySession = UUID.randomUUID();
        UUID otherSession = UUID.randomUUID();
        registry.register(new NodeRegistration("proxy-a", proxySession, "Proxy A", "VELOCITY", "fixture", 1,
                Set.of("presence.snapshot"), Set.of()));
        registry.register(new NodeRegistration("proxy-b", otherSession, "Proxy B", "BUNGEECORD", "fixture", 1,
                Set.of("presence.snapshot"), Set.of()));
        for (String id : List.of("backend-a", "backend-b")) {
            registry.register(new NodeRegistration(id, UUID.randomUUID(), id, "BUKKIT", "fixture", 1,
                    Set.of(), Set.of()));
        }
        var member = new BackendServerIdentity("backend-a", "A", false, true, 0);
        var outsider = new BackendServerIdentity("backend-b", "B", false, true, 0);
        registry.replacePresence("proxy-a", new PresenceSnapshot(proxySession, 1, 1, List.of(member)));
        registry.replacePresence("proxy-b", new PresenceSnapshot(otherSession, 1, 1, List.of(outsider)));
        try (var configuration = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, configuration, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of(), List.of("discover", "read"), 1, "proxy-a"));
            assertEquals("proxy-a", agents.list().get(0).proxyNodeId());
            assertEquals(2, agents.list().get(0).nodeCount());
            var first = agents.discover(grant.token(), new AgentOperations.Discovery(0)).toString();
            assertTrue(first.contains("backend-a"));
            assertFalse(first.contains("backend-b"));
            assertThrows(SecurityException.class, () -> agents.read(grant.token(),
                    new AgentOperations.Read("backend-b", "example")));
            assertThrows(ValidationException.class, () -> agents.read(grant.token(),
                    new AgentOperations.Read("backend-a", "example")));
            registry.replacePresence("proxy-a", new PresenceSnapshot(proxySession, 1, 2, List.of(outsider)));
            assertThrows(SecurityException.class, () -> agents.read(grant.token(),
                    new AgentOperations.Read("backend-a", "example")));
            assertThrows(SecurityException.class, () -> agents.read(grant.token(),
                    new AgentOperations.Read("backend-b", "example")));
            agents.revoke(grant.id());
            assertThrows(SecurityException.class, () -> agents.discover(grant.token(), new AgentOperations.Discovery(0)));
        }
    }

    @Test void networkDiscoveryIsPagedAndAProxyReconnectInvalidatesScope() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration("proxy", session, "Proxy", "VELOCITY", "fixture", 1,
                Set.of("presence.snapshot"), Set.of()));
        var members = java.util.stream.IntStream.range(0, 51).mapToObj(index -> {
            String id = "backend-" + index;
            registry.register(new NodeRegistration(id, UUID.randomUUID(), id, "BUKKIT", "fixture", 1,
                    Set.of(), Set.of()));
            return new BackendServerIdentity(id, id, false, true, 0);
        }).toList();
        registry.replacePresence("proxy", new PresenceSnapshot(session, 1, 1, members));
        try (var configuration = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, configuration, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of(), List.of("discover"), 1, "proxy"));
            Map<?, ?> first = (Map<?, ?>) agents.discover(grant.token(), new AgentOperations.Discovery(0));
            assertEquals(50, first.get("nextOffset"));
            assertEquals(50, ((List<?>) first.get("items")).size());
            Map<?, ?> second = (Map<?, ?>) agents.discover(grant.token(), new AgentOperations.Discovery(50));
            assertEquals(2, ((List<?>) second.get("items")).size());
            registry.register(new NodeRegistration("proxy", UUID.randomUUID(), "Proxy", "VELOCITY", "fixture", 1,
                    Set.of("presence.snapshot"), Set.of()));
            assertEquals(Map.of("items", List.of()), agents.discover(grant.token(), new AgentOperations.Discovery(0)));
        }
    }

    @Test void healthRequiresExplicitActionAndNeverUsesHeartbeatAsTopologyEvidence() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        Set<String> proxyCaps = Set.of("presence.snapshot", "data.inspect.v1", "data.network-health.v1");
        registry.register(new NodeRegistration("proxy", session, "Proxy", "VELOCITY", "fixture", 1,
                proxyCaps, Set.of()));
        registry.register(new NodeRegistration("member", UUID.randomUUID(), "Member", "BUKKIT", "fixture", 1,
                Set.of(), Set.of()));
        registry.replacePresence("proxy", new PresenceSnapshot(session, 1, 1, List.of(
                new BackendServerIdentity("member", "Member", false, true, 0))));
        try (var configuration = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, configuration, clock);
            var discover = agents.issue(new AgentOperations.Issue(List.of("proxy"), List.of("discover"), 30, null));
            assertThrows(SecurityException.class, () -> agents.topology(discover.token(), new AgentOperations.Discovery(null)));
            var health = agents.issue(new AgentOperations.Issue(List.of("proxy", "member"), List.of("health"), 30, null));
            assertEquals(List.of("member"), agents.topology(health.token(), new AgentOperations.Discovery(null))
                    .items().stream().filter(node -> node.nodeId().equals("proxy")).findFirst().orElseThrow().backendIds());
            var limited = agents.issue(new AgentOperations.Issue(List.of("proxy"), List.of("health"), 30, null));
            assertEquals(List.of(), agents.topology(limited.token(), new AgentOperations.Discovery(null))
                    .items().get(0).backendIds());
            assertThrows(SecurityException.class, () -> agents.health(limited.token(),
                    new AgentOperations.HealthRequest(List.of("proxy", "member"))));
            assertEquals("NODE_UNAVAILABLE", assertThrows(ValidationException.class, () -> agents.refreshHealth(
                    health.token(), new AgentOperations.RefreshHealth("member"))).code());
            assertEquals("RUNNING", agents.refreshHealth(health.token(), new AgentOperations.RefreshHealth("proxy")).get("state"));
            assertThrows(IllegalArgumentException.class,
                    () -> agents.refreshHealth(health.token(), new AgentOperations.RefreshHealth("proxy")));
            clock.now = clock.now.plus(Duration.ofMinutes(6));
            registry.heartbeat("proxy", new com.bencodez.votingplugin.control.protocol.Heartbeat(session, 1,
                    proxyCaps, Set.of()));
            var stale = agents.topology(health.token(), new AgentOperations.Discovery(null))
                    .items().stream().filter(node -> node.nodeId().equals("proxy")).findFirst().orElseThrow();
            assertTrue(stale.online());
            assertFalse(stale.topologyFresh());
            assertEquals(List.of(), stale.backendIds());
            var report = agents.health(health.token(), new AgentOperations.HealthRequest(List.of("proxy")));
            assertTrue(report.truncated());
            assertTrue(report.checks().stream().anyMatch(check -> check.status().equals("UNKNOWN")));
            agents.revoke(health.id());
            assertThrows(SecurityException.class, () -> agents.health(health.token(),
                    new AgentOperations.HealthRequest(List.of("proxy"))));
        }
    }

    @Test void agentRefreshCoalescesAndReservesSharedInspectionCapacity() {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        var ops = new InspectionOperations(registry, clock);
        for (int index = 0; index < 13; index++) {
            String id = "fixture-" + index;
            registry.register(new NodeRegistration(id, UUID.randomUUID(), id, "BUKKIT", "1", 1, Set.of(InspectionQuery.CAPABILITY, NetworkHealthEvidence.CAPABILITY), Set.of()));
        }
        for (int index = 0; index < 12; index++) ops.refreshAgentHealth("fixture-" + index);
        var first = ops.refreshAgentHealth("fixture-0");
        assertEquals(first.inspectionId(), ops.refreshAgentHealth("fixture-0").inspectionId());
        assertEquals("OPERATION_LIMIT", assertThrows(ValidationException.class,
                () -> ops.refreshAgentHealth("fixture-12")).code());
        assertEquals("RUNNING", ops.create("fixture-12", new InspectionQuery("network-health", Map.of())).state());
    }

    @Test void routingPreviewUsesExactReadRevisionAndOriginalState() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration("proxy", session, "Proxy", "VELOCITY", "fixture", 1,
                Set.of(ConfigurationOperations.CAPABILITY), Set.of()));
        try (var operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var readOnly = agents.issue(new AgentOperations.Issue(List.of("proxy"), List.of("read"), 10, null));
            assertThrows(SecurityException.class, () -> agents.readRouting(readOnly.token(),
                    new AgentOperations.RoutingRead("proxy")));
            UUID principal = UUID.randomUUID();
            var current = new ProxyRoutingConfiguration(false, List.of());
            var proposal = new ProxyRoutingConfiguration(true, List.of("backend-a"));
            var read = operations.createAgentProxyRouting(principal, "proxy", null, null);
            var readTask = operations.claim("proxy", session);
            String revision = "a".repeat(64);
            operations.complete(read.operationId(), "proxy", routingResult(session, readTask.attemptId(),
                    revision, current, List.of()));
            var invalid = operations.createAgentProxyRouting(principal, "proxy", proposal, read.operationId());
            var invalidTask = operations.claim("proxy", session);
            operations.complete(invalid.operationId(), "proxy", routingResult(session, invalidTask.attemptId(),
                    revision, proposal, List.of()));
            assertFalse(operations.get(invalid.operationId()).browserReviewReady());
            var preview = operations.createAgentProxyRouting(principal, "proxy", proposal, read.operationId());
            var previewTask = operations.claim("proxy", session);
            operations.complete(preview.operationId(), "proxy", routingResult(session, previewTask.attemptId(),
                    revision, current, List.of("sendVotesToAllServers: false -> true",
                            "blockedServers: [] -> [backend-a]")));
            assertTrue(operations.get(preview.operationId()).browserReviewReady());
            org.junit.jupiter.api.Assertions.assertNull(operations.get(preview.operationId()).approvalToken());
            assertThrows(ValidationException.class, () -> operations.createApply(preview.operationId(), "invalid"));
            var apply = operations.approveAgentPreview(preview.operationId());
            var applyTask = operations.claim("proxy", session);
            assertEquals(apply.operationId(), applyTask.operationId());
            assertEquals(revision, applyTask.expectedRevision());
            assertThrows(ValidationException.class, () -> operations.approveAgentPreview(preview.operationId()));
        }
    }

    private static ConfigurationTaskResult routingResult(UUID session, UUID attempt, String revision,
            ProxyRoutingConfiguration configuration, List<String> changes) {
        return new ConfigurationTaskResult(session, true, "OK", "fixture", revision,
                configuration, changes, false, false, attempt);
    }

    @Test void transportRequiresBothGrantedPeersAndRejectsReconnectedBackend() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID proxySession = UUID.randomUUID();
        UUID backendSession = UUID.randomUUID();
        registry.register(new NodeRegistration("proxy", proxySession, "Proxy", "VELOCITY", "fixture", 1,
                Set.of("presence.snapshot", ConfigurationOperations.TRANSPORT_TEST_CAPABILITY), Set.of()));
        registry.register(new NodeRegistration("backend", backendSession, "Backend", "BUKKIT", "fixture", 1,
                Set.of(), Set.of()));
        registry.replacePresence("proxy", new PresenceSnapshot(proxySession, 1, 1, List.of(
                new BackendServerIdentity("backend", "Backend", false, true, 0))));
        try (var operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var request = new AgentOperations.TransportRequest("proxy", "backend");
            var limited = agents.issue(new AgentOperations.Issue(List.of("proxy"), List.of("transport"), 10, null));
            assertThrows(SecurityException.class, () -> agents.checkTransport(limited.token(), request));
            var grant = agents.issue(new AgentOperations.Issue(List.of("proxy", "backend"),
                    List.of("transport", "status"), 10, null));
            Map<?, ?> queued = (Map<?, ?>) agents.checkTransport(grant.token(), request);
            UUID id = (UUID) queued.get("operationId");
            var claim = operations.claim("proxy", proxySession);
            assertEquals("READ", claim.type());
            assertEquals("communication-test", claim.configuration().preset());
            assertEquals("backend", claim.configuration().options().get("server"));
            registry.register(new NodeRegistration("backend", UUID.randomUUID(), "Backend", "BUKKIT", "fixture", 1,
                    Set.of(), Set.of()));
            operations.complete(id, "proxy", new ConfigurationTaskResult(proxySession, true, "OK", "fixture",
                    "a".repeat(64), claim.configuration(), List.of(), false, false, claim.attemptId()));
            var result = (Map<?, ?>) agents.status(grant.token(), id);
            assertEquals("COMPLETED_WITH_ERRORS", result.get("state"));
            assertTrue(result.get("results").toString().contains("TARGET_CHANGED"));
            assertFalse(result.containsKey("approvalToken"));
        }
    }

    @Test void sitePolicyRequiresSeparateGrantAndExactOneFieldPreview() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration("backend", session, "Backend", "BUKKIT", "fixture", 1,
                Set.of(ConfigurationOperations.QUICK_SETUP_CAPABILITY), Set.of()));
        try (var operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var legacy = agents.issue(new AgentOperations.Issue(List.of("backend"), List.of("read", "preview"), 10, null));
            var request = new AgentOperations.SitePolicyRead("backend");
            assertThrows(SecurityException.class, () -> agents.readSitePolicy(legacy.token(), request));
            var granted = agents.issue(new AgentOperations.Issue(List.of("backend"), List.of("site-policy", "status"), 10, null));
            var read = (Map<?, ?>) agents.readSitePolicy(granted.token(), request);
            UUID readId = (UUID) read.get("operationId");
            var readTask = operations.claim("backend", session);
            String revision = "a".repeat(64);
            operations.complete(readId, "backend", new ConfigurationTaskResult(session, true, "OK", "fixture", revision,
                    AgentSetup.sitePolicy(false), List.of(), false, false, readTask.attemptId()));
            assertEquals(Map.of("enabled", "false"), ((Map<?, ?>) agents.status(granted.token(), readId)).get("values"));
            var proposal = new AgentOperations.SitePolicyPreview("backend", readId.toString(), true);
            var invalid = (Map<?, ?>) agents.previewSitePolicy(granted.token(), proposal);
            var invalidTask = operations.claim("backend", session);
            UUID invalidId = (UUID) invalid.get("operationId");
            operations.complete(invalidId, "backend", new ConfigurationTaskResult(session, true, "OK", "fixture", revision,
                    AgentSetup.sitePolicy(true), List.of("changed AutoCreateVoteSites", "changed ProcessRewards"),
                    false, false, invalidTask.attemptId()));
            assertFalse(operations.get(invalidId).browserReviewReady());
            var preview = (Map<?, ?>) agents.previewSitePolicy(granted.token(), proposal);
            var previewTask = operations.claim("backend", session);
            UUID previewId = (UUID) preview.get("operationId");
            operations.complete(previewId, "backend", new ConfigurationTaskResult(session, true, "OK", "fixture", revision,
                    AgentSetup.sitePolicy(true), List.of("changed AutoCreateVoteSites"), false, false, previewTask.attemptId()));
            assertTrue(operations.get(previewId).browserReviewReady());
            assertThrows(ValidationException.class, () -> operations.createApply(previewId, "invalid"));
            agents.approve(previewId);
            assertEquals(revision, operations.claim("backend", session).expectedRevision());
            assertThrows(ValidationException.class, () -> agents.approve(previewId));
        }
    }

    @Test void batchPolicyRequiresEveryCurrentRevisionAndReportsPartialApply() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        Map<String, UUID> sessions = new java.util.LinkedHashMap<>();
        for (String node : List.of("backend-a", "backend-b")) {
            UUID session = UUID.randomUUID();
            sessions.put(node, session);
            registry.register(new NodeRegistration(node, session, node, "BUKKIT", "fixture", 1,
                    Set.of(ConfigurationOperations.QUICK_SETUP_CAPABILITY), Set.of()));
        }
        try (var operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.copyOf(sessions.keySet()),
                    List.of("site-policy", "site-policy-batch", "status"), 10, null));
            Map<String, String> revisions = Map.of("backend-a", "a".repeat(64), "backend-b", "b".repeat(64));
            List<AgentOperations.SitePolicyTarget> targets = new java.util.ArrayList<>();
            for (var item : sessions.entrySet()) {
                String node = item.getKey();
                UUID session = item.getValue();
                var queued = (Map<?, ?>) agents.readSitePolicy(grant.token(), new AgentOperations.SitePolicyRead(node));
                UUID readId = (UUID) queued.get("operationId");
                var claim = operations.claim(node, session);
                operations.complete(readId, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revisions.get(node), AgentSetup.sitePolicy(false), List.of(), false, false, claim.attemptId()));
                targets.add(new AgentOperations.SitePolicyTarget(node, readId.toString()));
            }
            var limited = agents.issue(new AgentOperations.Issue(List.copyOf(sessions.keySet()),
                    List.of("site-policy"), 10, null));
            var request = new AgentOperations.SitePolicyBatch(targets, true);
            assertThrows(SecurityException.class, () -> agents.previewSitePolicyBatch(limited.token(), request));

            UUID stale = (UUID) ((Map<?, ?>) agents.previewSitePolicyBatch(grant.token(), request)).get("operationId");
            for (var item : sessions.entrySet()) {
                String node = item.getKey();
                UUID session = item.getValue();
                var claim = operations.claim(node, session);
                String revision = node.equals("backend-b") ? "c".repeat(64) : revisions.get(node);
                operations.complete(stale, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revision, AgentSetup.sitePolicy(true), List.of("changed AutoCreateVoteSites"),
                        false, false, claim.attemptId()));
            }
            assertFalse(operations.get(stale).browserReviewReady());
            assertThrows(ValidationException.class, () -> agents.approve(stale));

            UUID preview = (UUID) ((Map<?, ?>) agents.previewSitePolicyBatch(grant.token(), request)).get("operationId");
            for (var item : sessions.entrySet()) {
                String node = item.getKey();
                UUID session = item.getValue();
                var claim = operations.claim(node, session);
                operations.complete(preview, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revisions.get(node), AgentSetup.sitePolicy(true), List.of("changed AutoCreateVoteSites"),
                        false, false, claim.attemptId()));
            }
            assertEquals(sessions.keySet(), operations.getAgentReview(preview).beforeByNode().keySet());
            var apply = agents.approve(preview);
            assertThrows(ValidationException.class, () -> agents.approve(preview));
            for (var item : sessions.entrySet()) {
                String node = item.getKey();
                UUID session = item.getValue();
                var claim = operations.claim(node, session);
                assertEquals(revisions.get(node), claim.expectedRevision());
                boolean success = node.equals("backend-a");
                operations.complete(apply.operationId(), node, new ConfigurationTaskResult(session, success,
                        success ? "OK" : "RELOAD_FAILED", "fixture", success ? revisions.get(node) : null,
                        success ? AgentSetup.sitePolicy(true) : null, List.of(), success, !success, claim.attemptId()));
            }
            var status = (Map<?, ?>) agents.status(grant.token(), apply.operationId());
            assertEquals("COMPLETED_WITH_ERRORS", status.get("state"));
            var results = (Map<?, ?>) status.get("results");
            assertEquals(true, ((Map<?, ?>) results.get("backend-a")).get("reloaded"));
            assertEquals(true, ((Map<?, ?>) results.get("backend-b")).get("rolledBack"));
        }
    }

    @Test void generalSettingsEditorRetainsOnlyTypedReviewAndFlagsProxyRestart() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        for (boolean proxy : List.of(false, true)) {
            UUID session = UUID.randomUUID();
            String node = proxy ? "proxy" : "backend";
            String file = proxy ? "bungeeconfig.yml" : "Config.yml";
            String capability = proxy ? ConfigurationOperations.PROXY_FILE_CAPABILITY : ConfigurationOperations.FILE_CAPABILITY;
            registry.register(new NodeRegistration(node, session, node, proxy ? "VELOCITY" : "BUKKIT", "fixture", 1,
                    Set.of(capability), Set.of()));
            Path auditDirectory = java.nio.file.Files.createDirectory(directory.resolve(node));
            try (var operations = new ConfigurationOperations(registry, new ConfigurationAuditLog(auditDirectory, clock), clock)) {
                var agents = new AgentOperations(registry, operations, clock);
                var grant = agents.issue(new AgentOperations.Issue(List.of(node), List.of("general-settings", "status"), 10, null));
                var other = agents.issue(new AgentOperations.Issue(List.of(node), List.of("general-settings"), 10, null));
                UUID readId = (UUID) ((Map<?, ?>) agents.readGeneralSettings(grant.token(),
                        new AgentOperations.GeneralSettingsRead(node))).get("operationId");
                String content = "OnlineMode: false\nUnmanaged: preserved\n";
                String revision = "a".repeat(64);
                var current = ManagedConfiguration.file(file, content);
                var readTask = operations.claim(node, session);
                assertEquals(file, readTask.configuration().fileName());
                operations.complete(readId, node, new ConfigurationTaskResult(session, true, "OK", "fixture", revision,
                        current, List.of(), false, false, readTask.attemptId()));
                var result = (Map<?, ?>) agents.status(grant.token(), readId);
                assertEquals(Map.of("OnlineMode", "false"), result.get("values"));
                assertFalse(result.toString().contains("Unmanaged"));
                var request = new AgentOperations.GeneralSettingsPreview(node, readId.toString(), Map.of("OnlineMode", true));
                assertThrows(ValidationException.class, () -> agents.previewGeneralSettings(other.token(), request));
                assertThrows(ValidationException.class, () -> agents.previewGeneralSettings(grant.token(),
                        new AgentOperations.GeneralSettingsPreview(node, readId.toString(), Map.of("Unmanaged", true))));
                assertThrows(ValidationException.class, () -> operations.createGeneralSettingsPreview(readId, node,
                        Map.of("OnlineMode", true)));
                UUID invalid = (UUID) ((Map<?, ?>) agents.previewGeneralSettings(grant.token(), request)).get("operationId");
                var invalidTask = operations.claim(node, session);
                operations.complete(invalid, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revision, current, List.of("changed OnlineMode", "changed Unmanaged"),
                        false, false, invalidTask.attemptId()));
                assertFalse(operations.get(invalid).browserReviewReady());
                UUID preview = (UUID) ((Map<?, ?>) agents.previewGeneralSettings(grant.token(), request)).get("operationId");
                var previewTask = operations.claim(node, session);
                assertEquals(content.replace("false", "true"), previewTask.configuration().content());
                operations.complete(preview, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revision, current, List.of("changed OnlineMode"), false, false, previewTask.attemptId()));
                assertTrue(operations.get(preview).browserReviewReady());
                assertEquals(Map.of("OnlineMode", "true"), operations.getAgentReview(preview).after());
                assertThrows(ValidationException.class, () -> operations.createApply(preview, "invalid"));
                var apply = agents.approve(preview);
                var applyTask = operations.claim(node, session);
                assertEquals(revision, applyTask.expectedRevision());
                operations.complete(apply.operationId(), node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        "b".repeat(64), applyTask.configuration(), List.of("changed OnlineMode"),
                        !proxy, false, applyTask.attemptId()));
                var terminal = (Map<?, ?>) agents.status(grant.token(), apply.operationId());
                var row = (Map<?, ?>) ((Map<?, ?>) terminal.get("results")).get(node);
                assertEquals(proxy, Boolean.TRUE.equals(row.get("restartRequired")));
                assertFalse(terminal.toString().contains("Unmanaged"));
            }
        }
    }

    @Test void pendingAgentFileReviewsLeaveCapacityForBrowserReadAndApply() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        String node = "backend";
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration(node, session, node, "BUKKIT", "fixture", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        try (var operations = new ConfigurationOperations(registry, new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of(node), List.of("general-settings"), 10, null));
            var read = new AgentOperations.GeneralSettingsRead(node);
            var current = ManagedConfiguration.file("Config.yml", "OnlineMode: false\n");
            UUID firstPreview = null;
            for (int slot = 0; slot < 6; slot++) {
                UUID readId = (UUID) ((Map<?, ?>) agents.readGeneralSettings(grant.token(), read)).get("operationId");
                var readTask = operations.claim(node, session);
                operations.complete(readId, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        "a".repeat(64), current, List.of(), false, false, readTask.attemptId()));
                var proposal = new AgentOperations.GeneralSettingsPreview(node, readId.toString(), Map.of("OnlineMode", true));
                UUID previewId = (UUID) ((Map<?, ?>) agents.previewGeneralSettings(grant.token(), proposal)).get("operationId");
                var previewTask = operations.claim(node, session);
                operations.complete(previewId, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        "a".repeat(64), current, List.of("changed OnlineMode"), false, false, previewTask.attemptId()));
                assertTrue(operations.get(previewId).browserReviewReady());
                if (slot == 0) firstPreview = previewId;
            }
            var denied = assertThrows(ValidationException.class, () -> agents.readGeneralSettings(grant.token(), read));
            assertEquals("OPERATION_LIMIT", denied.code());
            assertNotNull(operations.createRead(List.of(node), ManagedConfiguration.file("Config.yml", null)));
            assertNotNull(agents.approve(firstPreview));
        }
    }

    @Test void typedSiteEditorBindsReviewToExactMaskedSourceAndRejectsOpaqueDeletion() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        String node = "backend";
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration(node, session, node, "BUKKIT", "fixture", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        try (var operations = new ConfigurationOperations(registry, new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of(node), List.of("vote-sites-editor", "status"), 10, null));
            var unrelated = agents.issue(new AgentOperations.Issue(List.of(node), List.of("read", "preview"), 10, null));
            var read = new AgentOperations.VoteSitesEditorRead(node, "Example");
            assertThrows(SecurityException.class, () -> agents.readVoteSitesEditor(unrelated.token(), read));
            UUID readId = (UUID) ((Map<?, ?>) agents.readVoteSitesEditor(grant.token(), read)).get("operationId");
            var current = ManagedConfiguration.file("VoteSites.yml",
                    "VoteSites:\n  Example:\n    Enabled: true\n    Rewards:\n      Messages:\n        Player: Welcome\n");
            var task = operations.claim(node, session);
            String revision = "b".repeat(64);
            operations.complete(readId, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                    revision, current, List.of(), false, false, task.attemptId()));
            var readStatus = (Map<?, ?>) agents.status(grant.token(), readId);
            assertEquals(Map.of("siteKey", "Example", "exists", "true", "Enabled", "true", "rewardsConfigured", "true"),
                    readStatus.get("values"));
            assertFalse(readStatus.toString().contains("Welcome"));
            var remove = new AgentOperations.VoteSitesEditorPreview(node, "Example", readId.toString(), "REMOVE", null);
            assertThrows(ValidationException.class, () -> agents.previewVoteSitesEditor(grant.token(), remove));
            assertThrows(ValidationException.class,
                    () -> operations.createVoteSitesPreview(readId, node, "REMOVE", "Example", null));
            var wrongKey = new AgentOperations.VoteSitesEditorPreview(node, "Other", readId.toString(), "EDIT",
                    Map.of("Enabled", false));
            assertThrows(ValidationException.class, () -> agents.previewVoteSitesEditor(grant.token(), wrongKey));
            var edit = new AgentOperations.VoteSitesEditorPreview(node, "Example", readId.toString(), "EDIT",
                    Map.of("Enabled", false));
            UUID bad = (UUID) ((Map<?, ?>) agents.previewVoteSitesEditor(grant.token(), edit)).get("operationId");
            var mismatch = operations.claim(node, session);
            var differentSource = ManagedConfiguration.file("VoteSites.yml", current.content().replace("Welcome", "Changed"));
            operations.complete(bad, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                    revision, differentSource, List.of("changed VoteSites.Example.Enabled"), false, false, mismatch.attemptId()));
            assertFalse(operations.get(bad).browserReviewReady());

            UUID preview = (UUID) ((Map<?, ?>) agents.previewVoteSitesEditor(grant.token(), edit)).get("operationId");
            var previewTask = operations.claim(node, session);
            assertTrue(previewTask.configuration().content().contains("Player: Welcome"));
            operations.complete(preview, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                    revision, current, List.of("changed VoteSites.Example.Enabled"), false, false, previewTask.attemptId()));
            assertEquals(Map.of("VoteSites.Example.Enabled", "false"), operations.getAgentReview(preview).after());
            assertThrows(ValidationException.class, () -> operations.createApply(preview, "invalid"));
            assertNull(operations.get(preview).approvalToken());
            assertNotNull(agents.approve(preview));
            assertThrows(ValidationException.class, () -> agents.approve(preview));
            assertEquals(revision, operations.claim(node, session).expectedRevision());
        }
    }

    @Test void typedSiteEditorSupportsExplicitAdditionAndSimpleRemoval() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        String node = "backend";
        registry.register(new NodeRegistration(node, session, node, "BUKKIT", "fixture", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        try (var operations = new ConfigurationOperations(registry, new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of(node), List.of("vote-sites-editor", "status"), 10, null));
            var fields = Map.<String, Object>of("Enabled", true, "Hidden", false, "Name", "Example",
                    "ServiceSite", "example.test", "VoteURL", "https://example.test/vote", "VoteDelay", "24h",
                    "Priority", 5, "DisplayItem.Material", "DIAMOND", "DisplayItem.Amount", 1);
            for (String action : List.of("ADD", "REMOVE")) {
                boolean adding = "ADD".equals(action);
                String yaml = adding ? "VoteSites:\n" : "VoteSites:\n  Example:\n    Enabled: true\n";
                var current = ManagedConfiguration.file("VoteSites.yml", yaml);
                UUID id = (UUID) ((Map<?, ?>) agents.readVoteSitesEditor(grant.token(),
                        new AgentOperations.VoteSitesEditorRead(node, "Example"))).get("operationId");
                var read = operations.claim(node, session);
                String revision = "a".repeat(64);
                operations.complete(id, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revision, current, List.of(), false, false, read.attemptId()));
                UUID preview = (UUID) ((Map<?, ?>) agents.previewVoteSitesEditor(grant.token(),
                        new AgentOperations.VoteSitesEditorPreview(node, "Example", id.toString(), action,
                                adding ? fields : null))).get("operationId");
                var task = operations.claim(node, session);
                var changed = (adding ? fields.keySet() : Set.of("Enabled")).stream()
                        .map(key -> "changed VoteSites.Example." + key).toList();
                operations.complete(preview, node, new ConfigurationTaskResult(session, true, "OK", "fixture",
                        revision, current, changed, false, false, task.attemptId()));
                assertTrue(operations.get(preview).browserReviewReady());
                assertEquals(Boolean.toString(adding), operations.getAgentReview(preview).after().get("VoteSites.Example exists"));
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
