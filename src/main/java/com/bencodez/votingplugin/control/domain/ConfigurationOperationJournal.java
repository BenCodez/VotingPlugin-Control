package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.DurableFiles;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Durable, redacted operation history. The journal intentionally excludes proposal values,
 * file contents, approval tokens, changes, messages, credentials, and task attempt identifiers.
 */
public final class ConfigurationOperationJournal {
    static final int SCHEMA_VERSION = 2;
    private static final int LEGACY_SCHEMA_VERSION = 1;
    static final int MAX_OPERATIONS = 1000;
    private static final int MAX_NODES = 100;
    static final int MAX_RESTART_SESSIONS = 1000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path directory;
    private final Path file;
    private final Clock clock;
    private final ObjectMapper json = strictMapper();

    private static ObjectMapper strictMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.getFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        return mapper;
    }

    public ConfigurationOperationJournal(Path dataDirectory, Clock clock) throws IOException {
        this.directory = dataDirectory.toAbsolutePath().normalize();
        this.file = directory.resolve("configuration-operations.json");
        this.clock = clock;
        boolean existed = Files.exists(directory, LinkOption.NOFOLLOW_LINKS);
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Configuration operation journal directory is unsafe");
        }
        if (!existed) setPermissions(directory, OWNER_DIRECTORY);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))) {
            throw new IOException("Configuration operation journal is unsafe");
        }
        removeAbandonedStagingFiles();
    }

    private void removeAbandonedStagingFiles() throws IOException {
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString()
                    .matches("configuration-operations-[0-9]+\\.temporary")).toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                    throw new IOException("Configuration operation journal staging file is unsafe");
                }
                Files.delete(path);
            }
        }
        DurableFiles.forceDirectory(directory);
    }

    public synchronized List<Entry> load() throws IOException {
        return loadState().operations();
    }

    public synchronized State loadState() throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new State(List.of(), Map.of());
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_BYTES) {
            throw new IOException("Configuration operation journal is invalid");
        }
        JournalFile stored;
        try {
            stored = json.readValue(Files.readAllBytes(file), JournalFile.class);
        } catch (RuntimeException failure) {
            throw new IOException("Configuration operation journal is invalid", failure);
        }
        if (stored == null || (stored.schemaVersion() != SCHEMA_VERSION
                && stored.schemaVersion() != LEGACY_SCHEMA_VERSION) || stored.operations() == null
                || stored.operations().size() > MAX_OPERATIONS) {
            throw new IOException("Configuration operation journal is invalid");
        }
        Instant cutoff = clock.instant().minus(RETENTION);
        List<Entry> result = new ArrayList<>();
        Set<UUID> operationIds = new java.util.HashSet<>();
        for (Entry entry : stored.operations()) {
            validate(entry);
            if (!operationIds.add(entry.operationId())) {
                throw new IOException("Configuration operation journal is invalid");
            }
            if (!entry.createdAt().isBefore(cutoff)) result.add(entry);
        }
        result.sort(Comparator.comparing(Entry::createdAt));
        Map<String, UUID> restartSessions = validateRestartSessions(stored.voteLoggingRestartSessions());
        return new State(List.copyOf(result), restartSessions);
    }

    public synchronized void save(List<Entry> entries) throws IOException {
        save(entries, Map.of());
    }

    public synchronized void save(List<Entry> entries, Map<String, UUID> voteLoggingRestartSessions) throws IOException {
        if (entries == null) throw new IOException("Configuration operation journal is invalid");
        Map<String, UUID> restartSessions = validateRestartSessions(voteLoggingRestartSessions);
        Instant cutoff = clock.instant().minus(RETENTION);
        List<Entry> filtered = entries.stream().filter(entry -> !entry.createdAt().isBefore(cutoff))
                .sorted(Comparator.comparing(Entry::createdAt)).toList();
        List<Entry> retained = new ArrayList<>(filtered.stream()
                .skip(Math.max(0, filtered.size() - MAX_OPERATIONS)).toList());
        Set<UUID> operationIds = new java.util.HashSet<>();
        for (Entry entry : retained) {
            validate(entry);
            if (!operationIds.add(entry.operationId())) {
                throw new IOException("Configuration operation journal is invalid");
            }
        }
        byte[] bytes = json.writeValueAsBytes(new JournalFile(SCHEMA_VERSION, retained, restartSessions));
        while (bytes.length > MAX_BYTES && retained.size() > 1) {
            int removable = 0;
            for (int index = 0; index < retained.size(); index++) {
                if (!isVoteLogging(retained.get(index))) { removable = index; break; }
            }
            retained.remove(removable);
            bytes = json.writeValueAsBytes(new JournalFile(SCHEMA_VERSION, retained, restartSessions));
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Configuration operation journal exceeds its bound");

        Path temporary = Files.createTempFile(directory, "configuration-operations-", ".temporary");
        try {
            setPermissions(temporary, OWNER_FILE);
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            setPermissions(file, OWNER_FILE);
            DurableFiles.forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Map<String, UUID> validateRestartSessions(Map<String, UUID> sessions) throws IOException {
        if (sessions == null) return Map.of();
        if (sessions.size() > MAX_RESTART_SESSIONS) throw new IOException("Configuration operation journal is invalid");
        LinkedHashMap<String, UUID> validated = new LinkedHashMap<>();
        for (Map.Entry<String, UUID> entry : sessions.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                    || entry.getValue() == null) {
                throw new IOException("Configuration operation journal is invalid");
            }
            validated.put(entry.getKey(), entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(validated);
    }

    private static boolean isVoteLogging(Entry entry) {
        return "APPLY".equals(entry.type()) && "quick-setup".equals(entry.domain())
                && "vote-logging".equals(entry.preset());
    }

    private static void validate(Entry entry) throws IOException {
        if (entry == null || entry.operationId() == null || entry.createdAt() == null
                || !List.of("READ", "PREVIEW", "APPLY").contains(entry.type())
                || !List.of("proxy-routing", "file", "quick-setup").contains(entry.domain())
                || entry.nodes() == null || entry.nodes().isEmpty() || entry.nodes().size() > MAX_NODES) {
            throw new IOException("Configuration operation journal is invalid");
        }
        if ("file".equals(entry.domain())) {
            if (entry.fileName() == null || entry.preset() != null) throw new IOException("Configuration operation journal is invalid");
            try {
                ManagedConfiguration.file(entry.fileName(), null);
            } catch (IllegalArgumentException failure) {
                throw new IOException("Configuration operation journal is invalid", failure);
            }
        } else if ("quick-setup".equals(entry.domain())) {
            if (entry.fileName() != null || entry.preset() == null
                    || !entry.preset().matches("[a-z][a-z0-9-]{0,39}")) {
                throw new IOException("Configuration operation journal is invalid");
            }
        } else if (entry.fileName() != null || entry.preset() != null) {
            throw new IOException("Configuration operation journal is invalid");
        }
        Set<String> nodeIds = new java.util.HashSet<>();
        for (NodeResult node : entry.nodes()) {
            if (node == null || node.nodeId() == null
                    || !node.nodeId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || !nodeIds.add(node.nodeId())) {
                throw new IOException("Configuration operation journal is invalid");
            }
            if (node.complete()) {
                if (node.sessionId() == null || node.success() == null || node.code() == null
                        || !node.code().matches("[A-Z][A-Z0-9_]{0,63}")
                        || Boolean.TRUE.equals(node.success()) && node.revision() == null
                        || node.revision() != null && !node.revision().matches("[0-9a-f]{64}")) {
                    throw new IOException("Configuration operation journal is invalid");
                }
            } else if (node.sessionId() != null || node.success() != null || node.code() != null || node.revision() != null
                    || node.reloaded() || node.rolledBack()) {
                throw new IOException("Configuration operation journal is invalid");
            }
        }
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some network filesystems do not expose POSIX permissions.
        }
    }

    public record Entry(UUID operationId, String type, Instant createdAt, String domain, String fileName,
                        String preset, UUID sourceOperationId, List<NodeResult> nodes) { }

    public record NodeResult(String nodeId, UUID sessionId, boolean complete, Boolean success, String code, String revision,
                             boolean reloaded, boolean rolledBack) { }

    public record State(List<Entry> operations, Map<String, UUID> voteLoggingRestartSessions) { }

    private record JournalFile(int schemaVersion, List<Entry> operations,
                               Map<String, UUID> voteLoggingRestartSessions) { }
}
