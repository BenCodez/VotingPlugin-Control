package com.bencodez.votingplugin.control.protocol;

import java.util.List;
import java.util.UUID;

public final class ConfigurationRequests {
    private ConfigurationRequests() { }

    public record Read(List<String> nodeIds, ManagedConfiguration configuration) {
        public Read { nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds); }
        public Read(List<String> nodeIds) { this(nodeIds, null); }
    }

    public record Preview(List<String> nodeIds, ManagedConfiguration configuration) {
        public Preview { nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds); }
        public Preview(List<String> nodeIds, ProxyRoutingConfiguration configuration) {
            this(nodeIds, configuration == null ? null : ManagedConfiguration.proxy(configuration));
        }
    }

    public record Apply(UUID previewOperationId, String approvalToken) { }

    public record Claim(UUID sessionId) { }
}
