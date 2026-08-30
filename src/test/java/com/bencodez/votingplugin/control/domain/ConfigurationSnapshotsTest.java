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

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
