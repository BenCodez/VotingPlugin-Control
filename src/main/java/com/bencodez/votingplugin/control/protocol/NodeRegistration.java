package com.bencodez.votingplugin.control.protocol;

import java.util.Set;

public record NodeRegistration(String nodeId, String displayName, String platform,
                               String pluginVersion, int protocolVersion, Set<String> capabilities) {
    public NodeRegistration {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
