package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.nio.file.Files;
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
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationSnapshotsTest {
    @TempDir Path directory;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-30T00:00:00Z"));

    @Test void createsListsAndReadsOnlySuccessfulRetainedFileDocuments() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        UUID sourceId = UUID.randomUUID();
        ManagedConfiguration redacted = ManagedConfiguration.file("Config.yml", "Password: <redacted>\n");
        ConfigurationTaskResult successful = result(true, "a".repeat(64), redacted);
        ConfigurationTaskResult failed = result(false, null,
                ManagedConfiguration.file("VoteSites.yml", "ShouldNotPersist: true\n"));
        ConfigurationOperations.OperationView source = new ConfigurationOperations.OperationView(sourceId, "READ",
                "COMPLETED_WITH_ERRORS", clock.instant(),
                ManagedConfiguration.file("Config.yml", "Password: super-secret\n"),
                Map.of("backend-a", "COMPLETE", "backend-b", "COMPLETE"),
                Map.of("backend-a", successful, "backend-b", failed), null, null, false, false);

        ConfigurationSnapshots.Snapshot created = snapshots.create("Before reward changes", source);

        assertEquals(sourceId, created.sourceOperationId());
        assertEquals(1, created.documents().size());
        assertEquals("backend-a", created.documents().get(0).nodeId());
        assertEquals("Password: <redacted>\n", created.documents().get(0).content());
        ConfigurationSnapshots.Snapshot loaded = snapshots.get(created.snapshotId());
        assertEquals(created, loaded);
        List<ConfigurationSnapshots.SnapshotSummary> listed = snapshots.list();
        assertEquals(1, listed.size());
        assertEquals(created.snapshotId(), listed.get(0).snapshotId());
        assertEquals("Config.yml", listed.get(0).documents().get(0).fileName());

        Path stored = directory.resolve("configuration-snapshots").resolve(created.snapshotId() + ".json");
        String persisted = Files.readString(stored);
        assertTrue(persisted.contains("&lt;redacted&gt;") || persisted.contains("<redacted>"));
        assertFalse(persisted.contains("super-secret"));
        assertFalse(persisted.contains("ShouldNotPersist"));
    }

    @Test void sourceMustBeACompletedReadWithRetainedFileContent() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        ConfigurationOperations.OperationView running = operation("READ", "RUNNING",
                ManagedConfiguration.file("Config.yml", "Feature: true\n"));
        ConfigurationOperations.OperationView preview = operation("PREVIEW", "SUCCEEDED",
                ManagedConfiguration.file("Config.yml", "Feature: true\n"));
        ConfigurationOperations.OperationView omitted = operation("READ", "SUCCEEDED",
                ManagedConfiguration.file("Config.yml", null));

        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> snapshots.create("running", running)).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> snapshots.create("preview", preview)).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                () -> snapshots.create("omitted", omitted)).code());
    }

    @Test void displayNamesAreBoundedAndNeverUsedAsFilesystemPaths() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        ConfigurationSnapshots.Snapshot snapshot = snapshots.create("../display-only",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n")));

        assertEquals("../display-only", snapshot.name());
        assertTrue(Files.exists(directory.resolve("configuration-snapshots")
                .resolve(snapshot.snapshotId() + ".json")));
        assertFalse(Files.exists(directory.resolve("display-only")));
        assertThrows(IllegalArgumentException.class, () -> snapshots.create(" ",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n"))));
        assertThrows(IllegalArgumentException.class, () -> snapshots.create("bad\nname",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n"))));
        assertThrows(IllegalArgumentException.class, () -> snapshots.create("x".repeat(81),
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n"))));
    }

    @Test void capacityPrunesTheOldestSnapshotAndListsNewestFirst() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        UUID first = null;
        UUID newest = null;
        for (int index = 0; index < 101; index++) {
            ConfigurationSnapshots.Snapshot snapshot = snapshots.create("snapshot-" + index,
                    operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml",
                            "Index: " + index + "\n")));
            if (index == 0) first = snapshot.snapshotId();
            newest = snapshot.snapshotId();
            clock.advance(Duration.ofSeconds(1));
        }

        List<ConfigurationSnapshots.SnapshotSummary> retained = snapshots.list();
        assertEquals(100, retained.size());
        assertEquals(newest, retained.get(0).snapshotId());
        UUID evicted = first;
        assertEquals("SNAPSHOT_NOT_FOUND", assertThrows(ValidationException.class,
                () -> snapshots.get(evicted)).code());
    }

    @Test void failedPublicationDoesNotEvictCountLimitedSnapshots() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        List<UUID> retainedBeforeFailure = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            retainedBeforeFailure.add(snapshots.create("snapshot-" + index,
                    operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml",
                            "Index: " + index + "\n"))).snapshotId());
            clock.advance(Duration.ofSeconds(1));
        }
        ConfigurationSnapshots failing = new ConfigurationSnapshots(directory, clock,
                () -> { throw new java.io.IOException("forced publication failure"); });
        assertThrows(java.io.IOException.class, () -> failing.create("failed",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "failed\n"))));
        for (UUID id : retainedBeforeFailure) assertEquals(id, snapshots.get(id).snapshotId());
        try (var files = Files.list(directory.resolve("configuration-snapshots"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains("temporary")
                    || path.getFileName().toString().contains("transaction")));
        }

        ConfigurationSnapshots.Snapshot created = snapshots.create("retry",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "retry\n")));
        assertEquals(100, snapshots.list().size());
        assertThrows(ValidationException.class, () -> snapshots.get(retainedBeforeFailure.get(0)));
        assertEquals(created, snapshots.get(created.snapshotId()));
    }

    @Test void failedTargetRollbackRetainsTransactionRecoveryData() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        Set<String> retainedNames = new java.util.HashSet<>();
        for (int index = 0; index < 100; index++) {
            ConfigurationSnapshots.Snapshot snapshot = snapshots.create("snapshot-" + index,
                    operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml",
                            "Index: " + index + "\n")));
            retainedNames.add(snapshot.snapshotId() + ".json");
            clock.advance(Duration.ofSeconds(1));
        }
        Path store = directory.resolve("configuration-snapshots");
        ConfigurationSnapshots failing = new ConfigurationSnapshots(directory, clock,
                new ConfigurationSnapshots.FailureInjector() {
                    @Override public void afterStaging() { }
                    @Override public void afterPublication() throws java.io.IOException {
                        Path target;
                        try (var files = Files.list(store)) {
                            target = files.filter(path -> path.getFileName().toString()
                                            .matches("[0-9a-f-]{36}\\.json"))
                                    .filter(path -> !retainedNames.contains(path.getFileName().toString()))
                                    .findFirst().orElseThrow();
                        }
                        Files.delete(target);
                        Files.createDirectory(target);
                        Files.writeString(target.resolve("prevents-delete"), "keep recovery transaction");
                        throw new java.io.IOException("forced rollback failure");
                    }
                });

        assertThrows(java.io.IOException.class, () -> failing.create("failed rollback",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "failed\n"))));

        try (var files = Files.list(store)) {
            List<Path> transactions = files.filter(path -> path.getFileName().toString()
                    .startsWith("snapshot-transaction-")).toList();
            assertEquals(1, transactions.size());
            try (var backups = Files.list(transactions.get(0))) {
                assertTrue(backups.findAny().isPresent());
            }
        }
        assertEquals(100, snapshots.list().size());
    }

    @Test void startupRemovesOnlyRecognizedAbandonedStagingFiles() throws Exception {
        Path snapshotDirectory = directory.resolve("configuration-snapshots");
        Files.createDirectories(snapshotDirectory);
        Path abandoned = snapshotDirectory.resolve("snapshot-123456.temporary");
        Path unrelated = snapshotDirectory.resolve("snapshot-not-ours.temporary");
        Files.writeString(abandoned, "partial snapshot");
        Files.writeString(unrelated, "leave me alone");

        new ConfigurationSnapshots(directory, clock);

        assertFalse(Files.exists(abandoned));
        assertTrue(Files.exists(unrelated));
    }

    @Test void recoveryRestoresBackupsTrimsOnceAndRemovesTheTransaction() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        List<UUID> existing = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            existing.add(snapshots.create("existing-" + index,
                    operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml",
                            "Index: " + index + "\n"))).snapshotId());
            clock.advance(Duration.ofSeconds(1));
        }
        Path sourceDirectory = directory.resolve("backup-source");
        ConfigurationSnapshots source = new ConfigurationSnapshots(sourceDirectory, clock);
        ConfigurationSnapshots.Snapshot recovered = source.create("recovered",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Recovered: true\n")));
        Path store = directory.resolve("configuration-snapshots");
        Path transaction = store.resolve("snapshot-transaction-123456");
        Files.createDirectory(transaction);
        Files.copy(sourceDirectory.resolve("configuration-snapshots").resolve(recovered.snapshotId() + ".json"),
                transaction.resolve(recovered.snapshotId() + ".json"),
                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);

        ConfigurationSnapshots restarted = new ConfigurationSnapshots(directory, clock);

        assertEquals(100, restarted.list().size());
        assertEquals(recovered.snapshotId(), restarted.get(recovered.snapshotId()).snapshotId());
        assertThrows(ValidationException.class, () -> restarted.get(existing.get(0)));
        assertFalse(Files.exists(transaction));
    }

    @Test void recoveryRejectsTruncatedBackupsBeforeReplacingValidSnapshots() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        ConfigurationSnapshots.Snapshot existing = snapshots.create("existing",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n")));
        Path transaction = directory.resolve("configuration-snapshots/snapshot-transaction-777777");
        Files.createDirectory(transaction);
        Path backup = transaction.resolve(existing.snapshotId() + ".json");
        Files.writeString(backup, "{\"snapshotId\":\"" + existing.snapshotId() + "\"");

        assertThrows(java.io.IOException.class, () -> new ConfigurationSnapshots(directory, clock));

        assertEquals(existing, snapshots.get(existing.snapshotId()));
        assertTrue(Files.exists(backup));
        assertTrue(Files.exists(transaction));
    }

    @Test void recoveryRejectsBackupWhoseEmbeddedIdentityDoesNotMatchItsName() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        ConfigurationSnapshots.Snapshot existing = snapshots.create("existing",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n")));
        Path store = directory.resolve("configuration-snapshots");
        Path transaction = store.resolve("snapshot-transaction-888888");
        Files.createDirectory(transaction);
        Path mismatched = transaction.resolve(UUID.randomUUID() + ".json");
        Files.copy(store.resolve(existing.snapshotId() + ".json"), mismatched);

        assertThrows(java.io.IOException.class, () -> new ConfigurationSnapshots(directory, clock));

        assertEquals(existing, snapshots.get(existing.snapshotId()));
        assertTrue(Files.exists(mismatched));
    }

    @Test void byteLimitEvictionIsDeterministicAndOccursAfterSuccessfulPublication() throws Exception {
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(directory, clock);
        String content = "Value: " + "x".repeat(400_000) + "\n";
        List<UUID> ids = new java.util.ArrayList<>();
        for (int index = 0; index < 90; index++) {
            ids.add(snapshots.create("large-" + index,
                    operationWithFiles(content)).snapshotId());
            clock.advance(Duration.ofSeconds(1));
        }
        assertTrue(snapshots.list().size() < 90);
        assertThrows(ValidationException.class, () -> snapshots.get(ids.get(0)));
        assertEquals(ids.get(89), snapshots.list().get(0).snapshotId());

        List<ConfigurationSnapshots.SnapshotSummary> beforeRecovery = snapshots.list();
        UUID oldestBeforeRecovery = beforeRecovery.get(beforeRecovery.size() - 1).snapshotId();
        Path sourceDirectory = directory.resolve("byte-recovery-source");
        ConfigurationSnapshots source = new ConfigurationSnapshots(sourceDirectory, clock);
        ConfigurationSnapshots.Snapshot recovered = source.create("recovered-large", operationWithFiles(content));
        Path transaction = directory.resolve("configuration-snapshots/snapshot-transaction-654321");
        Files.createDirectory(transaction);
        Files.copy(sourceDirectory.resolve("configuration-snapshots").resolve(recovered.snapshotId() + ".json"),
                transaction.resolve(recovered.snapshotId() + ".json"),
                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);

        ConfigurationSnapshots restarted = new ConfigurationSnapshots(directory, clock);
        assertEquals(beforeRecovery.size(), restarted.list().size());
        assertEquals(recovered.snapshotId(), restarted.get(recovered.snapshotId()).snapshotId());
        assertThrows(ValidationException.class, () -> restarted.get(oldestBeforeRecovery));
        assertFalse(Files.exists(transaction));
    }

    @Test void durableJsonRejectsUnknownDuplicateAndTrailingContent() throws Exception {
        assertSnapshotJsonRejected("unknown", json -> json.substring(0, json.lastIndexOf('}'))
                + ",\"unexpected\":true}");
        assertSnapshotJsonRejected("duplicate", json -> "{\"name\":\"duplicate\"," + json.substring(1));
        assertSnapshotJsonRejected("trailing", json -> json + "{}");
    }

    private void assertSnapshotJsonRejected(String directoryName, UnaryOperator<String> corrupt) throws Exception {
        Path dataDirectory = directory.resolve(directoryName);
        ConfigurationSnapshots snapshots = new ConfigurationSnapshots(dataDirectory, clock);
        ConfigurationSnapshots.Snapshot created = snapshots.create("Strict JSON",
                operation("READ", "SUCCEEDED", ManagedConfiguration.file("Config.yml", "Feature: true\n")));
        Path stored = dataDirectory.resolve("configuration-snapshots").resolve(created.snapshotId() + ".json");
        Files.writeString(stored, corrupt.apply(Files.readString(stored)));

        assertThrows(java.io.IOException.class, () -> snapshots.get(created.snapshotId()));
    }

    private ConfigurationOperations.OperationView operation(String type, String state,
                                                              ManagedConfiguration returned) {
        UUID source = UUID.randomUUID();
        return new ConfigurationOperations.OperationView(source, type, state, clock.instant(),
                ManagedConfiguration.file("Config.yml", null), Map.of("backend-a", "COMPLETE"),
                Map.of("backend-a", result(true, "a".repeat(64), returned)), null, null, false, false);
    }

    private ConfigurationTaskResult result(boolean success, String revision, ManagedConfiguration configuration) {
        return new ConfigurationTaskResult(UUID.randomUUID(), success, success ? "OK" : "READ_FAILED",
                success ? "read" : "failed", revision, configuration, List.of(), false, false, UUID.randomUUID());
    }

    private ConfigurationOperations.OperationView operationWithFiles(String content) {
        UUID source = UUID.randomUUID();
        ManagedConfiguration first = ManagedConfiguration.file("Config.yml", content);
        ManagedConfiguration second = ManagedConfiguration.file("VoteSites.yml", content);
        return new ConfigurationOperations.OperationView(source, "READ", "SUCCEEDED", clock.instant(), first,
                Map.of("backend-a", "COMPLETE", "backend-b", "COMPLETE"),
                Map.of("backend-a", result(true, "a".repeat(64), first),
                        "backend-b", result(true, "b".repeat(64), second)), null, null, false, false);
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
