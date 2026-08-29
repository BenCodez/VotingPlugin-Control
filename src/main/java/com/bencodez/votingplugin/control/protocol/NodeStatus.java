package com.bencodez.votingplugin.control.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NodeStatus(String nodeId, UUID sessionId, String displayName, String platform, String pluginVersion,
                         int protocolVersion, Set<String> advertisedCapabilities, Set<String> acceptedCapabilities,
                         Set<String> detectedPlugins, List<BackendServerIdentity> backends, long snapshotSequence,
                         Instant lastSeen, Instant lastAuthenticatedUpdate, boolean online) {
    public NodeStatus {
        advertisedCapabilities = Set.copyOf(advertisedCapabilities);
        acceptedCapabilities = Set.copyOf(acceptedCapabilities);
        detectedPlugins = Set.copyOf(detectedPlugins);
        backends = List.copyOf(backends);
    }

    public NodeStatus(String nodeId, UUID sessionId, String displayName, String platform, String pluginVersion,
                      int protocolVersion, Set<String> advertisedCapabilities, Set<String> acceptedCapabilities,
                      List<BackendServerIdentity> backends, long snapshotSequence, Instant lastSeen,
                      Instant lastAuthenticatedUpdate, boolean online) {
        this(nodeId, sessionId, displayName, platform, pluginVersion, protocolVersion, advertisedCapabilities,
                acceptedCapabilities, Set.of(), backends, snapshotSequence, lastSeen, lastAuthenticatedUpdate, online);
    }
}
