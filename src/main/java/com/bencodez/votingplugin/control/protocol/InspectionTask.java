package com.bencodez.votingplugin.control.protocol;

import java.util.UUID;

public record InspectionTask(UUID inspectionId, InspectionQuery query, UUID attemptId) { }
