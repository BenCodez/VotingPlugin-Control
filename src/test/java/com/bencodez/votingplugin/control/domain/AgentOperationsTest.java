package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentOperationsTest {
    @TempDir Path directory;

    @Test void grantEnforcesActionsExpiryAndProcessLifetime() throws Exception {
        MutableClock clock = new MutableClock();
        var registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        try (var operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock)) {
            var agents = new AgentOperations(registry, operations, clock);
            var grant = agents.issue(new AgentOperations.Issue(List.of("backend-a"), List.of("discover"), 1));
            agents.discover(grant.token());
            assertThrows(SecurityException.class, () -> agents.read(grant.token(),
                    new AgentOperations.Read("backend-a", "example")));
            var restarted = new AgentOperations(registry, operations, clock);
            assertThrows(SecurityException.class, () -> restarted.discover(grant.token()));

            clock.now = grant.expiresAt();
            assertThrows(SecurityException.class, () -> agents.discover(grant.token()));
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

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
