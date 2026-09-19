package com.bencodez.votingplugin.control.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManagedConfigurationRewardFilesTest {
    @Test void namedRewardsUseSeparateVersionedCapability() {
        assertEquals(ManagedConfiguration.REWARD_FILE_CAPABILITY,
                ManagedConfiguration.file("Rewards/StandardVote.yml", null).capability());
        assertEquals("config.files.v1", ManagedConfiguration.file("VoteSites.yml", null).capability());
    }

    @Test void namedRewardsRejectPathsAndUnapprovedNames() {
        for (String file : new String[] {"Rewards/../Config.yml", "Rewards/%2e%2e.yml",
                "Rewards/Other/File.yml", "Rewards\\Other.yml", "Rewards/.hidden.yml",
                "Rewards/Foo.yaml", "/Rewards/Foo.yml", "Rewards/Foo Bar.yml", "Rewards/Foo.yml/Child"}) {
            assertThrows(IllegalArgumentException.class, () -> ManagedConfiguration.file(file, null), file);
        }
    }
}
