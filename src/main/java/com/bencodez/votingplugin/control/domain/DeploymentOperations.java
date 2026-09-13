package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.DurableFiles;
import com.bencodez.votingplugin.control.protocol.DeploymentRequest;
import com.bencodez.votingplugin.control.protocol.DeploymentResult;
import com.bencodez.votingplugin.control.protocol.DeploymentTask;
import com.bencodez.votingplugin.control.protocol.DeploymentTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinator for the narrow plugin deployment capability. It only stages a
 * verified artifact on an explicitly selected node; it never writes to a node
 * and never starts or restarts a server itself.
 */
public final class DeploymentOperations {
    public static final String CAPABILITY = DeploymentRequest.CAPABILITY;
    public static final Duration LEASE = Duration.ofMinutes(2);
    public static final Duration ACTIVE_RETENTION = Duration.ofMinutes(15);
    public static final Duration COMPLETE_RETENTION = Duration.ofMinutes(30);
    public static final int MAX_RETAINED = 100;
    static final int MAX_PRUNE_TRANSITIONS = 100;
    private static final int MAX_JOURNAL_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DEPLOYMENT_BYTES = 64 * 1024;
    private static final int MAX_MESSAGE_CHARS = 500;
    private static final Set<String> FAILURE_CODES = Set.of(
            "ARTIFACT_NOT_FOUND", "HASH_MISMATCH", "SIZE_MISMATCH", "INVALID_ARTIFACT",
            "UNSUPPORTED", "DOWNLOAD_FAILED", "STAGING_FAILED", "WRITE_FAILED", "RESTART_FAILED",
            "RESTART_TIMEOUT", "CAPABILITY_LOST", "CANCELLED", "TIMEOUT", "DEPLOYMENT_FAILED",
            "CONTROL_RESTARTED", "INTERNAL_ERROR");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final NodeRegistry registry;
    private final ConfigurationAuditLog audit;
    private final Clock clock;
    /** The pre-per-deployment journal, retained only to import existing installations. */
    private final Path journal;
    private final Path index;
    private final Path deploymentDirectory;
    private final LinkedHashMap<UUID, StoredDeployment> deployments = new LinkedHashMap<>();

    public DeploymentOperations(NodeRegistry registry, Clock clock) {
        this(registry, null, null, clock, false);
    }

    public DeploymentOperations(NodeRegistry registry, ConfigurationAuditLog audit, Clock clock) {
        this(registry, audit, null, clock, false);
    }

    public DeploymentOperations(NodeRegistry registry, Path dataDirectory, Clock clock) throws IOException {
        this(registry, null, dataDirectory, clock);
    }

    public DeploymentOperations(NodeRegistry registry, ConfigurationAuditLog audit, Path dataDirectory,
                                 Clock clock) throws IOException {
        this(registry, audit, dataDirectory, clock, true);
    }

    public DeploymentOperations(NodeRegistry registry, ConfigurationAuditLog audit, Clock clock,
                                 Path dataDirectory) throws IOException {
        this(registry, audit, dataDirectory, clock, true);
    }

    private DeploymentOperations(NodeRegistry registry, ConfigurationAuditLog audit, Path dataDirectory,
                                 Clock clock, boolean checked) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.audit = audit;
        this.clock = Objects.requireNonNull(clock, "clock");
        try {
            this.journal = dataDirectory == null ? null : prepareJournal(dataDirectory);
            this.index = dataDirectory == null ? null : prepareIndex(dataDirectory);
            this.deploymentDirectory = dataDirectory == null ? null : prepareDeploymentDirectory(dataDirectory);
            if (journal != null) loadJournal();
        } catch (IOException failure) {
            throw new IllegalStateException("Deployment journal could not be opened", failure);
        }
    }

    /** Creates a deployment, pinning each selected node's current session. */
    public synchronized DeploymentResult create(DeploymentRequest request) {
        Objects.requireNonNull(request, "request");
        prune();
        LinkedHashMap<UUID, StoredDeployment> prior = copyDeployments();
        try {
        List<Target> targets = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (String nodeId : request.nodeIds()) {
            NodeStatus node = registry.find(nodeId);
            if (node == null || !node.online() || !node.acceptedCapabilities().contains(CAPABILITY)) {
                unavailable.add(nodeId);
            } else {
                targets.add(new Target(nodeId, node.sessionId()));
            }
        }
        if (!unavailable.isEmpty()) {
            throw new ValidationException("NODE_UNAVAILABLE",
                    "Every deployment target must be online and accept plugin.deploy.v1", unavailable);
        }
        if (deployments.size() >= MAX_RETAINED) evictOldestCompleted();
        if (deployments.size() >= MAX_RETAINED) {
            throw new ValidationException("OPERATION_LIMIT", "Too many retained deployments", List.of());
        }
        StoredDeployment stored = new StoredDeployment(UUID.randomUUID(), request.artifactId(), request.sha256(),
                request.size(), clock.instant(), targets);
        deployments.put(stored.id, stored);
        if (deploymentDirectory != null) {
            persistDeployment(stored.id);
            persistIndex();
            for (UUID id : prior.keySet()) {
                if (!deployments.containsKey(id)) deleteDeployment(id);
            }
        }
        if (audit != null) audit.append("DEPLOYMENT_CREATED", stored.id, null, "QUEUED");
        return view(stored);
        } catch (RuntimeException failure) {
            restore(prior, failure);
            throw failure;
        }
    }

    /** Returns the oldest unclaimed work for this exact node session. */
    public synchronized DeploymentTask claim(String nodeId, UUID sessionId) {
        return registry.withSession(nodeId, sessionId, node -> claimCurrent(nodeId, node));
    }

    private DeploymentTask claimCurrent(String nodeId, NodeStatus node) {
        prune();
        LinkedHashMap<UUID, StoredDeployment> prior = copyDeployments();
        UUID changedDeployment = null;
        try {
        if (!node.online() || !node.acceptedCapabilities().contains(CAPABILITY)) return null;
        Instant now = clock.instant();
        for (StoredDeployment deployment : deployments.values()) {
            Target target = deployment.targets.get(nodeId);
            if (target == null || target.state.equals("SUCCEEDED") || target.state.equals("FAILED")) continue;
            if (!target.pinnedSession.equals(node.sessionId())) {
                changedDeployment = deployment.id;
                failUnavailable(deployment, target, "Node reconnected before this artifact was staged");
                prior = copyDeployments();
                continue;
            }
            if (target.state.equals("IN_PROGRESS")) {
                if (target.leasedAt != null && now.isBefore(target.leasedAt.plus(LEASE))) return null;
                target.state = "QUEUED";
                target.leasedAt = null;
                target.attemptId = null;
            }
            if (!target.pinnedSession.equals(node.sessionId())) continue;
            changedDeployment = deployment.id;
            target.state = "IN_PROGRESS";
            target.leasedAt = now;
            target.attemptId = UUID.randomUUID();
            commit(deployment.id, nodeId, "DEPLOYMENT_CLAIMED", "IN_PROGRESS");
            return new DeploymentTask(deployment.id, deployment.artifactId, deployment.sha256, deployment.size,
                    target.attemptId);
        }
        return null;
        } catch (RuntimeException failure) {
            if (changedDeployment == null) restore(prior, failure);
            else restore(prior, failure, changedDeployment);
            throw failure;
        }
    }

    /** Completes an attempt only while its session, attempt and two-minute lease remain current. */
    public synchronized DeploymentResult complete(UUID deploymentId, String nodeId, DeploymentTaskResult result) {
        if (result == null) throw invalid("deployment result is required");
        return registry.withSession(nodeId, result.sessionId(), node -> completeCurrent(deploymentId, node, result));
    }

    /**
     * Authorizes a node artifact download without exposing the artifact bytes
     * or metadata to an unrelated session. The caller must present the exact
     * attempt returned by {@link #claim(String, UUID)} while its lease remains
     * active.
     */
    public synchronized DeploymentTask authorizeArtifact(UUID deploymentId, String nodeId, UUID sessionId,
                                                          UUID attemptId) {
        if (deploymentId == null || attemptId == null) throw invalid("deploymentId and attemptId are required");
        return registry.withSession(nodeId, sessionId, node -> {
            if (!node.online() || !node.acceptedCapabilities().contains(CAPABILITY)) {
                throw new ValidationException("NODE_UNAVAILABLE", "Node cannot download this deployment", List.of());
            }
            StoredDeployment deployment = deployments.get(deploymentId);
            if (deployment == null) {
                throw new ValidationException("OPERATION_NOT_FOUND", "Deployment was not found", List.of());
            }
            Target target = deployment.targets.get(nodeId);
            if (target == null || !target.pinnedSession.equals(sessionId)) {
                throw new ValidationException("SESSION_MISMATCH", "Deployment is not authorized for this session", List.of());
            }
            if (!"IN_PROGRESS".equals(target.state) || target.leasedAt == null
                    || !clock.instant().isBefore(target.leasedAt.plus(LEASE))) {
                throw new ValidationException("TASK_LEASE_EXPIRED", "Deployment lease expired", List.of());
            }
            if (!attemptId.equals(target.attemptId)) {
                throw new ValidationException("TASK_NOT_CLAIMED", "Deployment attempt does not match", List.of());
            }
            return new DeploymentTask(deployment.id, deployment.artifactId, deployment.sha256,
                    deployment.size, target.attemptId);
        });
    }

    private DeploymentResult completeCurrent(UUID deploymentId, NodeStatus node, DeploymentTaskResult result) {
        prune();
        LinkedHashMap<UUID, StoredDeployment> prior = copyDeployments();
        try {
        StoredDeployment deployment = deployments.get(deploymentId);
        if (deployment == null) throw new ValidationException("OPERATION_NOT_FOUND", "Deployment was not found", List.of());
        Target target = deployment.targets.get(node.nodeId());
        if (target == null) throw new ValidationException("NODE_NOT_TARGETED", "Node was not selected for deployment", List.of());
        if (!target.pinnedSession.equals(result.sessionId())) {
            throw new ValidationException("SESSION_MISMATCH", "Deployment was claimed by another node session", List.of());
        }
        if (!target.state.equals("IN_PROGRESS")) {
            throw new ValidationException("TASK_NOT_CLAIMED", "Deployment was not claimed", List.of());
        }
        if (target.leasedAt == null || !clock.instant().isBefore(target.leasedAt.plus(LEASE))) {
            throw new ValidationException("TASK_LEASE_EXPIRED", "Deployment lease expired", List.of());
        }
        if (!Objects.equals(target.attemptId, result.attemptId())) {
            throw new ValidationException("TASK_NOT_CLAIMED", "Deployment attempt does not match", List.of());
        }
        validateResult(result);
        target.result = safeResult(result);
        target.state = result.success() ? "SUCCEEDED" : "FAILED";
        target.leasedAt = null;
        target.attemptId = null;
        commit(deployment.id, node.nodeId(), "DEPLOYMENT_COMPLETED", result.code());
        return view(deployment);
        } catch (RuntimeException failure) {
            restore(prior, failure, deploymentId);
            throw failure;
        }
    }

    /** Creates a new operation for failed targets that are eligible now; successes are never copied. */
    public synchronized DeploymentResult retry(UUID deploymentId) {
        prune();
        StoredDeployment original = deployments.get(deploymentId);
        if (original == null) throw new ValidationException("OPERATION_NOT_FOUND", "Deployment was not found", List.of());
        List<String> eligible = new ArrayList<>();
        for (Target target : original.targets.values()) {
            if (!target.state.equals("FAILED")) continue;
            NodeStatus node = registry.find(target.nodeId);
            if (node != null && node.online() && node.acceptedCapabilities().contains(CAPABILITY)) {
                eligible.add(target.nodeId);
            }
        }
        if (eligible.isEmpty()) {
            throw new ValidationException("NO_RETRYABLE_TARGETS", "No failed deployment target is currently eligible", List.of());
        }
        return create(new DeploymentRequest(original.artifactId, original.sha256, original.size, eligible));
    }

    public synchronized DeploymentResult get(UUID deploymentId) {
        prune();
        StoredDeployment deployment = deployments.get(deploymentId);
        if (deployment == null) throw new ValidationException("OPERATION_NOT_FOUND", "Deployment was not found", List.of());
        return view(deployment);
    }

    public synchronized List<DeploymentResult> list(int offset, int limit) {
        prune();
        if (offset < 0 || limit < 1 || limit > 100) throw invalid("offset must be >= 0 and limit must be between 1 and 100");
        List<StoredDeployment> newestFirst = new ArrayList<>(deployments.values());
        Collections.reverse(newestFirst);
        return newestFirst.stream().skip(offset).limit(limit).map(this::view).toList();
    }

    /** Artifact IDs still referenced by the retained durable journal. */
    public synchronized Set<String> referencedArtifactIds() {
        prune();
        return deployments.values().stream().map(deployment -> deployment.artifactId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void validateResult(DeploymentTaskResult result) {
        if (result.attemptId() == null) throw invalid("attemptId is required");
        if (result.code() == null || result.code().isBlank() || result.code().length() > 64
                || !result.code().matches("[A-Z][A-Z0-9_]{1,63}")) throw invalid("result code is invalid");
        if (result.message() != null && (result.message().length() > MAX_MESSAGE_CHARS
                || result.message().chars().anyMatch(Character::isISOControl))) throw invalid("result message is invalid");
        if (result.success() && !"RESTART_REQUIRED".equals(result.code())) {
            throw invalid("successful deployment results must use RESTART_REQUIRED");
        }
        if (!result.success() && !FAILURE_CODES.contains(result.code())) {
            throw invalid("result code is not supported");
        }
    }

    private static DeploymentTaskResult safeResult(DeploymentTaskResult result) {
        if (result == null) return null;
        String message = switch (result.code()) {
            case "RESTART_REQUIRED" -> "Artifact staged successfully; restart required";
            case "ARTIFACT_NOT_FOUND" -> "The verified artifact is no longer available";
            case "HASH_MISMATCH" -> "The staged artifact hash did not match";
            case "SIZE_MISMATCH" -> "The staged artifact size did not match";
            case "INVALID_ARTIFACT" -> "The node rejected the artifact as invalid";
            case "UNSUPPORTED" -> "The node does not support this deployment";
            case "DOWNLOAD_FAILED" -> "The node could not download the verified artifact";
            case "STAGING_FAILED" -> "The node could not stage the artifact";
            case "WRITE_FAILED" -> "The node could not write the staged artifact";
            case "RESTART_FAILED" -> "The node reported a restart failure";
            case "RESTART_TIMEOUT" -> "The node reported a restart timeout";
            case "CAPABILITY_LOST" -> "The node session or deployment capability changed";
            case "CANCELLED" -> "The deployment was cancelled";
            case "TIMEOUT" -> "The deployment did not finish before its deadline";
            case "DEPLOYMENT_FAILED" -> "The node could not complete the deployment";
            case "CONTROL_RESTARTED" -> "Control restarted before staging completed";
            case "INTERNAL_ERROR" -> "The node reported an internal deployment error";
            default -> "The node reported a deployment result";
        };
        return new DeploymentTaskResult(result.sessionId(), result.success(), result.code(), message,
                result.attemptId());
    }

    private DeploymentResult view(StoredDeployment deployment) {
        List<DeploymentResult.NodeResult> nodes = deployment.targets.values().stream()
                .map(target -> new DeploymentResult.NodeResult(target.nodeId, target.pinnedSession, target.state,
                        safeResult(target.result), target.leasedAt, target.attemptId)).toList();
        return new DeploymentResult(deployment.id, deployment.artifactId, deployment.sha256, deployment.size,
                state(deployment), deployment.createdAt, nodes);
    }

    private static String state(StoredDeployment deployment) {
        boolean queued = false, running = false, success = false, failure = false;
        for (Target target : deployment.targets.values()) {
            switch (target.state) {
                case "QUEUED" -> queued = true;
                case "IN_PROGRESS" -> running = true;
                case "SUCCEEDED" -> success = true;
                case "FAILED" -> failure = true;
                default -> throw new IllegalStateException("unknown deployment target state");
            }
        }
        if (running) return "RUNNING";
        if (queued) return success || failure ? "PARTIAL" : "QUEUED";
        if (success && failure) return "PARTIALLY_FAILED";
        return success ? "SUCCEEDED" : "FAILED";
    }

    private void commit(UUID operationId, String nodeId, String action, String outcome) {
        if (deploymentDirectory != null) persistDeployment(operationId);
        if (audit != null) audit.append(action, operationId, nodeId, outcome);
    }

    private LinkedHashMap<UUID, StoredDeployment> copyDeployments() {
        LinkedHashMap<UUID, StoredDeployment> result = new LinkedHashMap<>();
        for (StoredDeployment deployment : deployments.values()) {
            List<Target> targets = new ArrayList<>();
            for (Target original : deployment.targets.values()) {
                Target target = new Target(original.nodeId, original.pinnedSession);
                target.state = original.state;
                target.leasedAt = original.leasedAt;
                target.attemptId = original.attemptId;
                target.result = original.result;
                targets.add(target);
            }
            result.put(deployment.id, new StoredDeployment(deployment.id, deployment.artifactId,
                    deployment.sha256, deployment.size, deployment.createdAt, targets));
        }
        return result;
    }

    private void restore(LinkedHashMap<UUID, StoredDeployment> prior, RuntimeException failure,
                         UUID... changedDeployments) {
        deployments.clear();
        deployments.putAll(prior);
        if (deploymentDirectory != null) {
            try {
                persistDeployments(Set.of(changedDeployments));
                persistIndex();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void restore(LinkedHashMap<UUID, StoredDeployment> prior, RuntimeException failure) {
        Set<UUID> changed = new java.util.LinkedHashSet<>(deployments.keySet());
        changed.addAll(prior.keySet());
        restore(prior, failure, changed.toArray(UUID[]::new));
    }

    private void prune() {
        Instant now = clock.instant();
        List<PendingFailure> failures = new ArrayList<>();
        outer: for (StoredDeployment deployment : deployments.values()) {
            for (Target target : deployment.targets.values()) {
                if ("SUCCEEDED".equals(target.state) || "FAILED".equals(target.state)) continue;
                NodeStatus node = registry.find(target.nodeId);
                if (node != null && node.online() && (!target.pinnedSession.equals(node.sessionId())
                        || !node.acceptedCapabilities().contains(CAPABILITY))) {
                    failures.add(new PendingFailure(deployment.id, target.nodeId, "CAPABILITY_LOST",
                            "Node session or deployment capability changed before staging completed"));
                } else {
                    boolean activeLease = "IN_PROGRESS".equals(target.state) && target.leasedAt != null
                            && now.isBefore(target.leasedAt.plus(LEASE));
                    if (!activeLease && !now.isBefore(deployment.createdAt.plus(ACTIVE_RETENTION))) {
                        failures.add(new PendingFailure(deployment.id, target.nodeId, "TIMEOUT",
                                "Node did not stage the artifact before the deployment deadline"));
                    }
                }
                if (failures.size() >= MAX_PRUNE_TRANSITIONS) break outer;
            }
        }
        applyPruneFailures(failures);

        LinkedHashMap<UUID, StoredDeployment> prior = copyDeployments();
        Instant cutoff = clock.instant().minus(COMPLETE_RETENTION);
        Set<UUID> removed = new java.util.LinkedHashSet<>();
        try {
            deployments.values().removeIf(deployment -> {
                boolean remove = isTerminal(deployment) && deployment.createdAt.isBefore(cutoff);
                if (remove) removed.add(deployment.id);
                return remove;
            });
            evictCompleted();
            for (UUID id : prior.keySet()) {
                if (!deployments.containsKey(id)) removed.add(id);
            }
            if (!removed.isEmpty() && deploymentDirectory != null) {
                persistIndex();
                persistDeployments(removed);
            }
        } catch (RuntimeException failure) {
            restore(prior, failure, removed.toArray(UUID[]::new));
            throw failure;
        }
    }

    private void applyPruneFailures(List<PendingFailure> failures) {
        if (failures.isEmpty()) return;
        LinkedHashMap<UUID, StoredDeployment> prior = copyDeployments();
        Set<UUID> changed = failures.stream().map(PendingFailure::deploymentId)
                .collect(java.util.stream.Collectors.toSet());
        for (PendingFailure failure : failures) {
            markFailed(deployments.get(failure.deploymentId).targets.get(failure.nodeId),
                    failure.code, failure.message);
        }
        try {
            if (deploymentDirectory != null) persistDeployments(failures.stream()
                    .map(PendingFailure::deploymentId).collect(java.util.stream.Collectors.toSet()));
        } catch (RuntimeException failure) {
            restore(prior, failure, changed.toArray(UUID[]::new));
            throw failure;
        }
        if (audit == null) return;
        for (int index = 0; index < failures.size(); index++) {
            PendingFailure transition = failures.get(index);
            try {
                audit.append("DEPLOYMENT_CANCELLED", transition.deploymentId, transition.nodeId, transition.code);
            } catch (RuntimeException failure) {
                for (int rollback = index; rollback < failures.size(); rollback++) {
                    PendingFailure unapplied = failures.get(rollback);
                    Target current = deployments.get(unapplied.deploymentId).targets.get(unapplied.nodeId);
                    Target previous = prior.get(unapplied.deploymentId).targets.get(unapplied.nodeId);
                    copyTargetState(previous, current);
                }
                if (deploymentDirectory != null) {
                    try { persistDeployments(failures.subList(index, failures.size()).stream()
                            .map(PendingFailure::deploymentId).collect(java.util.stream.Collectors.toSet())); }
                    catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                }
                throw failure;
            }
        }
    }

    private void failUnavailable(StoredDeployment deployment, Target target, String message) {
        failTarget(deployment, target, "CAPABILITY_LOST", message);
    }

    private void failTarget(StoredDeployment deployment, Target target, String code, String message) {
        markFailed(target, code, message);
        commit(deployment.id, target.nodeId, "DEPLOYMENT_CANCELLED", code);
    }

    private void markFailed(Target target, String code, String message) {
        UUID attempt = target.attemptId == null ? UUID.randomUUID() : target.attemptId;
        target.state = "FAILED";
        target.result = new DeploymentTaskResult(target.pinnedSession, false, code, message, attempt);
        target.leasedAt = null;
        target.attemptId = null;
    }

    private static void copyTargetState(Target source, Target destination) {
        destination.state = source.state;
        destination.leasedAt = source.leasedAt;
        destination.attemptId = source.attemptId;
        destination.result = source.result;
    }

    private void evictCompleted() {
        while (deployments.size() > MAX_RETAINED) {
            if (!evictOldestCompleted()) return;
        }
    }

    private boolean evictOldestCompleted() {
        UUID candidate = deployments.entrySet().stream().filter(entry -> isTerminal(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (candidate == null) return false;
        deployments.remove(candidate);
        return true;
    }

    private static boolean isTerminal(StoredDeployment deployment) {
        return deployment.targets.values().stream().allMatch(target -> target.state.equals("SUCCEEDED") || target.state.equals("FAILED"));
    }

    private static ValidationException invalid(String message) {
        return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(message));
    }

    private static Path prepareJournal(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        if (Files.isSymbolicLink(dataDirectory) || !Files.isDirectory(dataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Deployment data directory is not a real directory");
        }
        Path file = dataDirectory.resolve("plugin-deployments.json");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))) {
            throw new IOException("Deployment journal is not a regular file");
        }
        return file;
    }

    private static Path prepareDeploymentDirectory(Path dataDirectory) throws IOException {
        Path directory = dataDirectory.resolve("plugin-deployments");
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Deployment journal directory is not a real directory");
            }
        } else {
            Files.createDirectory(directory);
        }
        return directory;
    }

    private static Path prepareIndex(Path dataDirectory) throws IOException {
        Path file = dataDirectory.resolve("plugin-deployments-index.json");
        checkRegularFile(file, "Deployment journal index");
        return file;
    }

    private void persistDeployment(UUID deploymentId) {
        StoredDeployment deployment = deployments.get(deploymentId);
        if (deployment == null) {
            deleteDeployment(deploymentId);
            return;
        }
        try {
            byte[] bytes = JSON.writeValueAsBytes(persistedValue(deployment));
            if (bytes.length > MAX_DEPLOYMENT_BYTES) throw new IOException("Deployment journal entry exceeds its bound");
            Path file = deploymentFile(deploymentId);
            writeAtomically(file, bytes, "Deployment journal entry");
        } catch (IOException e) {
            throw new IllegalStateException("Deployment journal entry could not be written", e);
        }
    }

    private void persistDeployments(Set<UUID> deploymentIds) {
        for (UUID deploymentId : deploymentIds) persistDeployment(deploymentId);
    }

    private void persistIndex() {
        try {
            byte[] bytes = JSON.writeValueAsBytes(deployments.keySet());
            if (bytes.length > MAX_RETAINED * 40) throw new IOException("Deployment journal index exceeds its bound");
            writeAtomically(index, bytes, "Deployment journal index");
        } catch (IOException e) {
            throw new IllegalStateException("Deployment journal index could not be written", e);
        }
    }

    private static void writeAtomically(Path file, byte[] bytes, String description) throws IOException {
        checkRegularFile(file, description);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        if (Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) {
            checkRegularFile(temp, description + " temporary file");
            Files.delete(temp);
        }
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        DurableFiles.forceDirectory(file.getParent());
    }

    private void deleteDeployment(UUID deploymentId) {
        Path file = deploymentFile(deploymentId);
        try {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
            checkRegularFile(file, "Deployment journal entry");
            Files.delete(file);
            DurableFiles.forceDirectory(deploymentDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Deployment journal entry could not be removed", e);
        }
    }

    private Path deploymentFile(UUID deploymentId) {
        return deploymentDirectory.resolve(deploymentId + ".json");
    }

    private static void checkRegularFile(Path file, String description) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException(description + " is not a regular file");
        }
    }

    private static Map<String, Object> persistedValue(StoredDeployment deployment) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", deployment.id);
        value.put("artifactId", deployment.artifactId);
        value.put("sha256", deployment.sha256);
        value.put("size", deployment.size);
        value.put("createdAt", deployment.createdAt);
        List<Map<String, Object>> targets = new ArrayList<>();
        for (Target target : deployment.targets.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", target.nodeId);
            item.put("sessionId", target.pinnedSession);
            item.put("state", target.state);
            item.put("leasedAt", target.leasedAt);
            item.put("attemptId", target.attemptId);
            item.put("result", target.result);
            targets.add(item);
        }
        value.put("targets", targets);
        return value;
    }

    private void loadJournal() throws IOException {
        if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
            loadLegacyJournal();
            normalizeLoadedEntries();
            clearPartialMigrationEntries();
            persistDeployments(new java.util.LinkedHashSet<>(deployments.keySet()));
            persistIndex();
            Files.delete(journal);
            DurableFiles.forceDirectory(journal.getParent());
        } else {
            loadDeploymentDirectory();
            Set<UUID> changed = normalizeLoadedEntries();
            if (!changed.isEmpty()) persistDeployments(changed);
        }
    }

    private void loadLegacyJournal() throws IOException {
        byte[] bytes;
        try (var channel = Files.newByteChannel(journal, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            if (Files.size(journal) > MAX_JOURNAL_BYTES) throw new IOException("Deployment journal exceeds its bound");
            ByteBuffer buffer = ByteBuffer.allocate((int) Files.size(journal));
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            if (buffer.hasRemaining()) throw new IOException("Deployment journal could not be read");
            bytes = buffer.array();
        }
        if (bytes.length > MAX_JOURNAL_BYTES) throw new IOException("Deployment journal exceeds its bound");
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        JsonNode root;
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            root = JSON.readTree(parser);
            if (parser.nextToken() != null) throw new IOException("Deployment journal has trailing data");
        }
        if (root == null || !root.isArray() || root.size() > MAX_RETAINED) throw new IOException("Deployment journal is invalid");
        for (JsonNode item : root) {
            StoredDeployment deployment = parseDeployment(item);
            if (deployments.put(deployment.id, deployment) != null) throw new IOException("Duplicate deployment ID");
        }
    }

    private void loadDeploymentDirectory() throws IOException {
        if (!Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
            clearPartialMigrationEntries();
            return;
        }
        List<UUID> retained = loadIndex();
        Map<UUID, Path> entriesById = new LinkedHashMap<>();
        List<Path> staleEntries = new ArrayList<>();
        int bytes = 0;
        try (var entries = Files.newDirectoryStream(deploymentDirectory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".json.tmp") && isDeploymentFile(name.substring(0, name.length() - 4))) {
                    checkRegularFile(entry, "Deployment journal temporary file");
                    Files.delete(entry);
                    DurableFiles.forceDirectory(deploymentDirectory);
                    continue;
                }
                if (!isDeploymentFile(name)) throw new IOException("Deployment journal directory contains an unexpected file");
                checkRegularFile(entry, "Deployment journal entry");
                UUID id = UUID.fromString(name.substring(0, name.length() - 5));
                if (!retained.contains(id)) {
                    staleEntries.add(entry);
                    continue;
                }
                long size = Files.size(entry);
                if (size > MAX_DEPLOYMENT_BYTES || size > MAX_JOURNAL_BYTES - bytes) {
                    throw new IOException("Deployment journal exceeds its bound");
                }
                bytes += (int) size;
                if (entriesById.put(id, entry) != null) throw new IOException("Deployment journal is invalid");
            }
        }
        for (UUID id : retained) {
            Path entry = entriesById.get(id);
            if (entry == null) throw new IOException("Deployment journal entry is missing");
            StoredDeployment deployment = parseJournalFile(entry);
            if (!deployment.id.equals(id) || deployments.put(id, deployment) != null) {
                throw new IOException("Deployment journal is invalid");
            }
        }
        for (Path stale : staleEntries) Files.delete(stale);
        if (!staleEntries.isEmpty()) DurableFiles.forceDirectory(deploymentDirectory);
    }

    private List<UUID> loadIndex() throws IOException {
        long size = Files.size(index);
        if (size > MAX_RETAINED * 40) throw new IOException("Deployment journal index exceeds its bound");
        byte[] bytes;
        try (var channel = Files.newByteChannel(index, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            if (buffer.hasRemaining()) throw new IOException("Deployment journal index could not be read");
            bytes = buffer.array();
        }
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            JsonNode root = JSON.readTree(parser);
            if (parser.nextToken() != null || root == null || !root.isArray() || root.size() > MAX_RETAINED) {
                throw new IOException("Deployment journal index is invalid");
            }
            List<UUID> ids = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isTextual()) throw new IOException("Deployment journal index is invalid");
                UUID id = UUID.fromString(item.textValue());
                if (!id.toString().equals(item.textValue()) || ids.contains(id)) {
                    throw new IOException("Deployment journal index is invalid");
                }
                ids.add(id);
            }
            return ids;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Deployment journal index is invalid", invalid);
        }
    }

    /** The legacy array remains authoritative until every partial import file is safely discarded. */
    private void clearPartialMigrationEntries() throws IOException {
        try (var entries = Files.newDirectoryStream(deploymentDirectory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!isDeploymentFile(name) && !(name.endsWith(".json.tmp")
                        && isDeploymentFile(name.substring(0, name.length() - 4)))) {
                    throw new IOException("Deployment journal directory contains an unexpected file");
                }
                checkRegularFile(entry, "Deployment journal migration entry");
                Files.delete(entry);
            }
        }
        DurableFiles.forceDirectory(deploymentDirectory);
    }

    private static boolean isDeploymentFile(String name) {
        if (!name.endsWith(".json")) return false;
        try {
            return UUID.fromString(name.substring(0, name.length() - 5)).toString().concat(".json").equals(name);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private StoredDeployment parseJournalFile(Path file) throws IOException {
        byte[] bytes;
        try (var channel = Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            long size = Files.size(file);
            if (size > MAX_DEPLOYMENT_BYTES) throw new IOException("Deployment journal entry exceeds its bound");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            if (buffer.hasRemaining()) throw new IOException("Deployment journal entry could not be read");
            bytes = buffer.array();
        }
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            JsonNode value = JSON.readTree(parser);
            if (parser.nextToken() != null) throw new IOException("Deployment journal entry has trailing data");
            return parseDeployment(value);
        }
    }

    private Set<UUID> normalizeLoadedEntries() {
        Set<UUID> changedDeployments = new java.util.LinkedHashSet<>();
        for (StoredDeployment deployment : deployments.values()) {
            for (Target target : deployment.targets.values()) {
                if (target.result != null) {
                    DeploymentTaskResult safe = safeResult(target.result);
                    if (!safe.equals(target.result)) {
                        target.result = safe;
                        changedDeployments.add(deployment.id);
                    }
                }
                if (!"IN_PROGRESS".equals(target.state)) continue;
                UUID interruptedAttempt = target.attemptId;
                target.state = "FAILED";
                target.result = new DeploymentTaskResult(target.pinnedSession, false, "CONTROL_RESTARTED",
                        "Control restarted while this staging attempt was in progress", interruptedAttempt);
                target.leasedAt = null;
                target.attemptId = null;
                changedDeployments.add(deployment.id);
            }
        }
        return changedDeployments;
    }

    private StoredDeployment parseDeployment(JsonNode item) throws IOException {
        if (!item.isObject()) throw new IOException("Deployment journal entry is invalid");
        try {
            requireExactFields(item, Set.of("id", "artifactId", "sha256", "size", "createdAt", "targets"));
            UUID id = UUID.fromString(item.path("id").asText());
            String artifactId = item.path("artifactId").asText();
            String sha256 = item.path("sha256").asText();
            if (!artifactId.matches("[0-9a-f]{64}") || !artifactId.equals(sha256)) {
                throw new IOException("Invalid artifact identity");
            }
            long size = item.path("size").asLong(-1);
            Instant createdAt = Instant.parse(item.path("createdAt").asText());
            validatePersistedInstant(createdAt, ACTIVE_RETENTION);
            JsonNode targetsNode = item.path("targets");
            if (!targetsNode.isArray() || targetsNode.isEmpty() || targetsNode.size() > 100) throw new IOException("Invalid targets");
            List<Target> targets = new ArrayList<>();
            for (JsonNode node : targetsNode) {
                requireExactFields(node, Set.of("nodeId", "sessionId", "state", "leasedAt", "attemptId", "result"));
                Target target = new Target(node.path("nodeId").asText(), UUID.fromString(node.path("sessionId").asText()));
                target.state = node.path("state").asText();
                if (!List.of("QUEUED", "IN_PROGRESS", "SUCCEEDED", "FAILED").contains(target.state)) throw new IOException("Invalid target state");
                target.leasedAt = node.path("leasedAt").isNull() ? null : Instant.parse(node.path("leasedAt").asText());
                if (target.leasedAt != null) validatePersistedInstant(target.leasedAt, LEASE);
                target.attemptId = node.path("attemptId").isNull() ? null : UUID.fromString(node.path("attemptId").asText());
                if (node.has("result") && !node.path("result").isNull()) {
                    target.result = JSON.treeToValue(node.path("result"), DeploymentTaskResult.class);
                }
                boolean queued = "QUEUED".equals(target.state);
                boolean inProgress = "IN_PROGRESS".equals(target.state);
                boolean terminal = "SUCCEEDED".equals(target.state) || "FAILED".equals(target.state);
                if (queued && (target.leasedAt != null || target.attemptId != null || target.result != null)
                        || inProgress && (target.leasedAt == null || target.attemptId == null || target.result != null)
                        || terminal && (target.leasedAt != null || target.attemptId != null || target.result == null)) {
                    throw new IOException("Invalid target state fields");
                }
                if (terminal) {
                    validateResult(target.result);
                    if (!target.pinnedSession.equals(target.result.sessionId())
                            || ("SUCCEEDED".equals(target.state) != target.result.success())) {
                        throw new IOException("Invalid target result");
                    }
                }
                targets.add(target);
            }
            DeploymentRequest request = new DeploymentRequest(artifactId, sha256, size,
                    targets.stream().map(target -> target.nodeId).toList());
            return new StoredDeployment(id, request.artifactId(), request.sha256(), request.size(), createdAt, targets);
        } catch (RuntimeException e) {
            throw new IOException("Deployment journal entry is invalid", e);
        }
    }

    private void validatePersistedInstant(Instant value, Duration arithmeticBound) throws IOException {
        try {
            if (value.isAfter(clock.instant())) throw new IOException("Deployment journal timestamp is in the future");
            value.plus(arithmeticBound);
        } catch (java.time.DateTimeException failure) {
            throw new IOException("Deployment journal timestamp is out of range", failure);
        }
    }

    private static void requireExactFields(JsonNode value, Set<String> expected) throws IOException {
        if (!value.isObject()) throw new IOException("Deployment journal entry is invalid");
        Set<String> actual = new java.util.HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new IOException("Deployment journal entry is invalid");
    }

    private record PendingFailure(UUID deploymentId, String nodeId, String code, String message) { }

    private static final class StoredDeployment {
        private final UUID id;
        private final String artifactId;
        private final String sha256;
        private final long size;
        private final Instant createdAt;
        private final LinkedHashMap<String, Target> targets = new LinkedHashMap<>();

        private StoredDeployment(UUID id, String artifactId, String sha256, long size, Instant createdAt, List<Target> targets) {
            this.id = id;
            this.artifactId = artifactId;
            this.sha256 = sha256;
            this.size = size;
            this.createdAt = createdAt;
            for (Target target : targets) {
                if (this.targets.put(target.nodeId, target) != null) throw new IllegalArgumentException("duplicate target");
            }
        }
    }

    private static final class Target {
        private final String nodeId;
        private final UUID pinnedSession;
        private String state = "QUEUED";
        private Instant leasedAt;
        private UUID attemptId;
        private DeploymentTaskResult result;

        private Target(String nodeId, UUID pinnedSession) {
            this.nodeId = nodeId;
            this.pinnedSession = pinnedSession;
        }
    }
}
