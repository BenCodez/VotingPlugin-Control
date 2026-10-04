package com.bencodez.votingplugin.control.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Strict allow-list for untrusted read-only evidence. Missing is not false. */
public final class NetworkHealthEvidence {
    private NetworkHealthEvidence() { }
    public static final String CAPABILITY = "data.network-health.v1";
    private static final Set<String> BOOLEANS = Set.of("proxyMode", "broadcastServersApplicable", "offlineForwardServersApplicable", "configurationHealthy", "onlineMode",
            "resetMilestonesMonthly", "monthDateTotals", "automaticTimeChanges", "encryption", "transportInitialized",
            "restartRequired", "triggerVotifierEvent", "votifierProviderPresent", "votifierListenerInitialized",
            "votifierForwardingKnown", "bungeeManageTotals", "dedicatedVotingProxy", "httpPublicEndpointConfigured",
            "httpEnrolled", "httpIdentityValid", "httpConnectionCodePresent", "redisSsl", "databaseInitialized",
            "jdbcDriverAvailable", "voteLoggingEnabled", "voteLogReadable", "voteCacheMysql", "nonVotedCacheMysql",
            "voteCacheMainMysql", "nonVotedCacheMainMysql", "voteCacheDatabaseInitialized", "nonVotedCacheDatabaseInitialized",
            "globalDataEnabled", "globalDataUseMainMysql", "processRewards", "perServerRewards", "perServerPoints",
            "giveExtraAllSitesRewards", "extraAllSitesCheck", "autoCreateVoteSites", "allowUnjoined", "waitForUserOnline",
            "votePartyEnabled", "votePartyEffectiveReward", "multiProxySupport", "primaryServer",
            "multiProxyOneGlobalReward", "sendVotesToAllServers", "topologyComplete", "voteSitesComplete", "rewardsComplete");
    private static final Set<String> STRINGS = Set.of("serverName", "pluginMessageChannel", "bedrockPlayerPrefix",
            "transportNamespace", "mqttClientId", "globalDataPrefix", "databasePrefix", "proxyServerName", "databaseType");
    private static final Set<String> FINGERPRINTS = Set.of("sharedKeyFingerprint", "multiProxyKeyFingerprint", "storageFingerprint");
    private static final Set<String> INTEGERS = Set.of("timeHourOffset", "maxVotesPerDay", "votePartyVotesRequired",
            "carrierPlayers", "queuedVotes", "parkedVotes", "oldestPendingSeconds", "authenticationFailures");
    private static final Set<String> ARRAYS = Set.of("detectedPlugins", "backendNames", "forwardingDestinations", "blockedServers",
            "whitelistedServers", "broadcastServers", "offlineForwardServers", "votePartyServers", "proxyServers", "socketProxyServers",
            "waitUntilVoteDelayServices", "detectedServices", "loggedServices", "missingRewardFiles", "invalidRewardFiles",
            "missingRewardDependencies", "invalidConfigurationFields");
    private static final Set<String> METHODS = Set.of("PLUGINMESSAGING", "HTTP", "REDIS", "MQTT", "SOCKETS", "MYSQL", "INVALID");
    private static final Map<String, Set<String>> ENUMS = Map.of("role", Set.of("BACKEND", "PROXY"),
            "configuredMethod", METHODS, "activeMethod", METHODS, "multiProxyMethod", Set.of("SOCKET", "SOCKETS", "REDIS", "INVALID"),
            "sharedAuthentication", Set.of("REQUIRED", "COMPATIBILITY", "DISABLED", "INVALID"),
            "transportProbeState", Set.of("SUCCESS", "WAITING", "FAILED", "UNKNOWN"));

    public static boolean valid(JsonNode data) {
        if (data == null || !data.isObject() || !data.path("schemaVersion").isIntegralNumber()
                || data.path("schemaVersion").intValue() != 1 || !ENUMS.get("role").contains(data.path("role").asText())) return false;
        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
        while (fields.hasNext()) {
            var field = fields.next(); String name = field.getKey(); JsonNode v = field.getValue();
            if ("schemaVersion".equals(name)) { if (!v.canConvertToInt()) return false; }
            else if (BOOLEANS.contains(name)) { if (!v.isBoolean()) return false; }
            else if (STRINGS.contains(name)) { if (!text(v, 160)) return false; }
            else if (FINGERPRINTS.contains(name)) { if (!v.isTextual() || !v.asText().matches("[a-f0-9]{64}")) return false; }
            else if (INTEGERS.contains(name)) { if (!v.isIntegralNumber() || !v.canConvertToInt()
                    || Math.abs(v.longValue()) > 1_000_000_000L
                    || !"timeHourOffset".equals(name) && v.longValue() < 0) return false; }
            else if (ENUMS.containsKey(name)) { if (!v.isTextual() || !ENUMS.get(name).contains(v.asText())) return false; }
            else if (ARRAYS.contains(name)) { if (!strings(v)) return false; }
            else if ("voteSites".equals(name)) { if (!sites(v)) return false; }
            else return false;
        }
        return true;
    }
    private static boolean text(JsonNode v, int max) {
        return v.isTextual() && v.asText().length() <= max && v.asText().codePoints().noneMatch(Character::isISOControl);
    }
    private static boolean strings(JsonNode v) {
        if (!v.isArray() || v.size() > 100) return false;
        Set<String> seen = new HashSet<>();
        for (JsonNode value : v) if (!text(value, 80) || !seen.add(value.asText())) return false;
        return true;
    }
    private static boolean sites(JsonNode v) {
        if (!v.isArray() || v.size() > 100) return false;
        Set<String> names = new HashSet<>();
        for (JsonNode site : v) {
            if (!site.isObject() || !text(site.path("name"), 80) || !names.add(site.path("name").asText())) return false;
            var fields = site.fields();
            while (fields.hasNext()) {
                var f = fields.next(); JsonNode value = f.getValue();
                switch (f.getKey()) {
                    case "name" -> { }
                    case "serviceSite" -> { if (!text(value, 160)) return false; }
                    case "enabled", "delayValid", "autoCreated", "hasRewards" -> { if (!value.isBoolean()) return false; }
                    case "delayHours" -> { if (!value.isIntegralNumber() || !value.canConvertToInt()
                            || value.intValue() < 0 || value.intValue() > 1_000_000) return false; }
                    case "voteUrlState" -> { if (!value.isTextual() || !Set.of("VALID", "BLANK", "EXAMPLE", "INVALID").contains(value.asText())) return false; }
                    default -> { return false; }
                }
            }
        }
        return true;
    }
}
