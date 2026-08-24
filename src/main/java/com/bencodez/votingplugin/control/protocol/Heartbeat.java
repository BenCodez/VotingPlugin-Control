package com.bencodez.votingplugin.control.protocol;

import java.util.Set;

public record Heartbeat(int protocolVersion, Set<String> capabilities) {
    public Heartbeat {
        capabilities = capabilities == null ? null : Set.copyOf(capabilities);
    }
}
