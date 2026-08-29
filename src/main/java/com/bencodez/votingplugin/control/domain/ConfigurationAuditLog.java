package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.DurableFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Local append-only, hash-chained operation metadata. Values and approval tokens are never recorded. */
public final class ConfigurationAuditLog implements AutoCloseable {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_RECORD_BYTES = 16 * 1024;
    private final Path file;
    private final Path previousFile;
    private final Path checkpointFile;
    private final Path pendingFile;
    private final FileChannel lockChannel;
    private final FileLock processLock;
    private final Clock clock;
    private final long maxBytes;
    private final ObjectMapper json = new ObjectMapper();
    private String previousHash = "GENESIS";
    private long activeRecords;
    private String retainedHash = "GENESIS";
    private long retainedRecords;
    private long activeBytes;
    private long retainedBytes;

    public ConfigurationAuditLog(Path dataDirectory, Clock clock) throws IOException {
        this(dataDirectory, clock, MAX_BYTES);
    }

    ConfigurationAuditLog(Path dataDirectory, Clock clock, long maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        Files.createDirectories(dataDirectory);
        this.file = dataDirectory.resolve("configuration-audit.jsonl");
        this.previousFile = dataDirectory.resolve("configuration-audit.jsonl.1");
        this.checkpointFile = dataDirectory.resolve("configuration-audit.checkpoint");
        this.pendingFile = dataDirectory.resolve("configuration-audit.pending");
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
            recoverPending();
            SegmentState retained = validateOptional(previousFile, "Retained configuration audit log");
            SegmentState active = validateOptional(file, "Configuration audit log");
            retainedHash = retained.tailHash();
            retainedRecords = retained.records();
            retainedBytes = retained.bytes();
            previousHash = active.tailHash();
            activeRecords = active.records();
            activeBytes = active.bytes();
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
            // A prior append may have changed a segment before checkpoint publication failed.
            // Never replace its only durable recovery description with a new transaction.
            if (Files.exists(pendingFile, LinkOption.NOFOLLOW_LINKS)) recoverPending();
            validateExpectedTail(previousFile, retainedHash, retainedRecords, retainedBytes,
                    "Retained configuration audit log");
            validateExpectedTail(file, previousHash, activeRecords, activeBytes,
                    "Configuration audit log");
            boolean rotate = activeBytes >= maxBytes;
            String recordPrevious = rotate ? "GENESIS" : previousHash;
            Map<String, Object> core = new LinkedHashMap<>();
            core.put("time", clock.instant().toString());
            core.put("action", action);
            core.put("operationId", operationId == null ? null : operationId.toString());
            core.put("nodeId", nodeId);
            core.put("outcome", outcome);
            core.put("previousHash", recordPrevious);
            byte[] canonical = json.writeValueAsBytes(core);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
            core.put("hash", hash);
            String line = json.writeValueAsString(core) + System.lineSeparator();
            PendingAppend pending = new PendingAppend(line, rotate, previousHash, activeRecords, retainedHash,
                    retainedRecords, activeBytes, hash, rotate ? 1 : activeRecords + 1,
                    rotate ? previousHash : retainedHash, rotate ? activeRecords : retainedRecords);
            writePending(pending);
            completeFreshPending(pending);
        } catch (Exception e) {
            throw new AuditException(e);
        }
    }

    private void recoverPending() throws IOException {
        if (!Files.exists(pendingFile, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(pendingFile, LinkOption.NOFOLLOW_LINKS) || Files.size(pendingFile) > 16384) {
            throw new IOException("Configuration audit pending transaction is invalid");
        }
        try {
            JsonNode stored = json.readTree(readBounded(pendingFile, 16384,
                    "Configuration audit pending transaction is invalid"));
            if (stored == null || !stored.isObject() || stored.size() != 11
                    || !stored.path("rotate").isBoolean()
                    || !integral(stored, "preActiveRecords") || !integral(stored, "preRetainedRecords")
                    || !integral(stored, "preActiveBytes")
                    || !integral(stored, "postActiveRecords") || !integral(stored, "postRetainedRecords")) {
                throw new IOException("Configuration audit pending transaction is invalid");
            }
            completePending(new PendingAppend(stored.path("line").asText(), stored.path("rotate").asBoolean(),
                    stored.path("preActiveHash").asText(), stored.path("preActiveRecords").asLong(),
                    stored.path("preRetainedHash").asText(), stored.path("preRetainedRecords").asLong(),
                    stored.path("preActiveBytes").asLong(),
                    stored.path("postActiveHash").asText(), stored.path("postActiveRecords").asLong(),
                    stored.path("postRetainedHash").asText(), stored.path("postRetainedRecords").asLong()));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Configuration audit pending transaction is invalid", e);
        }
    }

    private void completePending(PendingAppend pending) throws IOException {
        SegmentState retained = validateOptional(previousFile, "Retained configuration audit log");
        SegmentState active;
        try {
            active = validateOptional(file, "Configuration audit log");
        } catch (IOException invalidActive) {
            active = repairTornActive(pending, retained, invalidActive);
        }
        if (matches(active, pending.postActiveHash(), pending.postActiveRecords())
                && matches(retained, pending.postRetainedHash(), pending.postRetainedRecords())
                && !hasCompletePendingBytes(pending)) {
            active = repairTornActive(pending, retained,
                    new IOException("Configuration audit pending record is incomplete"));
        }
        if (matches(active, pending.postActiveHash(), pending.postActiveRecords())
                && matches(retained, pending.postRetainedHash(), pending.postRetainedRecords())) {
            // The record is durable; only checkpoint publication or cleanup was interrupted.
        } else if (!pending.rotate() && matches(active, pending.preActiveHash(), pending.preActiveRecords())
                && matches(retained, pending.preRetainedHash(), pending.preRetainedRecords())) {
            appendDurably(pending.line());
        } else if (pending.rotate() && matches(active, pending.preActiveHash(), pending.preActiveRecords())
                && matches(retained, pending.preRetainedHash(), pending.preRetainedRecords())) {
            moveReplacing(file, previousFile);
            appendDurably(pending.line());
        } else if (pending.rotate() && !active.present()
                && matches(retained, pending.postRetainedHash(), pending.postRetainedRecords())) {
            appendDurably(pending.line());
        } else {
            throw new IOException("Configuration audit pending transaction does not match the audit chain");
        }
        applyPendingState(pending);
        writeCheckpoint();
        deleteDurably(pendingFile);
    }

    private void completeFreshPending(PendingAppend pending) throws IOException {
        if (pending.rotate()) moveReplacing(file, previousFile);
        appendDurably(pending.line());
        applyPendingState(pending);
        writeCheckpoint();
        deleteDurably(pendingFile);
    }

    private void applyPendingState(PendingAppend pending) {
        int appendedBytes = pending.line().getBytes(StandardCharsets.UTF_8).length;
        retainedHash = pending.postRetainedHash();
        retainedRecords = pending.postRetainedRecords();
        retainedBytes = pending.rotate() ? pending.preActiveBytes() : retainedBytes;
        previousHash = pending.postActiveHash();
        activeRecords = pending.postActiveRecords();
        activeBytes = (pending.rotate() ? 0 : pending.preActiveBytes()) + appendedBytes;
    }

    private SegmentState repairTornActive(PendingAppend pending, SegmentState retained,
                                          IOException invalidActive) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw invalidActive;
        byte[] intended = pending.line().getBytes(StandardCharsets.UTF_8);
        long baseLong = pending.rotate() ? 0 : pending.preActiveBytes();
        long maximumRepairBytes = Math.min(maxBytes, MAX_BYTES) + 64 * 1024;
        if (baseLong > Integer.MAX_VALUE - intended.length
                || baseLong + intended.length > maximumRepairBytes) throw invalidActive;
        int exclusiveLimit = (int) baseLong + intended.length;
        ByteBuffer buffer = ByteBuffer.allocate(exclusiveLimit);
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (channel.read(buffer) >= 0 && buffer.hasRemaining()) { }
        }
        if (!buffer.hasRemaining()) throw invalidActive;
        buffer.flip();
        byte[] actual = new byte[buffer.remaining()];
        buffer.get(actual);
        if (actual.length <= baseLong) throw invalidActive;
        int base = (int) baseLong;
        if (pending.rotate()) {
            if (!matches(retained, pending.postRetainedHash(), pending.postRetainedRecords())) throw invalidActive;
        } else {
            SegmentState prefix = validateBytes(java.util.Arrays.copyOf(actual, base));
            if (!matches(prefix, pending.preActiveHash(), pending.preActiveRecords())
                    || !matches(retained, pending.preRetainedHash(), pending.preRetainedRecords())) {
                throw invalidActive;
            }
        }
        for (int index = base; index < actual.length; index++) {
            if (actual[index] != intended[index - base]) throw invalidActive;
        }
        if (base == 0) {
            deleteDurably(file);
        } else {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.truncate(base);
                channel.force(true);
            }
        }
        return validateOptional(file, "Configuration audit log");
    }

    private boolean hasCompletePendingBytes(PendingAppend pending) throws IOException {
        long base = pending.rotate() ? 0 : pending.preActiveBytes();
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && Files.size(file) == base + pending.line().getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean matches(SegmentState state, String hash, long records) {
        return state.tailHash().equals(hash) && state.records() == records
                && (records > 0 ? state.present() : !state.present() || "GENESIS".equals(hash));
    }

    private void appendDurably(String line) throws IOException {
        boolean existed = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
        if (!existed) forceDirectory(file.getParent());
    }

    private void writePending(PendingAppend pending) throws IOException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("line", pending.line());
        value.put("rotate", pending.rotate());
        value.put("preActiveHash", pending.preActiveHash());
        value.put("preActiveRecords", pending.preActiveRecords());
        value.put("preRetainedHash", pending.preRetainedHash());
        value.put("preRetainedRecords", pending.preRetainedRecords());
        value.put("preActiveBytes", pending.preActiveBytes());
        value.put("postActiveHash", pending.postActiveHash());
        value.put("postActiveRecords", pending.postActiveRecords());
        value.put("postRetainedHash", pending.postRetainedHash());
        value.put("postRetainedRecords", pending.postRetainedRecords());
        writeAtomically(pendingFile, json.writeValueAsBytes(value));
    }

    private static boolean integral(JsonNode object, String name) {
        return object.path(name).isIntegralNumber() && object.path(name).canConvertToLong()
                && object.path(name).asLong() >= 0;
    }

    private SegmentState validateOptional(Path selected, String label) throws IOException {
        if (!Files.exists(selected, LinkOption.NOFOLLOW_LINKS)) return new SegmentState(false, "GENESIS", 0, 0);
        if (!Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS))
            throw new IOException(label + " is not a regular file");
        long maximumSegmentBytes = Math.min(maxBytes, MAX_BYTES) + 64 * 1024;
        if (Files.size(selected) > maximumSegmentBytes)
            throw new IOException(label + " exceeds its bounded size");
        return validateBytes(readBounded(selected, maximumSegmentBytes, label + " exceeds its bounded size"));
    }

    private static byte[] readBounded(Path selected, long maximumBytes, String failure) throws IOException {
        if (maximumBytes < 0 || maximumBytes >= Integer.MAX_VALUE) throw new IOException(failure);
        ByteBuffer buffer = ByteBuffer.allocate((int) maximumBytes + 1);
        try (SeekableByteChannel channel = Files.newByteChannel(selected,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (channel.read(buffer) >= 0 && buffer.hasRemaining()) { }
        }
        if (!buffer.hasRemaining()) throw new IOException(failure);
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private SegmentState validateBytes(byte[] bytes) throws IOException {
        if (bytes.length > 0 && bytes[bytes.length - 1] != '\n') {
            throw new IOException("Configuration audit segment is missing its terminal separator");
        }
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return validateLines(content.lines().iterator(), bytes.length);
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IOException("Configuration audit chain is invalid", e);
        }
    }

    private SegmentState validateLines(Iterator<String> lines, long bytes) throws IOException {
        String expectedPrevious = "GENESIS";
        long records = 0;
        while (lines.hasNext()) {
            String line = lines.next();
            if (line.isBlank()) continue;
            AuditRecord record = validateRecord(line);
            if (!expectedPrevious.equals(record.previousHash())) {
                throw new IOException("Configuration audit chain is invalid");
            }
            expectedPrevious = record.hash();
            records++;
        }
        return new SegmentState(true, expectedPrevious, records, bytes);
    }

    private AuditRecord validateRecord(String line) throws IOException {
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
            if (!calculated.equals(stored.path("hash").asText())) {
                throw new IOException("Configuration audit chain is invalid");
            }
            return new AuditRecord(stored.path("previousHash").asText(), calculated);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Configuration audit chain is invalid", e);
        }
    }

    private void validateExpectedTail(Path selected, String expectedHash, long expectedRecords,
                                      long expectedBytes, String label) throws IOException {
        if (!Files.exists(selected, LinkOption.NOFOLLOW_LINKS)) {
            if (expectedBytes == 0 && expectedRecords == 0 && "GENESIS".equals(expectedHash)) return;
            throw new IOException(label + " no longer matches its validated state");
        }
        if (!Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a regular file");
        }
        try (SeekableByteChannel channel = Files.newByteChannel(selected,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            long size = channel.size();
            if (size != expectedBytes || size > Math.min(maxBytes, MAX_BYTES) + 64 * 1024) {
                throw new IOException(label + " no longer matches its validated state");
            }
            if (expectedRecords == 0) return;
            int window = (int) Math.min(size, MAX_RECORD_BYTES + 2L);
            ByteBuffer buffer = ByteBuffer.allocate(window);
            channel.position(size - window);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            if (channel.size() != size || buffer.position() != window) {
                throw new IOException(label + " changed during validation");
            }
            byte[] tail = buffer.array();
            if (tail.length == 0 || tail[tail.length - 1] != '\n') {
                throw new IOException(label + " is missing its terminal separator");
            }
            int previousSeparator = tail.length - 2;
            while (previousSeparator >= 0 && tail[previousSeparator] != '\n') previousSeparator--;
            if (previousSeparator < 0 && size > tail.length) {
                throw new IOException(label + " has an oversized final record");
            }
            int start = previousSeparator + 1;
            int length = tail.length - start - 1;
            if (length <= 0 || length > MAX_RECORD_BYTES) {
                throw new IOException(label + " has an invalid final record");
            }
            String line = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(tail, start, length)).toString();
            if (!expectedHash.equals(validateRecord(line).hash())) {
                throw new IOException(label + " no longer matches its validated state");
            }
        }
    }

    private void validateCheckpoint() throws IOException {
        if (!Files.isRegularFile(checkpointFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(checkpointFile) > 4096) {
            throw new IOException("Configuration audit checkpoint is missing or invalid");
        }
        try {
            JsonNode checkpoint = json.readTree(readBounded(checkpointFile, 4096,
                    "Configuration audit checkpoint is missing or invalid"));
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
        writeAtomically(checkpointFile, json.writeValueAsBytes(checkpoint));
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "configuration-audit-", ".temporary");
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary,
                    StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        forceDirectory(target.getParent());
        if (!source.getParent().equals(target.getParent())) forceDirectory(source.getParent());
    }

    private static void deleteDurably(Path target) throws IOException {
        if (Files.deleteIfExists(target)) forceDirectory(target.getParent());
    }

    private static void forceDirectory(Path directory) throws IOException {
        DurableFiles.forceDirectory(directory);
    }

    @Override
    public synchronized void close() throws IOException {
        processLock.close();
        lockChannel.close();
    }

    private record SegmentState(boolean present, String tailHash, long records, long bytes) { }
    private record AuditRecord(String previousHash, String hash) { }
    private record PendingAppend(String line, boolean rotate, String preActiveHash, long preActiveRecords,
                                 String preRetainedHash, long preRetainedRecords, long preActiveBytes,
                                 String postActiveHash,
                                 long postActiveRecords, String postRetainedHash, long postRetainedRecords) { }

    @SuppressWarnings("serial")
    public static final class AuditException extends RuntimeException {
        private AuditException(Throwable cause) { super(cause); }
    }
}
