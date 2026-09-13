package com.bencodez.votingplugin.control.protocol;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Admin request for a single, explicitly targeted plugin deployment. */
public record DeploymentRequest(String artifactId, String sha256, long size, List<String> nodeIds) {
    public static final String CAPABILITY = "plugin.deploy.v1";
    public static final long MAX_ARTIFACT_BYTES = 64L * 1024 * 1024;
    private static final Pattern ARTIFACT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public DeploymentRequest {
        if (artifactId == null || !ARTIFACT_ID.matcher(artifactId).matches()) {
            throw new IllegalArgumentException("artifactId is invalid");
        }
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("sha256 must be a 64-character hexadecimal digest");
        }
        sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
        if (size < 1 || size > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("size is outside the deployment limit");
        }
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > 100) {
            throw new IllegalArgumentException("nodeIds must contain between 1 and 100 nodes");
        }
        nodeIds = List.copyOf(nodeIds);
        Set<String> unique = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (nodeId == null || !NODE_ID.matcher(nodeId).matches() || !unique.add(nodeId)) {
                throw new IllegalArgumentException("nodeIds must contain unique valid node IDs");
            }
        }
    }
}
