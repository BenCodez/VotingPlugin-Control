package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
import com.bencodez.votingplugin.control.protocol.DeploymentRequest;
import com.bencodez.votingplugin.control.protocol.DeploymentResult;
import com.bencodez.votingplugin.control.protocol.DeploymentTask;
import com.bencodez.votingplugin.control.protocol.DeploymentTaskResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

class DeploymentOperationsTest {
    private static final UUID SESSION_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_REPLACED = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-13T00:00:00Z"));

    @Test
    void createExcludesMixedVersionNodesBeforeQueueingAnything() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("new", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        registry.add("old", SESSION_B, Set.of());
        DeploymentOperations operations = new DeploymentOperations(registry, clock);

        ValidationException rejected = assertThrows(ValidationException.class,
                () -> operations.create(request("new", "old")));
        assertEquals("NODE_UNAVAILABLE", rejected.code());
        assertEquals(List.of("old"), rejected.details());
        assertEquals(0, operations.list(0, 100).size());
        assertEquals("QUEUED", operations.create(request("new")).state());
    }

    @Test
    void completionRequiresThePinnedSessionAttemptAndLease() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult created = operations.create(request("backend"));
        DeploymentTask task = operations.claim("backend", SESSION_A);
        assertNotNull(task);

        ValidationException wrongAttempt = assertThrows(ValidationException.class,
                () -> operations.complete(created.deploymentId(), "backend",
                        new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "restart", UUID.randomUUID())));
        assertEquals("TASK_NOT_CLAIMED", wrongAttempt.code());

        registry.add("backend", SESSION_REPLACED, Set.of(DeploymentRequest.CAPABILITY));
        assertEquals("SESSION_MISMATCH", assertThrows(ValidationException.class,
                () -> operations.complete(created.deploymentId(), "backend",
                        new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "restart", task.attemptId()))).code());

        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        clock.advance(Duration.ofMinutes(2));
        assertEquals("TASK_LEASE_EXPIRED", assertThrows(ValidationException.class,
                () -> operations.complete(created.deploymentId(), "backend",
                        new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "restart", task.attemptId()))).code());
    }

    @Test
    void partialFailureRetryCreatesNewOperationForFailedNodesOnly() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("success", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        registry.add("failed", SESSION_B, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult created = operations.create(request("success", "failed"));
        DeploymentTask successTask = operations.claim("success", SESSION_A);
        DeploymentTask failedTask = operations.claim("failed", SESSION_B);
        operations.complete(created.deploymentId(), "success",
                new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "staged", successTask.attemptId()));
        operations.complete(created.deploymentId(), "failed",
                new DeploymentTaskResult(SESSION_B, false, "WRITE_FAILED", "could not stage", failedTask.attemptId()));

        DeploymentResult retry = operations.retry(created.deploymentId());
        assertEquals(1, retry.nodes().size());
        assertEquals("failed", retry.nodes().get(0).nodeId());
        assertNull(operations.claim("success", SESSION_A));
        assertNotNull(operations.claim("failed", SESSION_B));
        assertNotEquals(created.deploymentId(), retry.deploymentId());
    }

    @Test
    void deploymentJournalRestoresMetadataAndPerNodeState(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, directory, clock);
        DeploymentResult created = operations.create(request("backend"));
        DeploymentTask task = operations.claim("backend", SESSION_A);
        operations.complete(created.deploymentId(), "backend",
                new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "staged", task.attemptId()));

        DeploymentOperations reopened = new DeploymentOperations(registry, directory, clock);
        DeploymentResult restored = reopened.get(created.deploymentId());
        assertEquals("SUCCEEDED", restored.state());
        assertEquals("RESTART_REQUIRED", restored.nodes().get(0).result().code());
    }

    @Test
    void nodeMessagesAreReplacedBeforePersistenceAndPublicDisplay(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, directory, clock);
        DeploymentResult created = operations.create(request("backend"));
        DeploymentTask task = operations.claim("backend", SESSION_A);

        DeploymentResult completed = operations.complete(created.deploymentId(), "backend",
                new DeploymentTaskResult(SESSION_A, false, "WRITE_FAILED",
                        "secret=/private/server/path/token", task.attemptId()));

        assertEquals("The node could not write the staged artifact", completed.nodes().get(0).result().message());
        assertFalse(durableJournal(directory).contains("/private/server/path/token"));
    }

    @Test
    void restartInvalidatesAnInProgressAttemptAndRequiresExplicitRetry(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, directory, clock);
        DeploymentResult created = operations.create(request("backend"));
        DeploymentTask interrupted = operations.claim("backend", SESSION_A);

        DeploymentOperations reopened = new DeploymentOperations(registry, directory, clock);
        DeploymentResult recovered = reopened.get(created.deploymentId());
        assertEquals("FAILED", recovered.state());
        assertEquals("CONTROL_RESTARTED", recovered.nodes().get(0).result().code());
        assertEquals("TASK_LEASE_EXPIRED", assertThrows(ValidationException.class,
                () -> reopened.authorizeArtifact(created.deploymentId(), "backend", SESSION_A,
                        interrupted.attemptId())).code());
        DeploymentResult retry = reopened.retry(created.deploymentId());
        assertNotEquals(created.deploymentId(), retry.deploymentId());
        assertNotNull(reopened.claim("backend", SESSION_A));
    }

    @Test
    void reconnectOrCapabilityLossTerminatesStaleWorkInsteadOfLeavingItQueuedForever() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult created = operations.create(request("backend"));

        registry.add("backend", SESSION_REPLACED, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentResult changed = operations.get(created.deploymentId());
        assertEquals("FAILED", changed.state());
        assertEquals("CAPABILITY_LOST", changed.nodes().get(0).result().code());
        assertNull(operations.claim("backend", SESSION_REPLACED));
    }

    @Test
    void knownCapabilityLossInvalidatesAnOfflineNodesActiveLease() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult created = operations.create(request("backend"));
        DeploymentTask claimed = operations.claim("backend", SESSION_A);

        registry.add("backend", SESSION_A, Set.of(), false);

        assertEquals("TASK_NOT_CLAIMED", assertThrows(ValidationException.class,
                () -> operations.complete(created.deploymentId(), "backend",
                        new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "staged",
                                claimed.attemptId()))).code());
        DeploymentResult failed = operations.get(created.deploymentId());
        assertEquals("FAILED", failed.state());
        assertEquals("CAPABILITY_LOST", failed.nodes().get(0).result().code());
    }

    @Test
    void auditFailureRollsBackTheUnauditedPruneBatch(@TempDir Path directory) throws Exception {
        Path auditDirectory = directory.resolve("audit");
        Path journalDirectory = directory.resolve("journal");
        FakeRegistry registry = new FakeRegistry();
        registry.add("first", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        registry.add("second", SESSION_B, Set.of(DeploymentRequest.CAPABILITY));
        UUID deploymentId;
        try (ConfigurationAuditLog audit = new ConfigurationAuditLog(auditDirectory, clock)) {
            DeploymentOperations operations = new DeploymentOperations(registry, audit, journalDirectory, clock);
            deploymentId = operations.create(request("first", "second")).deploymentId();
            registry.add("first", SESSION_REPLACED, Set.of(DeploymentRequest.CAPABILITY));
            registry.add("second", SESSION_REPLACED, Set.of(DeploymentRequest.CAPABILITY));
            registry.runBeforeFind(2, () -> {
                try {
                    Files.writeString(auditDirectory.resolve("configuration-audit.jsonl"), "corrupt",
                            StandardCharsets.UTF_8);
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            });

            assertThrows(ConfigurationAuditLog.AuditException.class, () -> operations.get(deploymentId));
        }

        registry.add("first", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        registry.add("second", SESSION_B, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentResult restored = new DeploymentOperations(registry, journalDirectory, clock).get(deploymentId);
        assertEquals("QUEUED", restored.nodes().get(0).state());
        assertEquals("QUEUED", restored.nodes().get(1).state());
    }

    @Test
    void pruneBoundsAndBatchesExpiredTargetTransitions() {
        FakeRegistry registry = new FakeRegistry();
        String[] nodeIds = new String[DeploymentOperations.MAX_PRUNE_TRANSITIONS];
        for (int index = 0; index < nodeIds.length; index++) {
            nodeIds[index] = "backend-" + index;
            registry.add(nodeIds[index], UUID.randomUUID(), Set.of(DeploymentRequest.CAPABILITY));
        }
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult first = operations.create(request(nodeIds));
        DeploymentResult second = operations.create(request(nodeIds));
        for (String nodeId : nodeIds) registry.remove(nodeId);
        clock.advance(DeploymentOperations.ACTIVE_RETENTION);

        List<DeploymentResult> firstSweep = operations.list(0, 100);
        assertEquals("FAILED", firstSweep.stream().filter(item -> item.deploymentId().equals(first.deploymentId()))
                .findFirst().orElseThrow().state());
        assertEquals("QUEUED", firstSweep.stream().filter(item -> item.deploymentId().equals(second.deploymentId()))
                .findFirst().orElseThrow().state());

        List<DeploymentResult> secondSweep = operations.list(0, 100);
        assertTrue(secondSweep.stream().allMatch(item -> item.state().equals("FAILED")));
    }

    @Test
    void transitionPersistenceFailureRestoresThePriorDeploymentState(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, directory, clock);
        DeploymentResult created = operations.create(request("backend"));
        Path temporary = Files.createDirectory(deploymentFile(directory, created.deploymentId())
                .resolveSibling(created.deploymentId() + ".json.tmp"));
        Files.writeString(temporary.resolve("blocker"), "block", StandardCharsets.UTF_8);

        clock.advance(DeploymentOperations.ACTIVE_RETENTION);
        assertThrows(IllegalStateException.class, () -> operations.list(0, 100));
        Files.delete(temporary.resolve("blocker"));
        Files.delete(temporary);
        clock.advance(DeploymentOperations.ACTIVE_RETENTION.negated());

        assertEquals("QUEUED", operations.get(created.deploymentId()).state());
        assertEquals("QUEUED", new DeploymentOperations(registry, directory, clock)
                .get(created.deploymentId()).state());
    }

    @Test
    void activeLeaseBlocksLaterDeploymentForTheSameNode() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult first = operations.create(request("backend"));
        operations.create(request("backend"));

        DeploymentTask claimed = operations.claim("backend", SESSION_A);
        assertEquals(first.deploymentId(), claimed.deploymentId());
        assertNull(operations.claim("backend", SESSION_A));

        clock.advance(DeploymentOperations.LEASE);
        assertEquals(first.deploymentId(), operations.claim("backend", SESSION_A).deploymentId());
    }

    @Test
    void unavailableTargetExpiresAndStopsProtectingItsArtifact() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
        DeploymentResult created = operations.create(request("backend"));

        registry.remove("backend");
        clock.advance(DeploymentOperations.ACTIVE_RETENTION);
        DeploymentResult expired = operations.get(created.deploymentId());
        assertEquals("FAILED", expired.state());
        assertEquals("TIMEOUT", expired.nodes().get(0).result().code());
        assertEquals(Set.of(SHA), operations.referencedArtifactIds());

        clock.advance(DeploymentOperations.COMPLETE_RETENTION.plusSeconds(1));
        assertEquals(Set.of(), operations.referencedArtifactIds());
    }

    @Test
    void retentionEvictsTheOldestCompletedDeploymentAndListsNewestFirst(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, directory, clock);
        UUID oldest = null;
        for (int index = 0; index < DeploymentOperations.MAX_RETAINED; index++) {
            DeploymentResult created = operations.create(request("backend"));
            if (oldest == null) oldest = created.deploymentId();
            DeploymentTask task = operations.claim("backend", SESSION_A);
            operations.complete(created.deploymentId(), "backend",
                    new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "staged", task.attemptId()));
        }

        DeploymentResult newest = operations.create(request("backend"));
        assertEquals(DeploymentOperations.MAX_RETAINED, operations.list(0, 100).size());
        assertEquals(newest.deploymentId(), operations.list(0, 1).get(0).deploymentId());
        UUID evicted = oldest;
        assertEquals("OPERATION_NOT_FOUND", assertThrows(ValidationException.class,
                () -> operations.get(evicted)).code());
        DeploymentOperations reopened = new DeploymentOperations(registry, directory, clock);
        assertEquals(DeploymentOperations.MAX_RETAINED, reopened.list(0, 100).size());
        assertEquals("OPERATION_NOT_FOUND", assertThrows(ValidationException.class,
                () -> reopened.get(evicted)).code());
    }

    @Test
    void journalRejectsDuplicateFieldsAtEveryPersistedObjectLevel(@TempDir Path directory) throws Exception {
        String id = "10000000-0000-0000-0000-000000000001";
        String attempt = "20000000-0000-0000-0000-000000000001";
        String prefix = "[{\"id\":\"" + id + "\",\"artifactId\":\"" + SHA + "\","
                + "\"sha256\":\"" + SHA + "\",\"size\":1234,\"createdAt\":\"2026-09-13T00:00:00Z\",\"targets\":[";
        String queued = "{\"nodeId\":\"backend\",\"sessionId\":\"" + SESSION_A
                + "\",\"state\":\"QUEUED\",\"leasedAt\":null,\"attemptId\":null,\"result\":null}";
        String succeeded = "{\"nodeId\":\"backend\",\"sessionId\":\"" + SESSION_A
                + "\",\"state\":\"SUCCEEDED\",\"leasedAt\":null,\"attemptId\":null,\"result\":{"
                + "\"sessionId\":\"" + SESSION_A + "\",\"success\":true,\"code\":\"RESTART_REQUIRED\","
                + "\"message\":\"staged\",\"attemptId\":\"" + attempt + "\"}}";
        List<String> invalid = List.of(
                prefix.replace("[{", "[{\"id\":\"" + id + "\",") + queued + "]}]",
                prefix + queued.replace("{", "{\"nodeId\":\"backend\",") + "]}]",
                prefix + succeeded.replace("\"code\":\"RESTART_REQUIRED\"",
                        "\"code\":\"RESTART_REQUIRED\",\"code\":\"RESTART_REQUIRED\"") + "]}]");
        for (int index = 0; index < invalid.size(); index++) {
            Path data = directory.resolve("case-" + index);
            Files.createDirectories(data);
            Files.writeString(data.resolve("plugin-deployments.json"), invalid.get(index), StandardCharsets.UTF_8);
            assertThrows(IllegalStateException.class, () -> new DeploymentOperations(new FakeRegistry(), data, clock));
        }
    }

    @Test
    void journalRejectsFutureAndOverflowingTimestampsAtStartup(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentResult created = new DeploymentOperations(registry, directory, clock).create(request("backend"));
        Path journal = deploymentFile(directory, created.deploymentId());
        String valid = Files.readString(journal, StandardCharsets.UTF_8);

        Files.writeString(journal, valid.replace(clock.instant().toString(), Instant.MAX.toString()),
                StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> new DeploymentOperations(registry, directory, clock));
    }

    @Test
    void deploymentIdentityMustBeTheCanonicalArtifactDigest() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeploymentRequest("VotingPlugin.jar", SHA, 1234, List.of("backend")));
        assertThrows(IllegalArgumentException.class,
                () -> new DeploymentRequest(SHA.toUpperCase(), SHA.toUpperCase(), 1234, List.of("backend")));
        assertThrows(IllegalArgumentException.class,
                () -> new DeploymentRequest("b".repeat(64), SHA, 1234, List.of("backend")));
		assertThrows(IllegalArgumentException.class,
				() -> new DeploymentRequest(SHA, SHA, 1234, java.util.Arrays.asList("backend", null)));
    }

	@Test
	void journalRejectsCoerciveOrOutOfRangeArtifactSizes(@TempDir Path directory) throws Exception {
		FakeRegistry registry = new FakeRegistry();
		registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
		DeploymentResult created = new DeploymentOperations(registry, directory, clock).create(request("backend"));
		Path journal = deploymentFile(directory, created.deploymentId());
		String valid = Files.readString(journal, StandardCharsets.UTF_8);

		for (String invalid : List.of("\"1234\"", "1234.5", "9223372036854775808")) {
			Files.writeString(journal, valid.replace("\"size\":1234", "\"size\":" + invalid),
					StandardCharsets.UTF_8);
			assertThrows(IllegalStateException.class,
					() -> new DeploymentOperations(registry, directory, clock));
		}
	}

    @Test
    void journalRejectsNonCanonicalOrMismatchedArtifactIdentity(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentResult created = new DeploymentOperations(registry, directory, clock).create(request("backend"));
        Path journal = deploymentFile(directory, created.deploymentId());
        String valid = Files.readString(journal, StandardCharsets.UTF_8);

        for (String invalidId : List.of("VotingPlugin.jar", SHA.toUpperCase(), "b".repeat(64))) {
            Files.writeString(journal, valid.replace("\"artifactId\":\"" + SHA + "\"",
                    "\"artifactId\":\"" + invalidId + "\""), StandardCharsets.UTF_8);
            assertThrows(IllegalStateException.class,
                    () -> new DeploymentOperations(registry, directory, clock));
        }
    }

    private static DeploymentRequest request(String... nodes) {
        return new DeploymentRequest(SHA, SHA, 1234, List.of(nodes));
    }

    @Test
    void claimAndCompletionOnlyRewriteTheAffectedDeploymentEntry(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, directory, clock);
        List<UUID> deploymentIds = new java.util.ArrayList<>();
        for (int index = 0; index < DeploymentOperations.MAX_RETAINED; index++) {
            deploymentIds.add(operations.create(request("backend")).deploymentId());
        }
        Map<UUID, String> untouched = new HashMap<>();
        for (int index = 1; index < deploymentIds.size(); index++) {
            UUID id = deploymentIds.get(index);
            untouched.put(id, Files.readString(deploymentFile(directory, id), StandardCharsets.UTF_8));
        }

        DeploymentTask task = operations.claim("backend", SESSION_A);
        assertEquals(deploymentIds.get(0), task.deploymentId());
        assertUntouchedDeploymentFiles(directory, untouched);
        operations.complete(task.deploymentId(), "backend",
                new DeploymentTaskResult(SESSION_A, true, "RESTART_REQUIRED", "staged", task.attemptId()));
        assertUntouchedDeploymentFiles(directory, untouched);
    }

    @Test
    void legacyMonolithicJournalIsImportedOnceIntoPerDeploymentEntries(@TempDir Path directory) throws Exception {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentResult created = new DeploymentOperations(registry, directory, clock).create(request("backend"));
        Path entry = deploymentFile(directory, created.deploymentId());
        String legacy = "[" + Files.readString(entry, StandardCharsets.UTF_8) + "]";
        Files.delete(entry);
        Files.writeString(directory.resolve("plugin-deployments.json"), legacy, StandardCharsets.UTF_8);

        DeploymentResult restored = new DeploymentOperations(registry, directory, clock).get(created.deploymentId());
        assertEquals("QUEUED", restored.state());
        assertTrue(Files.exists(entry));
        assertFalse(Files.exists(directory.resolve("plugin-deployments.json")));
    }

    @Test
    void rejectsASymlinkedPerDeploymentDirectory(@TempDir Path directory) throws Exception {
        Path target = Files.createDirectory(directory.resolve("target"));
        try {
            Files.createSymbolicLink(directory.resolve("plugin-deployments"), target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            return; // The test account/filesystem cannot create links.
        }

        assertThrows(IllegalStateException.class, () -> new DeploymentOperations(new FakeRegistry(), directory, clock));
    }

    private static Path deploymentFile(Path directory, UUID deploymentId) {
        return directory.resolve("plugin-deployments").resolve(deploymentId + ".json");
    }

    private static String durableJournal(Path directory) throws java.io.IOException {
        try (Stream<Path> files = Files.list(directory.resolve("plugin-deployments"))) {
            StringBuilder text = new StringBuilder();
            for (Path file : files.toList()) text.append(Files.readString(file, StandardCharsets.UTF_8));
            return text.toString();
        }
    }

    private static void assertUntouchedDeploymentFiles(Path directory, Map<UUID, String> expected) throws Exception {
        for (Map.Entry<UUID, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(deploymentFile(directory, entry.getKey()), StandardCharsets.UTF_8));
        }
    }

    private static final class FakeRegistry implements NodeRegistry {
        private final Map<String, NodeStatus> nodes = new HashMap<>();
        private int findCount;
        private int callbackFind;
        private Runnable findCallback;

        void add(String nodeId, UUID session, Set<String> capabilities) {
            add(nodeId, session, capabilities, true);
        }

        void add(String nodeId, UUID session, Set<String> capabilities, boolean online) {
            nodes.put(nodeId, new NodeStatus(nodeId, session, nodeId, "BUKKIT", "7.1.2-SNAPSHOT", 1,
                    capabilities, capabilities, Set.of(), List.of(), 0, Instant.EPOCH, Instant.EPOCH, online));
        }

        void remove(String nodeId) { nodes.remove(nodeId); }

        void runBeforeFind(int findNumber, Runnable callback) {
            findCount = 0;
            callbackFind = findNumber;
            findCallback = callback;
        }

        @Override public NodeStatus find(String nodeId) {
            findCount++;
            if (findCallback != null && findCount == callbackFind) {
                Runnable callback = findCallback;
                findCallback = null;
                callback.run();
            }
            return nodes.get(nodeId);
        }

        @Override public <T> T withSession(String nodeId, UUID sessionId,
                                           java.util.function.Function<NodeStatus, T> action) {
            NodeStatus node = nodes.get(nodeId);
            if (node == null) throw new ValidationException("NODE_NOT_FOUND", "Node was not registered", List.of());
            if (!node.sessionId().equals(sessionId)) throw new ValidationException("SESSION_MISMATCH",
                    "Node session does not match current registration", List.of());
            return action.apply(node);
        }

        @Override public RegistrationResult register(NodeRegistration registration) { throw new UnsupportedOperationException(); }
        @Override public NodeStatus heartbeat(String nodeId, Heartbeat heartbeat) { throw new UnsupportedOperationException(); }
        @Override public SnapshotResult replacePresence(String nodeId, PresenceSnapshot snapshot) { throw new UnsupportedOperationException(); }
        @Override public List<NodeStatus> list(int offset, int limit) { return List.copyOf(nodes.values()); }
        @Override public RegistryPage page(int offset, int limit, Long expectedRevision) { throw new UnsupportedOperationException(); }
        @Override public void requireSession(String nodeId, UUID sessionId) { throw new UnsupportedOperationException(); }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
