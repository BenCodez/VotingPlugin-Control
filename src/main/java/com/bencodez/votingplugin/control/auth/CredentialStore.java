package com.bencodez.votingplugin.control.auth;

import com.bencodez.votingplugin.control.DurableFiles;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Persistent token verifiers and a salted PBKDF2 WebUI password verifier. */
public final class CredentialStore {
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final int MAX_ENROLLMENTS = 10_000;
    private static final int MAX_STORE_BYTES = 2 * 1024 * 1024;
    private static final int WEB_PASSWORD_ITERATIONS = 600_000;
    private static final int WEB_PASSWORD_BYTES = 32;
    private static final int WEB_PASSWORD_MINIMUM = 12;
    private static final int MAX_SETUP_CODE_BYTES = 128;
    private static final String WEB_SETUP_CODE_FILE = "web-setup-code.txt";
    private static final byte[] DUMMY_PASSWORD_SALT = new byte[16];
    private static final byte[] DUMMY_PASSWORD_HASH = new byte[WEB_PASSWORD_BYTES];
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

    /** Replaces the WebUI password verifier. The supplied password is never persisted. */
    public void setWebPassword(char[] password) throws IOException {
        validateWebPassword(password);
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] verifier = derivePassword(password, salt, WEB_PASSWORD_ITERATIONS);
        mutate(data -> {
            data.webPasswordSalt = Base64.getEncoder().encodeToString(salt);
            data.webPasswordHash = Base64.getEncoder().encodeToString(verifier);
            data.webPasswordIterations = WEB_PASSWORD_ITERATIONS;
            data.webSetupHash = null;
        });
        Files.deleteIfExists(setupCodeFile());
    }

    public boolean hasWebPassword() throws IOException {
        return passwordRevision(read()) != null;
    }

    /**
     * Creates a one-time first-run setup code when no WebUI password exists.
     * The raw code is stored only in a permission-restricted file for the server
     * owner; credentials.json retains only its SHA-256 verifier.
     */
    public synchronized Path ensureWebSetupCode() throws IOException {
        Path setupFile = setupCodeFile();
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            StoreData current = read();
            if (passwordRevision(current) != null) {
                Files.deleteIfExists(setupFile);
                return null;
            }

            String existing = readSetupCode(setupFile);
            if (existing != null && constantTimeMatches(current.webSetupHash, existing)) {
                return setupFile;
            }

            String code = token("vpctl_setup_");
            current.webSetupHash = hashHex(code);
            writeStore(current);
            writeSetupCode(setupFile, code);
            return setupFile;
        }
    }

    /**
     * Atomically consumes the first-run setup code and installs the WebUI password.
     * Returns the exact verifier revision installed, or {@code null} when the code
     * was invalid or setup had already completed.
     */
    public String completeWebSetup(String setupCode, char[] password) throws IOException {
        String boundedCode = setupCode != null && setupCode.length() <= MAX_SETUP_CODE_BYTES ? setupCode : null;
        StoreData current = read();
        if (passwordRevision(current) != null || !constantTimeMatches(current.webSetupHash, boundedCode)) {
            return null;
        }

        validateWebPassword(password);
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] verifier = derivePassword(password, salt, WEB_PASSWORD_ITERATIONS);
        String[] installedRevision = {null};
        mutate(data -> {
            if (passwordRevision(data) == null && constantTimeMatches(data.webSetupHash, boundedCode)) {
                data.webPasswordSalt = Base64.getEncoder().encodeToString(salt);
                data.webPasswordHash = Base64.getEncoder().encodeToString(verifier);
                data.webPasswordIterations = WEB_PASSWORD_ITERATIONS;
                data.webSetupHash = null;
                installedRevision[0] = passwordRevision(data);
            }
        });
        if (installedRevision[0] != null) {
            Files.deleteIfExists(setupCodeFile());
        }
        return installedRevision[0];
    }

    public java.util.List<String> enrolledNodeIds() throws IOException {
        return read().nodeHashes.keySet().stream().sorted().toList();
    }

    /** Opaque verifier revision used to invalidate sessions immediately after password rotation. */
    public String webPasswordRevision() {
        try {
            StoreData data = read();
            return passwordRevision(data);
        } catch (IOException e) {
            return null;
        }
    }

    /** Performs the same bounded password derivation even when the verifier is absent or malformed. */
    public boolean verifyWebPassword(String password) {
        return authenticateWebPassword(password) != null;
    }

    /** Returns an opaque verifier revision only when the supplied password is valid. */
    public String authenticateWebPassword(String password) {
        byte[] salt = DUMMY_PASSWORD_SALT;
        byte[] expected = DUMMY_PASSWORD_HASH;
        int iterations = WEB_PASSWORD_ITERATIONS;
        boolean configured = false;
        String revision = null;
        try {
            StoreData data = read();
            if (data.webPasswordSalt != null && data.webPasswordHash != null
                    && data.webPasswordIterations >= 100_000 && data.webPasswordIterations <= 1_000_000) {
                byte[] parsedSalt = Base64.getDecoder().decode(data.webPasswordSalt);
                byte[] parsedHash = Base64.getDecoder().decode(data.webPasswordHash);
                if (parsedSalt.length == 16 && parsedHash.length == WEB_PASSWORD_BYTES) {
                    salt = parsedSalt;
                    expected = parsedHash;
                    iterations = data.webPasswordIterations;
                    configured = true;
                    revision = passwordRevision(data);
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Authentication remains closed if the verifier cannot be read.
        }
        char[] supplied = password == null ? new char[0] : password.toCharArray();
        try {
            byte[] actual = derivePassword(supplied, salt, iterations);
            return configured && password != null && MessageDigest.isEqual(expected, actual) ? revision : null;
        } finally {
            java.util.Arrays.fill(supplied, '\0');
        }
    }

    private static String passwordRevision(StoreData data) {
        try {
            if (data.webPasswordSalt == null || data.webPasswordHash == null
                    || data.webPasswordIterations < 100_000 || data.webPasswordIterations > 1_000_000
                    || Base64.getDecoder().decode(data.webPasswordSalt).length != 16
                    || Base64.getDecoder().decode(data.webPasswordHash).length != WEB_PASSWORD_BYTES) {
                return null;
            }
            return hashHex(data.webPasswordSalt + ":" + data.webPasswordHash + ":" + data.webPasswordIterations);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return new StoreData();
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Credential store is unsafe");
        }
        long checkedSize = Files.size(file);
        if (checkedSize > MAX_STORE_BYTES) {
            throw new IOException("Credential store is too large");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) checkedSize + 1);
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (channel.read(buffer) >= 0 && buffer.hasRemaining()) { }
        }
        if (!buffer.hasRemaining()) throw new IOException("Credential store changed while it was read");
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
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
            writeStore(data);
        }
    }

    private void writeStore(StoreData data) throws IOException {
        byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        Path temporary = Files.createTempFile(directory, "credentials-", ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel temporaryChannel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                temporaryChannel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            DurableFiles.forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path setupCodeFile() {
        return directory.resolve(WEB_SETUP_CODE_FILE);
    }

    private String readSetupCode(Path setupFile) throws IOException {
        if (!Files.exists(setupFile, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(setupFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(setupFile)
                || Files.size(setupFile) > MAX_SETUP_CODE_BYTES) {
            throw new IOException("Web setup code file is unsafe");
        }
        ByteBuffer buffer = ByteBuffer.allocate(MAX_SETUP_CODE_BYTES + 1);
        try (SeekableByteChannel channel = Files.newByteChannel(setupFile,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (channel.read(buffer) >= 0 && buffer.hasRemaining()) { }
        }
        if (!buffer.hasRemaining()) {
            throw new IOException("Web setup code file changed while it was read");
        }
        buffer.flip();
        String value = StandardCharsets.UTF_8.decode(buffer).toString().trim();
        return value.isEmpty() ? null : value;
    }

    private void writeSetupCode(Path setupFile, String code) throws IOException {
        Path temporary = Files.createTempFile(directory, "web-setup-", ".tmp");
        try {
            try {
                Files.setPosixFilePermissions(temporary, Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // The platform does not expose POSIX permissions.
            }
            Files.writeString(temporary, code + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, setupFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, setupFile, StandardCopyOption.REPLACE_EXISTING);
            }
            DurableFiles.forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
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

    private static byte[] derivePassword(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, WEB_PASSWORD_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static void validateWebPassword(char[] password) {
        if (password == null || password.length < WEB_PASSWORD_MINIMUM || password.length > 256) {
            throw new IllegalArgumentException("WebUI password must contain 12 to 256 characters");
        }
        for (char value : password) {
            if (Character.isISOControl(value)) {
                throw new IllegalArgumentException("WebUI password cannot contain control characters");
            }
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
        public String webPasswordSalt;
        public String webPasswordHash;
        public int webPasswordIterations;
        public String webSetupHash;
        public Map<String, String> nodeHashes = new HashMap<>();
    }
}
