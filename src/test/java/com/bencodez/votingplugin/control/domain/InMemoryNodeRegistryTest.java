package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryNodeRegistryTest {
    @Test void heartbeatUpdatesCapabilitiesAndOfflineStateTracksTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofSeconds(90));
        registry.register(new NodeRegistration("proxy-a", "Proxy A", "VELOCITY", "6.20", 1, Set.of("status.read")));
        clock.advance(Duration.ofSeconds(89));
        assertTrue(registry.list(0, 10).get(0).online());
        NodeStatus heartbeat = registry.heartbeat("proxy-a", new Heartbeat(1, Set.of("status.read", "servers.list")));
        assertEquals(Set.of("status.read", "servers.list"), heartbeat.capabilities());
        clock.advance(Duration.ofSeconds(90));
        assertFalse(registry.list(0, 10).get(0).online());
    }

    @Test void rejectsInvalidIdentifiersAndUnknownNodes() {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(Clock.systemUTC(), Duration.ofSeconds(90));
        ValidationException invalid = assertThrows(ValidationException.class, () -> registry.register(
                new NodeRegistration("bad/id", "Proxy", "BUNGEECORD", "1", 1, Set.of())));
        assertEquals("VALIDATION_ERROR", invalid.code());
        assertEquals("NODE_NOT_FOUND", assertThrows(ValidationException.class,
                () -> registry.heartbeat("missing", new Heartbeat(1, null))).code());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
