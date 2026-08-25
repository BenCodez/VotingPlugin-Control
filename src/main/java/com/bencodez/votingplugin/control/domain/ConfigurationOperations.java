package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.bencodez.votingplugin.control.protocol.ProxyRoutingConfiguration;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** In-memory, bounded coordinator for outbound node configuration tasks. */
public final class ConfigurationOperations implements AutoCloseable {
    public static final String CAPABILITY = "config.proxy-routing.v1";
    public static final String FILE_CAPABILITY = "config.files.v1";
    public static final String QUICK_SETUP_CAPABILITY = "config.quick-setup.v1";
    private static final int MAX_OPERATIONS = 1000;
    private static final int MAX_FILE_OPERATIONS = 16;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration ACTIVE_RETENTION = Duration.ofMinutes(15);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final NodeRegistry registry;
    private final ConfigurationAuditLog audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<UUID, StoredOperation> operations = new LinkedHashMap<>();

    public ConfigurationOperations(NodeRegistry registry, ConfigurationAuditLog audit, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized OperationView createRead(List<String> nodeIds) {
        return createRead(nodeIds, ManagedConfiguration.proxy(new ProxyRoutingConfiguration(false, List.of())));
    }

    public synchronized OperationView createPreview(List<String> nodeIds, ProxyRoutingConfiguration configuration) {
        return createPreview(nodeIds, configuration == null ? null : ManagedConfiguration.proxy(configuration));
    }

    public synchronized OperationView createRead(List<String> nodeIds, ManagedConfiguration selector) {
        if (selector == null) selector = ManagedConfiguration.proxy(new ProxyRoutingConfiguration(false, List.of()));
        return create("READ", validateTargets(nodeIds, selector.capability()), selector, null);
    }

    public synchronized OperationView createPreview(List<String> nodeIds, ManagedConfiguration configuration) {
        if (configuration == null) throw invalid("configuration is required");
        byte[] token = new byte[32];
        random.nextBytes(token);
        return create("PREVIEW", validateTargets(nodeIds, configuration.capability()), configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token));
    }

    public synchronized OperationView createApply(UUID previewId, String approvalToken) {
        prune();
        StoredOperation preview = operations.get(previewId);
        if (preview == null || !"PREVIEW".equals(preview.type)) throw invalid("preview operation was not found");
        if (!preview.complete() || preview.results.values().stream().anyMatch(result -> !result.success())) {
            throw new ValidationException("PREVIEW_INCOMPLETE", "Every node must pass preview before apply", List.of());
        }
        if (preview.approvalUsed || approvalToken == null || preview.approvalToken == null
                || !MessageDigest.isEqual(preview.approvalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                approvalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ValidationException("APPROVAL_REQUIRED", "A valid unused preview approval is required", List.of());
        }
        Map<String, String> revisions = new LinkedHashMap<>();
        preview.results.forEach((node, result) -> revisions.put(node, result.revision()));
        StoredOperation apply = store("APPLY", new ArrayList<>(preview.states.keySet()), preview.configuration,
                null, revisions, preview.id);
        preview.approvalUsed = true;
        try {
            audit.append("APPLY_APPROVED", apply.id, null, "QUEUED");
        } catch (RuntimeException e) {
            operations.remove(apply.id);
            preview.approvalUsed = false;
            throw e;
        }
        return view(apply);
    }

    public synchronized OperationView get(UUID id) {
        prune();
        StoredOperation operation = operations.get(id);
        if (operation == null) throw new ValidationException("OPERATION_NOT_FOUND", "Operation was not found", List.of());
        return view(operation);
    }

    public synchronized ConfigurationTask claim(String nodeId, UUID sessionId) {
        return registry.withSession(nodeId, sessionId, node -> claimCurrentSession(nodeId, node));
    }

    private ConfigurationTask claimCurrentSession(String nodeId, NodeStatus node) {
        prune();
        Instant now = clock.instant();
        for (StoredOperation operation : operations.values()) {
            String state = operation.states.get(nodeId);
            Instant leased = operation.leasedAt.get(nodeId);
            if ("QUEUED".equals(state) || ("IN_PROGRESS".equals(state) && leased != null
                    && !now.isBefore(leased.plus(LEASE)))) {
                if (!node.online() || !node.acceptedCapabilities().contains(operation.configuration.capability())) {
                    audit.append("TASK_CANCELLED", operation.id, nodeId, "CAPABILITY_LOST");
                    operation.results.put(nodeId, new ConfigurationTaskResult(sessionId(node), false,
                            "CAPABILITY_LOST", "Node no longer accepts this configuration capability", null,
                            (ManagedConfiguration) null, List.of(), false, false));
                    operation.states.put(nodeId, "COMPLETE");
                    operation.leasedAt.remove(nodeId);
                    continue;
                }
                operation.states.put(nodeId, "IN_PROGRESS");
                operation.leasedAt.put(nodeId, now);
                try {
                    audit.append("TASK_CLAIMED", operation.id, nodeId, operation.type);
                } catch (RuntimeException e) {
                    operation.states.put(nodeId, state);
                    if (leased == null) operation.leasedAt.remove(nodeId); else operation.leasedAt.put(nodeId, leased);
                    throw e;
                }
                return new ConfigurationTask(operation.id, operation.type, operation.configuration,
                        operation.expectedRevisions.get(nodeId));
            }
        }
        return null;
    }

    public synchronized OperationView complete(UUID operationId, String nodeId, ConfigurationTaskResult result) {
        validateResult(result);
        return registry.withSession(nodeId, result.sessionId(),
                ignored -> completeCurrentSession(operationId, nodeId, result));
    }

    private static UUID sessionId(NodeStatus node) {
        return node.sessionId();
    }

    private OperationView completeCurrentSession(UUID operationId, String nodeId, ConfigurationTaskResult result) {
        StoredOperation operation = operations.get(operationId);
        if (operation == null || !operation.states.containsKey(nodeId)) {
            throw new ValidationException("OPERATION_NOT_FOUND", "Operation task was not found", List.of());
        }
        if ("COMPLETE".equals(operation.states.get(nodeId))) return view(operation);
        if (!"IN_PROGRESS".equals(operation.states.get(nodeId))) {
            throw new ValidationException("TASK_NOT_CLAIMED", "Operation task must be claimed before completion", List.of());
        }
        audit.append("TASK_COMPLETED", operation.id, nodeId, result.success() ? "SUCCESS" : result.code());
        operation.results.put(nodeId, boundedResult(operation, result));
        operation.states.put(nodeId, "COMPLETE");
        operation.leasedAt.remove(nodeId);
        return view(operation);
    }

    private OperationView create(String type, List<String> targets, ManagedConfiguration config, String token) {
        StoredOperation operation = store(type, targets, config, token, Map.of(), null);
        try {
            audit.append("OPERATION_CREATED", operation.id, null, type);
        } catch (RuntimeException e) {
            operations.remove(operation.id);
            throw e;
        }
        return view(operation);
    }

    private StoredOperation store(String type, List<String> targets, ManagedConfiguration config,
                                  String token, Map<String, String> revisions, UUID protectedOperation) {
        prune();
        ensureFileCapacity(config, protectedOperation);
        if (operations.size() >= MAX_OPERATIONS) throw new ValidationException("OPERATION_LIMIT",
                "Too many retained operations", List.of());
        UUID id = UUID.randomUUID();
        LinkedHashMap<String, String> states = new LinkedHashMap<>();
        targets.forEach(node -> states.put(node, "QUEUED"));
        StoredOperation result = new StoredOperation(id, type, config, token, clock.instant(), states,
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(revisions));
        operations.put(id, result);
        return result;
    }

    private void ensureFileCapacity(ManagedConfiguration config, UUID protectedOperation) {
        if (config == null || !ManagedConfiguration.FILE.equals(config.domain())) return;
        long retained = operations.values().stream().filter(StoredOperation::fileOperation).count();
        Iterator<Map.Entry<UUID, StoredOperation>> iterator = operations.entrySet().iterator();
        while (retained >= MAX_FILE_OPERATIONS && iterator.hasNext()) {
            StoredOperation candidate = iterator.next().getValue();
            if (candidate.fileOperation() && candidate.complete() && !candidate.id.equals(protectedOperation)) {
                audit.append("OPERATION_EVICTED", candidate.id, null, "FILE_RETENTION_LIMIT");
                iterator.remove();
                retained--;
            }
        }
        if (retained >= MAX_FILE_OPERATIONS) {
            throw new ValidationException("OPERATION_LIMIT", "Too many active file operations", List.of());
        }
    }

    private static ConfigurationTaskResult boundedResult(StoredOperation operation, ConfigurationTaskResult result) {
        ManagedConfiguration configuration = result.configuration();
        if (configuration == null || !ManagedConfiguration.FILE.equals(configuration.domain())) return result;
        boolean keepContent = "READ".equals(operation.type) && operation.results.values().stream()
                .map(ConfigurationTaskResult::configuration).filter(Objects::nonNull)
                .noneMatch(value -> ManagedConfiguration.FILE.equals(value.domain()) && value.content() != null);
        if (keepContent) return result;
        return new ConfigurationTaskResult(result.sessionId(), result.success(), result.code(), result.message(),
                result.revision(), configuration.publicView(), result.changes(), result.reloaded(), result.rolledBack());
    }

    private List<String> validateTargets(List<String> nodeIds, String capability) {
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > 100) throw invalid("nodeIds must contain 1 to 100 entries");
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String nodeId : nodeIds) {
            if (nodeId == null || !unique.add(nodeId)) throw invalid("nodeIds must be unique");
            NodeStatus node = registry.find(nodeId);
            if (node == null || !node.online() || !node.acceptedCapabilities().contains(capability)) {
                throw new ValidationException("NODE_UNAVAILABLE", "Node cannot accept configuration operations", List.of(nodeId));
            }
            result.add(nodeId);
        }
        return List.copyOf(result);
    }

    private OperationView view(StoredOperation operation) {
        String state = operation.complete() ? (operation.results.values().stream().allMatch(ConfigurationTaskResult::success)
                ? "SUCCEEDED" : "COMPLETED_WITH_ERRORS") : "RUNNING";
        String approval = "PREVIEW".equals(operation.type) && operation.complete()
                && operation.results.values().stream().allMatch(ConfigurationTaskResult::success)
                && !operation.approvalUsed ? operation.approvalToken : null;
        return new OperationView(operation.id, operation.type, state, operation.createdAt,
                operation.configuration == null ? null : operation.configuration.publicView(),
                Map.copyOf(operation.states), Map.copyOf(operation.results), approval);
    }

    private void prune() {
        Instant now = clock.instant();
        Instant completedCutoff = now.minus(RETENTION);
        Instant activeCutoff = now.minus(ACTIVE_RETENTION);
        Iterator<Map.Entry<UUID, StoredOperation>> iterator = operations.entrySet().iterator();
        while (iterator.hasNext()) {
            StoredOperation operation = iterator.next().getValue();
            if (operation.createdAt.isBefore(operation.complete() ? completedCutoff : activeCutoff)) {
                audit.append("OPERATION_EXPIRED", operation.id, null,
                        operation.complete() ? "RETAINED" : "ABANDONED");
                iterator.remove();
            }
        }
    }

    private static ValidationException invalid(String detail) {
        return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail));
    }

    private static void validateResult(ConfigurationTaskResult result) {
        if (result == null || result.sessionId() == null || result.code() == null
                || !result.code().matches("[A-Z][A-Z0-9_]{0,63}") || result.message() == null
                || result.message().isBlank() || result.message().length() > 500 || result.changes().size() > 20
                || result.changes().stream().anyMatch(value -> value == null || value.length() > 500)) {
            throw invalid("result is invalid");
        }
        if (result.success() && (result.revision() == null || !result.revision().matches("[0-9a-f]{64}")
                || result.configuration() == null)) {
            throw invalid("successful result must include a typed configuration and revision");
        }
    }

    @Override
    public void close() throws java.io.IOException {
        audit.close();
    }

    public record OperationView(UUID operationId, String type, String state, Instant createdAt,
                                ManagedConfiguration configuration, Map<String, String> nodeStates,
                                Map<String, ConfigurationTaskResult> results, String approvalToken) { }

    private static final class StoredOperation {
        private final UUID id;
        private final String type;
        private final ManagedConfiguration configuration;
        private final String approvalToken;
        private final Instant createdAt;
        private final LinkedHashMap<String, String> states;
        private final LinkedHashMap<String, ConfigurationTaskResult> results;
        private final LinkedHashMap<String, Instant> leasedAt;
        private final LinkedHashMap<String, String> expectedRevisions;
        private boolean approvalUsed;

        private StoredOperation(UUID id, String type, ManagedConfiguration configuration, String approvalToken,
                                Instant createdAt, LinkedHashMap<String, String> states,
                                LinkedHashMap<String, ConfigurationTaskResult> results,
                                LinkedHashMap<String, Instant> leasedAt,
                                LinkedHashMap<String, String> expectedRevisions) {
            this.id = id; this.type = type; this.configuration = configuration; this.approvalToken = approvalToken;
            this.createdAt = createdAt; this.states = states; this.results = results; this.leasedAt = leasedAt;
            this.expectedRevisions = expectedRevisions;
        }
        private boolean complete() { return states.values().stream().allMatch("COMPLETE"::equals); }
        private boolean fileOperation() {
            return configuration != null && ManagedConfiguration.FILE.equals(configuration.domain());
        }
    }
}
