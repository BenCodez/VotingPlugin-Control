package com.bencodez.votingplugin.control.domain;

import java.util.List;

@SuppressWarnings("serial")
public final class ValidationException extends RuntimeException {
    private final String code;
    private final List<String> details;
    public ValidationException(String code, String message, List<String> details) {
        super(message); this.code = code; this.details = List.copyOf(details);
    }
    public String code() { return code; }
    public List<String> details() { return details; }
}
