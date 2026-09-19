package com.bencodez.votingplugin.control.protocol;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A bounded, typed, read-only question that a VotingPlugin node can answer. */
public record InspectionQuery(String kind, Map<String, String> filters) {
    public static final String CAPABILITY = "data.inspect.v1";
    public static final String REWARD_FILE_CAPABILITY = ManagedConfiguration.REWARD_FILE_CAPABILITY;
    public static final int MAX_REWARD_PROPOSAL = 64 * 1024;
    public static final Set<String> KINDS = Set.of("overview", "player", "vote-site-health", "vote-log-summary",
            "vote-log-search", "vote-trace", "vote-site-resolution", "reward-simulation", "diagnostics",
            "reward-file-inventory");

    public InspectionQuery {
        filters = filters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(filters));
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("inspection kind is unsupported");
        if ("reward-file-inventory".equals(kind) && !filters.isEmpty())
            throw new IllegalArgumentException("reward file inventory takes no filters");
        if (filters.size() > 12 || filters.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || !entry.getKey().matches("[a-z][A-Za-z0-9]{0,39}") || entry.getValue() == null
                || invalidValue(kind, entry.getKey(), entry.getValue()))) {
            throw new IllegalArgumentException("inspection filters are invalid");
        }
    }

    public boolean requiresRewardFiles() { return "reward-file-inventory".equals(kind); }

    private static boolean invalidValue(String kind, String key, String value) {
        int maximum = "reward-simulation".equals(kind) && "proposal".equals(key) ? MAX_REWARD_PROPOSAL : 500;
        return value.getBytes(StandardCharsets.UTF_8).length > maximum || value.indexOf('\0') >= 0;
    }
}
