package com.bencodez.votingplugin.control.protocol;

import java.util.UUID;

/** Work item claimed by one currently registered node. */
public record DeploymentTask(UUID deploymentId, String artifactId, String sha256, long size, UUID attemptId) {
    public DeploymentTask {
        if (deploymentId == null || attemptId == null || artifactId == null || sha256 == null) {
            throw new IllegalArgumentException("deployment task metadata is required");
        }
    }
}
