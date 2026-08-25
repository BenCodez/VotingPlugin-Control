package com.bencodez.votingplugin.control.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Persistent SHA-256 verifiers for high-entropy enrollment and local admin credentials. */
public final class CredentialStore {
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final int MAX_ENROLLMENTS = 10_000;
    private static final byte[] DUMMY_HASH = new byte[32];

    private final Path directory;
    private final Path file;
    private final Path lockFile;
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final SecureRandom random;

    public CredentialStore(Path directory) throws IOException {
        this(directory, new SecureRandom());
    }

    CredentialStore(Path directory, SecureRandom random) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.file = directory.resolve("credentials.json");
        this.lockFile = directory.resolve("credentials.lock");
        this.random = Objects.requireNonNull(random, "random");
        Files.createDirectories(directory);
    }

    /** Rotates or creates a node credential and returns the secret exactly once. */
    public String rotateNode(String nodeId) throws IOException {
        validateNodeId(nodeId);
        String token = token("vpctl_node_");
        mutate(data -> {
            if (!data.nodeHashes.containsKey(nodeId) && data.nodeHashes.size() >= MAX_ENROLLMENTS) {
                throw new IllegalStateException("Maximum enrollment count reached");
            }
            data.nodeHashes.put(nodeId, hashHex(token));
        });
        return token;
    }

    public void revokeNode(String nodeId) throws IOException {
        validateNodeId(nodeId);
        mutate(data -> data.nodeHashes.remove(nodeId));
    }

    /** Rotates or creates the local management API credential and returns it exactly once. */
    public String rotateAdmin() throws IOException {
        String token = token("vpctl_admin_");
        mutate(data -> data.adminHash = hashHex(token));
        return token;
    }

    public boolean hasAdmin() throws IOException {
        String hash = read().adminHash;
        return hash != null && !hash.isBlank();
    }

    public boolean verifyNode(String nodeId, String token) {
        String expected = null;
        try {
            if (nodeId != null && NODE_ID.matcher(nodeId).matches()) {
                expected = read().nodeHashes.get(nodeId);
            }
        } catch (IOException ignored) {
            // Authentication remains closed if the verifier file cannot be read.
        }
        return constantTimeMatches(expected, token);
    }

    public boolean verifyAdmin(String token) {
        String expected = null;
        try {
            expected = read().adminHash;
        } catch (IOException ignored) {
            // Authentication remains closed if the verifier file cannot be read.
        }
        return constantTimeMatches(expected, token);
    }

    private boolean constantTimeMatches(String expectedHex, String token) {
        byte[] expected = DUMMY_HASH;
        if (expectedHex != null) {
            try {
                expected = HexFormat.of().parseHex(expectedHex);
            } catch (IllegalArgumentException ignored) {
                expected = DUMMY_HASH;
            }
        }
        byte[] actual = token == null ? DUMMY_HASH : hash(token);
        boolean equal = MessageDigest.isEqual(expected, actual);
        return expectedHex != null && token != null && equal;
    }

    private synchronized StoreData read() throws IOException {
        if (!Files.exists(file)) {
            return new StoreData();
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > 2 * 1024 * 1024) {
            throw new IOException("Credential store is too large");
        }
        try {
            StoreData data = json.readValue(bytes, StoreData.class);
            if (data == null) {
                throw new IOException("Credential store is invalid");
            }
            if (data.nodeHashes == null) {
                data.nodeHashes = new HashMap<>();
            }
            return data;
        } catch (RuntimeException e) {
            throw new IOException("Credential store is invalid");
        }
    }

    private synchronized void mutate(Mutation mutation) throws IOException {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            StoreData data = read();
            mutation.apply(data);
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
            Path temporary = Files.createTempFile(directory, "credentials-", ".tmp");
            try {
                Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private String token(String prefix) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashHex(String token) {
        return HexFormat.of().formatHex(hash(token));
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static void validateNodeId(String nodeId) {
        if (nodeId == null || !NODE_ID.matcher(nodeId).matches()) {
            throw new IllegalArgumentException("nodeId must match " + NODE_ID.pattern());
        }
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(StoreData data);
    }

    private static final class StoreData {
        public String adminHash;
        public Map<String, String> nodeHashes = new HashMap<>();
    }
}
