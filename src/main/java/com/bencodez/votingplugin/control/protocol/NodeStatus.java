package com.bencodez.votingplugin.control.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NodeStatus(String nodeId, UUID sessionId, String displayName, String platform, String pluginVersion,
                         int protocolVersion, Set<String> advertisedCapabilities, Set<String> acceptedCapabilities,
                         List<BackendServerIdentity> backends, long snapshotSequence,
                         Instant lastSeen, Instant lastAuthenticatedUpdate, boolean online) {
    public NodeStatus {
        advertisedCapabilities = Set.copyOf(advertisedCapabilities);
        acceptedCapabilities = Set.copyOf(acceptedCapabilities);
        backends = List.copyOf(backends);
    }
}
