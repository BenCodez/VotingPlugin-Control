package com.bencodez.votingplugin.control.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** A node-produced inspection result. Data is a bounded JSON document, never arbitrary database output. */
public record InspectionTaskResult(UUID sessionId, boolean success, String code, String message, JsonNode data,
                                   UUID attemptId) { }
