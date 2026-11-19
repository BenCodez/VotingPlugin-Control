package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagedFieldsTest {
    @Test void editsOnlySelectedNestedScalarsAndListsAndPreservesAdjacentComments() {
        String source = """
                # preserve this note
                ProxyBroadcast:
                  Enabled: true # preserve this note too
                  OfflineForward:
                    Servers:
                    - first
                    - second
                Unrelated: unchanged
                """;
        var patch = ManagedFields.patch("bungeeconfig.yml", source, Map.of(
                "ProxyBroadcast.Enabled", false, "ProxyBroadcast.OfflineForward.Servers", List.of("third")), true);
        assertEquals(source.replace("Enabled: true", "Enabled: false")
                .replace("    - first\n    - second", "      ['third']"), patch.content());
        assertEquals(Set.of("changed ProxyBroadcast.Enabled", "changed ProxyBroadcast.OfflineForward.Servers"), patch.changes());
        assertEquals("['first', 'second']", patch.before().get("ProxyBroadcast.OfflineForward.Servers"));
        assertEquals("['third']", patch.after().get("ProxyBroadcast.OfflineForward.Servers"));
    }

    @Test void browserOnlyCommandAndPoolFieldsNeverEnterAgentCatalogOrRead() {
        String commands = "VoteParty:\n  Rewards:\n    Commands: ['sample-action']\n";
        assertTrue(ManagedFields.read("SpecialRewards.yml", commands, true).isEmpty());
        assertEquals(List.of("sample-action"), ManagedFields.read("SpecialRewards.yml", commands, false).get(0).value());
        assertFalse(ManagedFields.agentPaths().contains("Database.MaxConnections"));
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.patch("SpecialRewards.yml", commands,
                Map.of("VoteParty.Rewards.Commands", List.of("sample-next")), true));
        assertEquals(commands.replace("sample-action", "sample-next"), ManagedFields.patch("SpecialRewards.yml",
                commands, Map.of("VoteParty.Rewards.Commands", List.of("sample-next")), false).content());
        String database = "Database:\n  MaxConnections: 4\n  Password: %s%n"
                .formatted("__VOTINGPLUGIN_CONTROL_REDACTED__");
        assertEquals(database.replace("MaxConnections: 4", "MaxConnections: 8"), ManagedFields.patch(
                "Config.yml", database, Map.of("Database.MaxConnections", 8), false).content());
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.patch("Config.yml",
                database, Map.of("Database.Password", "fixture"), false));
    }

    @Test void rejectsUnknownFilesPathsAmbiguousKeysAndNonreviewableLists() {
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.catalog("ServerData.yml", false));
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.patch("Config.yml", "PointsOnVote: 1\n",
                Map.of("PointsOnVote", -1), true));
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.patch("Config.yml",
                "OfflineVotesLimit: {Amount: 2, amount: 3}\n", Map.of("OfflineVotesLimit.Amount", 5), true));
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.patch("Config.yml",
                "DefaultRewardWorlds:\n- world\n# Keep this note\n- world2\n", Map.of("DefaultRewardWorlds", List.of()), true));
        assertThrows(IllegalArgumentException.class, () -> ManagedFields.patch("Config.yml",
                "DefaultRewardWorlds: [world]\n", Map.of("DefaultRewardWorlds", List.of("has spaces")), true));
    }
}
