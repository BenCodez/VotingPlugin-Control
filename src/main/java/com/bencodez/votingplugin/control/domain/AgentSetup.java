package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

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
