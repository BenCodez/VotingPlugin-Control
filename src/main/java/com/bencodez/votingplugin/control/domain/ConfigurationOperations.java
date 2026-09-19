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
    public static final String PROXY_FILE_CAPABILITY = "config.proxy-files.v1";
    public static final String QUICK_SETUP_CAPABILITY = "config.quick-setup.v1";
    public static final String QUICK_SETUP_VOTE_PARTY_CAPABILITY = "config.quick-setup.v2";
    public static final String VOTE_SITES_SYNC_CAPABILITY = "config.vote-sites-sync.v1";
    public static final String TRANSPORT_TEST_CAPABILITY = "config.transport-test.v1";
    public static final String PROXY_METHOD_CAPABILITY = "config.proxy-method.v1";
    public static final String PROXY_METHOD_HTTP_CAPABILITY = "config.proxy-method.v2";
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
    private final LinkedHashMap<String, UUID> voteLoggingRestartSessions = new LinkedHashMap<>();
    private long configurationGeneration;
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
        restore(journal.loadState());
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
        ValidatedTargets targets = validateTargets(nodeIds, selector.capability());
        validateConfigurationTargets(targets, selector);
        return create("READ", targets, selector, null);
    }

    public synchronized OperationView createPreview(List<String> nodeIds, ManagedConfiguration configuration) {
        if (configuration == null) throw invalid("configuration is required");
        configuration.validateProposal();
        ValidatedTargets targets = validateTargets(nodeIds, configuration.capability());
        validateConfigurationTargets(targets, configuration);
        validateProxyMethodTargets(targets, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        return create("PREVIEW", targets, configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token));
    }

    public synchronized OperationView createApply(UUID previewId, String approvalToken) {
        prune();
        LinkedHashMap<UUID, StoredOperation> priorOperations = new LinkedHashMap<>(operations);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
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
        validateConfigurationTargets(targets, preview.configuration);
        validateProxyMethodTargets(targets, preview.configuration);
        rejectOverlappingProxyMethodApply(targets, preview.configuration);
        rejectOverlappingVoteLoggingApply(targets, preview.configuration);
        Map<String, String> revisions = new LinkedHashMap<>();
        preview.results.forEach((node, result) -> revisions.put(node, result.revision()));
        boolean priorApprovalUsed = preview.approvalUsed;
        StoredOperation apply = store("APPLY", targets, preview.configuration,
                null, revisions, preview.id);
        preview.approvalUsed = true;
        try {
            audit.append("APPLY_APPROVED", apply.id, null, "QUEUED");
            persist();
        } catch (RuntimeException e) {
            operations.clear();
            operations.putAll(priorOperations);
            retainedChangeBytes = priorChanges;
            retainedMessageBytes = priorMessages;
            retainedFileBytes = priorFiles;
            preview.approvalUsed = priorApprovalUsed;
            throw e;
        }
        return view(apply);
    }

    /** Typed view of a retained, successful read of this particular backend's Config.yml. */
    public synchronized SettingsState generalSettingsState(UUID readId, String nodeId) {
        ConfigurationTaskResult snapshot = settingsSnapshot(readId, nodeId);
        try {
            return new SettingsState(readId, nodeId, snapshot.sessionId(), snapshot.revision(),
                    GeneralSettingsDocument.fields(snapshot.configuration().content()));
        } catch (IllegalArgumentException malformed) {
            throw new ValidationException("INVALID_CONFIGURATION", "General Settings requires an unambiguous YAML mapping", List.of(nodeId));
        }
    }

    public synchronized OperationView createGeneralSettingsPreview(UUID readId, String nodeId,
            Map<String, Object> overrides) {
        ConfigurationTaskResult snapshot = settingsSnapshot(readId, nodeId);
        if (overrides == null || overrides.isEmpty() || overrides.size() > 9
                || overrides.values().stream().anyMatch(value -> !(value instanceof Boolean))) {
            throw invalid("General Settings requires one to nine boolean edits");
        }
        Map<String, Boolean> edits = new LinkedHashMap<>();
        overrides.forEach((key, value) -> edits.put(key, (Boolean) value));
        String proposal;
        try {
            proposal = GeneralSettingsDocument.patch(snapshot.configuration().content(), edits);
        } catch (IllegalArgumentException unsupported) {
            throw invalid("Only present, supported General Settings booleans can be edited");
        }
        ManagedConfiguration configuration = ManagedConfiguration.file("Config.yml", proposal);
        ValidatedTargets targets = validateTargets(List.of(nodeId), FILE_CAPABILITY);
        validateConfigurationTargets(targets, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        return create("PREVIEW", targets, configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token), Map.of(nodeId, snapshot.revision()));
    }

    private ConfigurationTaskResult settingsSnapshot(UUID readId, String nodeId) {
        return fileSnapshot(readId, nodeId, "Config.yml");
    }

    private ConfigurationTaskResult fileSnapshot(UUID readId, String nodeId, String fileName) {
        prune();
        ManagedConfiguration selector = ManagedConfiguration.file(fileName, null);
        ValidatedTargets targets = validateTargets(nodeId == null ? List.of() : List.of(nodeId), selector.capability());
        validateConfigurationTargets(targets, selector);
        StoredOperation read = operations.get(readId);
        ConfigurationTaskResult result = read == null ? null : read.results.get(nodeId);
        if (read == null || !"READ".equals(read.type)
                || !ManagedConfiguration.FILE.equals(read.configuration.domain())
                || !fileName.equals(read.configuration.fileName()) || result == null || !result.success()
                || result.configuration() == null || result.configuration().content() == null) {
            throw new ValidationException("READ_REQUIRED", "A retained successful " + fileName + " read is required",
                    List.of(nodeId));
        }
        if (!Objects.equals(result.sessionId(), targets.sessions().get(nodeId))) {
            throw new ValidationException("TARGET_CHANGED", "The backend reconnected; read it again", List.of(nodeId));
        }
        return result;
    }

    public record SettingsState(UUID readOperationId, String nodeId, UUID sessionId, String revision,
            Map<String, GeneralSettingsDocument.Field> fields) { }

    /** Typed, secret-bounded inventory from one retained successful VoteSites.yml read. */
    public synchronized VoteSitesState voteSitesState(UUID readId, String nodeId) {
        ConfigurationTaskResult snapshot = fileSnapshot(readId, nodeId, "VoteSites.yml");
        try {
            List<VoteSiteState> sites = VoteSitesDocument.inventory(snapshot.configuration().content()).sites().stream()
                    .map(site -> new VoteSiteState(site.key(), site.editable(), site.fields(), site.rewardsConfigured()))
                    .toList();
            return new VoteSitesState(readId, nodeId, snapshot.sessionId(), snapshot.revision(), sites);
        } catch (IllegalArgumentException malformed) {
            throw new ValidationException("INVALID_CONFIGURATION",
                    "Vote Sites requires one unambiguous main VoteSites.yml mapping", List.of(nodeId));
        }
    }

    /** Creates one revision-bound preview for one exact typed site action on one target. */
    public synchronized OperationView createVoteSitesPreview(UUID readId, String nodeId, String action,
            String siteKey, Map<String, Object> fields) {
        ConfigurationTaskResult snapshot = fileSnapshot(readId, nodeId, "VoteSites.yml");
        String proposal;
        try {
            proposal = switch (action == null ? "" : action) {
                case "ADD" -> VoteSitesDocument.add(snapshot.configuration().content(), siteKey, fields);
                case "EDIT" -> VoteSitesDocument.edit(snapshot.configuration().content(), siteKey, fields);
                case "REMOVE" -> {
                    if (fields != null && !fields.isEmpty()) throw new IllegalArgumentException();
                    yield VoteSitesDocument.remove(snapshot.configuration().content(), siteKey);
                }
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException invalidSiteOperation) {
            throw invalid("Vote Site action, key, fields, or source structure is invalid");
        }
        if (proposal.equals(snapshot.configuration().content())) {
            throw invalid("Vote Site action does not change this target");
        }
        ManagedConfiguration configuration = ManagedConfiguration.file("VoteSites.yml", proposal);
        ValidatedTargets targets = validateTargets(List.of(nodeId), FILE_CAPABILITY);
        validateConfigurationTargets(targets, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        return create("PREVIEW", targets, configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token), Map.of(nodeId, snapshot.revision()));
    }

    public record VoteSitesState(UUID readOperationId, String nodeId, UUID sessionId, String revision,
            List<VoteSiteState> sites) { }

    public record VoteSiteState(String siteKey, boolean editable, Map<String, VoteSitesDocument.Field> fields,
            boolean rewardsConfigured) { }

    private static boolean isNamedRewardFile(String fileName) {
        return fileName != null && fileName.matches("Rewards/[A-Za-z0-9][A-Za-z0-9_-]{0,99}\\.yml");
    }

    /** Narrow typed reward inventory from the retained READ, never raw YAML or approval material. */
    public synchronized RewardsState rewardsState(UUID readId, String nodeId, String fileName) {
        if (fileName == null || !Set.of("VoteSites.yml", "Config.yml", "SpecialRewards.yml").contains(fileName)
                && !isNamedRewardFile(fileName))
            throw invalid("Unsupported reward file");
        ConfigurationTaskResult snapshot = fileSnapshot(readId, nodeId, fileName);
        try {
            return new RewardsState(readId, nodeId, fileName, snapshot.sessionId(), snapshot.revision(),
                    RewardsDocument.inventory(snapshot.configuration().content(), fileName));
        } catch (IllegalArgumentException malformed) {
            throw new ValidationException("INVALID_CONFIGURATION", "Reward inventory requires an unambiguous YAML mapping", List.of(nodeId));
        }
    }

    /** Source-preserving one-target reward edit, approved through the existing PREVIEW/APPLY path. */
    public synchronized OperationView createRewardsPreview(UUID readId, String nodeId, String fileName,
            String rewardPath, String action, String field, Object value) {
        if (!"VoteSites.yml".equals(fileName) && !isNamedRewardFile(fileName))
            throw invalid("This reward scope is advanced-only");
        ConfigurationTaskResult snapshot = fileSnapshot(readId, nodeId, fileName);
        String proposal;
        try {
            proposal = RewardsDocument.patch(snapshot.configuration().content(), fileName, rewardPath,
                    new RewardsDocument.Edit(action, field, value));
        } catch (IllegalArgumentException unsupported) {
            throw invalid("Reward edit is invalid, unsafe, or unsupported for this source structure");
        }
        if (proposal.equals(snapshot.configuration().content())) throw invalid("Reward edit does not change this target");
        ManagedConfiguration configuration = ManagedConfiguration.file(fileName, proposal);
        ValidatedTargets targets = validateTargets(List.of(nodeId), configuration.capability());
        validateConfigurationTargets(targets, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        return create("PREVIEW", targets, configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token), Map.of(nodeId, snapshot.revision()));
    }

    public record RewardsState(UUID readOperationId, String nodeId, String fileName, UUID sessionId, String revision,
            List<RewardsDocument.Scope> scopes) { }

    public synchronized OperationView discardRewardsPreview(UUID id, String token) {
        return discardVisualPreview(id, token, "REWARDS");
    }

    /** Releases abandoned visual approvals without scheduling any work on a node. */
    public synchronized OperationView discardGeneralSettingsPreview(UUID id, String token) {
        return discardVisualPreview(id, token, "GENERAL_SETTINGS");
    }

    public synchronized OperationView discardVoteSitesPreview(UUID id, String token) {
        return discardVisualPreview(id, token, "VOTE_SITES");
    }

    private OperationView discardVisualPreview(UUID id, String token, String source) {
        prune();
        StoredOperation preview = operations.get(id);
        if (preview == null || !"PREVIEW".equals(preview.type) || preview.expectedRevisions.isEmpty()
                || token == null || token.length() > 128 || preview.approvalToken == null
                || !MessageDigest.isEqual(token.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    preview.approvalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ValidationException("APPROVAL_REQUIRED", "The exact visual preview approval is required", List.of());
        }
        // Tokens are intentionally never journaled and recovered previews are always non-applicable.
        // Append the audit event before changing the sole in-memory approval bit.
        audit.append("PREVIEW_DISCARDED", preview.id, null, source);
        preview.approvalUsed = true;
        return view(preview);
    }

    public synchronized OperationView get(UUID id) {
        prune();
        StoredOperation operation = operations.get(id);
        if (operation == null) throw new ValidationException("OPERATION_NOT_FOUND", "Operation was not found", List.of());
        return view(operation);
    }

    public synchronized List<OperationView> list() {
        return listView().items();
    }

    /** Returns bounded rendered history plus restart state derived from every retained operation. */
    public synchronized OperationListView listView() {
        prune();
        List<OperationView> result = new ArrayList<>(operations.values().stream()
                .skip(Math.max(0, operations.size() - MAX_LISTED_OPERATIONS)).map(this::summaryView).toList());
        Collections.reverse(result);
        return new OperationListView(List.copyOf(result), Map.copyOf(voteLoggingRestartSessions),
                configurationGeneration);
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
        if ("PREVIEW".equals(original.type) && !original.expectedRevisions.isEmpty()) {
            throw new ValidationException("PREVIEW_REQUIRED", "Visual settings require a fresh read and preview", List.of());
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
        validateConfigurationTargets(targets, original.configuration);
        if ("APPLY".equals(original.type)) {
            validateApprovedTargets(original, targets);
            rejectOverlappingVoteLoggingApply(targets, original.configuration);
        }
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
        LinkedHashMap<UUID, StoredOperation> priorOperations = new LinkedHashMap<>(operations);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
        StoredOperation retry = store(original.type, targets, original.configuration, token, revisions, original.id);
        try {
            audit.append("OPERATION_RETRIED", retry.id, null, original.id.toString());
            persist();
        } catch (RuntimeException failure) {
            operations.clear();
            operations.putAll(priorOperations);
            retainedChangeBytes = priorChanges;
            retainedMessageBytes = priorMessages;
            retainedFileBytes = priorFiles;
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
                if (cancelChangedFileRole(operation, node)) continue;
                if (cancelChangedBackendSetupRole(operation, node)) continue;
                if (cancelChangedProxyMethodRole(operation, node)) continue;
                if (deferProxyMethodApply(operation, node)) continue;
                if (cancelLostCapability(operation, node)) continue;
                UUID previousAttempt = operation.attemptIds.get(nodeId);
                UUID previousClaimSession = operation.claimSessions.get(nodeId);
                UUID attemptId = UUID.randomUUID();
                operation.states.put(nodeId, "IN_PROGRESS");
                operation.leasedAt.put(nodeId, now);
                operation.attemptIds.put(nodeId, attemptId);
                operation.claimSessions.put(nodeId, node.sessionId());
                try {
                    audit.append("TASK_CLAIMED", operation.id, nodeId, operation.type);
                } catch (RuntimeException e) {
                    operation.states.put(nodeId, state);
                    if (leased == null) operation.leasedAt.remove(nodeId); else operation.leasedAt.put(nodeId, leased);
                    if (previousAttempt == null) operation.attemptIds.remove(nodeId);
                    else operation.attemptIds.put(nodeId, previousAttempt);
                    if (previousClaimSession == null) operation.claimSessions.remove(nodeId);
                    else operation.claimSessions.put(nodeId, previousClaimSession);
                    throw e;
                }
                return new ConfigurationTask(operation.id, operation.type, configurationForTask(operation),
                        operation.expectedRevisions.get(nodeId), attemptId, operation.configuration.capability());
            }
        }
        return null;
    }

    private static ManagedConfiguration configurationForTask(StoredOperation operation) {
        ManagedConfiguration configuration = operation.configuration;
        if ("READ".equals(operation.type) && ManagedConfiguration.QUICK_SETUP.equals(configuration.domain())
                && "proxy-backend".equals(configuration.preset()) && configuration.options().containsKey("method")) {
            return new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP, null, List.of(), null, null,
                    configuration.preset(), Map.of());
        }
        return configuration;
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
                UUID backendSession = backend == null ? result.sessionId() : sessionId(backend);
                automaticCancellation(operation, backendId, backendSession, "DEPENDENCY_CHANGED",
                        "Backend identity changed after apply; preview again", "DEPENDENCY_CHANGED");
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
            UUID backendSession = backend == null ? operation.targetSessions.get(backendId) : sessionId(backend);
            automaticCancellation(operation, backendId, backendSession, "CAPABILITY_LOST",
                    "Backend became unavailable before proxy method apply", "CAPABILITY_LOST");
        }
        if (backends.stream().anyMatch(id -> !"COMPLETE".equals(operation.states.get(id)))) return true;
        if (backends.stream().noneMatch(id -> operation.results.get(id) == null
                || !operation.results.get(id).success())) {
            Set<String> expectedBackends = new HashSet<>(backends);
            Set<String> currentBackends = new HashSet<>();
            node.backends().forEach(backend -> currentBackends.add(backend.backendId()));
            if (expectedBackends.equals(currentBackends)) return false;
            automaticCancellation(operation, node.nodeId(), sessionId(node), "DEPENDENCY_CHANGED",
                    "Proxy topology changed after approval; preview again", "TOPOLOGY_CHANGED");
            return true;
        }
        String causes = backends.stream().filter(backendId -> operation.results.get(backendId) == null
                        || !operation.results.get(backendId).success())
                .map(backendId -> {
                    ConfigurationTaskResult failure = operation.results.get(backendId);
                    return failure == null ? backendId + " RESULT_UNAVAILABLE"
                            : backendId + " " + failure.code() + ": " + failure.message();
                })
                .collect(java.util.stream.Collectors.joining("; "));
        automaticCancellation(operation, node.nodeId(), sessionId(node), "DEPENDENCY_FAILED",
                truncateUtf8("Proxy not applied because " + causes, 500), "BACKEND_APPLY_FAILED");
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
        automaticCancellation(operation, node.nodeId(), sessionId(node), "TARGET_CHANGED",
                "Node platform changed after approval; preview again", "TARGET_ROLE_CHANGED");
        return true;
    }

    private boolean cancelChangedFileRole(StoredOperation operation, NodeStatus node) {
        if (!ManagedConfiguration.FILE.equals(operation.configuration.domain())) return false;
        String expectedPlatform = operation.targetPlatforms.get(node.nodeId());
        boolean proxyFile = "bungeeconfig.yml".equals(operation.configuration.fileName());
        boolean currentRoleMatches = proxyFile
                ? !"BUKKIT".equalsIgnoreCase(node.platform())
                : "BUKKIT".equalsIgnoreCase(node.platform());
        if (expectedPlatform != null && expectedPlatform.equalsIgnoreCase(node.platform()) && currentRoleMatches) {
            return false;
        }
        automaticCancellation(operation, node.nodeId(), sessionId(node), "TARGET_CHANGED",
                "Node platform changed after the task was created; create it again", "TARGET_ROLE_CHANGED");
        return true;
    }

    private boolean cancelChangedBackendSetupRole(StoredOperation operation, NodeStatus node) {
        if (!ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                || !"proxy-backend".equals(operation.configuration.preset())) return false;
        String expectedPlatform = operation.targetPlatforms.get(node.nodeId());
        if ("BUKKIT".equalsIgnoreCase(expectedPlatform) && "BUKKIT".equalsIgnoreCase(node.platform())) return false;
        automaticCancellation(operation, node.nodeId(), sessionId(node), "TARGET_CHANGED",
                "Node platform changed after the task was created; create it again", "TARGET_ROLE_CHANGED");
        return true;
    }

    private boolean cancelLostCapability(StoredOperation operation, NodeStatus node) {
        if (node.online() && node.acceptedCapabilities().contains(operation.configuration.capability())) return false;
        automaticCancellation(operation, node.nodeId(), sessionId(node), "CAPABILITY_LOST",
                "Node no longer accepts this configuration capability", "CAPABILITY_LOST");
        return true;
    }

    /**
     * Commits an automatic task completion only after the redacted journal is durable.  Both the
     * operation maps and retention counters are restored if either durable step fails, leaving the
     * task claimable again instead of manufacturing a completion that recovery cannot explain.
     */
    private void automaticCancellation(StoredOperation operation, String nodeId, UUID sessionId,
                                       String code, String message, String auditOutcome) {
        String priorState = operation.states.get(nodeId);
        ConfigurationTaskResult priorResult = operation.results.get(nodeId);
        Instant priorLease = operation.leasedAt.get(nodeId);
        UUID priorAttempt = operation.attemptIds.get(nodeId);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
        long priorConfigurationGeneration = configurationGeneration;
        LinkedHashMap<String, UUID> priorRestartSessions = new LinkedHashMap<>(voteLoggingRestartSessions);
        try {
            if (priorResult != null) releaseResultDetails(priorResult);
            operation.results.put(nodeId, boundedResult(operation, new ConfigurationTaskResult(sessionId, false,
                    code, message, null, (ManagedConfiguration) null, List.of(), false, false, null)));
            operation.states.put(nodeId, "COMPLETE");
            operation.leasedAt.remove(nodeId);
            operation.attemptIds.remove(nodeId);
            persist();
            audit.append("TASK_CANCELLED", operation.id, nodeId, auditOutcome);
        } catch (RuntimeException failure) {
            restoreTransition(operation, nodeId, priorState, priorResult, priorLease, priorAttempt,
                    priorChanges, priorMessages, priorFiles, priorConfigurationGeneration,
                    priorRestartSessions, failure);
            throw failure;
        }
    }

    private void restoreTransition(StoredOperation operation, String nodeId, String priorState,
                                     ConfigurationTaskResult priorResult, Instant priorLease, UUID priorAttempt,
                                     long priorChanges, long priorMessages, long priorFiles,
                                     long priorConfigurationGeneration,
                                     Map<String, UUID> priorRestartSessions, RuntimeException failure) {
        ConfigurationTaskResult current = operation.results.get(nodeId);
        if (current != null && current != priorResult) releaseResultDetails(current);
        if (priorState == null) operation.states.remove(nodeId); else operation.states.put(nodeId, priorState);
        if (priorResult == null) operation.results.remove(nodeId); else operation.results.put(nodeId, priorResult);
        if (priorLease == null) operation.leasedAt.remove(nodeId); else operation.leasedAt.put(nodeId, priorLease);
        if (priorAttempt == null) operation.attemptIds.remove(nodeId); else operation.attemptIds.put(nodeId, priorAttempt);
        retainedChangeBytes = priorChanges;
        retainedMessageBytes = priorMessages;
        retainedFileBytes = priorFiles;
        configurationGeneration = priorConfigurationGeneration;
        voteLoggingRestartSessions.clear();
        voteLoggingRestartSessions.putAll(priorRestartSessions);
        try {
            persist();
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
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

    private void rejectOverlappingVoteLoggingApply(ValidatedTargets targets, ManagedConfiguration configuration) {
        if (!ManagedConfiguration.QUICK_SETUP.equals(configuration.domain())
                || !"vote-logging".equals(configuration.preset())) return;
        Set<String> requested = new HashSet<>(targets.nodeIds());
        boolean conflict = operations.values().stream().anyMatch(operation -> "APPLY".equals(operation.type)
                && !operation.complete()
                && ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                && "vote-logging".equals(operation.configuration.preset())
                && operation.states.keySet().stream().anyMatch(requested::contains));
        if (conflict) {
            throw new ValidationException("OPERATION_CONFLICT",
                    "Another vote logging apply is still running for a target", List.of());
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

    private static void validateConfigurationTargets(ValidatedTargets targets,
                                                       ManagedConfiguration configuration) {
        if (ManagedConfiguration.QUICK_SETUP.equals(configuration.domain())
                && "proxy-backend".equals(configuration.preset())) {
            List<String> invalid = targets.nodeIds().stream()
                    .filter(nodeId -> !"BUKKIT".equalsIgnoreCase(targets.platforms().get(nodeId)))
                    .toList();
            if (!invalid.isEmpty()) {
                throw new ValidationException("INVALID_TARGET",
                        "Backend proxy settings require Bukkit nodes", invalid);
            }
            return;
        }
        if (!ManagedConfiguration.FILE.equals(configuration.domain())) return;
        boolean proxyFile = "bungeeconfig.yml".equals(configuration.fileName());
        List<String> invalid = targets.nodeIds().stream()
                .filter(nodeId -> proxyFile
                        ? "BUKKIT".equalsIgnoreCase(targets.platforms().get(nodeId))
                        : !"BUKKIT".equalsIgnoreCase(targets.platforms().get(nodeId)))
                .toList();
        if (!invalid.isEmpty()) {
            throw new ValidationException("INVALID_TARGET",
                    proxyFile ? "Proxy configuration files require proxy nodes"
                            : "Backend configuration files require Bukkit nodes", invalid);
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
                node -> completeCurrentSession(operationId, nodeId, result, node));
    }

    private static UUID sessionId(NodeStatus node) {
        return node.sessionId();
    }

    private OperationView completeCurrentSession(UUID operationId, String nodeId, ConfigurationTaskResult result,
            NodeStatus node) {
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
        if (!Objects.equals(operation.claimSessions.get(nodeId), result.sessionId())) {
            throw new ValidationException("SESSION_MISMATCH", "Operation task belongs to another node session", List.of());
        }
        if (cancelChangedFileRole(operation, node) || cancelChangedBackendSetupRole(operation, node)
                || cancelChangedProxyMethodRole(operation, node)
                || cancelLostCapability(operation, node)) {
            return view(operation);
        }
        validateResultConfiguration(operation, result, node);
        if ("PREVIEW".equals(operation.type) && result.success()
                && operation.expectedRevisions.containsKey(nodeId)
                && !Objects.equals(operation.expectedRevisions.get(nodeId), result.revision())) {
            result = new ConfigurationTaskResult(result.sessionId(), false, "STALE_REVISION",
                    "Configuration changed since READ; read and preview again", null,
                    (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
        }
        String priorState = operation.states.get(nodeId);
        ConfigurationTaskResult priorResult = operation.results.get(nodeId);
        Instant priorLease = operation.leasedAt.get(nodeId);
        UUID priorAttempt = operation.attemptIds.get(nodeId);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
        long priorConfigurationGeneration = configurationGeneration;
        LinkedHashMap<String, UUID> priorRestartSessions = new LinkedHashMap<>(voteLoggingRestartSessions);
        try {
            operation.results.put(nodeId, boundedResult(operation, result));
            if ("APPLY".equals(operation.type) && result.success()) {
                configurationGeneration++;
            }
            if (requiresVoteLoggingRestart(operation, result)) {
                reclaimStaleRestartSessions(nodeId);
                voteLoggingRestartSessions.remove(nodeId);
                voteLoggingRestartSessions.put(nodeId, result.sessionId());
            }
            operation.states.put(nodeId, "COMPLETE");
            operation.leasedAt.remove(nodeId);
            operation.attemptIds.remove(nodeId);
            persist();
            audit.append("TASK_COMPLETED", operation.id, nodeId, result.success() ? "SUCCESS" : result.code());
        } catch (RuntimeException failure) {
            restoreTransition(operation, nodeId, priorState, priorResult, priorLease, priorAttempt,
                    priorChanges, priorMessages, priorFiles, priorConfigurationGeneration,
                    priorRestartSessions, failure);
            throw failure;
        }
        return view(operation);
    }

    private OperationView create(String type, ValidatedTargets targets, ManagedConfiguration config, String token) {
        return create(type, targets, config, token, Map.of());
    }

    private OperationView create(String type, ValidatedTargets targets, ManagedConfiguration config, String token,
            Map<String, String> revisions) {
        LinkedHashMap<UUID, StoredOperation> priorOperations = new LinkedHashMap<>(operations);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
        StoredOperation operation = store(type, targets, config, token, revisions, null);
        try {
            audit.append("OPERATION_CREATED", operation.id, null, type);
            persist();
        } catch (RuntimeException e) {
            operations.clear();
            operations.putAll(priorOperations);
            retainedChangeBytes = priorChanges;
            retainedMessageBytes = priorMessages;
            retainedFileBytes = priorFiles;
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
            boolean boundApprovalPending = "PREVIEW".equals(candidate.type) && !candidate.expectedRevisions.isEmpty()
                    && !candidate.approvalUsed && candidate.createdAt.plus(ACTIVE_RETENTION).isAfter(clock.instant())
                    && candidate.results.values().stream().allMatch(ConfigurationTaskResult::success);
            if (candidate.fileOperation() && candidate.complete() && !candidate.id.equals(protectedOperation)
                    && !boundApprovalPending) {
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

    private static boolean requiresVoteLoggingRestart(StoredOperation operation, ConfigurationTaskResult result) {
        return result.success() && "APPLY".equals(operation.type)
                && ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                && "vote-logging".equals(operation.configuration.preset())
                && result.changes().stream()
                .anyMatch(change -> change.matches(".*VoteLogging\\.(Enabled|UseMainMySQL)\\b.*"));
    }

    private void reclaimStaleRestartSessions(String incomingNodeId) {
        voteLoggingRestartSessions.entrySet().removeIf(entry -> {
            NodeStatus current = registry.find(entry.getKey());
            return current != null && !entry.getValue().equals(current.sessionId());
        });
        if (voteLoggingRestartSessions.containsKey(incomingNodeId)
                || voteLoggingRestartSessions.size() < ConfigurationOperationJournal.MAX_RESTART_SESSIONS) return;
        if (voteLoggingRestartSessions.size() >= ConfigurationOperationJournal.MAX_RESTART_SESSIONS) {
            throw new IllegalStateException("Vote logging restart state capacity is unavailable");
        }
    }

    private static void validateResultConfiguration(StoredOperation operation, ConfigurationTaskResult result,
            NodeStatus node) {
        ManagedConfiguration actual = result.configuration();
        if (actual == null) return;
        ManagedConfiguration expected = operation.configuration;
        boolean mismatch = expected == null || !expected.domain().equals(actual.domain())
                || (ManagedConfiguration.FILE.equals(expected.domain()) && !expected.fileName().equals(actual.fileName()))
                || (ManagedConfiguration.QUICK_SETUP.equals(expected.domain()) && !expected.preset().equals(actual.preset()))
                || (!expected.capability().equals(actual.capability())
                && !compatibleActiveMethodRead(operation, expected, actual, node));
        if (mismatch) throw invalid("result configuration does not match the operation selector");
    }

    private static boolean compatibleActiveMethodRead(StoredOperation operation, ManagedConfiguration expected,
            ManagedConfiguration actual, NodeStatus node) {
        if (!activeMethodRead(operation, expected)) return false;
        try {
            actual.validateProposal();
        } catch (IllegalArgumentException invalidMethod) {
            return false;
        }
        if (PROXY_METHOD_HTTP_CAPABILITY.equals(expected.capability())
                && actual.options().containsKey("method")
                && !"HTTP".equals(actual.options().get("method"))) return true;
        return node.acceptedCapabilities().contains(actual.capability());
    }

    private static boolean activeMethodRead(StoredOperation operation, ManagedConfiguration expected) {
        return "READ".equals(operation.type) && ManagedConfiguration.QUICK_SETUP.equals(expected.domain())
                && (ManagedConfiguration.PROXY_METHOD.equals(expected.preset())
                || "proxy-backend".equals(expected.preset()));
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
        if ("PREVIEW".equals(operation.type) && !operation.expectedRevisions.isEmpty()) return false;
        return !("APPLY".equals(operation.type)
                && ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                && ManagedConfiguration.PROXY_METHOD.equals(operation.configuration.preset()));
    }

    private void restore(ConfigurationOperationJournal.State state) {
        voteLoggingRestartSessions.putAll(state.voteLoggingRestartSessions());
        configurationGeneration = state.configurationGeneration();
        for (ConfigurationOperationJournal.Entry entry : state.operations()) {
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
                        success, code, retainMessage(message),
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
                boolean complete = "COMPLETE".equals(node.getValue()) && result != null
                        && result.sessionId() != null;
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
            journal.save(entries, voteLoggingRestartSessions, configurationGeneration);
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

    public record OperationListView(List<OperationView> items, Map<String, UUID> voteLoggingRestartSessions,
                                    long configurationGeneration) {
        public OperationListView(List<OperationView> items, Map<String, UUID> voteLoggingRestartSessions) {
            this(items, voteLoggingRestartSessions, 0L);
        }
    }

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
        private final LinkedHashMap<String, UUID> claimSessions = new LinkedHashMap<>();
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
