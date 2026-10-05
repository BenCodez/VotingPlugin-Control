package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class NetworkHealthEvidenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void acceptsMinimalRoleAndKnownTypedFields() {
        ObjectNode value = JSON.createObjectNode().put("schemaVersion", 1).put("role", "BACKEND")
                .put("serverName", "backend-a").put("triggerVotifierEvent", true);
        value.putArray("detectedPlugins").add("VotifierPlus");
        assertTrue(NetworkHealthEvidence.valid(value));
    }

    @Test
    void rejectsUnknownFieldsSecretsWrongTypesAndEnums() {
        ObjectNode unknown = base().put("password", "secret");
        assertFalse(NetworkHealthEvidence.valid(unknown));
        assertFalse(NetworkHealthEvidence.valid(base().put("onlineMode", "true")));
        assertFalse(NetworkHealthEvidence.valid(base().put("votifierForwardingEnabled", "true")));
        assertTrue(NetworkHealthEvidence.valid(base().put("votifierForwardingEnabled", true)));
        assertFalse(NetworkHealthEvidence.valid(base().put("configuredMethod", "TCP")));
        assertFalse(NetworkHealthEvidence.valid(base().put("sharedKeyFingerprint", "secret-key")));
    }

    @Test
    void enforcesArrayStringAndFingerprintBounds() {
        ObjectNode tooLong = base();
        tooLong.putArray("detectedPlugins").add("x".repeat(81));
        assertFalse(NetworkHealthEvidence.valid(tooLong));

        ObjectNode duplicate = base();
        duplicate.putArray("blockedServers").add("backend-a").add("backend-a");
        assertFalse(NetworkHealthEvidence.valid(duplicate));

        ObjectNode tooMany = base();
        for (int i = 0; i < 101; i++) tooMany.withArray("proxyServers").add("proxy-" + i);
        assertFalse(NetworkHealthEvidence.valid(tooMany));

        assertFalse(NetworkHealthEvidence.valid(base().put("storageFingerprint", "z".repeat(64))));
        assertTrue(NetworkHealthEvidence.valid(base().put("storageFingerprint", "a".repeat(64))
                .put("timeHourOffset", 1_000_000_000)));
        assertFalse(NetworkHealthEvidence.valid(base().put("timeHourOffset", 1_000_000_001)));
    }

    @Test
    void validatesVoteSiteObjectsAndBounds() {
        ObjectNode valid = base();
        valid.putArray("voteSites").addObject().put("name", "Site A").put("serviceSite", "service")
                .put("voteUrlState", "BLANK").put("enabled", true).put("delayValid", true)
                .put("delayHours", 24).put("autoCreated", false).put("hasRewards", true);
        assertTrue(NetworkHealthEvidence.valid(valid));

        ObjectNode badDelay = base();
        badDelay.putArray("voteSites").addObject().put("name", "Site A").put("delayHours", -1);
        assertFalse(NetworkHealthEvidence.valid(badDelay));
        ObjectNode duplicateNames = base();
        duplicateNames.putArray("voteSites").addObject().put("name", "Site A");
        duplicateNames.withArray("voteSites").addObject().put("name", "Site A");
        assertFalse(NetworkHealthEvidence.valid(duplicateNames));
    }

    private static ObjectNode base() {
        return JSON.createObjectNode().put("schemaVersion", 1).put("role", "BACKEND");
    }
}
