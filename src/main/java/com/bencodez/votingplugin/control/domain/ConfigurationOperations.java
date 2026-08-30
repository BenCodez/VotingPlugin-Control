package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.bencodez.votingplugin.control.protocol.ProxyRoutingConfiguration;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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
    public static final String VOTE_SITES_SYNC_CAPABILITY = "config.vote-sites-sync.v1";
    public static final String TRANSPORT_TEST_CAPABILITY = "config.transport-test.v1";
    public static final String PROXY_METHOD_CAPABILITY = "config.proxy-method.v1";
    private static final int MAX_OPERATIONS = 1000;
    private static final int MAX_LISTED_OPERATIONS = 100;
    private static final int MAX_FILE_OPERATIONS = 16;
    static final int MAX_RETAINED_CHANGE_BYTES = 256 * 1024;
    static final int MAX_RETAINED_MESSAGE_BYTES = 256 * 1024;
    static final int MAX_RETAINED_FILE_BYTES = 8 * 1024 * 1024;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration ACTIVE_RETENTION = Duration.ofMinutes(15);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final NodeRegistry registry;
    private final ConfigurationAuditLog audit;
    private final Clock clock;
    private final ConfigurationOperationJournal journal;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<UUID, StoredOperation> operations = new LinkedHashMap<>();
    private long retainedChangeBytes;
    private long retainedMessageBytes;
    private long retainedFileBytes;

    public ConfigurationOperations(NodeRegistry registry, ConfigurationAuditLog audit, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.journal = null;
    }

    public ConfigurationOperations(NodeRegistry registry, ConfigurationAuditLog audit, Clock clock,
                                   ConfigurationOperationJournal journal) throws IOException {
        this.registry = Objects.requireNonNull(registry);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
        this.journal = Objects.requireNonNull(journal);
        restore(journal.load());
    }

    public synchronized OperationView createRead(List<String> nodeIds) {
        return createRead(nodeIds, ManagedConfiguration.proxy(new ProxyRoutingConfiguration(false, List.of())));
    }

    public synchronized OperationView createPreview(List<String> nodeIds, ProxyRoutingConfiguration configuration) {
        return createPreview(nodeIds, configuration == null ? null : ManagedConfiguration.proxy(configuration));
    }

    public synchronized OperationView createRead(List<String> nodeIds, ManagedConfiguration selector) {
        if (selector == null) selector = ManagedConfiguration.proxy(new ProxyRoutingConfiguration(false, List.of()));
        if (ManagedConfiguration.QUICK_SETUP.equals(selector.domain())
                && ManagedConfiguration.REWARD_BUILDER.equals(selector.preset())) {
            throw invalid("reward builder is preview/apply only");
        }
        selector.validateProposal();
        return create("READ", validateTargets(nodeIds, selector.capability()), selector, null);
    }

    public synchronized OperationView createPreview(List<String> nodeIds, ManagedConfiguration configuration) {
        if (configuration == null) throw invalid("configuration is required");
        configuration.validateProposal();
        ValidatedTargets targets = validateTargets(nodeIds, configuration.capability());
        validateProxyMethodTargets(targets, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        return create("PREVIEW", targets, configuration,
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
        ValidatedTargets targets = validateTargets(new ArrayList<>(preview.states.keySet()),
                preview.configuration.capability());
        validateApprovedTargets(preview, targets);
        validateProxyMethodTargets(targets, preview.configuration);
        rejectOverlappingProxyMethodApply(targets, preview.configuration);
        Map<String, String> revisions = new LinkedHashMap<>();
        preview.results.forEach((node, result) -> revisions.put(node, result.revision()));
        StoredOperation apply = store("APPLY", targets, preview.configuration,
                null, revisions, preview.id);
        preview.approvalUsed = true;
        try {
            audit.append("APPLY_APPROVED", apply.id, null, "QUEUED");
            persist();
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

    public synchronized List<OperationView> list() {
        prune();
        List<OperationView> result = new ArrayList<>(operations.values().stream()
                .skip(Math.max(0, operations.size() - MAX_LISTED_OPERATIONS)).map(this::summaryView).toList());
        Collections.reverse(result);
        return List.copyOf(result);
    }

    /** Reissues safe work without repeating nodes that already applied successfully. */
    public synchronized OperationView retry(UUID id) {
        prune();
        StoredOperation original = operations.get(id);
        if (original == null) throw new ValidationException("OPERATION_NOT_FOUND", "Operation was not found", List.of());
        if (original.recovered) {
            throw new ValidationException("RETRY_REQUIRES_INPUT",
                    "Recovered operations are history only; start a fresh read or preview", List.of());
        }
        if (!original.complete()) {
            throw new ValidationException("OPERATION_INCOMPLETE", "Wait for the operation to finish before retrying",
                    List.of());
        }
        List<String> failed = original.results.entrySet().stream().filter(entry -> !entry.getValue().success())
                .map(Map.Entry::getKey).toList();
        if (failed.isEmpty()) throw invalid("operation has no failed nodes");
        if ("APPLY".equals(original.type) && ManagedConfiguration.QUICK_SETUP.equals(original.configuration.domain())
                && ManagedConfiguration.PROXY_METHOD.equals(original.configuration.preset())) {
            throw new ValidationException("PREVIEW_REQUIRED", "Proxy method changes must be previewed again",
                    List.of());
        }
        List<String> requested = "PREVIEW".equals(original.type)
                ? new ArrayList<>(original.states.keySet()) : failed;
        ValidatedTargets targets = validateTargets(requested, original.configuration.capability());
        if ("APPLY".equals(original.type)) validateApprovedTargets(original, targets);
        String token = null;
        if ("PREVIEW".equals(original.type)) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        Map<String, String> revisions = new LinkedHashMap<>();
        if ("APPLY".equals(original.type)) {
            requested.forEach(nodeId -> revisions.put(nodeId, original.expectedRevisions.get(nodeId)));
        }
        StoredOperation retry = store(original.type, targets, original.configuration, token, revisions, original.id);
        try {
            audit.append("OPERATION_RETRIED", retry.id, null, original.id.toString());
            persist();
        } catch (RuntimeException failure) {
            operations.remove(retry.id);
            throw failure;
        }
        return view(retry);
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
                if (cancelChangedProxyMethodRole(operation, node)) continue;
                if (deferProxyMethodApply(operation, node)) continue;
                if (!node.online() || !node.acceptedCapabilities().contains(operation.configuration.capability())) {
                    audit.append("TASK_CANCELLED", operation.id, nodeId, "CAPABILITY_LOST");
                    operation.results.put(nodeId, boundedResult(operation, new ConfigurationTaskResult(sessionId(node), false,
                            "CAPABILITY_LOST", "Node no longer accepts this configuration capability", null,
                            (ManagedConfiguration) null, List.of(), false, false, null)));
                    operation.states.put(nodeId, "COMPLETE");
                    operation.leasedAt.remove(nodeId);
                    operation.attemptIds.remove(nodeId);
                    continue;
                }
                UUID previousAttempt = operation.attemptIds.get(nodeId);
                UUID attemptId = UUID.randomUUID();
                operation.states.put(nodeId, "IN_PROGRESS");
                operation.leasedAt.put(nodeId, now);
                operation.attemptIds.put(nodeId, attemptId);
                try {
                    audit.append("TASK_CLAIMED", operation.id, nodeId, operation.type);
                } catch (RuntimeException e) {
                    operation.states.put(nodeId, state);
                    if (leased == null) operation.leasedAt.remove(nodeId); else operation.leasedAt.put(nodeId, leased);
                    if (previousAttempt == null) operation.attemptIds.remove(nodeId);
                    else operation.attemptIds.put(nodeId, previousAttempt);
                    throw e;
                }
                return new ConfigurationTask(operation.id, operation.type, operation.configuration,
                        operation.expectedRevisions.get(nodeId), attemptId);
            }
        }
        return null;
    }

    private boolean deferProxyMethodApply(StoredOperation operation, NodeStatus node) {
        if (!"APPLY".equals(operation.type)
                || !ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                || !ManagedConfiguration.PROXY_METHOD.equals(operation.configuration.preset())
                || "BUKKIT".equalsIgnoreCase(operation.targetPlatforms.get(node.nodeId()))) return false;
        List<String> backends = operation.targetPlatforms.entrySet().stream()
                .filter(entry -> "BUKKIT".equalsIgnoreCase(entry.getValue()))
                .map(Map.Entry::getKey).toList();
        for (String backendId : backends) {
            String state = operation.states.get(backendId);
            NodeStatus backend = registry.find(backendId);
            if ("COMPLETE".equals(state)) {
                ConfigurationTaskResult result = operation.results.get(backendId);
                if (result == null || !result.success() || completedBackendStillValid(operation, backendId, backend,
                        result)) continue;
                audit.append("TASK_INVALIDATED", operation.id, backendId, "DEPENDENCY_CHANGED");
                UUID backendSession = backend == null ? result.sessionId() : sessionId(backend);
                releaseResultDetails(result);
                operation.results.put(backendId, boundedResult(operation, new ConfigurationTaskResult(backendSession,
                        false, "DEPENDENCY_CHANGED", "Backend identity changed after apply; preview again", null,
                        (ManagedConfiguration) null, List.of(), false, false, null)));
                continue;
            }
            if ("IN_PROGRESS".equals(state)) {
                Instant leased = operation.leasedAt.get(backendId);
                if (leased != null && clock.instant().isBefore(leased.plus(LEASE))) continue;
                if (backend != null && backend.online()
                        && backend.acceptedCapabilities().contains(operation.configuration.capability())) {
                    audit.append("TASK_REQUEUED", operation.id, backendId, "LEASE_EXPIRED");
                    operation.states.put(backendId, "QUEUED");
                    operation.leasedAt.remove(backendId);
                    operation.attemptIds.remove(backendId);
                    continue;
                }
            } else if (!"QUEUED".equals(state)) {
                continue;
            }
            if (backend != null && backend.online()
                    && backend.acceptedCapabilities().contains(operation.configuration.capability())) continue;
            audit.append("TASK_CANCELLED", operation.id, backendId, "CAPABILITY_LOST");
            UUID backendSession = backend == null ? operation.targetSessions.get(backendId) : sessionId(backend);
            operation.results.put(backendId, boundedResult(operation, new ConfigurationTaskResult(backendSession, false,
                    "CAPABILITY_LOST", "Backend became unavailable before proxy method apply", null,
                    (ManagedConfiguration) null, List.of(), false, false, null)));
            operation.states.put(backendId, "COMPLETE");
            operation.leasedAt.remove(backendId);
            operation.attemptIds.remove(backendId);
        }
        if (backends.stream().anyMatch(id -> !"COMPLETE".equals(operation.states.get(id)))) return true;
        if (backends.stream().noneMatch(id -> operation.results.get(id) == null
                || !operation.results.get(id).success())) {
            Set<String> expectedBackends = new HashSet<>(backends);
            Set<String> currentBackends = new HashSet<>();
            node.backends().forEach(backend -> currentBackends.add(backend.backendId()));
            if (expectedBackends.equals(currentBackends)) return false;
            audit.append("TASK_CANCELLED", operation.id, node.nodeId(), "TOPOLOGY_CHANGED");
            operation.results.put(node.nodeId(), boundedResult(operation, new ConfigurationTaskResult(sessionId(node),
                    false, "DEPENDENCY_CHANGED", "Proxy topology changed after approval; preview again", null,
                    (ManagedConfiguration) null, List.of(), false, false, null)));
            operation.states.put(node.nodeId(), "COMPLETE");
            operation.leasedAt.remove(node.nodeId());
            operation.attemptIds.remove(node.nodeId());
            return true;
        }
        audit.append("TASK_CANCELLED", operation.id, node.nodeId(), "BACKEND_APPLY_FAILED");
        operation.results.put(node.nodeId(), boundedResult(operation, new ConfigurationTaskResult(sessionId(node), false,
                "DEPENDENCY_FAILED", "A backend failed the proxy method apply", null,
                (ManagedConfiguration) null, List.of(), false, false, null)));
        operation.states.put(node.nodeId(), "COMPLETE");
        operation.leasedAt.remove(node.nodeId());
        operation.attemptIds.remove(node.nodeId());
        return true;
    }

    private boolean completedBackendStillValid(StoredOperation operation, String backendId, NodeStatus backend,
            ConfigurationTaskResult result) {
        return backend != null && backend.online()
                && "BUKKIT".equalsIgnoreCase(operation.targetPlatforms.get(backendId))
                && "BUKKIT".equalsIgnoreCase(backend.platform())
                && backend.acceptedCapabilities().contains(operation.configuration.capability())
                && result.sessionId().equals(sessionId(backend));
    }

    private boolean cancelChangedProxyMethodRole(StoredOperation operation, NodeStatus node) {
        if (!"APPLY".equals(operation.type)
                || !ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                || !ManagedConfiguration.PROXY_METHOD.equals(operation.configuration.preset())) return false;
        String expectedPlatform = operation.targetPlatforms.get(node.nodeId());
        if (expectedPlatform == null || expectedPlatform.equalsIgnoreCase(node.platform())) return false;
        audit.append("TASK_CANCELLED", operation.id, node.nodeId(), "TARGET_ROLE_CHANGED");
        operation.results.put(node.nodeId(), boundedResult(operation, new ConfigurationTaskResult(sessionId(node),
                false, "TARGET_CHANGED", "Node platform changed after approval; preview again", null,
                (ManagedConfiguration) null, List.of(), false, false, null)));
        operation.states.put(node.nodeId(), "COMPLETE");
        operation.leasedAt.remove(node.nodeId());
        operation.attemptIds.remove(node.nodeId());
        return true;
    }

    private void rejectOverlappingProxyMethodApply(ValidatedTargets targets, ManagedConfiguration configuration) {
        if (!ManagedConfiguration.QUICK_SETUP.equals(configuration.domain())
                || !ManagedConfiguration.PROXY_METHOD.equals(configuration.preset())) return;
        Set<String> requested = new HashSet<>(targets.nodeIds());
        boolean conflict = operations.values().stream().anyMatch(operation -> "APPLY".equals(operation.type)
                && !operation.complete()
                && ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                && ManagedConfiguration.PROXY_METHOD.equals(operation.configuration.preset())
                && operation.states.keySet().stream().anyMatch(requested::contains));
        if (conflict) {
            throw new ValidationException("OPERATION_CONFLICT",
                    "Another proxy method apply is still running for this network", List.of());
        }
    }

    private void validateProxyMethodTargets(ValidatedTargets targets, ManagedConfiguration configuration) {
        if (!ManagedConfiguration.QUICK_SETUP.equals(configuration.domain())
                || !ManagedConfiguration.PROXY_METHOD.equals(configuration.preset())) return;
        List<NodeStatus> proxies = targets.nodeIds().stream()
                .filter(id -> !"BUKKIT".equalsIgnoreCase(targets.platforms().get(id)))
                .map(registry::find).filter(Objects::nonNull).toList();
        if (proxies.size() != 1 || proxies.get(0).backends().isEmpty()) {
            throw invalid("proxy method requires one proxy and its reported backends");
        }
        java.util.Set<String> expected = new java.util.LinkedHashSet<>();
        expected.add(proxies.get(0).nodeId());
        proxies.get(0).backends().forEach(backend -> expected.add(backend.backendId()));
        if (!expected.equals(new java.util.LinkedHashSet<>(targets.nodeIds()))) {
            throw invalid("proxy method targets must match the complete reported backend network");
        }
    }

    private static void validateApprovedTargets(StoredOperation preview, ValidatedTargets current) {
        for (String nodeId : current.nodeIds()) {
            if (!Objects.equals(preview.targetSessions.get(nodeId), current.sessions().get(nodeId))
                    || !Objects.equals(preview.targetPlatforms.get(nodeId), current.platforms().get(nodeId))) {
                throw new ValidationException("TARGET_CHANGED",
                        "A preview target reconnected or changed role; preview again", List.of(nodeId));
            }
        }
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
        if ("COMPLETE".equals(operation.states.get(nodeId))) {
            persist();
            return view(operation);
        }
        if (!"IN_PROGRESS".equals(operation.states.get(nodeId))) {
            throw new ValidationException("TASK_NOT_CLAIMED", "Operation task must be claimed before completion", List.of());
        }
        Instant leasedAt = operation.leasedAt.get(nodeId);
        if (!Objects.equals(operation.attemptIds.get(nodeId), result.attemptId()) || leasedAt == null
                || !clock.instant().isBefore(leasedAt.plus(LEASE))) {
            throw new ValidationException("TASK_LEASE_EXPIRED", "Operation task lease is no longer active", List.of());
        }
        validateResultConfiguration(operation, result);
        audit.append("TASK_COMPLETED", operation.id, nodeId, result.success() ? "SUCCESS" : result.code());
        operation.results.put(nodeId, boundedResult(operation, result));
        operation.states.put(nodeId, "COMPLETE");
        operation.leasedAt.remove(nodeId);
        operation.attemptIds.remove(nodeId);
        persist();
        return view(operation);
    }

    private OperationView create(String type, ValidatedTargets targets, ManagedConfiguration config, String token) {
        StoredOperation operation = store(type, targets, config, token, Map.of(), null);
        try {
            audit.append("OPERATION_CREATED", operation.id, null, type);
            persist();
        } catch (RuntimeException e) {
            operations.remove(operation.id);
            throw e;
        }
        return view(operation);
    }

    private StoredOperation store(String type, ValidatedTargets targets, ManagedConfiguration config,
                                  String token, Map<String, String> revisions, UUID protectedOperation) {
        prune();
        ensureFileCapacity(config, protectedOperation);
        if (operations.size() >= MAX_OPERATIONS) throw new ValidationException("OPERATION_LIMIT",
                "Too many retained operations", List.of());
        UUID id = UUID.randomUUID();
        LinkedHashMap<String, String> states = new LinkedHashMap<>();
        targets.nodeIds().forEach(node -> states.put(node, "QUEUED"));
        StoredOperation result = new StoredOperation(id, type, config, token, clock.instant(), states,
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(revisions),
                new LinkedHashMap<>(targets.platforms()), new LinkedHashMap<>(targets.sessions()), protectedOperation,
                false);
        operations.put(id, result);
        return result;
    }

    private void ensureFileCapacity(ManagedConfiguration config, UUID protectedOperation) {
        if (config == null || !largeContentOperation(config)) return;
        long retained = operations.values().stream().filter(StoredOperation::fileOperation).count();
        Iterator<Map.Entry<UUID, StoredOperation>> iterator = operations.entrySet().iterator();
        while (retained >= MAX_FILE_OPERATIONS && iterator.hasNext()) {
            StoredOperation candidate = iterator.next().getValue();
            if (candidate.fileOperation() && candidate.complete() && !candidate.id.equals(protectedOperation)) {
                audit.append("OPERATION_EVICTED", candidate.id, null, "FILE_RETENTION_LIMIT");
                releaseResultDetails(candidate);
                iterator.remove();
                retained--;
            }
        }
        if (retained >= MAX_FILE_OPERATIONS) {
            throw new ValidationException("OPERATION_LIMIT", "Too many active file operations", List.of());
        }
    }

    private static boolean largeContentOperation(ManagedConfiguration config) {
        return ManagedConfiguration.FILE.equals(config.domain()) ||
                ManagedConfiguration.VOTE_SITES_SYNC.equals(config.preset());
    }

    private ConfigurationTaskResult boundedResult(StoredOperation operation, ConfigurationTaskResult result) {
        ManagedConfiguration configuration = result.success() ? result.configuration() : null;
        if (configuration != null && (ManagedConfiguration.VOTE_SITES_SYNC.equals(configuration.preset())
                || ManagedConfiguration.REWARD_BUILDER.equals(configuration.preset()))) {
            configuration = configuration.publicView();
        } else if (configuration != null && ManagedConfiguration.FILE.equals(configuration.domain())) {
            int contentBytes = configuration.content() == null ? 0
                    : configuration.content().getBytes(StandardCharsets.UTF_8).length;
            boolean keepContent = result.success() && "READ".equals(operation.type)
                    && configuration.content() != null
                    && retainedFileBytes + contentBytes <= MAX_RETAINED_FILE_BYTES;
            if (keepContent) retainedFileBytes += contentBytes;
            else configuration = configuration.publicView();
        } else if (configuration != null && operation.results.values().stream()
                .filter(ConfigurationTaskResult::success)
                .anyMatch(existing -> existing.configuration() != null)) {
            configuration = null;
        }
        List<String> changes = retainChanges(result.changes());
        return new ConfigurationTaskResult(result.sessionId(), result.success(), result.code(), retainMessage(result.message()),
                result.revision(), configuration, changes, result.reloaded(), result.rolledBack(), result.attemptId());
    }

    private static void validateResultConfiguration(StoredOperation operation, ConfigurationTaskResult result) {
        ManagedConfiguration actual = result.configuration();
        if (actual == null) return;
        ManagedConfiguration expected = operation.configuration;
        boolean mismatch = expected == null || !expected.domain().equals(actual.domain())
                || (ManagedConfiguration.FILE.equals(expected.domain()) && !expected.fileName().equals(actual.fileName()))
                || (ManagedConfiguration.QUICK_SETUP.equals(expected.domain()) && !expected.preset().equals(actual.preset()));
        if (mismatch) throw invalid("result configuration does not match the operation selector");
    }

    private String retainMessage(String message) {
        int remaining = (int) Math.max(0, MAX_RETAINED_MESSAGE_BYTES - retainedMessageBytes);
        String retained = truncateUtf8(message, remaining);
        retainedMessageBytes += retained.getBytes(StandardCharsets.UTF_8).length;
        return retained;
    }

    private List<String> retainChanges(List<String> changes) {
        long remaining = Math.max(0, MAX_RETAINED_CHANGE_BYTES - retainedChangeBytes);
        if (remaining == 0 || changes.isEmpty()) return List.of();
        List<String> retained = new ArrayList<>();
        long added = 0;
        for (String change : changes) {
            byte[] bytes = change.getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= remaining) {
                retained.add(change);
                remaining -= bytes.length;
                added += bytes.length;
                continue;
            }
            String truncated = truncateUtf8(change, (int) remaining);
            if (!truncated.isEmpty()) {
                retained.add(truncated);
                added += truncated.getBytes(StandardCharsets.UTF_8).length;
            }
            break;
        }
        retainedChangeBytes += added;
        return List.copyOf(retained);
    }

    private static String truncateUtf8(String value, int maximumBytes) {
        int end = 0;
        int bytes = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int encoded = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (bytes + encoded > maximumBytes) break;
            bytes += encoded;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private void releaseResultDetails(StoredOperation operation) {
        operation.results.values().forEach(this::releaseResultDetails);
    }

    private void releaseResultDetails(ConfigurationTaskResult result) {
        retainedMessageBytes -= result.message().getBytes(StandardCharsets.UTF_8).length;
        for (String change : result.changes()) {
            retainedChangeBytes -= change.getBytes(StandardCharsets.UTF_8).length;
        }
        if (result.configuration() != null && result.configuration().content() != null) {
            retainedFileBytes -= result.configuration().content().getBytes(StandardCharsets.UTF_8).length;
        }
        if (retainedChangeBytes < 0) retainedChangeBytes = 0;
        if (retainedMessageBytes < 0) retainedMessageBytes = 0;
        if (retainedFileBytes < 0) retainedFileBytes = 0;
    }

    private ValidatedTargets validateTargets(List<String> nodeIds, String capability) {
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > 100) throw invalid("nodeIds must contain 1 to 100 entries");
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        Map<String, String> platforms = new LinkedHashMap<>();
        Map<String, UUID> sessions = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            if (nodeId == null || !unique.add(nodeId)) throw invalid("nodeIds must be unique");
            NodeStatus node = registry.find(nodeId);
            if (node == null || !node.online() || !node.acceptedCapabilities().contains(capability)) {
                throw new ValidationException("NODE_UNAVAILABLE", "Node cannot accept configuration operations", List.of(nodeId));
            }
            result.add(nodeId);
            platforms.put(nodeId, node.platform());
            sessions.put(nodeId, node.sessionId());
        }
        return new ValidatedTargets(List.copyOf(result), Map.copyOf(platforms), Map.copyOf(sessions));
    }

    private OperationView view(StoredOperation operation) {
        return view(operation, true);
    }

    private OperationView summaryView(StoredOperation operation) {
        return view(operation, false);
    }

    private OperationView view(StoredOperation operation, boolean includeRetainedContent) {
        String state = operation.complete() ? (operation.results.values().stream().allMatch(ConfigurationTaskResult::success)
                ? "SUCCEEDED" : "COMPLETED_WITH_ERRORS") : "RUNNING";
        String approval = "PREVIEW".equals(operation.type) && operation.complete()
                && operation.results.values().stream().allMatch(ConfigurationTaskResult::success)
                && !operation.approvalUsed ? operation.approvalToken : null;
        Map<String, ConfigurationTaskResult> results = operation.results;
        if (!includeRetainedContent) {
            LinkedHashMap<String, ConfigurationTaskResult> summaries = new LinkedHashMap<>();
            operation.results.forEach((nodeId, result) -> summaries.put(nodeId,
                    result.configuration() == null ? result : new ConfigurationTaskResult(result.sessionId(),
                            result.success(), result.code(), result.message(), result.revision(),
                            result.configuration().publicView(), result.changes(), result.reloaded(),
                            result.rolledBack(), result.attemptId())));
            results = summaries;
        }
        return new OperationView(operation.id, operation.type, state, operation.createdAt,
                operation.configuration == null ? null : operation.configuration.publicView(),
                Map.copyOf(operation.states), Map.copyOf(results), approval, operation.sourceOperationId,
                operation.recovered, retryable(operation));
    }

    private static boolean retryable(StoredOperation operation) {
        if (operation.recovered || !operation.complete()
                || operation.results.values().stream().allMatch(ConfigurationTaskResult::success)) return false;
        return !("APPLY".equals(operation.type)
                && ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                && ManagedConfiguration.PROXY_METHOD.equals(operation.configuration.preset()));
    }

    private void restore(List<ConfigurationOperationJournal.Entry> entries) {
        for (ConfigurationOperationJournal.Entry entry : entries) {
            ManagedConfiguration configuration = switch (entry.domain()) {
                case ManagedConfiguration.PROXY_ROUTING -> ManagedConfiguration.proxy(
                        new ProxyRoutingConfiguration(false, List.of()));
                case ManagedConfiguration.FILE -> ManagedConfiguration.file(entry.fileName(), null);
                case ManagedConfiguration.QUICK_SETUP -> ManagedConfiguration.redactedQuickSetup(entry.preset());
                default -> throw new IllegalStateException("unsupported journal configuration domain");
            };
            LinkedHashMap<String, String> states = new LinkedHashMap<>();
            LinkedHashMap<String, ConfigurationTaskResult> results = new LinkedHashMap<>();
            for (ConfigurationOperationJournal.NodeResult node : entry.nodes()) {
                states.put(node.nodeId(), "COMPLETE");
                boolean completed = node.complete();
                boolean success = completed && Boolean.TRUE.equals(node.success());
                String code = completed ? node.code() : "CONTROL_RESTARTED";
                String message = completed ? "Recovered from durable operation history"
                        : "Control restarted before this node reported completion";
                results.put(node.nodeId(), new ConfigurationTaskResult(completed ? node.sessionId() : null,
                        success, code, message,
                        completed ? node.revision() : null, (ManagedConfiguration) null, List.of(),
                        completed && node.reloaded(),
                        completed && node.rolledBack(), null));
            }
            StoredOperation restored = new StoredOperation(entry.operationId(), entry.type(), configuration, null,
                    entry.createdAt(), states, results, new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), entry.sourceOperationId(),
                    true);
            restored.approvalUsed = true;
            operations.put(restored.id, restored);
        }
    }

    private void persist() {
        if (journal == null) return;
        List<ConfigurationOperationJournal.Entry> entries = new ArrayList<>();
        for (StoredOperation operation : operations.values()) {
            ManagedConfiguration configuration = operation.configuration;
            List<ConfigurationOperationJournal.NodeResult> nodes = new ArrayList<>();
            for (Map.Entry<String, String> node : operation.states.entrySet()) {
                ConfigurationTaskResult result = operation.results.get(node.getKey());
                boolean complete = "COMPLETE".equals(node.getValue()) && result != null;
                nodes.add(new ConfigurationOperationJournal.NodeResult(node.getKey(),
                        complete ? result.sessionId() : null, complete,
                        complete ? result.success() : null, complete ? result.code() : null,
                        complete ? result.revision() : null, complete && result.reloaded(),
                        complete && result.rolledBack()));
            }
            entries.add(new ConfigurationOperationJournal.Entry(operation.id, operation.type, operation.createdAt,
                    configuration.domain(), configuration.fileName(), configuration.preset(),
                    operation.sourceOperationId, List.copyOf(nodes)));
        }
        try {
            journal.save(entries);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not persist redacted configuration operation history", failure);
        }
    }

    private void prune() {
        Instant now = clock.instant();
        Instant completedCutoff = now.minus(RETENTION);
        Instant activeCutoff = now.minus(ACTIVE_RETENTION);
        Iterator<Map.Entry<UUID, StoredOperation>> iterator = operations.entrySet().iterator();
        while (iterator.hasNext()) {
            StoredOperation operation = iterator.next().getValue();
            boolean activeLease = operation.leasedAt.values().stream()
                    .anyMatch(leased -> now.isBefore(leased.plus(LEASE)));
            if (!activeLease && operation.createdAt.isBefore(operation.complete() ? completedCutoff : activeCutoff)) {
                audit.append("OPERATION_EXPIRED", operation.id, null,
                        operation.complete() ? "RETAINED" : "ABANDONED");
                releaseResultDetails(operation);
                iterator.remove();
            }
        }
    }

    private static ValidationException invalid(String detail) {
        return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail));
    }

    private static void validateResult(ConfigurationTaskResult result) {
        if (result == null || result.sessionId() == null || result.attemptId() == null || result.code() == null
                || !result.code().matches("[A-Z][A-Z0-9_]{0,63}") || result.message() == null
                || result.message().isBlank() || result.message().length() > 500 || result.changes().size() > 20
                || result.changes().stream().anyMatch(value -> value == null || value.length() > 500)
                || (result.revision() != null && !result.revision().matches("[0-9a-f]{64}"))) {
            throw invalid("result is invalid");
        }
        if (result.success() && (result.revision() == null || !result.revision().matches("[0-9a-f]{64}")
                || result.configuration() == null)) {
            throw invalid("successful result must include a typed configuration and revision");
        }
    }

    @Override
    public void close() throws java.io.IOException {
        IOException failure = null;
        try {
            persist();
        } catch (IllegalStateException journalFailure) {
            failure = new IOException("Could not persist configuration operation history", journalFailure);
        }
        try {
            audit.close();
        } catch (IOException auditFailure) {
            if (failure == null) failure = auditFailure;
            else failure.addSuppressed(auditFailure);
        }
        if (failure != null) throw failure;
    }

    public record OperationView(UUID operationId, String type, String state, Instant createdAt,
                                ManagedConfiguration configuration, Map<String, String> nodeStates,
                                Map<String, ConfigurationTaskResult> results, String approvalToken,
                                UUID sourceOperationId, boolean recovered, boolean retryable) { }

    private record ValidatedTargets(List<String> nodeIds, Map<String, String> platforms,
                                    Map<String, UUID> sessions) { }

    private static final class StoredOperation {
        private final UUID id;
        private final String type;
        private final ManagedConfiguration configuration;
        private final String approvalToken;
        private final Instant createdAt;
        private final LinkedHashMap<String, String> states;
        private final LinkedHashMap<String, ConfigurationTaskResult> results;
        private final LinkedHashMap<String, Instant> leasedAt;
        private final LinkedHashMap<String, UUID> attemptIds;
        private final LinkedHashMap<String, String> expectedRevisions;
        private final LinkedHashMap<String, String> targetPlatforms;
        private final LinkedHashMap<String, UUID> targetSessions;
        private final UUID sourceOperationId;
        private final boolean recovered;
        private boolean approvalUsed;

        private StoredOperation(UUID id, String type, ManagedConfiguration configuration, String approvalToken,
                                Instant createdAt, LinkedHashMap<String, String> states,
                                LinkedHashMap<String, ConfigurationTaskResult> results,
                                LinkedHashMap<String, Instant> leasedAt,
                                LinkedHashMap<String, UUID> attemptIds,
                                LinkedHashMap<String, String> expectedRevisions,
                                LinkedHashMap<String, String> targetPlatforms,
                                LinkedHashMap<String, UUID> targetSessions, UUID sourceOperationId,
                                boolean recovered) {
            this.id = id; this.type = type; this.configuration = configuration; this.approvalToken = approvalToken;
            this.createdAt = createdAt; this.states = states; this.results = results; this.leasedAt = leasedAt;
            this.attemptIds = attemptIds;
            this.expectedRevisions = expectedRevisions;
            this.targetPlatforms = targetPlatforms;
            this.targetSessions = targetSessions;
            this.sourceOperationId = sourceOperationId;
            this.recovered = recovered;
        }
        private boolean complete() { return states.values().stream().allMatch("COMPLETE"::equals); }
        private boolean fileOperation() {
            return configuration != null && largeContentOperation(configuration);
        }
    }
}
