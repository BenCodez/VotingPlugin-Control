package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.InspectionQuery;
import com.bencodez.votingplugin.control.protocol.InspectionTask;
import com.bencodez.votingplugin.control.protocol.InspectionTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InspectionOperationsTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-30T00:00:00Z"));
    private final InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
    private final UUID session = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void typedInspectionCompletesAndReturnsItsBoundedData() {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        InspectionOperations operations = new InspectionOperations(registry, clock);
        InspectionOperations.InspectionView created = operations.create("backend-a",
                new InspectionQuery("player", Map.of("player", "Example")));

        InspectionTask task = operations.claim("backend-a", session);
        assertNotNull(task);
        assertEquals(created.inspectionId(), task.inspectionId());
        assertEquals("player", task.query().kind());
        ObjectNode data = envelope("player");
        ((ObjectNode) data.get("result")).put("name", "Example");
        InspectionOperations.InspectionView completed = operations.complete(created.inspectionId(), "backend-a",
                new InspectionTaskResult(session, true, null, "Current data", data, task.attemptId()));

        assertEquals("SUCCEEDED", completed.state());
        assertEquals(data, completed.result().data());
        assertEquals("SUCCEEDED", operations.get(created.inspectionId()).state());
    }

    @Test void rejectsUnavailableNodesAndAReplacementSession() {
        register(session, Set.of());
        InspectionOperations operations = new InspectionOperations(registry, clock);
        ValidationException unavailable = assertThrows(ValidationException.class,
                () -> operations.create("backend-a", new InspectionQuery("overview", Map.of())));
        assertEquals("NODE_UNAVAILABLE", unavailable.code());

        register(session, Set.of(InspectionQuery.CAPABILITY));
        InspectionOperations.InspectionView created = operations.create("backend-a",
                new InspectionQuery("overview", Map.of()));
        InspectionTask task = operations.claim("backend-a", session);
        UUID replacement = UUID.fromString("00000000-0000-0000-0000-000000000002");
        register(replacement, Set.of(InspectionQuery.CAPABILITY));

        ValidationException mismatch = assertThrows(ValidationException.class,
                () -> operations.complete(created.inspectionId(), "backend-a",
                        new InspectionTaskResult(session, true, null, "done", envelope("overview"), task.attemptId())));
        assertEquals("SESSION_MISMATCH", mismatch.code());
    }

    @Test void capabilityLossCompletesQueuedInspectionWithoutLeasingIt() {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        InspectionOperations operations = new InspectionOperations(registry, clock);
        UUID inspection = operations.create("backend-a", new InspectionQuery("overview", Map.of())).inspectionId();
        registry.heartbeat("backend-a", new Heartbeat(session, 1, Set.of("discovery.read"), Set.of()));

        assertNull(operations.claim("backend-a", session));
        InspectionOperations.InspectionView result = operations.get(inspection);
        assertEquals("FAILED", result.state());
        assertEquals("CAPABILITY_LOST", result.result().code());
    }

    @Test void completionRequiresTheCurrentAttemptAndAnActiveLease() {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        InspectionOperations operations = new InspectionOperations(registry, clock);
        UUID inspection = operations.create("backend-a", new InspectionQuery("overview", Map.of())).inspectionId();
        InspectionTask first = operations.claim("backend-a", session);

        ValidationException wrongAttempt = assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a",
                        new InspectionTaskResult(session, true, null, "done", envelope("overview"), UUID.randomUUID())));
        assertEquals("TASK_NOT_CLAIMED", wrongAttempt.code());

        clock.advance(Duration.ofMinutes(2));
        InspectionTask second = operations.claim("backend-a", session);
        assertNotNull(second);
        assertNotEquals(first.attemptId(), second.attemptId());
        ValidationException staleAttempt = assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a",
                        new InspectionTaskResult(session, true, null, "done", envelope("overview"), first.attemptId())));
        assertEquals("TASK_NOT_CLAIMED", staleAttempt.code());

        clock.advance(Duration.ofMinutes(2));
        ValidationException expired = assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a",
                        new InspectionTaskResult(session, true, null, "done", envelope("overview"), second.attemptId())));
        assertEquals("TASK_LEASE_EXPIRED", expired.code());
    }

    @Test void failedClaimAuditRollsBackTheInspectionLease() throws Exception {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        Path auditDirectory = directory.resolve("audit");
        try (ConfigurationAuditLog audit = new ConfigurationAuditLog(auditDirectory, clock)) {
            InspectionOperations operations = new InspectionOperations(registry, audit, clock);
            operations.create("backend-a", new InspectionQuery("overview", Map.of()));
            Path auditFile = auditDirectory.resolve("configuration-audit.jsonl");
            byte[] validAudit = Files.readAllBytes(auditFile);
            Files.writeString(auditFile, "tampered", StandardOpenOption.APPEND);

            assertThrows(ConfigurationAuditLog.AuditException.class,
                    () -> operations.claim("backend-a", session));

            Files.write(auditFile, validAudit, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            assertNotNull(operations.claim("backend-a", session));
        }
    }

    @Test void failedCompletionAuditRollsBackTheInspectionResult() throws Exception {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        Path auditDirectory = directory.resolve("completion-audit");
        try (ConfigurationAuditLog audit = new ConfigurationAuditLog(auditDirectory, clock)) {
            InspectionOperations operations = new InspectionOperations(registry, audit, clock);
            UUID inspection = operations.create("backend-a", new InspectionQuery("overview", Map.of())).inspectionId();
            InspectionTask task = operations.claim("backend-a", session);
            InspectionTaskResult result = new InspectionTaskResult(session, true, "OK", "done",
                    envelope("overview"), task.attemptId());
            Path auditFile = auditDirectory.resolve("configuration-audit.jsonl");
            byte[] validAudit = Files.readAllBytes(auditFile);
            Files.writeString(auditFile, "tampered", StandardOpenOption.APPEND);

            assertThrows(ConfigurationAuditLog.AuditException.class,
                    () -> operations.complete(inspection, "backend-a", result));

            Files.write(auditFile, validAudit, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            assertEquals("SUCCEEDED", operations.complete(inspection, "backend-a", result).state());
            assertTrue(Files.readString(auditFile).contains("INSPECTION_COMPLETED"));
        }
    }

    @Test void failedCapabilityLossAuditRollsBackTheInspectionCancellation() throws Exception {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        Path auditDirectory = directory.resolve("capability-loss-audit");
        try (ConfigurationAuditLog audit = new ConfigurationAuditLog(auditDirectory, clock)) {
            InspectionOperations operations = new InspectionOperations(registry, audit, clock);
            UUID inspection = operations.create("backend-a", new InspectionQuery("overview", Map.of())).inspectionId();
            registry.heartbeat("backend-a", new Heartbeat(session, 1, Set.of("discovery.read"), Set.of()));
            Path auditFile = auditDirectory.resolve("configuration-audit.jsonl");
            byte[] validAudit = Files.readAllBytes(auditFile);
            Files.writeString(auditFile, "tampered", StandardOpenOption.APPEND);

            assertThrows(ConfigurationAuditLog.AuditException.class,
                    () -> operations.claim("backend-a", session));
            assertEquals("RUNNING", operations.get(inspection).state());
            assertNull(operations.get(inspection).result());

            Files.write(auditFile, validAudit, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            assertNull(operations.claim("backend-a", session));
            assertEquals("FAILED", operations.get(inspection).state());
            assertEquals("CAPABILITY_LOST", operations.get(inspection).result().code());
            assertTrue(Files.readString(auditFile).contains("INSPECTION_CANCELLED"));
        }
    }

    @Test void resultShapeAndUtf8ByteLimitsAreEnforced() {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        InspectionOperations operations = new InspectionOperations(registry, clock);
        UUID inspection = operations.create("backend-a", new InspectionQuery("diagnostics", Map.of())).inspectionId();
        InspectionTask task = operations.claim("backend-a", session);

        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        "NOT_OK", "done", envelope("diagnostics"), task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, false,
                        "bad-code", "failed", null, task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        null, "done", envelope("diagnostics").put("payload",
                                "x".repeat(InspectionOperations.MAX_DATA_BYTES + 1)), task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        null, "done", envelope("overview"), task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        null, "done", envelope("diagnostics").remove("generatedAt"), task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        null, "done", envelope("diagnostics").put("generatedAt", "not-a-time"),
                        task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        null, "done", envelope("diagnostics").remove("result"), task.attemptId()))).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> operations.complete(inspection, "backend-a", new InspectionTaskResult(session, true,
                        null, "done", envelope("diagnostics").put("schemaVersion", "1"),
                        task.attemptId()))).code());
    }

    @Test void abandonedAndCompletedInspectionsHaveSeparateRetentionWindows() {
        register(session, Set.of(InspectionQuery.CAPABILITY));
        InspectionOperations operations = new InspectionOperations(registry, clock);
        UUID abandoned = operations.create("backend-a", new InspectionQuery("overview", Map.of())).inspectionId();
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertEquals("OPERATION_NOT_FOUND", assertThrows(ValidationException.class,
                () -> operations.get(abandoned)).code());

        UUID completed = operations.create("backend-a", new InspectionQuery("overview", Map.of())).inspectionId();
        InspectionTask task = operations.claim("backend-a", session);
        operations.complete(completed, "backend-a",
                new InspectionTaskResult(session, true, "OK", "done", envelope("overview"), task.attemptId()));
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertEquals("OPERATION_NOT_FOUND", assertThrows(ValidationException.class,
                () -> operations.get(completed)).code());
    }

    private void register(UUID sessionId, Set<String> capabilities) {
        registry.register(new NodeRegistration("backend-a", sessionId, "Backend A", "BUKKIT", "test", 1,
                capabilities, Set.of()));
    }

    private static ObjectNode envelope(String kind) {
        ObjectNode envelope = JSON.createObjectNode().put("schemaVersion", 1).put("kind", kind)
                .put("generatedAt", "2026-08-30T00:00:00Z");
        envelope.putObject("result");
        return envelope;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
