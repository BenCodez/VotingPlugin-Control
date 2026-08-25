package com.bencodez.votingplugin.control.protocol;

import java.util.Set;
import java.util.UUID;

/** Immutable proxy registration. Retries for the same node/session are idempotent. */
public record NodeRegistration(String nodeId, UUID sessionId, String displayName, String platform,
                               String pluginVersion, int protocolVersion, Set<String> capabilities,
                               Set<String> requiredCapabilities) {
    public NodeRegistration {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
    }
}
