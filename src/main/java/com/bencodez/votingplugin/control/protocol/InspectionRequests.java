package com.bencodez.votingplugin.control.protocol;

import java.util.UUID;

public final class InspectionRequests {
    private InspectionRequests() { }

    public record Start(String nodeId, InspectionQuery query) { }

    public record Claim(UUID sessionId) { }
}
