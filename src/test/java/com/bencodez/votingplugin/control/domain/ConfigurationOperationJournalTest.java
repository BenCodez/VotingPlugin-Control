package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationOperationJournalTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC);

    @Test void savesAndLoadsOnlyTheBoundedRedactedOperationShape() throws Exception {
        ConfigurationOperationJournal journal = new ConfigurationOperationJournal(directory, clock);
        UUID operation = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        ConfigurationOperationJournal.Entry entry = new ConfigurationOperationJournal.Entry(operation, "APPLY",
                clock.instant(), "file", "Config.yml", null, source, List.of(
                new ConfigurationOperationJournal.NodeResult("backend-a", UUID.randomUUID(), true, false,
                        "WRITE_FAILED", null,
                        false, true)));

        journal.save(List.of(entry));

        assertEquals(List.of(entry), journal.load());
        String stored = Files.readString(directory.resolve("configuration-operations.json"));
        assertFalse(stored.contains("content"));
        assertFalse(stored.contains("message"));
        assertFalse(stored.contains("approvalToken"));
        assertTrue(stored.contains("WRITE_FAILED"));
    }

    @Test void rejectsMalformedOversizedAndUnsafeJournalFiles() throws Exception {
        Path malformedDirectory = directory.resolve("malformed");
        ConfigurationOperationJournal malformed = new ConfigurationOperationJournal(malformedDirectory, clock);
        Files.writeString(malformedDirectory.resolve("configuration-operations.json"), "{}");
        assertThrows(IOException.class, malformed::load);

        Path oversizedDirectory = directory.resolve("oversized");
        ConfigurationOperationJournal oversized = new ConfigurationOperationJournal(oversizedDirectory, clock);
        Files.writeString(oversizedDirectory.resolve("configuration-operations.json"), "x".repeat(2 * 1024 * 1024 + 1));
        assertThrows(IOException.class, oversized::load);

        Path duplicateDirectory = directory.resolve("duplicate");
        ConfigurationOperationJournal duplicate = new ConfigurationOperationJournal(duplicateDirectory, clock);
        duplicate.save(List.of());
        Path duplicateFile = duplicateDirectory.resolve("configuration-operations.json");
        String valid = Files.readString(duplicateFile);
        Files.writeString(duplicateFile, "{\"schemaVersion\":1," + valid.substring(1));
        assertThrows(IOException.class, duplicate::load);

        Path unsafeDirectory = directory.resolve("unsafe");
        Files.createDirectories(unsafeDirectory);
        Path target = directory.resolve("target.json");
        Files.writeString(target, "{}");
        try {
            Files.createSymbolicLink(unsafeDirectory.resolve("configuration-operations.json"), target);
            assertThrows(IOException.class, () -> new ConfigurationOperationJournal(unsafeDirectory, clock));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            // The platform does not support symbolic links for this test user.
        }
    }

    @Test void completedNodesRequireSessionsAndIncompleteNodesMustOmitThem() throws Exception {
        ConfigurationOperationJournal journal = new ConfigurationOperationJournal(
                directory.resolve("invalid-node-sessions"), clock);
        UUID session = UUID.randomUUID();
        ConfigurationOperationJournal.NodeResult completed =
                new ConfigurationOperationJournal.NodeResult("backend-a", session, true, true, "OK",
                        "a".repeat(64), false, false);
        ConfigurationOperationJournal.NodeResult incompleteWithSession =
                new ConfigurationOperationJournal.NodeResult("backend-a", UUID.randomUUID(), false, null, null,
                        null, false, false);

        journal.save(List.of(journalEntry(completed)));
        Path stored = directory.resolve("invalid-node-sessions/configuration-operations.json");
        Files.writeString(stored, Files.readString(stored).replace('"' + session.toString() + '"', "null"));
        assertThrows(IOException.class, journal::load);
        assertThrows(IOException.class, () -> journal.save(List.of(journalEntry(incompleteWithSession))));
    }

    @Test void restartClosesIncompleteWorkAndDoesNotReissueIt() throws Exception {
        InMemoryNodeRegistry registry = registry();
        Path auditDirectory = directory.resolve("audit");
        Path journalDirectory = directory.resolve("journal");
        UUID operation;
        try (ConfigurationOperations first = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(auditDirectory, clock), clock,
                new ConfigurationOperationJournal(journalDirectory, clock))) {
            operation = first.createRead(List.of("backend-a"), ManagedConfiguration.file("Config.yml", null))
                    .operationId();
        }

        try (ConfigurationOperations restarted = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(auditDirectory, clock), clock,
                new ConfigurationOperationJournal(journalDirectory, clock))) {
            ConfigurationOperations.OperationView recovered = restarted.get(operation);
            assertTrue(recovered.recovered());
            assertFalse(recovered.retryable());
            assertEquals("COMPLETED_WITH_ERRORS", recovered.state());
            assertEquals("CONTROL_RESTARTED", recovered.results().get("backend-a").code());
            assertNull(restarted.claim("backend-a", registry.find("backend-a").sessionId()));
        }
    }

    @Test void completedHistorySurvivesRestartWithoutFileContentsOrMessages() throws Exception {
        InMemoryNodeRegistry registry = registry();
        Path auditDirectory = directory.resolve("audit-complete");
        Path journalDirectory = directory.resolve("journal-complete");
        UUID operation;
        UUID session = registry.find("backend-a").sessionId();
        try (ConfigurationOperations first = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(auditDirectory, clock), clock,
                new ConfigurationOperationJournal(journalDirectory, clock))) {
            operation = first.createRead(List.of("backend-a"), ManagedConfiguration.file("Config.yml", null))
                    .operationId();
            ConfigurationTask task = first.claim("backend-a", session);
            first.complete(operation, "backend-a", new ConfigurationTaskResult(session, true, "OK",
                    "contains operational detail", "a".repeat(64),
                    ManagedConfiguration.file("Config.yml", "Password: super-secret\n"), List.of("changed"),
                    false, false, task.attemptId()));
        }
        String stored = Files.readString(journalDirectory.resolve("configuration-operations.json"));
        assertFalse(stored.contains("super-secret"));
        assertFalse(stored.contains("operational detail"));
        assertFalse(stored.contains("changed"));

        try (ConfigurationOperations restarted = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(auditDirectory, clock), clock,
                new ConfigurationOperationJournal(journalDirectory, clock))) {
            ConfigurationOperations.OperationView recovered = restarted.get(operation);
            assertEquals("SUCCEEDED", recovered.state());
            assertTrue(recovered.recovered());
            assertEquals("a".repeat(64), recovered.results().get("backend-a").revision());
            assertEquals(session, recovered.results().get("backend-a").sessionId());
            assertNull(recovered.results().get("backend-a").configuration());
        }
    }

    @Test void rewardBuilderHistoryRestoresAsANonExecutableRedactedView() throws Exception {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(5));
        UUID session = UUID.fromString("00000000-0000-0000-0000-000000000002");
        registry.register(new NodeRegistration("backend-a", session, "Backend A", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.QUICK_SETUP_CAPABILITY), Set.of()));
        Path auditDirectory = directory.resolve("audit-reward-history");
        Path journalDirectory = directory.resolve("journal-reward-history");
        ConfigurationOperationJournal journal = new ConfigurationOperationJournal(journalDirectory, clock);
        UUID operation = UUID.randomUUID();
        journal.save(List.of(new ConfigurationOperationJournal.Entry(operation, "PREVIEW", clock.instant(),
                ManagedConfiguration.QUICK_SETUP, null, ManagedConfiguration.REWARD_BUILDER, null, List.of(
                new ConfigurationOperationJournal.NodeResult("backend-a", null, false, null, null, null,
                        false, false)))));

        try (ConfigurationOperations restarted = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(auditDirectory, clock), clock, journal)) {
            ManagedConfiguration recovered = restarted.get(operation).configuration();
            assertTrue(recovered.redacted());
            assertTrue(recovered.options().isEmpty());
            assertThrows(IllegalArgumentException.class, recovered::validateProposal);
        }
    }

    @Test void voteLoggingRestartStateSurvivesBeyondTheListedHistoryWindow() throws Exception {
        UUID session = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<ConfigurationOperationJournal.Entry> entries = new ArrayList<>();
        entries.add(new ConfigurationOperationJournal.Entry(UUID.randomUUID(), "APPLY",
                clock.instant().minusSeconds(200), ManagedConfiguration.QUICK_SETUP, null, "vote-logging", null,
                List.of(new ConfigurationOperationJournal.NodeResult("backend-a", session, true, true, "OK",
                        "a".repeat(64), true, false))));
        for (int index = 0; index < 101; index++) {
            entries.add(new ConfigurationOperationJournal.Entry(UUID.randomUUID(), "READ",
                    clock.instant().minusSeconds(199L - index), ManagedConfiguration.PROXY_ROUTING, null, null, null,
                    List.of(new ConfigurationOperationJournal.NodeResult("backend-a", session, true, true, "OK",
                            "b".repeat(64), false, false))));
        }
        Path journalDirectory = directory.resolve("journal-restart-window");
        ConfigurationOperationJournal journal = new ConfigurationOperationJournal(journalDirectory, clock);
        journal.save(entries);

        try (ConfigurationOperations restarted = new ConfigurationOperations(registry(),
                new ConfigurationAuditLog(directory.resolve("audit-restart-window"), clock), clock, journal)) {
            ConfigurationOperations.OperationListView view = restarted.listView();
            assertEquals(100, view.items().size());
            assertTrue(view.items().stream().noneMatch(item -> "vote-logging".equals(item.configuration().preset())));
            assertEquals(Map.of("backend-a", session), view.voteLoggingRestartSessions());
        }
    }

    private InMemoryNodeRegistry registry() {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, Duration.ofMinutes(5));
        UUID session = UUID.fromString("00000000-0000-0000-0000-000000000001");
        registry.register(new NodeRegistration("backend-a", session, "Backend A", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        return registry;
    }

    private ConfigurationOperationJournal.Entry journalEntry(ConfigurationOperationJournal.NodeResult node) {
        return new ConfigurationOperationJournal.Entry(UUID.randomUUID(), "APPLY", clock.instant(),
                ManagedConfiguration.QUICK_SETUP, null, "vote-logging", null, List.of(node));
    }
}
