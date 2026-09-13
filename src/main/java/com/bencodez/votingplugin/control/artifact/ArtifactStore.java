package com.bencodez.votingplugin.control.artifact;

import com.bencodez.votingplugin.control.DurableFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Private, content-addressed staging for administrator-supplied VotingPlugin JARs.
 *
 * <p>This deliberately has no HTTP knowledge: callers receive only an opaque SHA-256
 * identifier and must separately authorize any deployment operation.</p>
 */
public final class ArtifactStore {
    public static final long MAX_UPLOAD_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_STORED_BYTES = 512L * 1024L * 1024L;
    public static final int MAX_STORED_ARTIFACTS = 32;
    private static final int MAX_ENTRY_COUNT = 8_192;
    private static final long MAX_ENTRY_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_COMPRESSION_RATIO = 200L;
    private static final int MAX_PLUGIN_YML_BYTES = 64 * 1024;
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path directory;
    private final long maximumStoredBytes;
    private final int maximumStoredArtifacts;
    private final IoAction afterPublishMove;

    /** Creates or opens an empty private directory owned by Control. */
    public ArtifactStore(Path directory) throws IOException {
        this(directory, MAX_STORED_BYTES, MAX_STORED_ARTIFACTS);
    }

    ArtifactStore(Path directory, long maximumStoredBytes, int maximumStoredArtifacts) throws IOException {
        this(directory, maximumStoredBytes, maximumStoredArtifacts, path -> { });
    }

    ArtifactStore(Path directory, long maximumStoredBytes, int maximumStoredArtifacts,
                  IoAction afterPublishMove) throws IOException {
        if (directory == null) throw rejected();
        if (maximumStoredBytes < 1 || maximumStoredArtifacts < 1 || afterPublishMove == null) throw rejected();
        this.directory = directory.toAbsolutePath().normalize();
        this.maximumStoredBytes = maximumStoredBytes;
        this.maximumStoredArtifacts = maximumStoredArtifacts;
        this.afterPublishMove = afterPublishMove;
        try {
            createPrivateDirectory(this.directory);
            removeIncompleteUploads();
        } catch (ArtifactException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw rejected();
        }
    }

    private void removeIncompleteUploads() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.matches("evict-[0-9a-f]{64}\\.part")) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                        throw rejected();
                    }
                    String artifactId = name.substring("evict-".length(), name.length() - ".part".length());
                    verifyExistingArtifact(file, artifactId);
                    Path original = artifactPath(artifactId);
                    if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                        verifyExistingArtifact(original, artifactId);
                        Files.delete(file);
                    } else {
                        move(file, original, false);
                    }
                    continue;
                }
                if (!name.startsWith("upload-") || !name.endsWith(".part")) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                    throw rejected();
                }
                Files.delete(file);
            }
        }
        DurableFiles.forceDirectory(directory);
    }

    /**
     * Streams, verifies and publishes one JAR. When supplied, the claimed digest must
     * match; otherwise the store establishes it. The returned identifier is the
     * lowercase SHA-256, not a filesystem path.
     */
    public synchronized Artifact upload(InputStream source, String displayFilename, String claimedSha256)
            throws IOException {
        return upload(source, displayFilename, claimedSha256, Set.of());
    }

    /** Uploads while preserving every artifact referenced by retained durable deployments. */
    public synchronized Artifact upload(InputStream source, String displayFilename, String claimedSha256,
                                        Set<String> protectedArtifactIds) throws IOException {
        if (source == null || !isSafeDisplayFilename(displayFilename)
                || claimedSha256 != null && !isSha256(claimedSha256)
                || protectedArtifactIds == null || protectedArtifactIds.stream().anyMatch(id -> !isSha256(id))) {
            throw rejected();
        }
        Path temporary = null;
        boolean published = false;
        try {
            verifyDirectory();
            temporary = Files.createTempFile(directory, "upload-", ".part");
            setPermissions(temporary, FILE_PERMISSIONS);
            DigestAndSize digest = copyBounded(source, temporary);
            String actual = digest.sha256();
            if (claimedSha256 != null && !actual.equals(claimedSha256)) throw rejected();
            inspectJar(temporary);

            Path artifact = artifactPath(actual);
            if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) {
                verifyExistingArtifact(artifact, actual);
                return new Artifact(actual, displayFilename, digest.size());
            }
            List<StoredFile> evictionPlan = planCapacity(digest.size(), protectedArtifactIds);
            publishWithRollback(temporary, artifact, evictionPlan);
            published = true;
            return new Artifact(actual, displayFilename, digest.size());
        } catch (ArtifactException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw rejected();
        } finally {
            if (!published && temporary != null) deleteTemporary(temporary);
        }
    }

    private List<StoredFile> planCapacity(long incomingBytes, Set<String> protectedArtifactIds) throws IOException {
        List<StoredFile> stored = new ArrayList<>();
        long bytes = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.matches("[0-9a-f]{64}\\.jar")) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) throw rejected();
                long size = Files.size(file);
                bytes = Math.addExact(bytes, size);
                stored.add(new StoredFile(file, name.substring(0, 64), size, Files.getLastModifiedTime(file).toMillis()));
            }
        } catch (ArithmeticException failure) {
            throw rejected();
        }
        stored.sort(Comparator.comparingLong(StoredFile::modified).thenComparing(item -> item.path().toString()));
        int count = stored.size();
        List<StoredFile> evictionPlan = new ArrayList<>();
        for (StoredFile candidate : stored) {
            if (count < maximumStoredArtifacts && bytes <= maximumStoredBytes - incomingBytes) break;
            if (protectedArtifactIds.contains(candidate.artifactId())) continue;
            evictionPlan.add(candidate);
            bytes -= candidate.size();
            count--;
        }
        if (count >= maximumStoredArtifacts || bytes > maximumStoredBytes - incomingBytes) throw rejected();
        return List.copyOf(evictionPlan);
    }

    private void publishWithRollback(Path temporary, Path artifact, List<StoredFile> evictionPlan)
            throws IOException {
        List<QuarantinedFile> quarantined = new ArrayList<>();
        try {
            for (StoredFile candidate : evictionPlan) {
                Path backup = directory.resolve("evict-" + candidate.artifactId() + ".part");
                if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) throw rejected();
                move(candidate.path(), backup, false);
                quarantined.add(new QuarantinedFile(candidate.path(), backup));
            }
            if (!quarantined.isEmpty()) DurableFiles.forceDirectory(directory);
            publish(temporary, artifact);
        } catch (IOException | RuntimeException failure) {
            IOException rollbackFailure = rollbackPublication(artifact, quarantined);
            if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
            throw failure;
        }
        for (QuarantinedFile file : quarantined) deleteTemporary(file.backup());
        if (!quarantined.isEmpty()) {
            try { DurableFiles.forceDirectory(directory); }
            catch (IOException ignored) { /* The canonical artifact is already durable; retry cleanup on startup. */ }
        }
    }

    private IOException rollbackPublication(Path artifact, List<QuarantinedFile> quarantined) {
        IOException failure = null;
        try {
            if (Files.exists(artifact, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(artifact)) {
                    throw rejected();
                }
                Files.delete(artifact);
            }
        } catch (IOException problem) {
            failure = problem;
        }
        for (int index = quarantined.size() - 1; index >= 0; index--) {
            QuarantinedFile file = quarantined.get(index);
            try {
                if (Files.exists(file.backup(), LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(file.original(), LinkOption.NOFOLLOW_LINKS)) {
                    move(file.backup(), file.original(), false);
                }
            } catch (IOException problem) {
                if (failure == null) failure = problem; else failure.addSuppressed(problem);
            }
        }
        try { DurableFiles.forceDirectory(directory); }
        catch (IOException problem) {
            if (failure == null) failure = problem; else failure.addSuppressed(problem);
        }
        return failure;
    }

    /** Opens a verified immutable artifact by its opaque identifier. */
    public InputStream open(String artifactId) throws IOException {
        if (!isSha256(artifactId)) throw rejected();
        try {
            verifyDirectory();
            Path artifact = artifactPath(artifactId);
            verifyExistingArtifact(artifact, artifactId);
            return Channels.newInputStream(FileChannel.open(artifact,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)));
        } catch (IOException failure) {
            throw rejected();
        }
    }

    /** Returns verified metadata without exposing the private backing path. */
    public Artifact describe(String artifactId) throws IOException {
        if (!isSha256(artifactId)) throw rejected();
        try {
            verifyDirectory();
            Path artifact = artifactPath(artifactId);
            verifyExistingArtifact(artifact, artifactId);
            return new Artifact(artifactId, "VotingPlugin.jar", Files.size(artifact));
        } catch (IOException failure) {
            throw rejected();
        }
    }

    private static DigestAndSize copyBounded(InputStream source, Path target) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        byte[] bytes = new byte[16 * 1024];
        try (FileChannel output = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int read; (read = source.read(bytes)) != -1;) {
                if (read == 0) continue;
                total = Math.addExact(total, read);
                if (total > MAX_UPLOAD_BYTES) throw rejected();
                digest.update(bytes, 0, read);
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, read);
                while (buffer.hasRemaining()) output.write(buffer);
            }
            output.force(true);
        } catch (ArithmeticException failure) {
            throw rejected();
        }
        return new DigestAndSize(hex(digest.digest()), total);
    }

    private static void inspectJar(Path file) throws IOException {
        int entries = 0;
        long expanded = 0;
        int pluginYmlEntries = 0;
        byte[] pluginYml = null;
        Set<String> entryNames = new HashSet<>();
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > MAX_ENTRY_COUNT || !isSafeZipEntryName(entry.getName())
                        || !entryNames.add(entry.getName())) throw rejected();
                long declaredSize = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (declaredSize < 0 || declaredSize > MAX_ENTRY_BYTES || compressedSize < 0
                        || (compressedSize > 0 && declaredSize > compressedSize * MAX_COMPRESSION_RATIO)) {
                    throw rejected();
                }
                expanded = addBounded(expanded, declaredSize, MAX_EXPANDED_BYTES);
                if ("plugin.yml".equals(entry.getName())) {
                    if (++pluginYmlEntries != 1 || entry.isDirectory() || declaredSize > MAX_PLUGIN_YML_BYTES) {
                        throw rejected();
                    }
                    pluginYml = readExactly(zip.getInputStream(entry), declaredSize);
                } else if (!entry.isDirectory()) {
                    consumeBounded(zip.getInputStream(entry), declaredSize);
                }
            }
        } catch (ArtifactException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw rejected();
        }
        if (entries == 0 || pluginYmlEntries != 1 || pluginYml == null || !declaresVotingPlugin(pluginYml)) throw rejected();
    }

    private static void consumeBounded(InputStream input, long expected) throws IOException {
        long actual = 0;
        byte[] buffer = new byte[8192];
        try (input) {
            for (int read; (read = input.read(buffer)) != -1;) {
                actual = addBounded(actual, read, expected);
            }
        }
        if (actual != expected) throw rejected();
    }

    private static byte[] readExactly(InputStream input, long expected) throws IOException {
        byte[] result = new byte[(int) expected];
        int offset = 0;
        try (input) {
            while (offset < result.length) {
                int read = input.read(result, offset, result.length - offset);
                if (read == -1) break;
                if (read == 0) continue;
                offset = Math.toIntExact(addBounded(offset, read, expected));
            }
            if (offset == result.length && input.read() != -1) throw rejected();
        }
        if (offset != expected) throw rejected();
        return result;
    }

    private static boolean declaresVotingPlugin(byte[] pluginYml) throws IOException {
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(pluginYml)).toString();
        } catch (CharacterCodingException failure) {
            throw rejected();
        }
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setCodePointLimit(MAX_PLUGIN_YML_BYTES);
            Object document = new Yaml(new SafeConstructor(options)).load(text);
            return document instanceof java.util.Map<?, ?> descriptor
                    && "VotingPlugin".equals(descriptor.get("name"));
        } catch (RuntimeException failure) {
            throw rejected();
        }
    }

    private void publish(Path temporary, Path artifact) throws IOException {
        try {
            move(temporary, artifact, false);
        } catch (java.nio.file.FileAlreadyExistsException collision) {
            verifyExistingArtifact(artifact, artifact.getFileName().toString().substring(0, 64));
            return;
        }
        afterPublishMove.run(artifact);
        setPermissions(artifact, FILE_PERMISSIONS);
        try (FileChannel channel = FileChannel.open(artifact, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
        DurableFiles.forceDirectory(directory);
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target);
        }
    }

    private void verifyExistingArtifact(Path artifact, String expectedHash) throws IOException {
        if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(artifact)
                || Files.size(artifact) > MAX_UPLOAD_BYTES || !hash(artifact).equals(expectedHash)) throw rejected();
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) {
                total = addBounded(total, read, MAX_UPLOAD_BYTES);
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private void verifyDirectory() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) throw rejected();
        rejectSymlinkedAncestors(directory);
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        Path parent = directory.getParent();
        if (parent == null) throw rejected();
        rejectSymlinkedAncestors(parent);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) throw rejected();
        } else {
            Files.createDirectory(directory);
        }
        setPermissions(directory, DIRECTORY_PERMISSIONS);
        DurableFiles.forceDirectory(parent);
    }

    private static void rejectSymlinkedAncestors(Path path) throws IOException {
        for (Path current = path.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw rejected();
        }
    }

    private Path artifactPath(String sha256) { return directory.resolve(sha256 + ".jar"); }

    private static boolean isSafeDisplayFilename(String filename) {
        return filename != null && filename.length() <= 120 && filename.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{0,115}\\.jar")
                && !filename.contains("..") && !filename.chars().anyMatch(Character::isISOControl);
    }

    private static boolean isSafeZipEntryName(String name) {
        if (name == null || name.isEmpty() || name.length() > 512 || name.startsWith("/") || name.startsWith("\\")
                || name.indexOf('\\') >= 0 || name.indexOf('\u0000') >= 0) return false;
        for (String component : name.split("/", -1)) if (component.equals(".") || component.equals("..")) return false;
        return true;
    }

    private static boolean isSha256(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static long addBounded(long current, long added, long maximum) throws IOException {
        try {
            long result = Math.addExact(current, added);
            if (result > maximum) throw rejected();
            return result;
        } catch (ArithmeticException failure) {
            throw rejected();
        }
    }

    private static MessageDigest sha256() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("Artifact upload rejected", impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02x", b));
        return value.toString();
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try { Files.setPosixFilePermissions(path, permissions); }
        catch (UnsupportedOperationException ignored) { /* Not available on Windows or some network filesystems. */ }
    }

    private static void deleteTemporary(Path temporary) {
        try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Private temporary cleanup only. */ }
    }

    private static ArtifactException rejected() { return new ArtifactException("Artifact upload rejected"); }

    public record Artifact(String artifactId, String displayFilename, long size) { }

    public static final class ArtifactException extends IOException {
        private ArtifactException(String message) { super(message); }
    }

    private record DigestAndSize(String sha256, long size) { }
    private record StoredFile(Path path, String artifactId, long size, long modified) { }
    private record QuarantinedFile(Path original, Path backup) { }
    @FunctionalInterface interface IoAction { void run(Path path) throws IOException; }
}
