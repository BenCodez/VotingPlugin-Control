package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryNodeRegistryTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
    private final InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofSeconds(90));
    private final UUID session = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void registrationNegotiatesCapabilitiesAndDefensivelyCopiesInput() {
        Set<String> capabilities = new HashSet<>(Set.of("discovery.read", "future.feature"));
        var result = registry.register(registration("proxy-a", session, capabilities, Set.of("discovery.read")));
        capabilities.clear();
        assertTrue(result.created());
        assertEquals(Set.of("discovery.read", "future.feature"), result.node().advertisedCapabilities());
        assertEquals(Set.of("discovery.read"), result.node().acceptedCapabilities());
        assertThrows(UnsupportedOperationException.class,
                () -> result.node().advertisedCapabilities().add("changed"));
    }

    @Test void heartbeatReplacesCapabilitiesAndRejectsUnavailableRequiredCapability() {
        registry.register(registration("proxy-a", session, Set.of("discovery.read"), Set.of()));
        var updated = registry.heartbeat("proxy-a", new Heartbeat(session, 1, Set.of("presence.snapshot"), Set.of()));
        assertEquals(Set.of("presence.snapshot"), updated.advertisedCapabilities());
        ValidationException error = assertThrows(ValidationException.class,
                () -> registry.heartbeat("proxy-a", new Heartbeat(session, 1, Set.of(), Set.of("future.required"))));
        assertEquals("INCOMPATIBLE_CAPABILITIES", error.code());
    }

    @Test void snapshotIsReplacementRejectsDuplicateIdsAndIgnoresReplayOrOutOfOrderDelivery() {
        registry.register(registration("proxy-a", session, Set.of("presence.snapshot"), Set.of()));
        var first = registry.replacePresence("proxy-a", snapshot(2, backend("lobby"), backend("survival")));
        assertTrue(first.applied());
        var replay = registry.replacePresence("proxy-a", snapshot(2, backend("other")));
        assertFalse(replay.applied());
        assertEquals(List.of("lobby", "survival"), replay.node().backends().stream()
                .map(BackendServerIdentity::backendId).toList());
        var stale = registry.replacePresence("proxy-a", snapshot(1, backend("other")));
        assertFalse(stale.applied());
        assertThrows(ValidationException.class,
                () -> registry.replacePresence("proxy-a", snapshot(3, backend("Lobby"), backend("lobby"))));
    }

    @Test void newSessionAtomicallyReplacesRegistrationAndClearsOldTopology() {
        registry.register(registration("proxy-a", session, Set.of("presence.snapshot"), Set.of()));
        registry.replacePresence("proxy-a", snapshot(1, backend("lobby")));
        UUID restarted = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var replacement = registry.register(registration("proxy-a", restarted, Set.of(), Set.of()));
        assertFalse(replacement.created());
        assertEquals(restarted, replacement.node().sessionId());
        assertTrue(replacement.node().backends().isEmpty());
        assertEquals(-1, replacement.node().snapshotSequence());
        assertEquals("SESSION_MISMATCH", assertThrows(ValidationException.class,
                () -> registry.heartbeat("proxy-a", new Heartbeat(session, 1, Set.of(), Set.of()))).code());
    }

    @Test void duplicateRegistrationIsAtomicUnderConcurrency() throws Exception {
        int count = 32;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        List<Boolean> created = java.util.Collections.synchronizedList(new ArrayList<>());
        var executor = Executors.newFixedThreadPool(count);
        try {
            for (int i = 0; i < count; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        created.add(registry.register(registration("proxy-a", session, Set.of(), Set.of())).created());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await();
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, created.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, registry.list(0, 100).size());
    }

    @Test void paginationIsSortedAndBoundedAndOfflineBoundaryIsExplicit() {
        registry.register(registration("proxy-b", UUID.randomUUID(), Set.of(), Set.of()));
        registry.register(registration("proxy-a", session, Set.of(), Set.of()));
        assertEquals("proxy-b", registry.list(1, 1).get(0).nodeId());
        assertThrows(ValidationException.class, () -> registry.list(0, 101));
        clock.advance(Duration.ofSeconds(89).plusMillis(999));
        assertTrue(registry.list(0, 1).get(0).online());
        clock.advance(Duration.ofMillis(1));
        assertFalse(registry.list(0, 1).get(0).online());
    }

    @Test void validatesNodeBackendProtocolCollectionAndStringBounds() {
        assertThrows(ValidationException.class,
                () -> registry.register(registration("bad/id", session, Set.of(), Set.of())));
        assertEquals("UNSUPPORTED_PROTOCOL", assertThrows(ValidationException.class,
                () -> registry.register(new NodeRegistration("proxy", session, "Proxy", "VELOCITY", "1", 2,
                        Set.of(), Set.of()))).code());
        registry.register(registration("proxy-a", session, Set.of(), Set.of()));
        assertThrows(ValidationException.class, () -> registry.replacePresence("proxy-a",
                new PresenceSnapshot(session, 1, 1, List.of(new BackendServerIdentity("bad/id", "x", true, true, 0)))));
    }

    private NodeRegistration registration(String nodeId, UUID registrationSession, Set<String> capabilities,
                                          Set<String> required) {
        return new NodeRegistration(nodeId, registrationSession, "Proxy A", "VELOCITY", "7.1.2", 1,
                capabilities, required);
    }

    private PresenceSnapshot snapshot(long sequence, BackendServerIdentity... backends) {
        return new PresenceSnapshot(session, 1, sequence, List.of(backends));
    }

    private static BackendServerIdentity backend(String id) {
        return new BackendServerIdentity(id, id, true, true, 1);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
