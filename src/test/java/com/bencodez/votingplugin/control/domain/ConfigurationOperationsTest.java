package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.ProxyRoutingConfiguration;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationOperationsTest {
    @TempDir Path directory;

    @Test void previewApprovalCarriesEachNodesRevisionIntoApplyAndIsSingleUse() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        registry.register(new NodeRegistration("proxy-a", UUID.randomUUID(), "Proxy A", "VELOCITY", "test", 1,
                Set.of("presence.snapshot", ConfigurationOperations.CAPABILITY), Set.of("presence.snapshot")));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ProxyRoutingConfiguration proposal = new ProxyRoutingConfiguration(true, List.of("lobby"));

        ConfigurationOperations.OperationView preview = operations.createPreview(List.of("proxy-a"), proposal);
        ConfigurationTask previewTask = operations.claim("proxy-a", registry.find("proxy-a").sessionId());
        assertEquals("PREVIEW", previewTask.type());
        preview = operations.complete(preview.operationId(), "proxy-a",
                new ConfigurationTaskResult(registry.find("proxy-a").sessionId(), true, "OK", "valid", "a".repeat(64), proposal,
                        List.of("blockedServers changed"), false, false, previewTask.attemptId()));
        assertEquals("SUCCEEDED", preview.state());
        assertNotNull(preview.approvalToken());

        UUID previewId = preview.operationId();
        String approvalToken = preview.approvalToken();
        ConfigurationOperations.OperationView apply = operations.createApply(previewId, approvalToken);
        ConfigurationTask applyTask = operations.claim("proxy-a", registry.find("proxy-a").sessionId());
        assertEquals("APPLY", applyTask.type());
        assertEquals("a".repeat(64), applyTask.expectedRevision());
        assertThrows(ValidationException.class,
                () -> operations.createApply(previewId, approvalToken));
        assertEquals(apply.operationId(), applyTask.operationId());
    }

    @Test void auditChainSurvivesRestartAndTamperingFailsClosed() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        ConfigurationAuditLog first = new ConfigurationAuditLog(directory, clock);
        first.append("TEST", UUID.randomUUID(), "proxy-a", "OK");
        first.close();
        ConfigurationAuditLog reopened = new ConfigurationAuditLog(directory, clock);
        reopened.append("TEST", UUID.randomUUID(), null, "OK");
        reopened.close();
        Path file = directory.resolve("configuration-audit.jsonl");
        Files.writeString(file, Files.readString(file).replaceFirst("TEST", "EDIT"));
        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
    }

    @Test void abandonedOperationsExpireAndCannotConsumeTheBoundForever() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        registry.register(new NodeRegistration("proxy-a", UUID.randomUUID(), "Proxy A", "VELOCITY", "test", 1,
                Set.of("presence.snapshot", ConfigurationOperations.CAPABILITY), Set.of("presence.snapshot")));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID abandoned = operations.createRead(List.of("proxy-a")).operationId();

        clock.advance(Duration.ofMinutes(16));

        assertEquals("OPERATION_NOT_FOUND",
                assertThrows(ValidationException.class, () -> operations.get(abandoned)).code());
    }

    @Test void replacementSessionExcludesOldClaimsAndCompletions() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        registry.register(registration(first));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ConfigurationOperations.OperationView operation = operations.createRead(List.of("proxy-a"));

        registry.register(registration(second));
        assertEquals("SESSION_MISMATCH",
                assertThrows(ValidationException.class, () -> operations.claim("proxy-a", first)).code());
        ConfigurationTask task = operations.claim("proxy-a", second);
        registry.register(registration(third));
        ProxyRoutingConfiguration configuration = new ProxyRoutingConfiguration(false, List.of());
        ConfigurationTaskResult stale = new ConfigurationTaskResult(second, true, "OK", "read", "b".repeat(64),
                configuration, List.of(), false, false, task.attemptId());
        assertEquals("SESSION_MISMATCH", assertThrows(ValidationException.class,
                () -> operations.complete(operation.operationId(), "proxy-a", stale)).code());
    }

    @Test void retainedAuditSegmentIsVerifiedBeforeStartup() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path retained = directory.resolve("configuration-audit.jsonl.1");
        ConfigurationAuditLog first = new ConfigurationAuditLog(directory, clock, 1);
        first.append("TEST", UUID.randomUUID(), "proxy-a", "OK");
        first.append("TEST", UUID.randomUUID(), "proxy-b", "OK");
        first.close();
        Files.writeString(retained, Files.readString(retained).replaceFirst("TEST", "EDIT"));

        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
    }

    @Test void auditCheckpointDetectsRecordBoundaryTruncation() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path active = directory.resolve("configuration-audit.jsonl");
        ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock);
        log.append("FIRST", UUID.randomUUID(), "proxy-a", "OK");
        log.append("SECOND", UUID.randomUUID(), "proxy-a", "OK");
        log.close();
        List<String> records = Files.readAllLines(active);
        Files.writeString(active, records.get(0) + System.lineSeparator());

        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
    }

    @Test void auditLogRejectsCommittedSegmentWithoutTerminalSeparator() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path active = directory.resolve("configuration-audit.jsonl");
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("FIRST", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] committed = Files.readAllBytes(active);
        assertEquals((byte) '\n', committed[committed.length - 1]);
        Files.write(active, java.util.Arrays.copyOf(committed, committed.length - 1));

        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
    }

    @Test void auditPendingTransactionRecoversTornRecordBeforeCheckpointCrash() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path active = directory.resolve("configuration-audit.jsonl");
        Path checkpoint = directory.resolve("configuration-audit.checkpoint");
        ObjectMapper json = new ObjectMapper();
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("FIRST", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] before = Files.readAllBytes(checkpoint);
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("SECOND", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] after = Files.readAllBytes(checkpoint);
        List<String> lines = Files.readAllLines(active);
        JsonNode pre = json.readTree(before);
        JsonNode post = json.readTree(after);
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("line", lines.get(1) + System.lineSeparator());
        pending.put("rotate", false);
        pending.put("preActiveHash", pre.path("activeHash").asText());
        pending.put("preActiveRecords", pre.path("activeRecords").asLong());
        pending.put("preRetainedHash", pre.path("retainedHash").asText());
        pending.put("preRetainedRecords", pre.path("retainedRecords").asLong());
        byte[] firstLine = (lines.get(0) + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        pending.put("preActiveBytes", firstLine.length);
        pending.put("postActiveHash", post.path("activeHash").asText());
        pending.put("postActiveRecords", post.path("activeRecords").asLong());
        pending.put("postRetainedHash", post.path("retainedHash").asText());
        pending.put("postRetainedRecords", post.path("retainedRecords").asLong());
        Files.write(directory.resolve("configuration-audit.pending"), json.writeValueAsBytes(pending));
        Files.write(checkpoint, before);
        byte[] secondLine = (lines.get(1) + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] torn = java.util.Arrays.copyOf(firstLine, firstLine.length + secondLine.length / 2);
        System.arraycopy(secondLine, 0, torn, firstLine.length, secondLine.length / 2);
        Files.write(active, torn);

        try (ConfigurationAuditLog ignored = new ConfigurationAuditLog(directory, clock)) { }
        assertEquals(json.readTree(after), json.readTree(Files.readAllBytes(checkpoint)));
        assertEquals(2, Files.readAllLines(active).size());
        assertEquals(false, Files.exists(directory.resolve("configuration-audit.pending")));
    }

    @Test void auditRepairRejectsOversizedActiveSegmentWithoutLoadingIt() throws Exception {
        Path active = directory.resolve("configuration-audit.jsonl");
        Files.writeString(active, "x".repeat(70 * 1024));
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("line", "{}" + System.lineSeparator());
        pending.put("rotate", false);
        pending.put("preActiveHash", "GENESIS");
        pending.put("preActiveRecords", 0);
        pending.put("preRetainedHash", "GENESIS");
        pending.put("preRetainedRecords", 0);
        pending.put("preActiveBytes", 0);
        pending.put("postActiveHash", "a".repeat(64));
        pending.put("postActiveRecords", 1);
        pending.put("postRetainedHash", "GENESIS");
        pending.put("postRetainedRecords", 0);
        Files.write(directory.resolve("configuration-audit.pending"), new ObjectMapper().writeValueAsBytes(pending));

        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC), 1));
    }

    @Test void auditValidationRejectsOversizedActiveSegmentWithoutLoadingIt() throws Exception {
        Files.writeString(directory.resolve("configuration-audit.jsonl"), "x".repeat(70 * 1024));

        assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC), 1));
    }

    @Test void newlineHeavyAuditSegmentIsValidatedLazily() throws Exception {
        Files.writeString(directory.resolve("configuration-audit.jsonl"), "\n".repeat(1024 * 1024));
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("activeHash", "GENESIS");
        checkpoint.put("activeRecords", 0);
        checkpoint.put("retainedHash", "GENESIS");
        checkpoint.put("retainedRecords", 0);
        Files.write(directory.resolve("configuration-audit.checkpoint"),
                new ObjectMapper().writeValueAsBytes(checkpoint));

        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC))) {
            log.append("AFTER_BLANKS", UUID.randomUUID(), "proxy-a", "OK");
        }
    }

    @Test void liveAuditAppendRejectsAChangedTail() throws Exception {
        Path active = directory.resolve("configuration-audit.jsonl");
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC))) {
            log.append("FIRST", UUID.randomUUID(), "proxy-a", "OK");
            Files.writeString(active, Files.readString(active).replaceFirst("FIRST", "OTHER"));

            assertThrows(ConfigurationAuditLog.AuditException.class,
                    () -> log.append("SECOND", UUID.randomUUID(), "proxy-a", "OK"));
        }
    }

    @Test void auditPendingTransactionRestoresMissingRecordSeparator() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path active = directory.resolve("configuration-audit.jsonl");
        Path checkpoint = directory.resolve("configuration-audit.checkpoint");
        ObjectMapper json = new ObjectMapper();
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("FIRST", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] before = Files.readAllBytes(checkpoint);
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("SECOND", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] after = Files.readAllBytes(checkpoint);
        List<String> lines = Files.readAllLines(active);
        JsonNode pre = json.readTree(before);
        JsonNode post = json.readTree(after);
        byte[] firstLine = (lines.get(0) + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("line", lines.get(1) + System.lineSeparator());
        pending.put("rotate", false);
        pending.put("preActiveHash", pre.path("activeHash").asText());
        pending.put("preActiveRecords", pre.path("activeRecords").asLong());
        pending.put("preRetainedHash", pre.path("retainedHash").asText());
        pending.put("preRetainedRecords", pre.path("retainedRecords").asLong());
        pending.put("preActiveBytes", firstLine.length);
        pending.put("postActiveHash", post.path("activeHash").asText());
        pending.put("postActiveRecords", post.path("activeRecords").asLong());
        pending.put("postRetainedHash", post.path("retainedHash").asText());
        pending.put("postRetainedRecords", post.path("retainedRecords").asLong());
        Files.write(directory.resolve("configuration-audit.pending"), json.writeValueAsBytes(pending));
        Files.write(checkpoint, before);
        Files.writeString(active, lines.get(0) + System.lineSeparator() + lines.get(1));

        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("THIRD", UUID.randomUUID(), "proxy-a", "OK");
        }
        assertEquals(3, Files.readAllLines(active).size());
        assertEquals(false, Files.exists(directory.resolve("configuration-audit.pending")));
    }

    @Test void liveAuditLogRecoversExistingPendingTransactionBeforeNextAppend() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        Path active = directory.resolve("configuration-audit.jsonl");
        Path checkpoint = directory.resolve("configuration-audit.checkpoint");
        Path pendingPath = directory.resolve("configuration-audit.pending");
        ObjectMapper json = new ObjectMapper();
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("FIRST", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] before = Files.readAllBytes(checkpoint);
        try (ConfigurationAuditLog log = new ConfigurationAuditLog(directory, clock)) {
            log.append("SECOND", UUID.randomUUID(), "proxy-a", "OK");
        }
        byte[] after = Files.readAllBytes(checkpoint);
        List<String> lines = Files.readAllLines(active);
        JsonNode pre = json.readTree(before);
        JsonNode post = json.readTree(after);
        byte[] firstLine = (lines.get(0) + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("line", lines.get(1) + System.lineSeparator());
        pending.put("rotate", false);
        pending.put("preActiveHash", pre.path("activeHash").asText());
        pending.put("preActiveRecords", pre.path("activeRecords").asLong());
        pending.put("preRetainedHash", pre.path("retainedHash").asText());
        pending.put("preRetainedRecords", pre.path("retainedRecords").asLong());
        pending.put("preActiveBytes", firstLine.length);
        pending.put("postActiveHash", post.path("activeHash").asText());
        pending.put("postActiveRecords", post.path("activeRecords").asLong());
        pending.put("postRetainedHash", post.path("retainedHash").asText());
        pending.put("postRetainedRecords", post.path("retainedRecords").asLong());
        Files.write(active, firstLine);
        Files.write(checkpoint, before);

        try (ConfigurationAuditLog live = new ConfigurationAuditLog(directory, clock)) {
            Files.writeString(active, lines.get(0) + System.lineSeparator() + lines.get(1)
                    + System.lineSeparator());
            Files.write(pendingPath, json.writeValueAsBytes(pending));
            live.append("THIRD", UUID.randomUUID(), "proxy-a", "OK");
        }
        assertEquals(3, Files.readAllLines(active).size());
        assertEquals(false, Files.exists(pendingPath));
    }

    @Test void auditLogRejectsASecondProcessWriter() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        try (ConfigurationAuditLog first = new ConfigurationAuditLog(directory, clock)) {
            assertThrows(java.io.IOException.class, () -> new ConfigurationAuditLog(directory, clock));
        }
    }

    @Test void operationLimitDoesNotConsumePreviewApproval() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        registry.register(registration(UUID.randomUUID()));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ProxyRoutingConfiguration proposal = new ProxyRoutingConfiguration(true, List.of());
        ConfigurationOperations.OperationView preview = operations.createPreview(List.of("proxy-a"), proposal);
        ConfigurationTask task = operations.claim("proxy-a", registry.find("proxy-a").sessionId());
        preview = operations.complete(preview.operationId(), "proxy-a",
                new ConfigurationTaskResult(registry.find("proxy-a").sessionId(), true, "OK", "valid",
                        "c".repeat(64), proposal, List.of(), false, false, task.attemptId()));
        for (int i = 0; i < 999; i++) operations.createRead(List.of("proxy-a"));

        UUID previewId = preview.operationId();
        String approval = preview.approvalToken();
        assertEquals("OPERATION_LIMIT", assertThrows(ValidationException.class,
                () -> operations.createApply(previewId, approval)).code());
        clock.advance(Duration.ofMinutes(16));
        assertEquals("APPLY", operations.createApply(previewId, approval).type());
    }

    @Test void activeTaskLeasePostponesOperationAbandonmentUntilLeaseExpires() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        UUID session = UUID.randomUUID();
        registry.register(registration(session));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID operation = operations.createRead(List.of("proxy-a")).operationId();

        clock.advance(Duration.ofMinutes(14));
        assertNotNull(operations.claim("proxy-a", session));
        clock.advance(Duration.ofSeconds(90));
        assertEquals("RUNNING", operations.get(operation).state());

        clock.advance(Duration.ofSeconds(31));
        assertEquals("OPERATION_NOT_FOUND", assertThrows(ValidationException.class,
                () -> operations.get(operation)).code());
    }

    @Test void expiredLeaseResultCannotCompleteBeforeOrAfterReissueForTheSameSession() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T00:00:00Z"));
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        UUID session = UUID.randomUUID();
        registry.register(registration(session));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID operation = operations.createRead(List.of("proxy-a")).operationId();
        ConfigurationTask first = operations.claim("proxy-a", session);

        clock.advance(Duration.ofMinutes(2));
        ProxyRoutingConfiguration configuration = new ProxyRoutingConfiguration(false, List.of());
        ConfigurationTaskResult expired = new ConfigurationTaskResult(session, true, "OK", "read", "a".repeat(64),
                configuration, List.of(), false, false, first.attemptId());
        assertEquals("TASK_LEASE_EXPIRED", assertThrows(ValidationException.class,
                () -> operations.complete(operation, "proxy-a", expired)).code());

        ConfigurationTask second = operations.claim("proxy-a", session);
        assertTrue(!first.attemptId().equals(second.attemptId()));
        ConfigurationTaskResult stale = new ConfigurationTaskResult(session, true, "OK", "read", "a".repeat(64),
                configuration, List.of(), false, false, first.attemptId());
        assertEquals("TASK_LEASE_EXPIRED", assertThrows(ValidationException.class,
                () -> operations.complete(operation, "proxy-a", stale)).code());

        ConfigurationTaskResult current = new ConfigurationTaskResult(session, true, "OK", "read", "a".repeat(64),
                configuration, List.of(), false, false, second.attemptId());
        assertEquals("SUCCEEDED", operations.complete(operation, "proxy-a", current).state());
    }

    @Test void failedTaskResultCannotRetainAnUnboundedRevision() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        UUID session = UUID.randomUUID();
        registry.register(registration(session));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID operation = operations.createRead(List.of("proxy-a")).operationId();
        ConfigurationTask task = operations.claim("proxy-a", session);

        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class, () -> operations.complete(operation,
                "proxy-a", new ConfigurationTaskResult(session, false, "FAILED", "failed", "x".repeat(1000),
                        (ManagedConfiguration) null, List.of(), false, false, task.attemptId()))).code());
    }

    @Test void taskResultConfigurationMustMatchItsOperationDomainAndSelector() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofHours(1));
        UUID session = UUID.randomUUID();
        registry.register(registration(session));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID operation = operations.createRead(List.of("proxy-a")).operationId();
        ConfigurationTask task = operations.claim("proxy-a", session);

        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class, () -> operations.complete(operation,
                "proxy-a", new ConfigurationTaskResult(session, true, "OK", "read", "a".repeat(64),
                        ManagedConfiguration.file("Config.yml", "large: value\n"), List.of(), false, false,
                        task.attemptId()))).code());
    }

    @Test void managedConfigurationRejectsFieldsFromAnotherUnionDomain() {
        assertThrows(IllegalArgumentException.class, () -> new ManagedConfiguration(
                ManagedConfiguration.PROXY_ROUTING, false, List.of(), null, "hidden", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ManagedConfiguration(
                ManagedConfiguration.QUICK_SETUP, null, List.of(), null, "hidden", "standalone", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ManagedConfiguration(
                ManagedConfiguration.FILE, null, List.of("other"), "Config.yml", null, null, Map.of()));
    }

    @Test void voteSitesSyncAllowsOneBoundedSourceDocumentAndHidesItFromOperationViews() throws Exception {
        String source = "VoteSites:\n" + "# owner comment\n".repeat(100);
        ManagedConfiguration sync = new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP, null, List.of(),
                null, null, ManagedConfiguration.VOTE_SITES_SYNC, Map.of("sourceContent", source));

        assertEquals(ConfigurationOperations.VOTE_SITES_SYNC_CAPABILITY, sync.capability());
        assertFalse(sync.publicView().options().containsKey("sourceContent"));
        assertThrows(IllegalArgumentException.class, () -> new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP,
                null, List.of(), null, null, ManagedConfiguration.VOTE_SITES_SYNC,
                Map.of("sourceContent", "x".repeat(ManagedConfiguration.MAX_CONTENT + 1))));

        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration("backend-a", session, "Backend A", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.VOTE_SITES_SYNC_CAPABILITY), Set.of()));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);

        ConfigurationOperations.OperationView preview = operations.createPreview(List.of("backend-a"), sync);
        assertFalse(preview.configuration().options().containsKey("sourceContent"));
        assertEquals(source, operations.claim("backend-a", session).configuration().options().get("sourceContent"));
    }

    @Test void fileAndQuickSetupOperationsRequireTheirNegotiatedCapabilities() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration("backend-a", session, "Backend A", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY, ConfigurationOperations.QUICK_SETUP_CAPABILITY),
                Set.of(ConfigurationOperations.FILE_CAPABILITY)));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);

        ManagedConfiguration file = ManagedConfiguration.file("Config.yml", "Feature: true\n");
        ConfigurationOperations.OperationView preview = operations.createPreview(List.of("backend-a"), file);
        assertEquals(null, preview.configuration().content());
        ConfigurationTask task = operations.claim("backend-a", session);
        assertEquals("file", task.configuration().domain());
        ManagedConfiguration current = ManagedConfiguration.file("Config.yml", "Feature: false\n");
        preview = operations.complete(preview.operationId(), "backend-a", new ConfigurationTaskResult(session, true,
                "OK", "valid", "d".repeat(64), current, List.of("changed Feature"), false, false,
                task.attemptId()));
        assertNotNull(preview.approvalToken());

        assertEquals("NODE_UNAVAILABLE", assertThrows(ValidationException.class,
                () -> operations.createPreview(List.of("backend-a"),
                        ManagedConfiguration.proxy(new ProxyRoutingConfiguration(false, List.of())))).code());
    }

    @Test void claimCancelsTaskWhenCurrentSessionLostItsCapability() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID session = UUID.randomUUID();
        registry.register(new NodeRegistration("backend-a", session, "Backend A", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of(ConfigurationOperations.FILE_CAPABILITY)));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        UUID operation = operations.createRead(List.of("backend-a"),
                ManagedConfiguration.file("Config.yml", null)).operationId();

        registry.heartbeat("backend-a", new Heartbeat(session, 1, Set.of("discovery.read"), Set.of()));

        assertEquals(null, operations.claim("backend-a", session));
        ConfigurationOperations.OperationView view = operations.get(operation);
        assertEquals("COMPLETED_WITH_ERRORS", view.state());
        assertEquals("CAPABILITY_LOST", view.results().get("backend-a").code());
    }

    @Test void fileOperationRetentionAndMultiNodeContentsAreBounded() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<String> capabilities = Set.of(ConfigurationOperations.FILE_CAPABILITY);
        registry.register(new NodeRegistration("backend-a", first, "Backend A", "BUKKIT", "test", 1,
                capabilities, capabilities));
        registry.register(new NodeRegistration("backend-b", second, "Backend B", "BUKKIT", "test", 1,
                capabilities, capabilities));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ManagedConfiguration selector = ManagedConfiguration.file("Config.yml", null);
        ConfigurationOperations.OperationView read = operations.createRead(List.of("backend-a", "backend-b"), selector);
        ConfigurationTask firstTask = operations.claim("backend-a", first);
        ConfigurationTask secondTask = operations.claim("backend-b", second);
        ManagedConfiguration content = ManagedConfiguration.file("Config.yml", "Feature: true\n");
        operations.complete(read.operationId(), "backend-a", new ConfigurationTaskResult(first, true, "OK", "read",
                "e".repeat(64), content, List.of(), false, false, firstTask.attemptId()));
        read = operations.complete(read.operationId(), "backend-b", new ConfigurationTaskResult(second, true, "OK",
                "read", "f".repeat(64), content, List.of(), false, false, secondTask.attemptId()));
        assertNotNull(read.results().get("backend-a").configuration().content());
        assertEquals(null, read.results().get("backend-b").configuration().content());

        for (int i = 0; i < 16; i++) operations.createRead(List.of("backend-a"), selector);
        assertEquals("OPERATION_LIMIT", assertThrows(ValidationException.class,
                () -> operations.createRead(List.of("backend-a"), selector)).code());
    }

    @Test void aggregateRetainedResultDetailsHaveGlobalByteBudgets() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        List<String> nodes = new java.util.ArrayList<>();
        Map<String, UUID> sessions = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            String node = "proxy-" + i;
            UUID session = UUID.randomUUID();
            nodes.add(node);
            sessions.put(node, session);
            registry.register(new NodeRegistration(node, session, node, "VELOCITY", "test", 1,
                    Set.of(ConfigurationOperations.CAPABILITY), Set.of()));
        }
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ConfigurationOperations.OperationView read = operations.createRead(nodes);
        ProxyRoutingConfiguration configuration = new ProxyRoutingConfiguration(false, List.of());
        List<String> maximumChanges = java.util.stream.IntStream.range(0, 20)
                .mapToObj(ignored -> "x".repeat(500)).toList();
        for (String node : nodes) {
            ConfigurationTask task = operations.claim(node, sessions.get(node));
            read = operations.complete(read.operationId(), node, new ConfigurationTaskResult(sessions.get(node),
                    true, "OK", "read", "a".repeat(64), configuration, maximumChanges, false, false,
                    task.attemptId()));
        }

        long retainedBytes = read.results().values().stream().flatMap(result -> result.changes().stream())
                .mapToLong(value -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        assertTrue(retainedBytes <= ConfigurationOperations.MAX_RETAINED_CHANGE_BYTES);
        assertTrue(retainedBytes < 100L * 20 * 500);
        assertEquals(1, read.results().values().stream().filter(result -> result.configuration() != null).count());

        List<UUID> messageOperations = new java.util.ArrayList<>();
        for (int operationIndex = 0; operationIndex < 6; operationIndex++) {
            ConfigurationOperations.OperationView messages = operations.createRead(nodes);
            messageOperations.add(messages.operationId());
            for (String node : nodes) {
                ConfigurationTask task = operations.claim(node, sessions.get(node));
                operations.complete(messages.operationId(), node, new ConfigurationTaskResult(sessions.get(node),
                        true, "OK", "m".repeat(500), "b".repeat(64), configuration, List.of(), false, false,
                        task.attemptId()));
            }
        }
        long retainedMessages = messageOperations.stream().map(operations::get)
                .flatMap(operation -> operation.results().values().stream())
                .mapToLong(result -> result.message().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        assertTrue(retainedMessages <= ConfigurationOperations.MAX_RETAINED_MESSAGE_BYTES);
        assertTrue(retainedMessages < 6L * 100 * 500);
    }

    @Test void failedReadCannotConsumeTheSuccessfulConfigurationSlot() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(2));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.register(new NodeRegistration("backend-a", first, "Backend A", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        registry.register(new NodeRegistration("backend-b", second, "Backend B", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory, clock), clock);
        ManagedConfiguration selector = ManagedConfiguration.file("Config.yml", null);
        ConfigurationOperations.OperationView read = operations.createRead(List.of("backend-a", "backend-b"), selector);
        ManagedConfiguration content = ManagedConfiguration.file("Config.yml", "Feature: true\n");

        ConfigurationTask firstTask = operations.claim("backend-a", first);
        operations.complete(read.operationId(), "backend-a", new ConfigurationTaskResult(first, false, "READ_FAILED",
                "failed", "a".repeat(64), content, List.of(), false, false, firstTask.attemptId()));
        ConfigurationTask secondTask = operations.claim("backend-b", second);
        read = operations.complete(read.operationId(), "backend-b", new ConfigurationTaskResult(second, true, "OK",
                "read", "b".repeat(64), content, List.of(), false, false, secondTask.attemptId()));

        assertEquals(null, read.results().get("backend-a").configuration());
        assertEquals("Feature: true\n", read.results().get("backend-b").configuration().content());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static NodeRegistration registration(UUID sessionId) {
        return new NodeRegistration("proxy-a", sessionId, "Proxy A", "VELOCITY", "test", 1,
                Set.of("presence.snapshot", ConfigurationOperations.CAPABILITY), Set.of("presence.snapshot"));
    }
}
