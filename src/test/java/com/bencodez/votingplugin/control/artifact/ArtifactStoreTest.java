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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
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
            assertEquals(1, entries.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
        }
    }

	@Test void artifactAccessWaitsForDirectoryMutationLock() throws Exception {
		Path artifacts = directory.resolve("locked-artifacts");
		byte[] jar = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1, 2, 3});
		ArtifactStore store = new ArtifactStore(artifacts);
		String artifactId = store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", sha256(jar)).artifactId();
		Field locksField = ArtifactStore.class.getDeclaredField("DIRECTORY_LOCKS");
		locksField.setAccessible(true);
		@SuppressWarnings("unchecked")
		ConcurrentMap<Path, ReentrantLock> locks = (ConcurrentMap<Path, ReentrantLock>) locksField.get(null);
		ReentrantLock lock = locks.get(artifacts.toAbsolutePath().normalize());
		CountDownLatch started = new CountDownLatch(1);
		var executor = Executors.newSingleThreadExecutor();
		lock.lock();
		try {
			var access = executor.submit(() -> {
				started.countDown();
				ArtifactStore.Artifact described = store.describe(artifactId);
				try (InputStream input = store.open(artifactId)) {
					return new Object[] {described, input.readAllBytes()};
				}
			});
			assertTrue(started.await(5, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> access.get(200, TimeUnit.MILLISECONDS));
			lock.unlock();
			Object[] result = access.get(5, TimeUnit.SECONDS);
			assertEquals(jar.length, ((ArtifactStore.Artifact) result[0]).size());
			assertArrayEquals(jar, (byte[]) result[1]);
		} finally {
			if (lock.isHeldByCurrentThread()) lock.unlock();
			executor.shutdownNow();
		}
	}

    @Test void rejectsWrongOrNonLowercaseClaimsAndDoesNotPublishPartialArtifacts() throws Exception {
        byte[] jar = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1});
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"));
        String actual = sha256(jar);

        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", "0".repeat(64)));
        assertRejected(() -> store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", actual.toUpperCase()));
        try (var entries = Files.list(directory.resolve("artifacts"))) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
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

    @Test void removesIncompleteUploadsBeforeEveryNewUpload() throws Exception {
        Path artifacts = directory.resolve("artifacts-retry-cleanup");
        ArtifactStore store = new ArtifactStore(artifacts);
        Path incomplete = Files.writeString(artifacts.resolve("upload-rejected.part"), "partial");
        byte[] valid = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1});

        ArtifactStore.Artifact uploaded = store.upload(
                new ByteArrayInputStream(valid), "VotingPlugin.jar", sha256(valid));

        assertFalse(Files.exists(incomplete));
        assertArrayEquals(valid, store.open(uploaded.artifactId()).readAllBytes());
    }

	@Test void removesAnUnpublishedTemporaryTransactionMarkerOnStartup() throws Exception {
		Path artifacts = directory.resolve("artifacts");
		Files.createDirectories(artifacts);
		Path incomplete = Files.writeString(artifacts.resolve("upload-marker-crashed.part"), "truncated");

		new ArtifactStore(artifacts);

		assertFalse(Files.exists(incomplete));
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
            assertEquals(0, entries.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
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

    @Test void publicationFailureRestoresEvictionsAndRemovesTheRejectedArtifact() throws Exception {
        AtomicBoolean failAfterMove = new AtomicBoolean();
        byte[] first = jar("name: VotingPlugin\n", "plugin/One.class", new byte[] {1});
        byte[] second = jar("name: VotingPlugin\n", "plugin/Two.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(directory.resolve("artifacts"), 1_000_000, 1,
                path -> { if (failAfterMove.get()) throw new IOException("simulated post-move failure"); });
        String firstId = store.upload(new ByteArrayInputStream(first), "first.jar", sha256(first)).artifactId();

        failAfterMove.set(true);
        String secondId = sha256(second);
        assertRejected(() -> store.upload(new ByteArrayInputStream(second), "second.jar", secondId));

        assertArrayEquals(first, store.open(firstId).readAllBytes());
        assertRejected(() -> store.open(secondId));
    }

    @Test void publicationCollisionCommitsPlannedEvictionsAndRemovesTheStagedUpload() throws Exception {
        Path artifacts = directory.resolve("collision-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {2});
        String oldId = sha256(old);
        String incomingId = sha256(incoming);
        AtomicBoolean createCollision = new AtomicBoolean();
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1, path -> {
            if (createCollision.get()) Files.write(path, incoming);
        }, path -> { });
        store.upload(new ByteArrayInputStream(old), "old.jar", oldId);

        createCollision.set(true);
        ArtifactStore.Artifact duplicate = store.upload(new ByteArrayInputStream(incoming), "incoming.jar", incomingId);

        assertEquals(incomingId, duplicate.artifactId());
        assertRejected(() -> store.open(oldId));
        assertArrayEquals(incoming, store.open(incomingId).readAllBytes());
        try (var entries = Files.list(artifacts)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith("upload-")
                    || path.getFileName().toString().startsWith("evict-")));
        }
    }

    @Test void collisionFinalizationFailureKeepsItsEvictionPlanForRecovery() throws Exception {
        Path artifacts = directory.resolve("collision-finalization-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {2});
        String oldId = sha256(old);
        String incomingId = sha256(incoming);
        AtomicBoolean createCollision = new AtomicBoolean();
        AtomicBoolean failFinalization = new AtomicBoolean();
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1, path -> {
            if (createCollision.get()) Files.write(path, incoming);
        }, path -> { }, path -> {
            if (failFinalization.get()) throw new IOException("simulated collision finalization failure");
        });
        store.upload(new ByteArrayInputStream(old), "old.jar", oldId);

        createCollision.set(true);
        failFinalization.set(true);
        assertRejected(() -> store.upload(new ByteArrayInputStream(incoming), "incoming.jar", incomingId));

        ArtifactStore recovered = new ArtifactStore(artifacts, 1_000_000, 1);
        assertRejected(() -> recovered.open(oldId));
        assertArrayEquals(incoming, recovered.open(incomingId).readAllBytes());
        try (var entries = Files.list(artifacts)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith("upload-")
                    || path.getFileName().toString().startsWith("evict-")));
        }
    }

    @Test void incompleteRollbackRetainsItsPendingRecoveryMarker() throws Exception {
        Path artifacts = directory.resolve("rollback-artifacts");
        AtomicBoolean failAfterMove = new AtomicBoolean();
        byte[] first = jar("name: VotingPlugin\n", "plugin/One.class", new byte[] {1});
        byte[] second = jar("name: VotingPlugin\n", "plugin/Two.class", new byte[] {2});
        String firstId = sha256(first);
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1, path -> {
            if (failAfterMove.get()) {
                Files.writeString(artifacts.resolve(firstId + ".jar"), "corrupt rollback target");
                throw new IOException("simulated post-move failure");
            }
        });
        store.upload(new ByteArrayInputStream(first), "first.jar", firstId);

        failAfterMove.set(true);
        assertRejected(() -> store.upload(new ByteArrayInputStream(second), "second.jar", sha256(second)));

        try (var entries = Files.list(artifacts)) {
            assertTrue(entries.anyMatch(path -> path.getFileName().toString().endsWith(".pending")));
        }
    }

    @Test void hardLinkCleanupFailureRetainsRecoveryStateUntilStartupCanReconcile() throws Exception {
        Path artifacts = directory.resolve("hard-link-cleanup-artifacts");
        AtomicBoolean failPublicationDeletes = new AtomicBoolean();
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {2});
        String oldId = sha256(old);
        String incomingId = sha256(incoming);
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1,
                path -> { }, path -> { }, path -> { }, path -> {
                    if (failPublicationDeletes.get()) throw new IOException("simulated delete failure");
                    Files.delete(path);
                });
        store.upload(new ByteArrayInputStream(old), "old.jar", oldId);

        failPublicationDeletes.set(true);
        assertRejected(() -> store.upload(new ByteArrayInputStream(incoming), "incoming.jar", incomingId));

        assertArrayEquals(old, store.open(oldId).readAllBytes());
        assertRejected(() -> store.open(incomingId));
        assertRejected(() -> store.describe(incomingId));
        try (var entries = Files.list(artifacts)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".pending")));
        }

        ArtifactStore recovered = new ArtifactStore(artifacts, 1_000_000, 1);
        assertArrayEquals(old, recovered.open(oldId).readAllBytes());
        assertRejected(() -> recovered.open(incomingId));
        try (var entries = Files.list(artifacts)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith("evict-")
                    || path.getFileName().toString().startsWith("upload-")));
        }
    }

    @Test void startupRestoresAnInterruptedEvictionQuarantine() throws Exception {
        Path artifacts = directory.resolve("artifacts");
        byte[] jar = jar("name: VotingPlugin\n", "plugin/Main.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/New.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts);
        String artifactId = store.upload(new ByteArrayInputStream(jar), "VotingPlugin.jar", sha256(jar)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "1".repeat(32);
        Path canonical = artifacts.resolve(artifactId + ".jar");
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + artifactId + ".part");
        Files.move(canonical, quarantine);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Files.writeString(artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending"),
                "upload-owned.part");

        ArtifactStore recovered = new ArtifactStore(artifacts);

        assertArrayEquals(jar, recovered.open(artifactId).readAllBytes());
        assertRejected(() -> recovered.open(incomingId));
        assertFalse(Files.exists(quarantine));
    }

    @Test void startupRecoversALegacyInterruptedEvictionMarker() throws Exception {
        Path artifacts = directory.resolve("legacy-pending-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/New.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "5".repeat(32);
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + oldId + ".part");
        Files.move(artifacts.resolve(oldId + ".jar"), quarantine);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending");
        Files.createFile(marker);

        ArtifactStore recovered = new ArtifactStore(artifacts);

        assertArrayEquals(old, recovered.open(oldId).readAllBytes());
        assertRejected(() -> recovered.open(incomingId));
        assertFalse(Files.exists(quarantine));
        assertFalse(Files.exists(marker));
    }

    @Test void legacyPendingCollisionRecoveryPreservesTheCompetingArtifact() throws Exception {
        Path artifacts = directory.resolve("legacy-collision-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/New.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "6".repeat(32);
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + oldId + ".part");
        Files.move(artifacts.resolve(oldId + ".jar"), quarantine);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Path staged = Files.write(artifacts.resolve("upload-legacy-collision.part"), incoming);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending");
        Files.createFile(marker);

        ArtifactStore recovered = new ArtifactStore(artifacts, 1_000_000, 1);

        assertRejected(() -> recovered.open(oldId));
        assertArrayEquals(incoming, recovered.open(incomingId).readAllBytes());
        assertFalse(Files.exists(staged));
        assertFalse(Files.exists(quarantine));
        assertFalse(Files.exists(marker));
        try (var files = Files.list(artifacts)) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
        }
    }

    @Test void legacyPendingCollisionBeforeAnyEvictionFailsClosedAtCapacity() throws Exception {
        Path artifacts = directory.resolve("legacy-pre-eviction-collision-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/New.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "8".repeat(32);
        Path oldArtifact = artifacts.resolve(oldId + ".jar");
        Path incomingArtifact = artifacts.resolve(incomingId + ".jar");
        Files.write(incomingArtifact, incoming);
        Path staged = Files.write(artifacts.resolve("upload-legacy-pre-eviction.part"), incoming);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending");
        Files.createFile(marker);

        assertRejected(() -> new ArtifactStore(artifacts, 1_000_000, 1));

        assertArrayEquals(old, Files.readAllBytes(oldArtifact));
        assertArrayEquals(incoming, Files.readAllBytes(incomingArtifact));
        assertArrayEquals(incoming, Files.readAllBytes(staged));
        assertTrue(Files.exists(marker));
        try (var files = Files.list(artifacts)) {
            assertEquals(2, files.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
        }
    }

    @Test void uploadsSharingADirectorySerializeCapacityPlanningAndPublication() throws Exception {
        Path artifacts = directory.resolve("shared-artifacts");
        CountDownLatch firstPublishing = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondPublishing = new CountDownLatch(1);
        ArtifactStore first = new ArtifactStore(artifacts, 1_000_000, 1, path -> {
            firstPublishing.countDown();
            try {
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out awaiting test release");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException(failure);
            }
        });
        ArtifactStore second = new ArtifactStore(artifacts, 1_000_000, 1,
                path -> secondPublishing.countDown());
        byte[] firstJar = jar("name: VotingPlugin\n", "plugin/First.class", new byte[] {1});
        byte[] secondJar = jar("name: VotingPlugin\n", "plugin/Second.class", new byte[] {2});

        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstUpload = executor.submit(() -> first.upload(
                    new ByteArrayInputStream(firstJar), "first.jar", sha256(firstJar)));
            assertTrue(firstPublishing.await(5, TimeUnit.SECONDS));
            var secondUpload = executor.submit(() -> second.upload(
                    new ByteArrayInputStream(secondJar), "second.jar", sha256(secondJar)));
            assertFalse(secondPublishing.await(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            firstUpload.get(5, TimeUnit.SECONDS);
            secondUpload.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        try (var files = Files.list(artifacts)) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
        }
    }

    @Test void startupCommitsEvictionAfterCrashFollowingACompetingPublishCollision() throws Exception {
        Path artifacts = directory.resolve("collision-recovery-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "4".repeat(32);
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + oldId + ".part");
        Files.move(artifacts.resolve(oldId + ".jar"), quarantine);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Path staged = Files.write(artifacts.resolve("upload-collision.part"), incoming);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending");
        Files.writeString(marker, staged.getFileName().toString());

        ArtifactStore recovered = new ArtifactStore(artifacts, 1_000_000, 1);

        assertRejected(() -> recovered.open(oldId));
        assertArrayEquals(incoming, recovered.open(incomingId).readAllBytes());
        assertFalse(Files.exists(staged));
        assertFalse(Files.exists(quarantine));
        assertFalse(Files.exists(marker));
        try (var files = Files.list(artifacts)) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
        }
    }

    @Test void collisionRecoveryCompletesEveryEvictionRecordedBeforeTheFirstMove() throws Exception {
        Path artifacts = directory.resolve("partial-collision-recovery-artifacts");
        Files.createDirectories(artifacts);
        byte[] first = jar("name: VotingPlugin\n", "plugin/First.class", new byte[] {1});
        byte[] second = jar("name: VotingPlugin\n", "plugin/Second.class", new byte[] {2});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {3});
        String firstId = sha256(first);
        String secondId = sha256(second);
        String incomingId = sha256(incoming);
        Files.write(artifacts.resolve(firstId + ".jar"), first);
        Files.write(artifacts.resolve(secondId + ".jar"), second);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Path staged = Files.write(artifacts.resolve("upload-partial-collision.part"), incoming);
        String transaction = "7".repeat(32);
        Path firstBackup = artifacts.resolve("evict-" + transaction + "-" + firstId + ".part");
        Files.move(artifacts.resolve(firstId + ".jar"), firstBackup);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending");
        Files.writeString(marker, staged.getFileName() + "\n" + firstId + "\n" + secondId);

        ArtifactStore recovered = new ArtifactStore(artifacts, 1_000_000, 1);

        assertRejected(() -> recovered.open(firstId));
        assertRejected(() -> recovered.open(secondId));
        assertArrayEquals(incoming, recovered.open(incomingId).readAllBytes());
        assertFalse(Files.exists(staged));
        assertFalse(Files.exists(firstBackup));
        assertFalse(Files.exists(marker));
        try (var files = Files.list(artifacts)) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().endsWith(".jar")).count());
        }
    }

    @Test void startupRollsBackAHardLinkedPublicationBeforeCommit() throws Exception {
        Path artifacts = directory.resolve("hard-link-recovery-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "7".repeat(32);
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + oldId + ".part");
        Files.move(artifacts.resolve(oldId + ".jar"), quarantine);
        Path staged = Files.write(artifacts.resolve("upload-linked.part"), incoming);
        Path published = artifacts.resolve(incomingId + ".jar");
        Files.createLink(published, staged);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".pending");
        Files.writeString(marker, staged.getFileName().toString());

        ArtifactStore recovered = new ArtifactStore(artifacts);

        assertArrayEquals(old, recovered.open(oldId).readAllBytes());
        assertRejected(() -> recovered.open(incomingId));
        assertFalse(Files.exists(staged));
        assertFalse(Files.exists(quarantine));
        assertFalse(Files.exists(marker));
    }

    @Test void startupFinishesACommittedEvictionWithoutRestoringOldArtifacts() throws Exception {
        Path artifacts = directory.resolve("committed-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/New.class", new byte[] {2});
        ArtifactStore store = new ArtifactStore(artifacts);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "2".repeat(32);
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + oldId + ".part");
        Files.move(artifacts.resolve(oldId + ".jar"), quarantine);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Files.createFile(artifacts.resolve("evict-" + transaction + "-" + incomingId + ".committed"));

        ArtifactStore recovered = new ArtifactStore(artifacts);

        assertArrayEquals(incoming, recovered.open(incomingId).readAllBytes());
        assertRejected(() -> recovered.open(oldId));
        assertFalse(Files.exists(quarantine));
    }

    @Test void nextUploadFinishesCommittedEvictionBeforePlanningCapacity() throws Exception {
        Path artifacts = directory.resolve("active-committed-artifacts");
        byte[] old = jar("name: VotingPlugin\n", "plugin/Old.class", new byte[] {1});
        byte[] incoming = jar("name: VotingPlugin\n", "plugin/Incoming.class", new byte[] {2});
        byte[] next = jar("name: VotingPlugin\n", "plugin/Next.class", new byte[] {3});
        ArtifactStore store = new ArtifactStore(artifacts, 1_000_000, 1);
        String oldId = store.upload(new ByteArrayInputStream(old), "old.jar", sha256(old)).artifactId();
        String incomingId = sha256(incoming);
        String transaction = "3".repeat(32);
        Path quarantine = artifacts.resolve("evict-" + transaction + "-" + oldId + ".part");
        Files.move(artifacts.resolve(oldId + ".jar"), quarantine);
        Files.write(artifacts.resolve(incomingId + ".jar"), incoming);
        Path marker = artifacts.resolve("evict-" + transaction + "-" + incomingId + ".committed");
        Files.writeString(marker, "upload-committed-next.part");

        String nextId = store.upload(new ByteArrayInputStream(next), "next.jar", sha256(next)).artifactId();
        ArtifactStore recovered = new ArtifactStore(artifacts, 1_000_000, 1);

        assertArrayEquals(next, recovered.open(nextId).readAllBytes());
        assertRejected(() -> recovered.open(incomingId));
        assertFalse(Files.exists(quarantine));
        assertFalse(Files.exists(marker));
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
