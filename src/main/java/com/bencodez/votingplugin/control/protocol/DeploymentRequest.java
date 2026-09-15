package com.bencodez.votingplugin.control.protocol;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Admin request for a single, explicitly targeted plugin deployment. */
public record DeploymentRequest(String artifactId, String sha256, long size, List<String> nodeIds) {
    public static final String CAPABILITY = "plugin.deploy.v1";
    public static final long MAX_ARTIFACT_BYTES = 64L * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public DeploymentRequest {
        if (artifactId == null || !SHA256.matcher(artifactId).matches()) {
            throw new IllegalArgumentException("artifactId must be a lowercase SHA-256 digest");
        }
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 digest");
        }
        if (!artifactId.equals(sha256)) {
            throw new IllegalArgumentException("artifactId must equal sha256");
        }
        if (size < 1 || size > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("size is outside the deployment limit");
        }
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > 100) {
            throw new IllegalArgumentException("nodeIds must contain between 1 and 100 nodes");
        }
        Set<String> unique = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (nodeId == null || !NODE_ID.matcher(nodeId).matches() || !unique.add(nodeId)) {
                throw new IllegalArgumentException("nodeIds must contain unique valid node IDs");
            }
        }
        nodeIds = List.copyOf(nodeIds);
    }
}
