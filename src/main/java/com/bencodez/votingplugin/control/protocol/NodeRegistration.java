package com.bencodez.votingplugin.control.protocol;

import java.util.Set;
import java.util.UUID;

/** Immutable proxy registration. Retries for the same node/session are idempotent. */
public record NodeRegistration(String nodeId, UUID sessionId, String displayName, String platform,
                               String pluginVersion, int protocolVersion, Set<String> capabilities,
                               Set<String> requiredCapabilities, Set<String> detectedPlugins) {
    public NodeRegistration {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        detectedPlugins = detectedPlugins == null ? Set.of() : Set.copyOf(detectedPlugins);
    }

    public NodeRegistration(String nodeId, UUID sessionId, String displayName, String platform,
                            String pluginVersion, int protocolVersion, Set<String> capabilities,
                            Set<String> requiredCapabilities) {
        this(nodeId, sessionId, displayName, platform, pluginVersion, protocolVersion, capabilities,
                requiredCapabilities, Set.of());
    }
}
