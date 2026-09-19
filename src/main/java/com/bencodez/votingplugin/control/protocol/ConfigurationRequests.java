package com.bencodez.votingplugin.control.protocol;

import java.util.List;
import java.util.Map;
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

    public record SettingsState(UUID readOperationId, String nodeId) { }

    /** Raw value types are checked explicitly; JSON strings/numbers are not boolean edits. */
    public record SettingsPreview(UUID readOperationId, String nodeId, Map<String, Object> overrides) { }

    public record SettingsDiscard(UUID previewOperationId, String approvalToken) { }

    public record VoteSitesState(UUID readOperationId, String nodeId) { }

    /** A bounded typed site mutation; fields are revalidated against the operation kind. */
    public record VoteSitesPreview(UUID readOperationId, String nodeId, String action, String siteKey,
            Map<String, Object> fields) { }

    public record VoteSitesDiscard(UUID previewOperationId, String approvalToken) { }

    public record RewardsState(UUID readOperationId, String nodeId, String fileName) { }

    public record RewardsPreview(UUID readOperationId, String nodeId, String fileName, String rewardPath,
            String action, String field, Object value) { }

    public record RewardsDiscard(UUID previewOperationId, String approvalToken) { }

    public record Claim(UUID sessionId) { }
}
