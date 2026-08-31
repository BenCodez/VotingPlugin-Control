package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.InspectionQuery;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
import com.bencodez.votingplugin.control.protocol.Protocol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Thread-safe, deterministic current topology registry. State is intentionally in memory for this milestone. */
public final class InMemoryNodeRegistry implements NodeRegistry {
    public static final Set<String> SUPPORTED_CAPABILITIES = Set.of("discovery.read", "presence.snapshot",
            ConfigurationOperations.CAPABILITY, ConfigurationOperations.FILE_CAPABILITY,
            ConfigurationOperations.QUICK_SETUP_CAPABILITY, ConfigurationOperations.VOTE_SITES_SYNC_CAPABILITY,
            ConfigurationOperations.TRANSPORT_TEST_CAPABILITY, ConfigurationOperations.PROXY_METHOD_CAPABILITY,
            "config.file-comments.v1", InspectionQuery.CAPABILITY);
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern CAPABILITY = Pattern.compile("[a-z][a-z0-9.-]{0,63}");
    private static final Set<String> PLATFORMS = Set.of("BUNGEECORD", "VELOCITY", "BUKKIT");
    private static final int MAX_CAPABILITIES = 64;
    private static final int MAX_DETECTED_PLUGINS = 128;
    private static final int MAX_BACKENDS = 4096;
    private static final int MAX_TOTAL_BACKENDS = 65536;
    private static final int MAX_TOTAL_DETECTED_PLUGINS = 16384;

    private final Map<String, StoredNode> nodes = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration offlineTimeout;
    private final int maxTotalBackends;
    private final int maxTotalDetectedPlugins;
    private int retainedBackends;
    private int retainedDetectedPlugins;
    private long membershipRevision;

    public InMemoryNodeRegistry(Clock clock, Duration offlineTimeout) {
        this(clock, offlineTimeout, MAX_TOTAL_BACKENDS, MAX_TOTAL_DETECTED_PLUGINS);
    }

    InMemoryNodeRegistry(Clock clock, Duration offlineTimeout, int maxTotalBackends) {
        this(clock, offlineTimeout, maxTotalBackends, MAX_TOTAL_DETECTED_PLUGINS);
    }

    InMemoryNodeRegistry(Clock clock, Duration offlineTimeout, int maxTotalBackends,
                         int maxTotalDetectedPlugins) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.offlineTimeout = Objects.requireNonNull(offlineTimeout, "offlineTimeout");
        if (maxTotalBackends < 1) throw new IllegalArgumentException("maxTotalBackends must be positive");
        if (maxTotalDetectedPlugins < 1) throw new IllegalArgumentException("maxTotalDetectedPlugins must be positive");
        this.maxTotalBackends = maxTotalBackends;
        this.maxTotalDetectedPlugins = maxTotalDetectedPlugins;
        if (offlineTimeout.isNegative() || offlineTimeout.isZero()) {
            throw new IllegalArgumentException("offlineTimeout must be positive");
        }
    }

    @Override
    public synchronized RegistrationResult register(NodeRegistration registration) {
        validate(registration);
        Instant now = clock.instant();
        if (reclaimOfflineTopology(now, registration.nodeId())) membershipRevision++;
        StoredNode current = nodes.get(registration.nodeId());
        int replacementPluginTotal = retainedDetectedPlugins
                - (current == null ? 0 : current.detectedPlugins.size()) + registration.detectedPlugins().size();
        if (replacementPluginTotal > maxTotalDetectedPlugins) {
            throw new ValidationException("REGISTRY_LIMIT", "Plugin inventory exceeds registry capacity",
                    List.of(registration.nodeId()));
        }
        AtomicBoolean created = new AtomicBoolean();
        AtomicReference<StoredNode> result = new AtomicReference<>();
        nodes.compute(registration.nodeId(), (ignored, existing) -> {
            created.set(existing == null);
            List<BackendServerIdentity> backends = existing != null
                    && existing.sessionId.equals(registration.sessionId()) ? existing.backends : List.of();
            if (existing != null && backends.isEmpty()) retainedBackends -= existing.backends.size();
            long sequence = existing != null && existing.sessionId.equals(registration.sessionId())
                    ? existing.snapshotSequence : -1L;
            StoredNode replacement = new StoredNode(registration.nodeId(), registration.sessionId(),
                    registration.displayName().trim(), registration.platform(), registration.pluginVersion().trim(),
                    registration.protocolVersion(), registration.capabilities(), accepted(registration.capabilities()),
                    registration.detectedPlugins(), backends, sequence, now, now);
            result.set(replacement);
            return replacement;
        });
        retainedDetectedPlugins = replacementPluginTotal;
        if (created.get()) membershipRevision++;
        return new RegistrationResult(view(result.get()), created.get());
    }

    @Override
    public NodeStatus heartbeat(String nodeId, Heartbeat heartbeat) {
        validateId(nodeId, "nodeId");
        if (heartbeat == null) {
            throw invalid("heartbeat is required");
        }
        validateSession(heartbeat.sessionId());
        validateProtocol(heartbeat.protocolVersion());
        validateCapabilities(heartbeat.capabilities(), heartbeat.requiredCapabilities());
        Instant now = clock.instant();
        AtomicReference<StoredNode> result = new AtomicReference<>();
        nodes.computeIfPresent(nodeId, (ignored, existing) -> {
            requireSession(existing, heartbeat.sessionId());
            StoredNode updated = new StoredNode(existing.nodeId, existing.sessionId, existing.displayName,
                    existing.platform, existing.pluginVersion, existing.protocolVersion, heartbeat.capabilities(),
                    accepted(heartbeat.capabilities()), existing.detectedPlugins, existing.backends,
                    existing.snapshotSequence, now, now);
            result.set(updated);
            return updated;
        });
        if (result.get() == null) {
            throw new ValidationException("NODE_NOT_FOUND", "Node is not registered", List.of());
        }
        return view(result.get());
    }

    @Override
    public synchronized SnapshotResult replacePresence(String nodeId, PresenceSnapshot snapshot) {
        validateId(nodeId, "nodeId");
        validateSnapshot(snapshot);
        Instant now = clock.instant();
        if (reclaimOfflineTopology(now, nodeId)) membershipRevision++;
        AtomicBoolean applied = new AtomicBoolean();
        AtomicReference<StoredNode> result = new AtomicReference<>();
        nodes.computeIfPresent(nodeId, (ignored, existing) -> {
            requireSession(existing, snapshot.sessionId());
            if (snapshot.sequence() <= existing.snapshotSequence) {
                result.set(existing);
                return existing;
            }
            List<BackendServerIdentity> sorted = snapshot.backends().stream()
                    .sorted(Comparator.comparing(BackendServerIdentity::backendId)).toList();
            int replacementTotal = retainedBackends - existing.backends.size() + sorted.size();
            if (replacementTotal > maxTotalBackends) {
                throw new ValidationException("REGISTRY_LIMIT", "Backend snapshot exceeds registry capacity",
                        List.of(nodeId));
            }
            StoredNode updated = new StoredNode(existing.nodeId, existing.sessionId, existing.displayName,
                    existing.platform, existing.pluginVersion, existing.protocolVersion,
                    existing.advertisedCapabilities, existing.acceptedCapabilities, existing.detectedPlugins,
                    sorted, snapshot.sequence(), now, now);
            applied.set(true);
            retainedBackends = replacementTotal;
            result.set(updated);
            return updated;
        });
        if (result.get() == null) {
            throw new ValidationException("NODE_NOT_FOUND", "Node is not registered", List.of());
        }
        return new SnapshotResult(view(result.get()), applied.get());
    }

    @Override
    public List<NodeStatus> list(int offset, int limit) {
        return page(offset, limit, null).items();
    }

    @Override
    public synchronized RegistryPage page(int offset, int limit, Long expectedRevision) {
        if (offset < 0 || limit < 1 || limit > 100) {
            throw invalid("offset must be >= 0 and limit must be between 1 and 100");
        }
        if (expectedRevision != null && expectedRevision.longValue() != membershipRevision) {
            throw new ValidationException("REGISTRY_CHANGED", "Node registry changed during pagination",
                    List.of("expectedRevision=" + expectedRevision, "actualRevision=" + membershipRevision));
        }
        List<NodeStatus> items = nodes.values().stream().sorted(Comparator.comparing(node -> node.nodeId))
                .skip(offset).limit(limit).map(this::view).toList();
        return new RegistryPage(items, membershipRevision, nodes.size());
    }

    @Override
    public NodeStatus find(String nodeId) {
        StoredNode node = nodes.get(nodeId);
        return node == null ? null : view(node);
    }

    @Override
    public void requireSession(String nodeId, UUID sessionId) {
        validateId(nodeId, "nodeId");
        validateSession(sessionId);
        StoredNode node = nodes.get(nodeId);
        if (node == null) throw new ValidationException("NODE_NOT_FOUND", "Node is not registered", List.of());
        requireSession(node, sessionId);
    }

    /** Runs one task mutation while replacement registration for this node is excluded. */
    @Override
    public <T> T withSession(String nodeId, UUID sessionId,
                             java.util.function.Function<NodeStatus, T> action) {
        validateId(nodeId, "nodeId");
        validateSession(sessionId);
        Objects.requireNonNull(action, "action");
        AtomicBoolean found = new AtomicBoolean();
        AtomicReference<T> result = new AtomicReference<>();
        nodes.computeIfPresent(nodeId, (ignored, existing) -> {
            found.set(true);
            requireSession(existing, sessionId);
            result.set(action.apply(view(existing)));
            return existing;
        });
        if (!found.get()) throw new ValidationException("NODE_NOT_FOUND", "Node is not registered", List.of());
        return result.get();
    }

    private NodeStatus view(StoredNode node) {
        // The exact boundary is offline: lastSeen + timeout must be strictly after now.
        boolean online = clock.instant().isBefore(node.lastSeen.plus(offlineTimeout));
        return new NodeStatus(node.nodeId, node.sessionId, node.displayName, node.platform, node.pluginVersion,
                node.protocolVersion, node.advertisedCapabilities, node.acceptedCapabilities, node.detectedPlugins,
                node.backends, node.snapshotSequence, node.lastSeen, node.lastAuthenticatedUpdate, online);
    }

    private boolean reclaimOfflineTopology(Instant now, String excludedNodeId) {
        AtomicBoolean reclaimed = new AtomicBoolean();
        for (String candidate : List.copyOf(nodes.keySet())) {
            if (candidate.equals(excludedNodeId)) continue;
            nodes.computeIfPresent(candidate, (ignored, existing) -> {
                if (now.isBefore(existing.lastSeen.plus(offlineTimeout))) return existing;
                retainedDetectedPlugins -= existing.detectedPlugins.size();
                retainedBackends -= existing.backends.size();
                reclaimed.set(true);
                return null;
            });
        }
        return reclaimed.get();
    }

    private static void validate(NodeRegistration registration) {
        if (registration == null) {
            throw invalid("registration is required");
        }
        validateId(registration.nodeId(), "nodeId");
        validateSession(registration.sessionId());
        requireText(registration.displayName(), "displayName", 100);
        requireText(registration.pluginVersion(), "pluginVersion", 40);
        if (registration.platform() == null || !PLATFORMS.contains(registration.platform())) {
            throw invalid("platform must be BUNGEECORD, VELOCITY, or BUKKIT");
        }
        validateProtocol(registration.protocolVersion());
        validateCapabilities(registration.capabilities(), registration.requiredCapabilities());
        validateDetectedPlugins(registration.detectedPlugins());
    }

    private static void validateSnapshot(PresenceSnapshot snapshot) {
        if (snapshot == null) {
            throw invalid("presence snapshot is required");
        }
        validateSession(snapshot.sessionId());
        validateProtocol(snapshot.protocolVersion());
        if (snapshot.sequence() < 0) {
            throw invalid("sequence must be non-negative");
        }
        if (snapshot.backends().size() > MAX_BACKENDS) {
            throw invalid("backends must contain at most " + MAX_BACKENDS + " entries");
        }
        Set<String> ids = new HashSet<>();
        for (BackendServerIdentity backend : snapshot.backends()) {
            if (backend == null) {
                throw invalid("backend entries must not be null");
            }
            validateId(backend.backendId(), "backendId");
            requireText(backend.displayName(), "backend displayName", 100);
            if (backend.playerCount() < 0 || backend.playerCount() > 100000) {
                throw invalid("backend playerCount must be between 0 and 100000");
            }
            if (!ids.add(backend.backendId().toLowerCase(java.util.Locale.ROOT))) {
                throw invalid("backend IDs must be unique within a snapshot");
            }
        }
    }

    private static void requireSession(StoredNode existing, UUID sessionId) {
        if (!existing.sessionId.equals(sessionId)) {
            throw new ValidationException("SESSION_MISMATCH", "Node session does not match current registration",
                    List.of());
        }
    }

    private static void validateSession(UUID sessionId) {
        if (sessionId == null) {
            throw invalid("sessionId is required");
        }
    }

    private static void validateId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw invalid(field + " must match " + ID.pattern());
        }
    }

    private static void validateProtocol(int version) {
        if (version != Protocol.VERSION) {
            throw new ValidationException("UNSUPPORTED_PROTOCOL", "Unsupported protocol version",
                    List.of("supported=" + Protocol.VERSION));
        }
    }

    private static void validateCapabilities(Set<String> advertised, Set<String> required) {
        validateCapabilitySet(advertised);
        validateCapabilitySet(required);
        List<String> unavailable = new ArrayList<>(required);
        unavailable.removeAll(SUPPORTED_CAPABILITIES);
        if (!unavailable.isEmpty()) {
            throw new ValidationException("INCOMPATIBLE_CAPABILITIES", "Required capabilities are unavailable",
                    unavailable.stream().sorted().toList());
        }
    }

    private static void validateCapabilitySet(Set<String> capabilities) {
        if (capabilities == null || capabilities.size() > MAX_CAPABILITIES
                || capabilities.stream().anyMatch(value -> value == null || !CAPABILITY.matcher(value).matches())) {
            throw invalid("capabilities must contain at most 64 valid identifiers");
        }
    }

    private static void validateDetectedPlugins(Set<String> plugins) {
        if (plugins == null || plugins.size() > MAX_DETECTED_PLUGINS || plugins.stream().anyMatch(value ->
                value == null || value.isBlank() || value.length() > 100
                        || value.chars().anyMatch(Character::isISOControl))) {
            throw invalid("detectedPlugins must contain at most 128 names of 1 to 100 characters");
        }
    }

    private static Set<String> accepted(Set<String> advertised) {
        Set<String> result = new HashSet<>(advertised);
        result.retainAll(SUPPORTED_CAPABILITIES);
        return Set.copyOf(result);
    }

    private static void requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid(field + " must contain 1 to " + max + " characters");
        }
    }

    private static ValidationException invalid(String detail) {
        return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail));
    }

    private record StoredNode(String nodeId, UUID sessionId, String displayName, String platform,
                              String pluginVersion, int protocolVersion, Set<String> advertisedCapabilities,
                              Set<String> acceptedCapabilities, Set<String> detectedPlugins,
                              List<BackendServerIdentity> backends,
                              long snapshotSequence, Instant lastSeen, Instant lastAuthenticatedUpdate) {
        private StoredNode {
            advertisedCapabilities = Set.copyOf(advertisedCapabilities);
            acceptedCapabilities = Set.copyOf(acceptedCapabilities);
            detectedPlugins = Set.copyOf(detectedPlugins);
            backends = List.copyOf(backends);
        }
    }
}
