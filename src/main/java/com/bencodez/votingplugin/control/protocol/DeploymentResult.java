package com.bencodez.votingplugin.control.protocol;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public, metadata-only view of a deployment and its per-node outcomes. */
public record DeploymentResult(UUID deploymentId, String artifactId, String sha256, long size, String state,
                               Instant createdAt, List<NodeResult> nodes) {
    public DeploymentResult {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public record NodeResult(String nodeId, UUID sessionId, String state, DeploymentTaskResult result,
                             Instant leasedAt, UUID attemptId) {
    }
}
