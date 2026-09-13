package com.bencodez.votingplugin.control.artifact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactStoreTest {
    @TempDir Path directory;

    @Test void streamsVerifiesAndPublishesAnImmutableContentAddressedVotingPluginJar() throws Exception {
        byte[] jar = jar("name: VotingPlugin\nversion: 7.1.2\n", "plugin/Main.class", new byte[] {1, 2, 3});
        String sha256 = sha256(jar);
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));

        ArtifactStore.Artifact first = store.upload(new ByteArrayInputStream(jar), "VotingPlugin-7.1.2.jar", sha256);
        ArtifactStore.Artifact duplicate = store.upload(new ByteArrayInputStream(jar), "same-content.jar", sha256);
        ArtifactStore.Artifact serverHashed = store.upload(new ByteArrayInputStream(jar), "browser-upload.jar", null);

        assertEquals(sha256, first.artifactId());
        assertEquals(jar.length, first.size());
        assertEquals(sha256, duplicate.artifactId());
        assertEquals(sha256, serverHashed.artifactId());
        assertArrayEquals(jar, store.open(sha256).readAllBytes());
        Path published = directory.resolve("artifacts").resolve(sha256 + ".jar");
        assertTrue(Files.isRegularFile(published));
        try (var entries = Files.list(directory.resolve("artifacts"))) {
            assertEquals(1, entries.count());
        }
    }

    @Test void rejectsWrongOrNonLowercaseClaimsAndDoesNotPublishPartialArtifacts() throws Exception {
        byte[] jar = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1});
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));
        String actual = sha256(jar);

        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", "0".repeat(64)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", actual.toUpperCase()));
        try (var entries = Files.list(directory.resolve("artifacts"))) {
            assertEquals(0, entries.count());
        }
    }

    @Test void rejectsUnsafeDisplayNamesAndNeverUsesThemAsPaths() throws Exception {
        byte[] jar = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1});
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));

        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "../VotingPlugin.jar", sha256(jar)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "VotingPlugin.zip", sha256(jar)));
        assertFalse(Files.exists(directory.resolve("VotingPlugin.jar")));
    }

    @Test void rejectsMissingOrWrongPluginDescriptorAndCompressedBombs() throws Exception {
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));
        byte[] missing = jar(null, "plugin/Main.class", new byte[] {1});
        byte[] wrong = jar("name: AnotherPlugin\n", "plugin/Main.class", new byte[] {1});
        byte[] ambiguous = jar("name: VotingPlugin\nname: AnotherPlugin\n", "plugin/Main.class", new byte[] {1});
        byte[] quotedDuplicate = jar("name: VotingPlugin\n\"name\": AnotherPlugin\n",
                "plugin/Main.class", new byte[] {1});
        byte[] bomb = jar("name: VotingPlugin\n", "data.bin", new byte[1_000_000]);

        assertRejected(() -> store.upload(new ByteArrayInputStream(missing), "VotingPlugin.jar", sha256(missing)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(wrong), "VotingPlugin.jar", sha256(wrong)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(ambiguous), "VotingPlugin.jar", sha256(ambiguous)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(quotedDuplicate),
                "VotingPlugin.jar", sha256(quotedDuplicate)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(bomb), "VotingPlugin.jar", sha256(bomb)));
    }

    @Test void removesIncompleteUploadsOnStartupAndRejectsDuplicateEntryNames() throws Exception {
        Path artifacts = directory.resolve("artifacts");
        Files.createDirectories(artifacts);
        Path incomplete = Files.writeString(artifacts.resolve("upload-crashed.part"), "partial");
        ArtifactStore store = new ArtifactStore(artifacts);
        assertFalse(Files.exists(incomplete));

        byte[] duplicate = jarWithDuplicateClassNames();
        assertRejected(() -> store.upload(new ByteArrayInputStream(duplicate), "VotingPlugin.jar", sha256(duplicate)));
    }

    @Test void rejectsSymlinkedStorageAndExistingArtifactTargets() throws Exception {
        Path real = directory.resolve("real");
        Files.createDirectory(real);
        Path linked = directory.resolve("linked");
        try {
            Files.createSymbolicLink(linked, real);
        } catch (UnsupportedOperationException | IOException unavailable) {
            return;
        }
        assertRejected(() -> new ArtifactStore(linked.resolve("artifacts")));

        byte[] jar = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1});
        String sha256 = sha256(jar);
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));
        Path published = directory.resolve("artifacts").resolve(sha256 + ".jar");
        Files.createSymbolicLink(published, directory.resolve("outside.jar"));

        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", sha256));
        assertRejected(() -> store.open(sha256));
    }

    @Test void rejectsAStreamThatExceedsTheUploadBound() throws Exception {
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));
        InputStream oversized = new InputStream() {
            private long remaining = ArtifactStore.MAX_UPLOAD_BYTES + 1;
            @Override public int read(byte[] target, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(length, remaining);
                Arrays.fill(target, offset, offset + count, (byte) 1);
                remaining -= count;
                return count;
            }
            @Override public int read() { return remaining-- > 0 ? 1 : -1; }
        };
        assertRejected(() -> store.upload(oversized, "VotingPlugin.jar", "0".repeat(64)));
        try (var entries = Files.list(directory.resolve("artifacts"))) {
            assertEquals(0, entries.count());
        }
    }

    @Test void evictsOnlyUnreferencedArtifactsAndRejectsWhenEverySlotIsProtected() throws Exception {
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"), 1_000_000, 2);
        byte[] first = jar("name: VotingPlugin\n", "plugin/One.class", new byte[] {1});
        byte[] second = jar("name: VotingPlugin\n", "plugin/Two.class", new byte[] {2});
        byte[] third = jar("name: VotingPlugin\n", "plugin/Three.class", new byte[] {3});
        String firstId = store.upload(new ByteArrayInputStream(first), "first.jar", sha256(first)).artifactId();
        String secondId = store.upload(new ByteArrayInputStream(second), "second.jar", sha256(second), Set.of(firstId)).artifactId();

        assertRejected(() -> store.upload(new ByteArrayInputStream(third), "third.jar", sha256(third),
                Set.of(firstId, secondId)));
        String thirdId = store.upload(new ByteArrayInputStream(third), "third.jar", sha256(third), Set.of(firstId)).artifactId();

        assertArrayEquals(first, store.open(firstId).readAllBytes());
        assertArrayEquals(third, store.open(thirdId).readAllBytes());
        assertRejected(() -> store.open(secondId));
    }

    @Test void infeasibleCapacityDoesNotEvictAnUnprotectedArtifact() throws Exception {
        byte[] first = jar("name: VotingPlugin\n", "plugin/One.class", new byte[] {1});
        byte[] second = jar("name: VotingPlugin\n", "plugin/Two.class", new byte[] {2});
        byte[] third = jar("name: VotingPlugin\n", "plugin/Three.class",
                "larger incoming artifact payload".repeat(20).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(third.length > first.length);
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"), first.length + second.length, 3);
        String firstId = store.upload(new ByteArrayInputStream(first), "first.jar", sha256(first)).artifactId();
        String secondId = store.upload(new ByteArrayInputStream(second), "second.jar", sha256(second)).artifactId();

        assertRejected(() -> store.upload(new ByteArrayInputStream(third), "third.jar", sha256(third),
                Set.of(secondId)));

        assertArrayEquals(first, store.open(firstId).readAllBytes());
        assertArrayEquals(second, store.open(secondId).readAllBytes());
    }

    private static byte[] jar(String pluginYml, String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (pluginYml != null) {
                zip.putNextEntry(new ZipEntry("plugin.yml"));
                zip.write(pluginYml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] jarWithDuplicateClassNames() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String name : List.of("plugin.yml", "plugin/One.class", "plugin/Two.class")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("plugin.yml".equals(name) ? "name: VotingPlugin\n".getBytes() : new byte[] {1});
                zip.closeEntry();
            }
        }
        byte[] bytes = output.toByteArray();
        byte[] from = "plugin/Two.class".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] to = "plugin/One.class".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= bytes.length - from.length; offset++) {
            if (java.util.Arrays.equals(bytes, offset, offset + from.length, from, 0, from.length)) {
                System.arraycopy(to, 0, bytes, offset, to.length);
            }
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void assertRejected(ThrowingRunnable operation) {
        ArtifactStore.ArtifactException failure = assertThrows(ArtifactStore.ArtifactException.class, operation::run);
        assertEquals("Artifact upload rejected", failure.getMessage());
        assertFalse(failure.getMessage().contains("/"));
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
