package com.bencodez.votingplugin.control.protocol;

import java.util.List;
import java.util.UUID;

/** Full replacement snapshot for one proxy session. */
public record PresenceSnapshot(UUID sessionId, int protocolVersion, long sequence,
                               List<BackendServerIdentity> backends) {
    public PresenceSnapshot {
        backends = backends == null ? List.of() : List.copyOf(backends);
    }
}
