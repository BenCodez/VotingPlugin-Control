package com.bencodez.votingplugin.control.protocol;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The deliberately small, non-secret configuration domain exposed by Control. */
public record ProxyRoutingConfiguration(boolean sendVotesToAllServers, List<String> blockedServers) {
    public ProxyRoutingConfiguration {
        blockedServers = blockedServers == null ? List.of() : blockedServers.stream().map(String::trim).toList();
        if (blockedServers.size() > 256) {
            throw new IllegalArgumentException("blockedServers must contain at most 256 entries");
        }
        Set<String> unique = new HashSet<>();
        for (String server : blockedServers) {
            if (server.isBlank() || server.length() > 100 || !unique.add(server.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("blockedServers must contain unique names of 1 to 100 characters");
            }
        }
        blockedServers = List.copyOf(blockedServers);
    }
}
