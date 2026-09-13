package com.bencodez.votingplugin.control.protocol;

import java.util.UUID;

/** Bounded result reported by a node for one deployment attempt. */
public record DeploymentTaskResult(UUID sessionId, boolean success, String code, String message, UUID attemptId) {
}
