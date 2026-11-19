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

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
