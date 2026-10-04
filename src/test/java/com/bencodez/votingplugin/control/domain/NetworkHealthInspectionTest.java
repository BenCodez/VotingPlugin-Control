package com.bencodez.votingplugin.control.domain;
import com.bencodez.votingplugin.control.protocol.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NetworkHealthInspectionTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
    private final UUID session = UUID.randomUUID();
    private final InspectionOperations ops = new InspectionOperations(registry, clock);
    private void register(UUID id, Set<String> caps) {
        registry.register(new NodeRegistration("backend-a", id, "a", "BUKKIT", "1", 1, caps, Set.of()));
    }
    private ObjectNode envelope() {
        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put("schemaVersion", 1).put("kind", "network-health").put("generatedAt", clock.instant().toString());
        data.putObject("result").put("schemaVersion", 1).put("role", "BACKEND").put("proxyMode", false);
        return data;
    }
    private Set<String> caps() { return Set.of(InspectionQuery.CAPABILITY, NetworkHealthEvidence.CAPABILITY); }
    @Test void oldPeerCannotReceiveNewKindAndFiltersAreRejected() {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        assertThrows(ValidationException.class, () -> ops.create("backend-a", new InspectionQuery("network-health", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> new InspectionQuery("network-health", Map.of("path", "secret")));
    }
    @Test void validatesEvidenceAndReconnectionInvalidatesOldCompletion() {
        register(session, caps());
        var created = ops.create("backend-a", new InspectionQuery("network-health", Map.of()));
        var task = ops.claim("backend-a", session);
        var data = envelope(); data.withObject("result").put("password", "must-not-leave-peer");
        assertThrows(ValidationException.class, () -> ops.complete(created.inspectionId(), "backend-a", new InspectionTaskResult(session, true, "OK", "ok", data, task.attemptId())));
        ops.complete(created.inspectionId(), "backend-a", new InspectionTaskResult(session, true, "OK", "ok", envelope(), task.attemptId()));
        assertEquals(1, ops.networkEvidence(registry.list(0, 100)).size());
        register(UUID.randomUUID(), caps());
        assertTrue(ops.networkEvidence(registry.list(0, 100)).isEmpty());
    }
    @Test void capabilityLossCancelsClaimAndDoesNotAcceptLateResult() {
        register(session, caps());
        var created = ops.create("backend-a", new InspectionQuery("network-health", Map.of()));
        register(session, Set.of(InspectionQuery.CAPABILITY));
        assertNull(ops.claim("backend-a", session));
        assertEquals("FAILED", ops.get(created.inspectionId()).state());
    }
    @Test void latestFailedObservationInvalidatesEarlierSuccess() {
        register(session, caps());
        var created = ops.create("backend-a", new InspectionQuery("network-health", Map.of()));
        var task = ops.claim("backend-a", session);
        ops.complete(created.inspectionId(), "backend-a", new InspectionTaskResult(session, true, "OK", "ok", envelope(), task.attemptId()));
        var next = ops.create("backend-a", new InspectionQuery("network-health", Map.of()));
        var nextTask = ops.claim("backend-a", session);
        ops.complete(next.inspectionId(), "backend-a", new InspectionTaskResult(session, false, "UNAVAILABLE", "unavailable", null, nextTask.attemptId()));
        assertTrue(ops.networkEvidence(registry.list(0, 100)).isEmpty());
    }
    @Test void pendingOrRejectedNewObservationInvalidatesEarlierSuccess() {
        register(session, caps());
        var first = ops.create("backend-a", new InspectionQuery("network-health", Map.of()));
        var claim = ops.claim("backend-a", session);
        ops.complete(first.inspectionId(), "backend-a", new InspectionTaskResult(session, true, "OK", "ok", envelope(), claim.attemptId()));
        assertEquals(1, ops.networkEvidence(registry.list(0, 100)).size());
        var next = ops.create("backend-a", new InspectionQuery("network-health", Map.of()));
        assertTrue(ops.networkEvidence(registry.list(0, 100)).isEmpty());
        var nextClaim = ops.claim("backend-a", session);
        var wrongRole = envelope(); wrongRole.withObject("result").put("role", "PROXY");
        assertThrows(ValidationException.class, () -> ops.complete(next.inspectionId(), "backend-a", new InspectionTaskResult(session, true, "OK", "ok", wrongRole, nextClaim.attemptId())));
        assertTrue(ops.networkEvidence(registry.list(0, 100)).isEmpty());
    }

    @Test void peerTimestampCannotExtendServerMeasuredFreshness() {
        var time = new java.util.concurrent.atomic.AtomicReference<>(clock.instant());
        Clock mutable = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return time.get(); }
        };
        var localRegistry = new InMemoryNodeRegistry(mutable, Duration.ofHours(1));
        localRegistry.register(new NodeRegistration("backend-a", session, "a", "BUKKIT", "1", 1, caps(), Set.of()));
        var localOps = new InspectionOperations(localRegistry, mutable);
        var first = localOps.create("backend-a", new InspectionQuery("network-health", Map.of()));
        var claim = localOps.claim("backend-a", session);
        var data = envelope(); data.put("generatedAt", "2099-01-01T00:00:00Z");
        localOps.complete(first.inspectionId(), "backend-a", new InspectionTaskResult(session, true, "OK", "ok", data, claim.attemptId()));
        assertEquals(1, localOps.networkEvidence(localRegistry.list(0, 100)).size());
        time.set(time.get().plusSeconds(301));
        assertTrue(localOps.networkEvidence(localRegistry.list(0, 100)).isEmpty());
    }

}
