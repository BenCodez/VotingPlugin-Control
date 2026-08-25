package com.bencodez.votingplugin.control.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Local append-only, hash-chained operation metadata. Values and approval tokens are never recorded. */
public final class ConfigurationAuditLog {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path file;
    private final Path previousFile;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private String previousHash = "GENESIS";

    public ConfigurationAuditLog(Path dataDirectory, Clock clock) throws IOException {
        Files.createDirectories(dataDirectory);
        this.file = dataDirectory.resolve("configuration-audit.jsonl");
        this.previousFile = dataDirectory.resolve("configuration-audit.jsonl.1");
        this.clock = clock;
        validateRetainedFile(previousFile);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Configuration audit log is not a regular file");
            if (Files.size(file) > MAX_BYTES + 64 * 1024)
                throw new IOException("Configuration audit log exceeds its bounded size");
            previousHash = validateExisting(file);
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
        } catch (Exception e) {
            throw new AuditException(e);
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.exists(file) && Files.size(file) >= MAX_BYTES) {
            Files.move(file, previousFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            previousHash = "GENESIS";
        }
    }

    private void validateRetainedFile(Path retained) throws IOException {
        if (!Files.exists(retained, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(retained, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Retained configuration audit log is not a regular file");
        if (Files.size(retained) > MAX_BYTES + 64 * 1024)
            throw new IOException("Retained configuration audit log exceeds its bounded size");
        validateExisting(retained);
    }

    private String validateExisting(Path selected) throws IOException {
        String expectedPrevious = "GENESIS";
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
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Configuration audit chain is invalid", e);
            }
        }
        return expectedPrevious;
    }

    @SuppressWarnings("serial")
    public static final class AuditException extends RuntimeException {
        private AuditException(Throwable cause) { super(cause); }
    }
}
