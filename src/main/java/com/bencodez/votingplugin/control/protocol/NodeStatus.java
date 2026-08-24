package com.bencodez.votingplugin.control.protocol;

import java.time.Instant;
import java.util.Set;

public record NodeStatus(String nodeId, String displayName, String platform, String pluginVersion,
                         int protocolVersion, Set<String> capabilities, Instant lastSeen, boolean online) {
    public NodeStatus {
        capabilities = Set.copyOf(capabilities);
    }
}
