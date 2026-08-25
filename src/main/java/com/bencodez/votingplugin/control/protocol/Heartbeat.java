package com.bencodez.votingplugin.control.protocol;

import java.util.Set;
import java.util.UUID;

/** A heartbeat replaces the node's previously advertised capability set. */
public record Heartbeat(UUID sessionId, int protocolVersion, Set<String> capabilities,
                        Set<String> requiredCapabilities) {
    public Heartbeat {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
    }
}
