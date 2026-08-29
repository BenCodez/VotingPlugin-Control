package com.bencodez.votingplugin.control.protocol;

import java.util.List;
import java.util.UUID;

public record ConfigurationTaskResult(UUID sessionId, boolean success, String code, String message, String revision,
                                      ManagedConfiguration configuration, List<String> changes,
                                      boolean reloaded, boolean rolledBack, UUID attemptId) {
    public ConfigurationTaskResult {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public ConfigurationTaskResult(UUID sessionId, boolean success, String code, String message, String revision,
                                   ProxyRoutingConfiguration configuration, List<String> changes,
                                   boolean reloaded, boolean rolledBack, UUID attemptId) {
        this(sessionId, success, code, message, revision,
                configuration == null ? null : ManagedConfiguration.proxy(configuration), changes, reloaded, rolledBack,
                attemptId);
    }
}
