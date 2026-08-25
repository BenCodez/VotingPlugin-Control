package com.bencodez.votingplugin.control.protocol;

import java.util.List;
import java.util.UUID;

public record ConfigurationTaskResult(UUID sessionId, boolean success, String code, String message, String revision,
                                      ProxyRoutingConfiguration configuration, List<String> changes,
                                      boolean reloaded, boolean rolledBack) {
    public ConfigurationTaskResult {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
