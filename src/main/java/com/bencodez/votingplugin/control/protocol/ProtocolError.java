package com.bencodez.votingplugin.control.protocol;

import java.util.List;

public record ProtocolError(String code, String message, List<String> details) {
    public ProtocolError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
