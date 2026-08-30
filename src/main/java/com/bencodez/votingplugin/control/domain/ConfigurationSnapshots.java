package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.DurableFiles;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.channels.FileChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/** Durable, bounded copies of redacted managed-file reads for comparison and approved restore. */
public final class ConfigurationSnapshots {
    private static final int MAX_SNAPSHOTS = 100;
    private static final int MAX_DOCUMENTS = 100;
    private static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STORED_BYTES = MAX_SNAPSHOT_BYTES + 256 * 1024;
    private static final long MAX_TOTAL_STORED_BYTES = 64L * 1024 * 1024;
    private final Path directory;
    private final Clock clock;
    private final ObjectMapper json = strictMapper();
    private final FailureInjector failureInjector;

    private static ObjectMapper strictMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.getFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        return mapper;
    }

    public ConfigurationSnapshots(Path dataDirectory, Clock clock) throws IOException {
        this(dataDirectory, clock, () -> { });
    }

    ConfigurationSnapshots(Path dataDirectory, Clock clock, FailureInjector failureInjector) throws IOException {
        this.directory = dataDirectory.resolve("configuration-snapshots").toAbsolutePath().normalize();
        this.clock = clock;
        this.failureInjector = failureInjector;
        boolean existed = Files.exists(directory, LinkOption.NOFOLLOW_LINKS);
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Configuration snapshot directory is unsafe");
        if (!existed) setPermissions(directory, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
    }

    public synchronized Snapshot create(String name, ConfigurationOperations.OperationView operation) throws IOException {
        validateName(name);
        if (operation == null || !"READ".equals(operation.type())
                || !List.of("SUCCEEDED", "COMPLETED_WITH_ERRORS").contains(operation.state())) {
            throw invalid("snapshot source must be a completed configuration read");
        }
        List<SnapshotDocument> documents = new ArrayList<>();
        int bytes = 0;
        for (Map.Entry<String, ConfigurationTaskResult> entry : operation.results().entrySet()) {
            ConfigurationTaskResult result = entry.getValue();
            ManagedConfiguration configuration = result == null ? null : result.configuration();
            if (result == null || !result.success() || configuration == null
                    || !ManagedConfiguration.FILE.equals(configuration.domain())
                    || configuration.content() == null) continue;
            int contentBytes = configuration.content().getBytes(StandardCharsets.UTF_8).length;
            if (documents.size() >= MAX_DOCUMENTS || bytes + contentBytes > MAX_SNAPSHOT_BYTES) {
                throw invalid("snapshot source exceeds the bounded snapshot size");
            }
            bytes += contentBytes;
            documents.add(new SnapshotDocument(entry.getKey(), configuration.fileName(), configuration.content(),
                    result.revision()));
        }
        if (documents.isEmpty()) throw invalid("snapshot source does not retain a readable file");
        Snapshot snapshot = new Snapshot(UUID.randomUUID(), name.trim(), clock.instant(), operation.operationId(),
                List.copyOf(documents));
        byte[] encoded = encode(snapshot);
        publish(snapshot, encoded);
        return snapshot;
    }

    public synchronized List<SnapshotSummary> list() throws IOException {
        List<SnapshotSummary> result = new ArrayList<>();
        for (Path file : files()) {
            Snapshot snapshot = read(file);
            result.add(new SnapshotSummary(snapshot.snapshotId(), snapshot.name(), snapshot.createdAt(),
                    snapshot.sourceOperationId(), snapshot.documents().stream()
                    .map(document -> new SnapshotDocumentSummary(document.nodeId(), document.fileName(),
                            document.revision())).toList()));
        }
        result.sort(Comparator.comparing(SnapshotSummary::createdAt).reversed());
        return List.copyOf(result);
    }

    public synchronized Snapshot get(UUID id) throws IOException {
        Path file = path(id);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ValidationException("SNAPSHOT_NOT_FOUND", "Configuration snapshot was not found", List.of());
        }
        Snapshot result = read(file);
        if (!id.equals(result.snapshotId())) throw new IOException("Configuration snapshot identity is invalid");
        return result;
    }

    private void publish(Snapshot snapshot, byte[] bytes) throws IOException {
        List<Path> current = new ArrayList<>(files());
        long retainedBytes = 0;
        for (Path path : current) retainedBytes += Files.size(path);
        List<Path> evictions = new ArrayList<>();
        long incomingBytes = bytes.length;
        while (current.size() + 1 > MAX_SNAPSHOTS || retainedBytes + incomingBytes > MAX_TOTAL_STORED_BYTES) {
            Path oldest = current.stream().min(snapshotOrder()).orElseThrow(() -> invalid("snapshot capacity is unavailable"));
            retainedBytes -= Files.size(oldest);
            evictions.add(oldest);
            current.remove(oldest);
        }
        Path temporary = Files.createTempFile(directory, "snapshot-", ".temporary");
        Path transaction;
        try {
            transaction = Files.createTempDirectory(directory, "snapshot-transaction-");
        } catch (IOException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
        Path target = path(snapshot.snapshotId());
        List<Path> backups = new ArrayList<>();
        boolean published = false;
        try {
            setPermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            failureInjector.afterStaging();
            for (Path evicted : evictions) {
                Path backup = transaction.resolve(evicted.getFileName().toString());
                Files.copy(evicted, backup, StandardCopyOption.COPY_ATTRIBUTES);
                try (FileChannel channel = FileChannel.open(backup, StandardOpenOption.WRITE)) { channel.force(true); }
                backups.add(backup);
            }
            move(temporary, target);
            setPermissions(target, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            DurableFiles.forceDirectory(directory);
            failureInjector.afterPublication();
            published = true;
            boolean cleanupComplete = true;
            for (Path evicted : evictions) {
                try { Files.delete(evicted); }
                catch (IOException ignored) { cleanupComplete = false; }
            }
            if (cleanupComplete) {
                try {
                    DurableFiles.forceDirectory(directory);
                    for (Path backup : backups) Files.deleteIfExists(backup);
                    DurableFiles.forceDirectory(transaction);
                    Files.deleteIfExists(transaction);
                } catch (IOException ignored) {
                    // The new snapshot is already durable. Retain the transaction copies for recovery/cleanup.
                }
            }
            return;
        } catch (Exception failure) {
            try { Files.deleteIfExists(target); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            try { DurableFiles.forceDirectory(directory); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            if (failure instanceof IOException io) throw io;
            throw new IOException("Configuration snapshot publication failed", failure);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            if (!published) {
                try (var leftovers = Files.list(transaction)) {
                    leftovers.forEach(leftover -> { try { Files.deleteIfExists(leftover); } catch (IOException ignored) { } });
                } catch (IOException ignored) { }
                try { Files.deleteIfExists(transaction); } catch (IOException ignored) { }
            }
        }
    }

    private List<Path> files() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().matches("[0-9a-f-]{36}\\.json"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
                    .limit(MAX_SNAPSHOTS + 1L).toList();
        }
    }

    private static Comparator<Path> snapshotOrder() {
        return Comparator.comparing((Path path) -> {
            try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(); }
            catch (IOException failure) { return Instant.MIN; }
        }).thenComparing(path -> path.getFileName().toString());
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Snapshot read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_STORED_BYTES) throw new IOException("Configuration snapshot is invalid");
        Snapshot snapshot = json.readValue(Files.readAllBytes(file), Snapshot.class);
        validate(snapshot);
        return snapshot;
    }

    private byte[] encode(Snapshot snapshot) throws IOException {
        byte[] bytes = json.writeValueAsBytes(snapshot);
        if (bytes.length > MAX_STORED_BYTES || bytes.length > MAX_TOTAL_STORED_BYTES) {
            throw invalid("snapshot exceeds the bounded snapshot size");
        }
        return bytes;
    }

    private Path path(UUID id) {
        return directory.resolve(id + ".json");
    }

    private static void validate(Snapshot snapshot) throws IOException {
        try {
            if (snapshot == null || snapshot.snapshotId() == null || snapshot.createdAt() == null
                    || snapshot.sourceOperationId() == null || snapshot.documents() == null
                    || snapshot.documents().isEmpty() || snapshot.documents().size() > MAX_DOCUMENTS) {
                throw new IllegalArgumentException();
            }
            validateName(snapshot.name());
            int bytes = 0;
            for (SnapshotDocument document : snapshot.documents()) {
                if (document.nodeId() == null || !document.nodeId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                        || document.fileName() == null || document.content() == null || document.revision() == null
                        || !document.revision().matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
                new ManagedConfiguration(ManagedConfiguration.FILE, null, List.of(), document.fileName(),
                        document.content(), null, Map.of());
                bytes += document.content().getBytes(StandardCharsets.UTF_8).length;
            }
            if (bytes > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException();
        } catch (IllegalArgumentException failure) {
            throw new IOException("Configuration snapshot is invalid", failure);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 80
                || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("snapshot name is invalid");
    }

    private static ValidationException invalid(String detail) {
        return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail));
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some network filesystems do not expose POSIX permissions.
        }
    }

    public record Snapshot(UUID snapshotId, String name, Instant createdAt, UUID sourceOperationId,
                           List<SnapshotDocument> documents) { }
    public record SnapshotDocument(String nodeId, String fileName, String content, String revision) { }
    public record SnapshotSummary(UUID snapshotId, String name, Instant createdAt, UUID sourceOperationId,
                                  List<SnapshotDocumentSummary> documents) { }
    public record SnapshotDocumentSummary(String nodeId, String fileName, String revision) { }

    @FunctionalInterface
    interface FailureInjector {
        void afterStaging() throws IOException;
        default void afterPublication() throws IOException { }
    }
}
