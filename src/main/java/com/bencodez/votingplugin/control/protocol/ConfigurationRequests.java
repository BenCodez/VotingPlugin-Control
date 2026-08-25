package com.bencodez.votingplugin.control.protocol;

import java.util.List;
import java.util.UUID;

public final class ConfigurationRequests {
    private ConfigurationRequests() { }

    public record Read(List<String> nodeIds) {
        public Read { nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds); }
    }

    public record Preview(List<String> nodeIds, ProxyRoutingConfiguration configuration) {
        public Preview { nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds); }
    }

    public record Apply(UUID previewOperationId, String approvalToken) { }

    public record Claim(UUID sessionId) { }
}
