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
    public static final int MAX_AGENT_TARGETS = 10;
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
    private static final int MAX_AGENT_FILE_READS_AND_PREVIEWS = 6;
    private static final int MAX_AGENT_FILE_APPLIES = 4;
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

    public synchronized void auditAgentGrant(UUID agent, String outcome) {
        audit.append("AGENT_GRANT", agent, null, outcome);
    }

    public synchronized OperationView createAgentVoteSite(UUID agent, String nodeId,
            ManagedConfiguration configuration, UUID readId) {
        prune();
        if (!"vote-site".equals(configuration.preset())) throw invalid("Unsupported agent preset");
        configuration.validateProposal();
        ValidatedTargets targets = validateTargets(List.of(nodeId), QUICK_SETUP_CAPABILITY);
        if (!"BUKKIT".equalsIgnoreCase(targets.platforms().get(nodeId))) throw invalid("Agent setup requires a Bukkit node");
        validateConfigurationTargets(targets, configuration);
        requireAgentCapacity(agent);
        if (readId == null) {
            var selector = ManagedConfiguration.file("VoteSites.yml", null);
            ValidatedTargets readTargets = validateTargets(List.of(nodeId), FILE_CAPABILITY);
            validateConfigurationTargets(readTargets, selector);
            OperationView view = create("READ", readTargets, selector, null, Map.of(), agent);
            operations.get(view.operationId()).agentSite = configuration.options().get("name");
            return view;
        }
        StoredOperation read = agentOperation(agent, readId);
        ConfigurationTaskResult result = read.results.get(nodeId);
        if (!"READ".equals(read.type) || result == null || !result.success()
                || !Objects.equals(read.agentSite, configuration.options().get("name"))
                || !Objects.equals(result.sessionId(), targets.sessions().get(nodeId))) {
            throw invalid("A successful READ of this site in the current node session is required");
        }
        ManagedConfiguration proposal = AgentSetup.proposal(read.agentBefore, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        OperationView preview = create("PREVIEW", targets, proposal,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token),
                Map.of(nodeId, result.revision()), agent);
        operations.get(preview.operationId()).agentBefore = read.agentBefore;
        return preview;
    }

    synchronized OperationView createAgentProxyRouting(UUID agent, String nodeId,
            ProxyRoutingConfiguration routing, UUID readId) {
        prune();
        requireAgentCapacity(agent);
        ValidatedTargets targets = validateTargets(List.of(nodeId), CAPABILITY);
        if ("BUKKIT".equals(targets.platforms().get(nodeId))) throw invalid("Routing requires a proxy node");
        if (readId == null) {
            return create("READ", targets, ManagedConfiguration.proxy(new ProxyRoutingConfiguration(false, List.of())),
                    null, Map.of(), agent);
        }
        StoredOperation read = agentOperation(agent, readId);
        ConfigurationTaskResult result = read.results.get(nodeId);
        if (!"READ".equals(read.type) || !ManagedConfiguration.PROXY_ROUTING.equals(read.configuration.domain())
                || result == null || !result.success()
                || !Objects.equals(result.sessionId(), targets.sessions().get(nodeId))) {
            throw invalid("A successful routing READ in the current proxy session is required");
        }
        Map<String, String> requested = AgentSetup.routingValues(ManagedConfiguration.proxy(routing));
        if (AgentSetup.routingChanges(read.agentBefore, requested).stream().anyMatch(change -> change.length() > 500)) {
            throw invalid("Routing diff exceeds the review limit");
        }
        byte[] token = new byte[32];
        random.nextBytes(token);
        OperationView preview = create("PREVIEW", targets, ManagedConfiguration.proxy(routing),
                Base64.getUrlEncoder().withoutPadding().encodeToString(token),
                Map.of(nodeId, result.revision()), agent);
        operations.get(preview.operationId()).agentBefore = read.agentBefore;
        return preview;
    }

    synchronized OperationView createAgentSitePolicy(UUID agent, String nodeId, Boolean enabled, UUID readId) {
        prune();
        requireAgentCapacity(agent);
        ValidatedTargets targets = validateTargets(List.of(nodeId), QUICK_SETUP_CAPABILITY);
        if (!"BUKKIT".equals(targets.platforms().get(nodeId))) throw invalid("Site policy requires a Bukkit backend");
        var configuration = AgentSetup.sitePolicy(enabled);
        if (readId == null) return create("READ", targets, configuration, null, Map.of(), agent);
        return createAgentSitePolicyBatch(agent, Map.of(nodeId, readId), enabled);
    }

    synchronized OperationView createAgentSitePolicyBatch(UUID agent, Map<String, UUID> readIds, boolean enabled) {
        prune();
        requireAgentCapacity(agent);
        if (readIds.isEmpty() || readIds.size() > MAX_AGENT_TARGETS) throw invalid("Invalid agent target count");
        var configuration = AgentSetup.sitePolicy(enabled);
        ValidatedTargets targets = validateTargets(new ArrayList<>(readIds.keySet()), QUICK_SETUP_CAPABILITY);
        Map<String, String> revisions = new LinkedHashMap<>();
        Map<String, Map<String, String>> before = new LinkedHashMap<>();
        for (var target : readIds.entrySet()) {
            String nodeId = target.getKey();
            StoredOperation read = agentOperation(agent, target.getValue());
            ConfigurationTaskResult current = read.results.get(nodeId);
            if (!"BUKKIT".equals(targets.platforms().get(nodeId)) || !"READ".equals(read.type)
                    || !AgentSetup.SITE_POLICY.equals(read.configuration.preset()) || current == null
                    || !current.success() || !Objects.equals(current.sessionId(), targets.sessions().get(nodeId))) {
                throw new ValidationException("READ_REQUIRED", "A current successful site-policy read is required", List.of(nodeId));
            }
            if (read.agentBefore.equals(configuration.options())) {
                throw new ValidationException("NO_CHANGE", "Site policy is already set to the requested value", List.of(nodeId));
            }
            before.put(nodeId, read.agentBefore);
            revisions.put(nodeId, current.revision());
        }
        byte[] token = new byte[32];
        random.nextBytes(token);
        OperationView preview = create("PREVIEW", targets, configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token), revisions, agent);
        StoredOperation stored = operations.get(preview.operationId());
        stored.agentBeforeByNode = Map.copyOf(before);
        if (before.size() == 1) stored.agentBefore = before.values().iterator().next();
        return preview;
    }

    synchronized OperationView createAgentTransportCheck(UUID agent, String proxyId, String backendId) {
        prune();
        requireAgentCapacity(agent);
        ValidatedTargets targets = validateTargets(List.of(proxyId), TRANSPORT_TEST_CAPABILITY);
        NodeStatus proxy = registry.find(proxyId);
        NodeStatus backend = registry.find(backendId);
        if (!transportPeerAvailable(proxy, backend)) {
            throw new ValidationException("NODE_UNAVAILABLE", "A live, reported proxy/backend relationship is required", List.of());
        }
        var selector = new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP, null, List.of(), null, null,
                ManagedConfiguration.COMMUNICATION_TEST, Map.of("server", backendId));
        selector.validateProposal();
        OperationView view = create("READ", targets, selector, null, Map.of(), agent);
        operations.get(view.operationId()).agentPeerSession = backend.sessionId();
        return view;
    }

    private boolean transportPeerAvailable(NodeStatus proxy, NodeStatus backend) {
        return proxy != null && !"BUKKIT".equals(proxy.platform()) && backend != null && backend.online()
                && "BUKKIT".equals(backend.platform())
                && AgentHealth.fresh(proxy, registry.topologyObservedAt(proxy.nodeId()), clock.instant())
                && proxy.backends().stream().anyMatch(member -> member.backendId().equals(backend.nodeId()));
    }

    synchronized boolean agentTransportCurrent(UUID agent, UUID id) {
        StoredOperation operation = agentOperation(agent, id);
        String proxyId = operation.states.keySet().iterator().next();
        NodeStatus proxy = registry.find(proxyId);
        return proxy != null && Objects.equals(operation.targetSessions.get(proxyId), proxy.sessionId())
                && agentTransportPeerCurrent(operation, proxy);
    }

    private boolean agentTransportPeerCurrent(StoredOperation operation, NodeStatus proxy) {
        NodeStatus backend = registry.find(operation.configuration.options().get("server"));
        return transportPeerAvailable(proxy, backend) && operation.agentPeerSession.equals(backend.sessionId());
    }

    synchronized OperationView readAgentGeneralSettings(UUID agent, String nodeId) {
        prune();
        requireAgentCapacity(agent);
        NodeStatus node = registry.find(nodeId);
        if (node == null) throw new ValidationException("NODE_UNAVAILABLE", "Node cannot accept configuration operations", List.of(nodeId));
        var profile = "BUKKIT".equals(node.platform()) ? GeneralSettingsDocument.Profile.BACKEND : GeneralSettingsDocument.Profile.PROXY;
        var selector = ManagedConfiguration.file(GeneralSettingsDocument.fileName(profile), null);
        ValidatedTargets targets = validateTargets(List.of(nodeId), selector.capability());
        validateConfigurationTargets(targets, selector);
        OperationView read = create("READ", targets, selector, null, Map.of(), agent);
        operations.get(read.operationId()).agentGeneralSettings = true;
        return read;
    }

    synchronized OperationView previewAgentGeneralSettings(UUID agent, String nodeId, UUID readId,
                                                            Map<String, Object> overrides) {
        prune();
        requireAgentCapacity(agent);
        if (!agentOperation(agent, readId).agentGeneralSettings) throw invalid("A General Settings read is required");
        return createGeneralSettingsPreview(readId, nodeId, overrides, agent);
    }

    synchronized OperationView readAgentVoteSitesEditor(UUID agent, String nodeId, String siteKey) {
        prune();
        requireAgentCapacity(agent);
        if (siteKey == null || !siteKey.matches("[A-Za-z0-9_-]{1,64}")) throw invalid("Invalid site key");
        var selector = ManagedConfiguration.file("VoteSites.yml", null);
        ValidatedTargets targets = validateTargets(List.of(nodeId), selector.capability());
        validateConfigurationTargets(targets, selector);
        OperationView read = create("READ", targets, selector, null, Map.of(), agent);
        operations.get(read.operationId()).agentEditorSite = siteKey;
        return read;
    }

    synchronized OperationView previewAgentVoteSitesEditor(UUID agent, String nodeId, UUID readId,
            String action, String siteKey, Map<String, Object> fields) {
        prune();
        requireAgentCapacity(agent);
        if (siteKey == null || !siteKey.equals(agentOperation(agent, readId).agentEditorSite)) {
            throw invalid("A current editor read of this exact site is required");
        }
        return createVoteSitesPreview(readId, nodeId, action, siteKey, fields, agent);
    }

    synchronized OperationView readAgentReward(UUID agent, String nodeId, AgentSetup.RewardField field) {
        prune();
        requireAgentCapacity(agent);
        var selector = ManagedConfiguration.file(field.file(), null);
        ValidatedTargets targets = validateTargets(List.of(nodeId), selector.capability());
        validateConfigurationTargets(targets, selector);
        var read = create("READ", targets, selector, null, Map.of(), agent);
        operations.get(read.operationId()).agentRewardField = field;
        return read;
    }

    synchronized OperationView previewAgentReward(UUID agent, String nodeId, UUID readId, Object value) {
        prune();
        requireAgentCapacity(agent);
        var field = agentOperation(agent, readId).agentRewardField;
        if (field == null) throw invalid("A typed reward field read is required");
        return createRewardsPreview(readId, nodeId, field.file(), field.scope(), "SET_SCALAR",
                field.field(), value, agent, field);
    }

    synchronized OperationView readAgentGuided(UUID agent, String nodeId, GuidedSettings preset, boolean presence) {
        prune();
        requireAgentCapacity(agent);
        var targets = validateTargets(List.of(nodeId), preset.capability());
        validateTargets(List.of(nodeId), QUICK_SETUP_CAPABILITY);
        validateTargets(List.of(nodeId), FILE_CAPABILITY);
        if (!"BUKKIT".equals(targets.platforms().get(nodeId))) throw invalid("Guided setup requires a Bukkit backend");
        var selector = presence ? ManagedConfiguration.file(preset.file(), null) : preset.selector();
        var read = create("READ", targets, selector, null, Map.of(), agent);
        operations.get(read.operationId()).agentGuidedRead = preset;
        return read;
    }

    synchronized OperationView previewAgentGuided(UUID agent, GuidedSettings preset,
            Map<String, AgentOperations.GuidedReadIds> ids, Map<String, Object> overrides) {
        prune();
        requireAgentCapacity(agent);
        var targets = validateTargets(new ArrayList<>(ids.keySet()), preset.capability());
        validateTargets(new ArrayList<>(ids.keySet()), QUICK_SETUP_CAPABILITY);
        var revisions = new LinkedHashMap<String, String>();
        var before = new LinkedHashMap<String, Map<String, String>>();
        Map<String, String> proposal = null;
        var after = new LinkedHashMap<String, String>();
        for (var entry : ids.entrySet()) {
            String nodeId = entry.getKey();
            var read = agentOperation(agent, entry.getValue().typed());
            var fileRead = agentOperation(agent, entry.getValue().presence());
            var typed = read.results.get(nodeId);
            if (!"READ".equals(read.type) || read.agentGuidedRead != preset
                    || !ManagedConfiguration.QUICK_SETUP.equals(read.configuration.domain())
                    || fileRead.agentGuidedRead != preset || typed == null || !typed.success()
                    || !Objects.equals(typed.sessionId(), targets.sessions().get(nodeId))) {
                throw new ValidationException("READ_REQUIRED", "Read the typed preset and managed file again", List.of(nodeId));
            }
            var snapshot = fileSnapshot(fileRead.id, nodeId, preset.file());
            if (!snapshot.sessionId().equals(typed.sessionId()) || !snapshot.revision().equals(typed.revision())) {
                throw new ValidationException("STALE_REVISION", "Preset and file must agree on revision; repeat both reads", List.of(nodeId));
            }
            GuidedSettings.Evidence evidence;
            try {
                evidence = preset.evidence(snapshot.configuration().content(), read.agentBefore, overrides);
            } catch (IllegalArgumentException unsupported) {
                throw new ValidationException("GUIDED_EVIDENCE_UNAVAILABLE", unsupported.getMessage(), List.of(nodeId));
            }
            if (evidence.after().isEmpty()) throw new ValidationException("NO_CHANGE", "No reviewable change on this target", List.of(nodeId));
            if (proposal != null && !proposal.equals(evidence.options())) {
                throw new ValidationException("INCOMPATIBLE_TARGETS", "Use separate previews for differing retained settings", List.of(nodeId));
            }
            proposal = evidence.options();
            before.put(nodeId, evidence.before());
            after.putAll(evidence.after());
            revisions.put(nodeId, snapshot.revision());
        }
        byte[] token = new byte[32];
        random.nextBytes(token);
        var preview = create("PREVIEW", targets, preset.configuration(proposal),
                Base64.getUrlEncoder().withoutPadding().encodeToString(token), revisions, agent);
        var stored = operations.get(preview.operationId());
        stored.agentGuidedPreview = preset;
        stored.agentBeforeByNode = Map.copyOf(before);
        if (before.size() == 1) stored.agentBefore = before.values().iterator().next();
        stored.agentAfter = Map.copyOf(after);
        return preview;
    }

    synchronized OperationView readAgentProxyMethod(UUID agent, String nodeId, String method) {
        prune();
        requireAgentCapacity(agent);
        var proposal = AgentSetup.proxyMethod(method);
        var targets = validateTargets(List.of(nodeId), proposal.capability());
        boolean backend = "BUKKIT".equals(targets.platforms().get(nodeId));
        ManagedConfiguration selector = proposal;
        if (backend) {
            validateTargets(List.of(nodeId), QUICK_SETUP_CAPABILITY);
            selector = new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP, null, List.of(), null, null,
                    "proxy-backend", "HTTP".equals(method) ? Map.of("method", "HTTP") : Map.of());
        }
        var read = create("READ", targets, selector, null, Map.of(), agent);
        operations.get(read.operationId()).agentMethodReadCapability = proposal.capability();
        return read;
    }

    synchronized OperationView previewAgentProxyMethod(UUID agent, String proxyId, String method,
            Map<String, UUID> readIds, Instant expiresAt) {
        prune();
        requireAgentCapacity(agent);
        if (readIds.size() < 2 || readIds.size() > MAX_AGENT_TARGETS) throw invalid("Invalid proxy method target count");
        var configuration = AgentSetup.proxyMethod(method);
        var targets = validateTargets(new ArrayList<>(readIds.keySet()), configuration.capability());
        validateProxyMethodTargets(targets, configuration);
        var scope = new AgentProxyRollout(proxyId, expiresAt);
        if (!currentProxyRollout(scope, configuration, targets.sessions(), targets.platforms())) {
            throw invalid("A fresh, complete, available proxy network is required");
        }
        Map<String, String> revisions = new LinkedHashMap<>();
        Map<String, Map<String, String>> before = new LinkedHashMap<>();
        boolean anyChange = false;
        for (var entry : readIds.entrySet()) {
            String node = entry.getKey();
            var read = agentOperation(agent, entry.getValue());
            var result = read.results.get(node);
            if (!"READ".equals(read.type) || !configuration.capability().equals(read.agentMethodReadCapability)
                    || result == null || !result.success() || !Objects.equals(result.sessionId(), targets.sessions().get(node))) {
                throw new ValidationException("READ_REQUIRED", "A current method read for the required capability is needed", List.of(node));
            }
            anyChange |= !AgentSetup.proxyMethodChanges(read.agentBefore, method).isEmpty();
            before.put(node, read.agentBefore);
            revisions.put(node, result.revision());
        }
        if (!anyChange) throw invalid("The requested proxy method is already set on every target");
        byte[] token = new byte[32];
        random.nextBytes(token);
        var view = create("PREVIEW", targets, configuration, Base64.getUrlEncoder().withoutPadding().encodeToString(token),
                revisions, agent);
        var stored = operations.get(view.operationId());
        stored.agentBeforeByNode = Map.copyOf(before);
        stored.agentProxyRollout = scope;
        return view;
    }

    private boolean currentProxyRollout(AgentProxyRollout scope, ManagedConfiguration configuration,
            Map<String, UUID> sessions, Map<String, String> platforms) {
        if (scope.revoked || !clock.instant().isBefore(scope.expiresAt)) return false;
        NodeStatus proxy = registry.find(scope.proxyId);
        if (proxy == null || "BUKKIT".equals(proxy.platform())
                || !AgentHealth.fresh(proxy, registry.topologyObservedAt(scope.proxyId), clock.instant())
                || proxy.backends().size() + 1 != sessions.size() || !sessions.containsKey(scope.proxyId)
                || proxy.backends().stream().anyMatch(row -> !sessions.containsKey(row.backendId()))) return false;
        for (var entry : sessions.entrySet()) {
            NodeStatus node = registry.find(entry.getKey());
            if (node == null || !node.online() || !entry.getValue().equals(node.sessionId())
                    || !platforms.get(entry.getKey()).equals(node.platform())
                    || !node.acceptedCapabilities().contains(configuration.capability())
                    || "BUKKIT".equals(node.platform()) && !node.acceptedCapabilities().contains(QUICK_SETUP_CAPABILITY)) {
                return false;
            }
        }
        return true;
    }

    synchronized void revokeCoordinatedAgent(UUID agent) {
        operations.values().stream().filter(operation -> agent.equals(operation.agent))
                .map(operation -> operation.agentProxyRollout).filter(Objects::nonNull)
                .forEach(scope -> scope.revoked = true);
    }

    private boolean currentAgentCoordination(StoredOperation operation) {
        return operation.agentProxyRollout == null || currentProxyRollout(operation.agentProxyRollout,
                operation.configuration, operation.targetSessions, operation.targetPlatforms);
    }

    private boolean cancelChangedAgentCoordination(StoredOperation operation) {
        if (currentAgentCoordination(operation)) return false;
        for (var entry : operation.states.entrySet()) {
            Instant leased = operation.leasedAt.get(entry.getKey());
            if ("QUEUED".equals(entry.getValue()) || "IN_PROGRESS".equals(entry.getValue()) && leased != null
                    && !clock.instant().isBefore(leased.plus(LEASE))) {
                automaticCancellation(operation, entry.getKey(), operation.targetSessions.get(entry.getKey()),
                        "TARGET_CHANGED", "Coordinated operation scope, grant or topology changed", "TARGET_CHANGED");
            }
        }
        operation.approvalUsed = true;
        return true;
    }

    private void requireAgentCapacity(UUID agent) {
        var retained = operations.values().stream().filter(op -> agent.equals(op.agent)).toList();
        if (retained.size() >= 48 || retained.stream().filter(op -> !op.complete()).count() >= 4) {
            throw invalid("Agent operation limit reached");
        }
    }

    public synchronized AgentReview getAgentReview(UUID id) {
        prune();
        StoredOperation operation = operations.get(id);
        if (operation == null || operation.agent == null || !"PREVIEW".equals(operation.type)) {
            throw new ValidationException("OPERATION_NOT_FOUND", "Agent preview was not found", List.of());
        }
        return new AgentReview(view(operation), operation.agentBefore, operation.agentBeforeByNode, operation.agentAfter);
    }

    public synchronized OperationView getAgentOperation(UUID agent, UUID id) {
        prune();
        return view(agentOperation(agent, id));
    }

    synchronized Map<String, String> agentReadValues(UUID agent, UUID id) {
        StoredOperation operation = agentOperation(agent, id);
        return "READ".equals(operation.type) ? operation.agentBefore : Map.of();
    }

    synchronized Set<String> agentVoteLogRestartTargets(UUID agent, UUID id) {
        return agentOperation(agent, id).agentVoteLogRestartTargets;
    }

    synchronized UUID agentApplyId(UUID agent, UUID previewId) {
        return operations.values().stream().filter(op -> agent.equals(op.agent) && "APPLY".equals(op.type)
                && previewId.equals(op.sourceOperationId)).map(op -> op.id).findFirst().orElse(null);
    }

    private StoredOperation agentOperation(UUID agent, UUID id) {
        StoredOperation operation = operations.get(id);
        if (operation == null || !agent.equals(operation.agent)) {
            throw new ValidationException("OPERATION_NOT_FOUND", "Operation was not found", List.of());
        }
        return operation;
    }

    public synchronized OperationView approveAgentPreview(UUID id) {
        prune();
        StoredOperation preview = operations.get(id);
        if (preview == null || !agentReviewReady(preview)) {
            if (preview != null && !preview.approvalUsed && !currentAgentCoordination(preview)) {
                audit.append("AGENT_PREVIEW_INVALIDATED", preview.id, null, "SCOPE_CHANGED");
                preview.approvalUsed = true;
            }
            throw invalid("Preview is unavailable for browser approval");
        }
        return createApply(id, preview.approvalToken, true);
    }

    private boolean agentReviewReady(StoredOperation operation) {
        return operation.agent != null && "PREVIEW".equals(operation.type)
                && !operation.approvalUsed && operation.complete()
                && currentAgentCoordination(operation)
                && operation.results.values().stream().allMatch(ConfigurationTaskResult::success)
                && clock.instant().isBefore(operation.createdAt.plus(ACTIVE_RETENTION));
    }

    public synchronized OperationView createApply(UUID previewId, String approvalToken) {
        return createApply(previewId, approvalToken, false);
    }

    private OperationView createApply(UUID previewId, String approvalToken, boolean browserAgentReview) {
        prune();
        LinkedHashMap<UUID, StoredOperation> priorOperations = new LinkedHashMap<>(operations);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
        StoredOperation preview = operations.get(previewId);
        if (preview == null || !"PREVIEW".equals(preview.type)) throw invalid("preview operation was not found");
        if ((preview.agent != null) != browserAgentReview) throw invalid("Preview requires its original approval route");
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
        if (preview.agentGuidedPreview != null) validateTargets(targets.nodeIds(), QUICK_SETUP_CAPABILITY);
        validateConfigurationTargets(targets, preview.configuration);
        validateProxyMethodTargets(targets, preview.configuration);
        rejectOverlappingProxyMethodApply(targets, preview.configuration);
        rejectOverlappingVoteLoggingApply(targets, preview.configuration);
        Map<String, String> revisions = new LinkedHashMap<>();
        preview.results.forEach((node, result) -> revisions.put(node, result.revision()));
        boolean priorApprovalUsed = preview.approvalUsed;
        StoredOperation apply = store("APPLY", targets, preview.configuration,
                null, revisions, preview.id, preview.agent);
        apply.agentProxyRollout = preview.agentProxyRollout;
        apply.agentGuidedPreview = preview.agentGuidedPreview;
        if (preview.agentGuidedPreview == GuidedSettings.LOG) {
            apply.agentVoteLogRestartTargets = preview.agentBeforeByNode.entrySet().stream()
                    .filter(row -> row.getValue().containsKey("VoteLogging.Enabled")).map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        preview.approvalUsed = true;
        try {
            if (browserAgentReview) {
                audit.append("AGENT_APPLY_APPROVED", apply.id, targets.nodeIds().size() == 1 ? targets.nodeIds().get(0) : null,
                        preview.id.toString());
            } else {
                audit.append("APPLY_APPROVED", apply.id, null, "QUEUED");
            }
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

    /** Typed view of a retained successful backend Config.yml or proxy bungeeconfig.yml read. */
    public synchronized SettingsState generalSettingsState(UUID readId, String nodeId) {
        SettingsSnapshot source = settingsSnapshot(readId, nodeId);
        ConfigurationTaskResult snapshot = source.result();
        try {
            return new SettingsState(readId, nodeId, snapshot.sessionId(), snapshot.revision(),
                    source.profile(), GeneralSettingsDocument.fields(snapshot.configuration().content(), source.profile()));
        } catch (IllegalArgumentException malformed) {
            throw new ValidationException("INVALID_CONFIGURATION", "General Settings requires an unambiguous YAML mapping", List.of(nodeId));
        }
    }

    public synchronized OperationView createGeneralSettingsPreview(UUID readId, String nodeId,
            Map<String, Object> overrides) {
        StoredOperation read = operations.get(readId);
        if (read != null && read.agent != null) throw invalid("Agent reads require the agent editor");
        return createGeneralSettingsPreview(readId, nodeId, overrides, null);
    }

    private OperationView createGeneralSettingsPreview(UUID readId, String nodeId,
            Map<String, Object> overrides, UUID agent) {
        SettingsSnapshot source = settingsSnapshot(readId, nodeId);
        ConfigurationTaskResult snapshot = source.result();
        if (overrides == null || overrides.isEmpty()
                || overrides.size() > GeneralSettingsDocument.maxOverrides(source.profile())) {
            throw invalid("General Settings requires one or more bounded typed edits");
        }
        String proposal;
        try {
            proposal = GeneralSettingsDocument.patch(snapshot.configuration().content(), source.profile(), overrides);
        } catch (IllegalArgumentException unsupported) {
            throw invalid("Only present, supported General Settings values can be edited");
        }
        ManagedConfiguration configuration = ManagedConfiguration.file(GeneralSettingsDocument.fileName(source.profile()), proposal);
        ValidatedTargets targets = validateTargets(List.of(nodeId), configuration.capability());
        validateConfigurationTargets(targets, configuration);
        byte[] token = new byte[32];
        random.nextBytes(token);
        if (agent == null) {
            return create("PREVIEW", targets, configuration,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(token), Map.of(nodeId, snapshot.revision()));
        }
        String current = snapshot.configuration().content();
        var before = GeneralSettingsDocument.fields(current, source.profile());
        var after = GeneralSettingsDocument.fields(proposal, source.profile());
        List<String> diff = GeneralSettingsDocument.reviewedChangedPaths(source.profile(), before, after);
        if (diff.isEmpty()) throw invalid("General Settings values are already set");
        var preview = create("PREVIEW", targets, configuration,
                Base64.getUrlEncoder().withoutPadding().encodeToString(token), Map.of(nodeId, snapshot.revision()), agent);
        StoredOperation stored = operations.get(preview.operationId());
        stored.agentGeneralSettings = true;
        stored.agentBefore = AgentSetup.settingsValues(before);
        var proposed = new LinkedHashMap<>(AgentSetup.settingsValues(after));
        proposed.entrySet().removeIf(entry -> entry.getValue().equals(stored.agentBefore.get(entry.getKey())));
        stored.agentAfter = Map.copyOf(proposed);
        stored.agentDocumentDigest = AgentSetup.documentDigest(current);
        stored.agentExpectedChanges = Set.copyOf(diff);
        return preview;
    }

    private SettingsSnapshot settingsSnapshot(UUID readId, String nodeId) {
        prune();
        StoredOperation read = operations.get(readId);
        if (read == null || !"READ".equals(read.type)
                || !ManagedConfiguration.FILE.equals(read.configuration.domain())) {
            throw new ValidationException("READ_REQUIRED", "A retained successful General Settings read is required",
                    List.of(nodeId));
        }
        GeneralSettingsDocument.Profile profile;
        try {
            profile = GeneralSettingsDocument.profileForFile(read.configuration.fileName());
        } catch (IllegalArgumentException invalidFile) {
            throw new ValidationException("READ_REQUIRED", "A retained successful General Settings read is required",
                    List.of(nodeId));
        }
        NodeStatus node = registry.find(nodeId);
        boolean proxy = node != null && !"BUKKIT".equalsIgnoreCase(node.platform());
        if (node == null || proxy != (profile == GeneralSettingsDocument.Profile.PROXY)) {
            throw new ValidationException("TARGET_ROLE_CHANGED", "The target role changed; read it again", List.of(nodeId));
        }
        return new SettingsSnapshot(fileSnapshot(readId, nodeId, GeneralSettingsDocument.fileName(profile)), profile);
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
            throw new ValidationException("TARGET_CHANGED", "The node reconnected; read it again", List.of(nodeId));
        }
        return result;
    }

    public record SettingsState(UUID readOperationId, String nodeId, UUID sessionId, String revision,
            GeneralSettingsDocument.Profile profile,
            Map<String, GeneralSettingsDocument.Field> fields) { }

    private record SettingsSnapshot(ConfigurationTaskResult result, GeneralSettingsDocument.Profile profile) { }

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
        StoredOperation read = operations.get(readId);
        if (read != null && read.agent != null) throw invalid("Agent reads require the agent editor");
        return createVoteSitesPreview(readId, nodeId, action, siteKey, fields, null);
    }

    private OperationView createVoteSitesPreview(UUID readId, String nodeId, String action, String siteKey,
            Map<String, Object> fields, UUID agent) {
        ConfigurationTaskResult snapshot = fileSnapshot(readId, nodeId, "VoteSites.yml");
        String proposal;
        try {
            proposal = switch (action == null ? "" : action) {
                case "ADD" -> VoteSitesDocument.add(snapshot.configuration().content(), siteKey, fields);
                case "EDIT" -> VoteSitesDocument.edit(snapshot.configuration().content(), siteKey, fields);
                case "REMOVE" -> {
                    if (fields != null && !fields.isEmpty()) throw new IllegalArgumentException();
                    if (agent != null) VoteSitesDocument.requireReviewableRemoval(snapshot.configuration().content(), siteKey);
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
        String approval = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        var revisions = Map.of(nodeId, snapshot.revision());
        if (agent == null) return create("PREVIEW", targets, configuration, approval, revisions);
        Map<String, String> before;
        Map<String, String> after;
        try {
            before = AgentSetup.editorSiteValues(siteKey, snapshot.configuration().content());
            after = AgentSetup.editorSiteValues(siteKey, proposal);
        } catch (IllegalArgumentException unsafe) {
            throw invalid("Site values cannot be reviewed safely");
        }
        var changes = new LinkedHashMap<String, String>();
        var prior = new LinkedHashMap<String, String>();
        var paths = new java.util.HashSet<String>();
        for (String key : VoteSitesDocument.editableKeys()) {
            if (Objects.equals(before.get(key), after.get(key))) continue;
            String path = "VoteSites." + siteKey + "." + key;
            if (before.containsKey(key)) prior.put(path, before.get(key));
            changes.put(path, after.getOrDefault(key, "(removed)"));
            paths.add("changed " + path);
        }
        if (paths.isEmpty()) throw invalid("Site action has no reviewable changes");
        if (!before.get("exists").equals(after.get("exists"))) {
            String path = "VoteSites." + siteKey + " exists";
            prior.put(path, before.get("exists"));
            changes.put(path, after.get("exists"));
        }
        var preview = create("PREVIEW", targets, configuration, approval, revisions, agent);
        StoredOperation stored = operations.get(preview.operationId());
        stored.agentBefore = Map.copyOf(prior);
        stored.agentAfter = Map.copyOf(changes);
        stored.agentDocumentDigest = AgentSetup.documentDigest(snapshot.configuration().content());
        stored.agentExpectedChanges = Set.copyOf(paths);
        return preview;
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
        StoredOperation read = operations.get(readId);
        if (read != null && read.agent != null) throw invalid("Agent reads require the agent editor");
        return createRewardsPreview(readId, nodeId, fileName, rewardPath, action, field, value, null, null);
    }

    private OperationView createRewardsPreview(UUID readId, String nodeId, String fileName,
            String rewardPath, String action, String field, Object value, UUID agent, AgentSetup.RewardField agentField) {
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
        String approval = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        var revisions = Map.of(nodeId, snapshot.revision());
        if (agent == null) return create("PREVIEW", targets, configuration, approval, revisions);
        String prior;
        String next;
        try {
            prior = agentField.values(snapshot.configuration().content()).get("rewardValue");
            next = agentField.values(proposal).get("rewardValue");
        } catch (IllegalArgumentException unsupported) {
            throw invalid("Reward field cannot be reviewed safely");
        }
        if (prior.equals(next)) throw invalid("Reward field has no reviewable change");
        var preview = create("PREVIEW", targets, configuration, approval, revisions, agent);
        var stored = operations.get(preview.operationId());
        String path = agentField.changePath();
        String label = fileName + " / " + path;
        stored.agentBefore = Map.of(label, prior);
        stored.agentAfter = Map.of(label, next);
        stored.agentDocumentDigest = AgentSetup.documentDigest(snapshot.configuration().content());
        stored.agentExpectedChanges = Set.of("changed " + path);
        return preview;
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
        if (preview == null || preview.agent != null || !"PREVIEW".equals(preview.type) || preview.expectedRevisions.isEmpty()
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
        if (original.recovered || original.agent != null) {
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
        StoredOperation retry = store(original.type, targets, original.configuration, token, revisions, original.id, null);
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
        return registry.withStableTopology(() -> registry.withSession(nodeId, sessionId,
                node -> claimCurrentSession(nodeId, node)));
    }

    private ConfigurationTask claimCurrentSession(String nodeId, NodeStatus node) {
        prune();
        Instant now = clock.instant();
        for (StoredOperation operation : operations.values()) {
            String state = operation.states.get(nodeId);
            Instant leased = operation.leasedAt.get(nodeId);
            if ("QUEUED".equals(state) || ("IN_PROGRESS".equals(state) && leased != null
                    && !now.isBefore(leased.plus(LEASE)))) {
                if (cancelChangedAgentCoordination(operation) || cancelChangedFileRole(operation, node)) continue;
                if (cancelChangedAgentTarget(operation, node) || cancelChangedBackendSetupRole(operation, node)) continue;
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

    private boolean cancelChangedAgentTarget(StoredOperation operation, NodeStatus node) {
        if (operation.agent == null || (Objects.equals(operation.targetPlatforms.get(node.nodeId()), node.platform())
                && Objects.equals(operation.targetSessions.get(node.nodeId()), node.sessionId())
                && (operation.agentPeerSession == null || agentTransportPeerCurrent(operation, node)))) return false;
        automaticCancellation(operation, node.nodeId(), node.sessionId(), "TARGET_CHANGED",
                "Agent operation target reconnected or changed role", "TARGET_CHANGED");
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
        if (node.online() && node.acceptedCapabilities().contains(operation.configuration.capability())
                && (operation.agentGuidedRead == null
                    || node.acceptedCapabilities().containsAll(Set.copyOf(List.of(QUICK_SETUP_CAPABILITY,
                            operation.agentGuidedRead.capability(), FILE_CAPABILITY))))
                && (operation.agentGuidedPreview == null || node.acceptedCapabilities().contains(QUICK_SETUP_CAPABILITY))
                && (operation.agentMethodReadCapability == null
                    || node.acceptedCapabilities().contains(operation.agentMethodReadCapability)
                    && (!"BUKKIT".equals(node.platform()) || node.acceptedCapabilities().contains(QUICK_SETUP_CAPABILITY)))) return false;
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

    private static boolean agentPreviewReviewable(StoredOperation operation, String nodeId, ConfigurationTaskResult result) {
        if (operation.agentGuidedPreview != null) {
            var changes = operation.agentBeforeByNode.get(nodeId).keySet().stream()
                    .map(path -> "changed " + path).collect(java.util.stream.Collectors.toSet());
            return operation.configuration.equals(result.configuration())
                    && changes.size() == result.changes().size() && changes.equals(Set.copyOf(result.changes()));
        }
        if (operation.agentProxyRollout != null) {
            var changes = AgentSetup.proxyMethodChanges(operation.agentBeforeByNode.get(nodeId),
                    operation.configuration.options().get("method"));
            return operation.configuration.equals(result.configuration())
                    && changes.size() == result.changes().size() && changes.equals(Set.copyOf(result.changes()));
        }
        if (operation.agentDocumentDigest != null) {
            return result.configuration() != null && result.configuration().content() != null
                    && operation.agentDocumentDigest.equals(AgentSetup.documentDigest(result.configuration().content()))
                    && result.changes().size() == operation.agentExpectedChanges.size()
                    && operation.agentExpectedChanges.equals(Set.copyOf(result.changes()));
        }
        if (AgentSetup.SITE_POLICY.equals(operation.configuration.preset())) {
            return operation.configuration.equals(result.configuration())
                    && result.changes().equals(List.of("changed AutoCreateVoteSites"));
        }
        if (!ManagedConfiguration.PROXY_ROUTING.equals(operation.configuration.domain())) {
            return operation.configuration.equals(result.configuration())
                    && AgentSetup.reviewableChanges(operation.configuration, operation.agentBefore, result.changes());
        }
        try {
            return operation.agentBefore.equals(AgentSetup.routingValues(result.configuration()))
                    && AgentSetup.routingChanges(operation.agentBefore,
                            AgentSetup.routingValues(operation.configuration)).equals(result.changes());
        } catch (IllegalArgumentException unsafe) {
            return false;
        }
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
        if (cancelChangedAgentTarget(operation, node) || cancelChangedFileRole(operation, node)
                || cancelChangedBackendSetupRole(operation, node)
                || cancelChangedProxyMethodRole(operation, node)
                || cancelLostCapability(operation, node)) {
            return view(operation);
        }
        try {
            validateResultConfiguration(operation, result, node);
        } catch (ValidationException incompatible) {
            if (operation.agentGuidedRead == null || !ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())) {
                throw incompatible;
            }
            result = new ConfigurationTaskResult(result.sessionId(), false, "UNSUPPORTED",
                    "The connector did not return the required versioned preset READ", null,
                    (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
        }
        if ("PREVIEW".equals(operation.type) && result.success()
                && operation.expectedRevisions.containsKey(nodeId)
                && !Objects.equals(operation.expectedRevisions.get(nodeId), result.revision())) {
            result = new ConfigurationTaskResult(result.sessionId(), false, "STALE_REVISION",
                    "Configuration changed since READ; read and preview again", null,
                    (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
        }
        if (operation.agent != null && "PREVIEW".equals(operation.type) && result.success()
                && (result.changes().size() >= 20 || result.changes().contains("additional changes omitted")
                || retainedChangeBytes + result.changes().stream().mapToLong(
                        change -> change.getBytes(StandardCharsets.UTF_8).length).sum() > MAX_RETAINED_CHANGE_BYTES
                || !agentPreviewReviewable(operation, nodeId, result))) {
            result = new ConfigurationTaskResult(result.sessionId(), false, "DIFF_UNAVAILABLE",
                    "Preview changes cannot be reviewed completely", null, (ManagedConfiguration) null,
                    List.of(), false, false, result.attemptId());
        }
        Map<String, String> agentRead = Map.of();
        if (operation.agent != null && operation.agentPeerSession == null
                && "READ".equals(operation.type) && result.success()) {
            try {
                if (operation.agentGuidedRead != null) {
                    if (ManagedConfiguration.FILE.equals(operation.configuration.domain())) {
                        if (result.configuration() == null || result.configuration().content() == null) {
                            throw new IllegalArgumentException("Missing masked presence evidence");
                        }
                    } else {
                        agentRead = operation.agentGuidedRead.effectiveValues(result.configuration());
                        result = new ConfigurationTaskResult(result.sessionId(), true, "OK", "Scoped configuration read",
                                result.revision(), (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
                    }
                } else if (operation.agentGeneralSettings) {
                    if (result.configuration() == null || result.configuration().content() == null) {
                        throw new IllegalArgumentException("Missing redacted settings");
                    }
                    agentRead = AgentSetup.settingsValues(GeneralSettingsDocument.fields(result.configuration().content(),
                            GeneralSettingsDocument.profileForFile(operation.configuration.fileName())));
                } else if (operation.agentMethodReadCapability != null) {
                    boolean backend = "proxy-backend".equals(operation.configuration.preset());
                    agentRead = AgentSetup.proxyMethodValues(result.configuration(), backend);
                    if (!backend && !AgentSetup.documentDigest(agentRead.get("method")).equals(result.revision())) {
                        throw new IllegalArgumentException("Unreviewable proxy method revision");
                    }
                    result = new ConfigurationTaskResult(result.sessionId(), true, "OK", "Scoped configuration read",
                            result.revision(), (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
                } else if (operation.agentRewardField != null) {
                    if (result.configuration() == null || result.configuration().content() == null) {
                        throw new IllegalArgumentException("Missing redacted reward source");
                    }
                    agentRead = operation.agentRewardField.values(result.configuration().content());
                } else if (operation.agentEditorSite != null) {
                    if (result.configuration() == null || result.configuration().content() == null) {
                        throw new IllegalArgumentException("Missing redacted VoteSites source");
                    }
                    agentRead = AgentSetup.editorSiteValues(operation.agentEditorSite, result.configuration().content());
                } else {
                    agentRead = ManagedConfiguration.PROXY_ROUTING.equals(operation.configuration.domain())
                            ? AgentSetup.routingValues(result.configuration())
                            : AgentSetup.SITE_POLICY.equals(operation.configuration.preset())
                                ? AgentSetup.sitePolicyValues(result.configuration())
                                : AgentSetup.readSite(operation.agentSite, result.configuration());
                    result = new ConfigurationTaskResult(result.sessionId(), true, "OK", "Scoped configuration read",
                            result.revision(), (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
                }
            } catch (IllegalArgumentException unreviewable) {
                result = new ConfigurationTaskResult(result.sessionId(), false,
                        operation.agentGuidedRead == null ? "DIFF_UNAVAILABLE" : "GUIDED_EVIDENCE_UNAVAILABLE",
                        "The requested configuration cannot be represented safely", null,
                        (ManagedConfiguration) null, List.of(), false, false, result.attemptId());
            }
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
            if (requiresVoteLoggingRestart(operation, nodeId, result)) {
                reclaimStaleRestartSessions(nodeId);
                voteLoggingRestartSessions.remove(nodeId);
                voteLoggingRestartSessions.put(nodeId, result.sessionId());
            }
            operation.states.put(nodeId, "COMPLETE");
            operation.leasedAt.remove(nodeId);
            operation.attemptIds.remove(nodeId);
            persist();
            audit.append("TASK_COMPLETED", operation.id, nodeId, result.success() ? "SUCCESS" : result.code());
            if (!agentRead.isEmpty()) operation.agentBefore = agentRead;
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
        return create(type, targets, config, token, revisions, null);
    }

    private OperationView create(String type, ValidatedTargets targets, ManagedConfiguration config, String token,
            Map<String, String> revisions, UUID agent) {
        LinkedHashMap<UUID, StoredOperation> priorOperations = new LinkedHashMap<>(operations);
        long priorChanges = retainedChangeBytes;
        long priorMessages = retainedMessageBytes;
        long priorFiles = retainedFileBytes;
        StoredOperation operation = store(type, targets, config, token, revisions, null, agent);
        try {
            if (agent == null) audit.append("OPERATION_CREATED", operation.id, null, type);
            else audit.append("AGENT_" + type + "_CREATED", operation.id,
                    targets.nodeIds().size() == 1 ? targets.nodeIds().get(0) : null, agent.toString());
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
                                  String token, Map<String, String> revisions, UUID protectedOperation, UUID agent) {
        prune();
        if (config != null && largeContentOperation(config)) {
            if (agent != null) {
                boolean apply = "APPLY".equals(type);
                ensureFileCapacity(protectedOperation,
                        candidate -> candidate.agent != null && "APPLY".equals(candidate.type) == apply,
                        apply ? MAX_AGENT_FILE_APPLIES : MAX_AGENT_FILE_READS_AND_PREVIEWS);
            }
            ensureFileCapacity(protectedOperation, candidate -> true, MAX_FILE_OPERATIONS);
        }
        if (operations.size() >= MAX_OPERATIONS) throw new ValidationException("OPERATION_LIMIT",
                "Too many retained operations", List.of());
        UUID id = UUID.randomUUID();
        LinkedHashMap<String, String> states = new LinkedHashMap<>();
        targets.nodeIds().forEach(node -> states.put(node, "QUEUED"));
        StoredOperation result = new StoredOperation(id, type, config, token, clock.instant(), states,
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(revisions),
                new LinkedHashMap<>(targets.platforms()), new LinkedHashMap<>(targets.sessions()), protectedOperation,
                false);
        result.agent = agent;
        operations.put(id, result);
        return result;
    }

    private void ensureFileCapacity(UUID protectedOperation,
            java.util.function.Predicate<StoredOperation> inLane, int limit) {
        long retained = operations.values().stream().filter(StoredOperation::fileOperation).filter(inLane).count();
        Iterator<Map.Entry<UUID, StoredOperation>> iterator = operations.entrySet().iterator();
        while (retained >= limit && iterator.hasNext()) {
            StoredOperation candidate = iterator.next().getValue();
            boolean boundApprovalPending = "PREVIEW".equals(candidate.type) && !candidate.expectedRevisions.isEmpty()
                    && !candidate.approvalUsed && candidate.createdAt.plus(ACTIVE_RETENTION).isAfter(clock.instant())
                    && candidate.results.values().stream().allMatch(ConfigurationTaskResult::success);
            if (candidate.fileOperation() && inLane.test(candidate) && candidate.complete() && !candidate.id.equals(protectedOperation)
                    && !boundApprovalPending) {
                audit.append("OPERATION_EVICTED", candidate.id, null, "FILE_RETENTION_LIMIT");
                releaseResultDetails(candidate);
                iterator.remove();
                retained--;
            }
        }
        if (retained >= limit) {
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

    private static boolean requiresVoteLoggingRestart(StoredOperation operation, String nodeId, ConfigurationTaskResult result) {
        return result.success() && "APPLY".equals(operation.type)
                && ManagedConfiguration.QUICK_SETUP.equals(operation.configuration.domain())
                && "vote-logging".equals(operation.configuration.preset())
                && (operation.agentVoteLogRestartTargets.contains(nodeId) || result.changes().stream()
                .anyMatch(change -> change.matches(".*VoteLogging\\.(Enabled|UseMainMySQL)\\b.*")));
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
                && !operation.approvalUsed && operation.agent == null ? operation.approvalToken : null;
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
                operation.recovered, retryable(operation), operation.agent, agentReviewReady(operation));
    }

    private static boolean retryable(StoredOperation operation) {
        if (operation.recovered || operation.agent != null || !operation.complete()
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
                                UUID sourceOperationId, boolean recovered, boolean retryable, UUID agentId, boolean browserReviewReady) {
        public OperationView(UUID operationId, String type, String state, Instant createdAt,
                             ManagedConfiguration configuration, Map<String, String> nodeStates,
                             Map<String, ConfigurationTaskResult> results, String approvalToken,
                             UUID sourceOperationId, boolean recovered, boolean retryable) {
            this(operationId, type, state, createdAt, configuration, nodeStates, results, approvalToken,
                    sourceOperationId, recovered, retryable, null, false);
        }
    }

    public record AgentReview(OperationView operation, Map<String, String> before,
                              Map<String, Map<String, String>> beforeByNode, Map<String, String> after) { }

    public record OperationListView(List<OperationView> items, Map<String, UUID> voteLoggingRestartSessions,
                                    long configurationGeneration) {
        public OperationListView(List<OperationView> items, Map<String, UUID> voteLoggingRestartSessions) {
            this(items, voteLoggingRestartSessions, 0L);
        }
    }

    private record ValidatedTargets(List<String> nodeIds, Map<String, String> platforms,
                                    Map<String, UUID> sessions) { }

    private static final class AgentProxyRollout {
        private final String proxyId;
        private final Instant expiresAt;
        private boolean revoked;

        private AgentProxyRollout(String proxyId, Instant expiresAt) {
            this.proxyId = proxyId;
            this.expiresAt = expiresAt;
        }
    }

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
        private UUID agent;
        private String agentSite;
        private String agentEditorSite;
        private AgentSetup.RewardField agentRewardField;
        private boolean agentGeneralSettings;
        private GuidedSettings agentGuidedRead;
        private GuidedSettings agentGuidedPreview;
        private Set<String> agentVoteLogRestartTargets = Set.of();
        private String agentDocumentDigest;
        private Set<String> agentExpectedChanges = Set.of();
        private Map<String, String> agentAfter = Map.of();
        private UUID agentPeerSession;
        private String agentMethodReadCapability;
        private AgentProxyRollout agentProxyRollout;
        private Map<String, String> agentBefore = Map.of();
        private Map<String, Map<String, String>> agentBeforeByNode = Map.of();

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
