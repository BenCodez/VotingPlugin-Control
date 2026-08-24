package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class InMemoryNodeRegistry implements NodeRegistry {
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern CAPABILITY = Pattern.compile("[a-z][a-z0-9.-]{0,63}");
    private static final Set<String> PLATFORMS = Set.of("BUNGEECORD", "VELOCITY", "BUKKIT", "OTHER");
    private final Map<String, StoredNode> nodes = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration offlineTimeout;

    public InMemoryNodeRegistry(Clock clock, Duration offlineTimeout) {
        this.clock = Objects.requireNonNull(clock); this.offlineTimeout = Objects.requireNonNull(offlineTimeout);
        if (offlineTimeout.isNegative() || offlineTimeout.isZero()) throw new IllegalArgumentException("offlineTimeout must be positive");
    }

    @Override public RegistrationResult register(NodeRegistration r) {
        validate(r);
        Instant now = clock.instant();
        StoredNode stored = new StoredNode(r.nodeId(), r.displayName(), r.platform(), r.pluginVersion(),
                r.protocolVersion(), r.capabilities(), now);
        AtomicBoolean created = new AtomicBoolean();
        nodes.compute(r.nodeId(), (ignored, existing) -> {
            created.set(existing == null);
            return stored;
        });
        return new RegistrationResult(view(stored), created.get());
    }

    @Override public NodeStatus heartbeat(String nodeId, Heartbeat heartbeat) {
        validateNodeId(nodeId);
        if (heartbeat == null) throw invalid("heartbeat is required");
        validateProtocol(heartbeat.protocolVersion());
        validateCapabilities(heartbeat.capabilities());
        StoredNode updated = nodes.computeIfPresent(nodeId, (ignored, old) -> new StoredNode(old.nodeId,
                old.displayName, old.platform, old.pluginVersion, old.protocolVersion,
                heartbeat.capabilities() == null ? old.capabilities : heartbeat.capabilities(), clock.instant()));
        if (updated == null) throw new ValidationException("NODE_NOT_FOUND", "Node is not registered", List.of(nodeId));
        return view(updated);
    }

    @Override public List<NodeStatus> list(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw invalid("offset must be >= 0 and limit must be between 1 and 100");
        return nodes.values().stream().sorted(Comparator.comparing(n -> n.nodeId)).skip(offset).limit(limit).map(this::view).toList();
    }

    private NodeStatus view(StoredNode n) {
        boolean online = clock.instant().isBefore(n.lastSeen.plus(offlineTimeout));
        return new NodeStatus(n.nodeId, n.displayName, n.platform, n.pluginVersion, n.protocolVersion, n.capabilities, n.lastSeen, online);
    }
    private static void validate(NodeRegistration r) {
        if (r == null) throw invalid("registration is required");
        validateNodeId(r.nodeId());
        requireText(r.displayName(), "displayName", 100);
        requireText(r.pluginVersion(), "pluginVersion", 40);
        if (r.platform() == null || !PLATFORMS.contains(r.platform())) throw invalid("platform must be one of " + PLATFORMS);
        validateProtocol(r.protocolVersion()); validateCapabilities(r.capabilities());
    }
    private static void validateNodeId(String id) { if (id == null || !NODE_ID.matcher(id).matches()) throw invalid("nodeId must match " + NODE_ID.pattern()); }
    private static void validateProtocol(int version) { if (version != Protocol.VERSION) throw new ValidationException("UNSUPPORTED_PROTOCOL", "Unsupported protocol version", List.of("supported=" + Protocol.VERSION)); }
    private static void validateCapabilities(Set<String> capabilities) {
        if (capabilities == null) return;
        if (capabilities.size() > 64 || capabilities.stream().anyMatch(c -> c == null || !CAPABILITY.matcher(c).matches())) throw invalid("capabilities must contain at most 64 valid identifiers");
    }
    private static void requireText(String value, String field, int max) { if (value == null || value.isBlank() || value.length() > max) throw invalid(field + " must contain 1 to " + max + " characters"); }
    private static ValidationException invalid(String detail) { return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail)); }
    private record StoredNode(String nodeId, String displayName, String platform, String pluginVersion,
                              int protocolVersion, Set<String> capabilities, Instant lastSeen) { StoredNode { capabilities = Set.copyOf(capabilities); } }
}
