package com.bencodez.votingplugin.control.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Local append-only, hash-chained operation metadata. Values and approval tokens are never recorded. */
public final class ConfigurationAuditLog implements AutoCloseable {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path file;
    private final Path previousFile;
    private final Path checkpointFile;
    private final FileChannel lockChannel;
    private final FileLock processLock;
    private final Clock clock;
    private final long maxBytes;
    private final ObjectMapper json = new ObjectMapper();
    private String previousHash = "GENESIS";
    private long activeRecords;
    private String retainedHash = "GENESIS";
    private long retainedRecords;

    public ConfigurationAuditLog(Path dataDirectory, Clock clock) throws IOException {
        this(dataDirectory, clock, MAX_BYTES);
    }

    ConfigurationAuditLog(Path dataDirectory, Clock clock, long maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        Files.createDirectories(dataDirectory);
        this.file = dataDirectory.resolve("configuration-audit.jsonl");
        this.previousFile = dataDirectory.resolve("configuration-audit.jsonl.1");
        this.checkpointFile = dataDirectory.resolve("configuration-audit.checkpoint");
        this.clock = clock;
        this.maxBytes = maxBytes;
        FileChannel acquiredChannel = FileChannel.open(dataDirectory.resolve("configuration-audit.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquiredLock = null;
        try {
            acquiredLock = acquiredChannel.tryLock();
            if (acquiredLock == null) throw new IOException("Configuration audit is already in use");
            this.lockChannel = acquiredChannel;
            this.processLock = acquiredLock;
            SegmentState retained = validateOptional(previousFile, "Retained configuration audit log");
            SegmentState active = validateOptional(file, "Configuration audit log");
            retainedHash = retained.tailHash();
            retainedRecords = retained.records();
            previousHash = active.tailHash();
            activeRecords = active.records();
            boolean hasSegments = retained.present() || active.present();
            if (hasSegments) {
                validateCheckpoint();
            } else if (Files.exists(checkpointFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Configuration audit checkpoint has no audit segments");
            }
        } catch (Exception e) {
            if (acquiredLock != null) acquiredLock.close();
            acquiredChannel.close();
            if (e instanceof IOException io) throw io;
            throw new IOException("Configuration audit is already in use", e);
        }
    }

    public synchronized void append(String action, UUID operationId, String nodeId, String outcome) {
        try {
            rotateIfNeeded();
            Map<String, Object> core = new LinkedHashMap<>();
            core.put("time", clock.instant().toString());
            core.put("action", action);
            core.put("operationId", operationId == null ? null : operationId.toString());
            core.put("nodeId", nodeId);
            core.put("outcome", outcome);
            core.put("previousHash", previousHash);
            byte[] canonical = json.writeValueAsBytes(core);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
            core.put("hash", hash);
            Files.writeString(file, json.writeValueAsString(core) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            previousHash = hash;
            activeRecords++;
            writeCheckpoint();
        } catch (Exception e) {
            throw new AuditException(e);
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.exists(file) && Files.size(file) >= maxBytes) {
            Files.move(file, previousFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            retainedHash = previousHash;
            retainedRecords = activeRecords;
            previousHash = "GENESIS";
            activeRecords = 0;
        }
    }

    private SegmentState validateOptional(Path selected, String label) throws IOException {
        if (!Files.exists(selected, LinkOption.NOFOLLOW_LINKS)) return new SegmentState(false, "GENESIS", 0);
        if (!Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS))
            throw new IOException(label + " is not a regular file");
        if (Files.size(selected) > maxBytes + 64 * 1024)
            throw new IOException(label + " exceeds its bounded size");
        return validateExisting(selected);
    }

    private SegmentState validateExisting(Path selected) throws IOException {
        String expectedPrevious = "GENESIS";
        long records = 0;
        for (String line : Files.readAllLines(selected, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            try {
                JsonNode stored = json.readTree(line);
                if (stored == null || !stored.isObject() || stored.size() != 7
                        || !stored.has("time") || !stored.has("action") || !stored.has("operationId")
                        || !stored.has("nodeId") || !stored.has("outcome") || !stored.has("previousHash")
                        || !stored.has("hash")) {
                    throw new IOException("Configuration audit chain is invalid");
                }
                Map<String, Object> core = new LinkedHashMap<>();
                core.put("time", stored.path("time").asText());
                core.put("action", stored.path("action").asText());
                core.put("operationId", stored.path("operationId").isNull() ? null : stored.path("operationId").asText());
                core.put("nodeId", stored.path("nodeId").isNull() ? null : stored.path("nodeId").asText());
                core.put("outcome", stored.path("outcome").isNull() ? null : stored.path("outcome").asText());
                core.put("previousHash", stored.path("previousHash").asText());
                String calculated = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(json.writeValueAsBytes(core)));
                if (!expectedPrevious.equals(core.get("previousHash")) || !calculated.equals(stored.path("hash").asText())) {
                    throw new IOException("Configuration audit chain is invalid");
                }
                expectedPrevious = calculated;
                records++;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Configuration audit chain is invalid", e);
            }
        }
        return new SegmentState(true, expectedPrevious, records);
    }

    private void validateCheckpoint() throws IOException {
        if (!Files.isRegularFile(checkpointFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(checkpointFile) > 4096) {
            throw new IOException("Configuration audit checkpoint is missing or invalid");
        }
        try {
            JsonNode checkpoint = json.readTree(Files.readAllBytes(checkpointFile));
            if (checkpoint == null || !checkpoint.isObject() || checkpoint.size() != 4
                    || !checkpoint.path("activeHash").asText().equals(previousHash)
                    || !checkpoint.path("retainedHash").asText().equals(retainedHash)
                    || !checkpoint.path("activeRecords").isIntegralNumber()
                    || !checkpoint.path("retainedRecords").isIntegralNumber()
                    || !checkpoint.path("activeRecords").canConvertToLong()
                    || !checkpoint.path("retainedRecords").canConvertToLong()
                    || checkpoint.path("activeRecords").asLong() != activeRecords
                    || checkpoint.path("retainedRecords").asLong() != retainedRecords) {
                throw new IOException("Configuration audit checkpoint does not match the audit chain");
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Configuration audit checkpoint is invalid", e);
        }
    }

    private void writeCheckpoint() throws IOException {
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("activeHash", previousHash);
        checkpoint.put("activeRecords", activeRecords);
        checkpoint.put("retainedHash", retainedHash);
        checkpoint.put("retainedRecords", retainedRecords);
        Path temporary = Files.createTempFile(checkpointFile.getParent(), "configuration-audit-", ".checkpoint");
        try {
            Files.write(temporary, json.writeValueAsBytes(checkpoint), StandardOpenOption.TRUNCATE_EXISTING);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary,
                    StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, checkpointFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, checkpointFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        processLock.close();
        lockChannel.close();
    }

    private record SegmentState(boolean present, String tailHash, long records) { }

    @SuppressWarnings("serial")
    public static final class AuditException extends RuntimeException {
        private AuditException(Throwable cause) { super(cause); }
    }
}
