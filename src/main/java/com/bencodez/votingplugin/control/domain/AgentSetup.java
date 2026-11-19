package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

final class AgentSetup {
    private static final Map<String, String> DEFAULTS = Map.of(
            "enabled", "true", "priority", "5", "hidden", "false", "voteDelay", "24h", "material", "DIAMOND");
    private static final Map<String, String> PATHS = Map.of(
            "enabled", "Enabled", "displayName", "Name", "priority", "Priority", "hidden", "Hidden",
            "serviceSite", "ServiceSite", "voteUrl", "VoteURL", "voteDelay", "VoteDelay",
            "material", "DisplayItem.Material", "displayItem.amount", "DisplayItem.Amount");

    private AgentSetup() { }

    static ManagedConfiguration proposal(Map<String, String> current, ManagedConfiguration requested) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        String name = requested.options().get("name");
        values.put("displayName", name);
        if (current == null || !name.equals(current.get("name"))
                || !List.of("true", "false").contains(current.getOrDefault("exists", ""))) {
            throw new IllegalArgumentException("Read did not return the requested site");
        }
        if ("true".equals(current.get("exists"))) {
            for (String field : List.of("enabled", "priority", "hidden", "voteDelay", "material", "displayName")) {
                String value = current.get(field);
                if (value != null) values.put(field, value);
            }
        }
        values.putAll(requested.options());
        if (!safeUrl(values.get("voteUrl"))) throw new IllegalArgumentException("Use a public-facing HTTP(S) vote URL without credentials, query or fragment");
        return new ManagedConfiguration("quick-setup", null, List.of(), null, null, "vote-site", values);
    }

    static Map<String, String> readSite(String name, ManagedConfiguration document) {
        if (document == null || document.content() == null) throw new IllegalArgumentException("Missing VoteSites read");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", name);
        var sites = VoteSitesDocument.inventory(document.content()).sites();
        var site = sites.stream().filter(item -> name.equalsIgnoreCase(item.key())).findFirst().orElse(null);
        values.put("exists", Boolean.toString(site != null));
        if (site == null) return Map.copyOf(values);
        if (!site.editable() || !name.equals(site.key())) throw new IllegalArgumentException("Ambiguous site name");
        PATHS.forEach((key, path) -> {
            var field = site.fields().get(path);
            if (field.status() == VoteSitesDocument.Status.UNSUPPORTED) {
                throw new IllegalArgumentException("Site contains unsupported field");
            }
            if (field.status() == VoteSitesDocument.Status.AVAILABLE) {
                String value = field.value().toString();
                if ("voteUrl".equals(key) && !safeUrl(value)) {
                    throw new IllegalArgumentException("Existing site URL cannot be reviewed");
                }
                values.put(key, value);
            }
        });
        return Map.copyOf(values);
    }

    static boolean safeUrl(String value) {
        if (value == null || value.length() > 500) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static final String SITE_POLICY = "auto-create-vote-sites";

    static ManagedConfiguration sitePolicy(Boolean enabled) {
        return new ManagedConfiguration(ManagedConfiguration.QUICK_SETUP, null, List.of(), null, null, SITE_POLICY,
                enabled == null ? Map.of() : Map.of("enabled", enabled.toString()));
    }

    static Map<String, String> sitePolicyValues(ManagedConfiguration configuration) {
        if (configuration == null || !SITE_POLICY.equals(configuration.preset())
                || !Set.of(Map.of("enabled", "true"), Map.of("enabled", "false")).contains(configuration.options())) {
            throw new IllegalArgumentException("Expected the explicit vote-site creation policy");
        }
        return configuration.options();
    }

    static Map<String, String> routingValues(ManagedConfiguration configuration) {
        if (configuration == null || !ManagedConfiguration.PROXY_ROUTING.equals(configuration.domain())
                || configuration.blockedServers().stream().anyMatch(id -> !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
            throw new IllegalArgumentException("Routing must contain exact server identifiers");
        }
        return Map.of("sendVotesToAllServers", configuration.sendVotesToAllServers().toString(),
                "blockedServers", configuration.blockedServers().toString());
    }

    static List<String> routingChanges(Map<String, String> before, Map<String, String> after) {
        return List.of("sendVotesToAllServers", "blockedServers").stream()
                .filter(key -> !before.get(key).equals(after.get(key)))
                .map(key -> key + ": " + before.get(key) + " -> " + after.get(key)).toList();
    }

    static boolean reviewableChanges(ManagedConfiguration configuration, Map<String, String> before,
                                     List<String> changes) {
        String root = "changed VoteSites." + configuration.options().get("name") + ".";
        var expected = new HashSet<String>();
        PATHS.forEach((key, path) -> {
            String next = "displayItem.amount".equals(key) ? "1" : configuration.options().get(key);
            if (!next.equals(before.get(key))) expected.add(root + path);
        });
        return changes.size() == expected.size() && expected.equals(new HashSet<>(changes));
    }
}
