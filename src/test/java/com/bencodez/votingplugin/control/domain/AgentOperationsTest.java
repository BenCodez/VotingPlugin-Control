package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
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

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
