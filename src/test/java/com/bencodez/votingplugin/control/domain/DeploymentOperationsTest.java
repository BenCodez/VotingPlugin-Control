package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertFalse(Files.readString(directory.resolve("plugin-deployments.json"), StandardCharsets.UTF_8)
                .contains("/private/server/path/token"));
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
        assertEquals(Set.of("VotingPlugin.jar"), operations.referencedArtifactIds());

        clock.advance(DeploymentOperations.COMPLETE_RETENTION.plusSeconds(1));
        assertEquals(Set.of(), operations.referencedArtifactIds());
    }

    @Test
    void retentionEvictsTheOldestCompletedDeploymentAndListsNewestFirst() {
        FakeRegistry registry = new FakeRegistry();
        registry.add("backend", SESSION_A, Set.of(DeploymentRequest.CAPABILITY));
        DeploymentOperations operations = new DeploymentOperations(registry, clock);
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
    }

    @Test
    void journalRejectsDuplicateFieldsAtEveryPersistedObjectLevel(@TempDir Path directory) throws Exception {
        String id = "10000000-0000-0000-0000-000000000001";
        String attempt = "20000000-0000-0000-0000-000000000001";
        String prefix = "[{\"id\":\"" + id + "\",\"artifactId\":\"VotingPlugin.jar\","
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

    private static DeploymentRequest request(String... nodes) {
        return new DeploymentRequest("VotingPlugin.jar", SHA, 1234, List.of(nodes));
    }

    private static final class FakeRegistry implements NodeRegistry {
        private final Map<String, NodeStatus> nodes = new HashMap<>();

        void add(String nodeId, UUID session, Set<String> capabilities) {
            nodes.put(nodeId, new NodeStatus(nodeId, session, nodeId, "BUKKIT", "7.1.2-SNAPSHOT", 1,
                    capabilities, capabilities, Set.of(), List.of(), 0, Instant.EPOCH, Instant.EPOCH, true));
        }

        void remove(String nodeId) { nodes.remove(nodeId); }

        @Override public NodeStatus find(String nodeId) { return nodes.get(nodeId); }

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
