package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GeneralSettingsDocumentTest {
    @Test void fieldsExposeOnlyKnownSettingsWithMissingAndTypeStatus() {
        String content = "\"ProcessRewards\": FALSE # deliberately uppercase\n"
                + "AutoCreateVoteSites: true\n"
                + "ExtraAllSitesCheck: 'false'\n"
                + "CountFakeVotes: null\n"
                + "SecretToken: __VOTINGPLUGIN_CONTROL_REDACTED__\n";

        Map<String, GeneralSettingsDocument.Field> fields = GeneralSettingsDocument.fields(content);

        assertEquals(9, fields.size());
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.AVAILABLE, false),
                fields.get("ProcessRewards"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.AVAILABLE, true),
                fields.get("AutoCreateVoteSites"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.UNSUPPORTED, null),
                fields.get("ExtraAllSitesCheck"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.UNSUPPORTED, null),
                fields.get("CountFakeVotes"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.MISSING, null),
                fields.get("DisableNoServiceSiteMessage"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.MISSING, null),
                fields.get("DisableUpdateChecking"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.MISSING, null),
                fields.get("UseVoteGUIMainCommand"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.MISSING, null),
                fields.get("CloseInventoryOnVote"));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.MISSING, null),
                fields.get("ExtraVoteShopCheck"));
    }

    @Test void patchChangesOnlySelectedScalarSpansAndPreservesIndependentDocuments() {
        String first = "# settings\n\"ProcessRewards\": true # quoted key intentionally\n"
                + "Unknown: keep\nPassword: __VOTINGPLUGIN_CONTROL_REDACTED__\n";
        String second = "Unknown: keep\nProcessRewards : FALSE # another layout\n";

        assertEquals("# settings\n\"ProcessRewards\": false # quoted key intentionally\n"
                        + "Unknown: keep\nPassword: __VOTINGPLUGIN_CONTROL_REDACTED__\n",
                GeneralSettingsDocument.patch(first, Map.of("ProcessRewards", false)));
        assertEquals("Unknown: keep\nProcessRewards : true # another layout\n",
                GeneralSettingsDocument.patch(second, Map.of("ProcessRewards", true)));
    }

    @Test void patchesVerifiedUiAndShopBooleansWithoutTouchingOtherConfiguration() {
        String source = "# GUI behavior\nUseVoteGUIMainCommand: false # /vote\n"
                + "CloseInventoryOnVote: true\nExtraVoteShopCheck: true\n"
                + "VoteShop: {Unknown: preserved}\nSecret: __VOTINGPLUGIN_CONTROL_REDACTED__\n";

        assertEquals("# GUI behavior\nUseVoteGUIMainCommand: true # /vote\n"
                        + "CloseInventoryOnVote: false\nExtraVoteShopCheck: true\n"
                        + "VoteShop: {Unknown: preserved}\nSecret: __VOTINGPLUGIN_CONTROL_REDACTED__\n",
                GeneralSettingsDocument.patch(source, Map.of("UseVoteGUIMainCommand", true,
                        "CloseInventoryOnVote", false)));
        assertEquals(new GeneralSettingsDocument.Field(GeneralSettingsDocument.Status.AVAILABLE, true),
                GeneralSettingsDocument.fields(source).get("ExtraVoteShopCheck"));
    }

    @Test void patchIsStrictAndNeverInventsOrCoercesSettings() {
        String missing = "Other: true\n";
        String unsupported = "ProcessRewards: 'true'\n";
        HashMap<String, Boolean> nullValue = new HashMap<>();
        nullValue.put("ProcessRewards", null);

        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch(missing, Map.of("ProcessRewards", true)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch(unsupported, Map.of("ProcessRewards", true)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("ProcessRewards: true\n", Map.of("unknown", true)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("ProcessRewards: true\n", nullValue));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("ProcessRewards: true\n", Map.of()));
    }

    @Test void noChangeReturnsTextVerbatimIncludingNonCanonicalBooleanSpelling() {
        String content = "ProcessRewards: TRUE # retain source style\n";
        assertEquals(content, GeneralSettingsDocument.patch(content, Map.of("ProcessRewards", true)));
    }

    @Test void supportsFlowRootWithoutReformattingIt() {
        String content = "{ProcessRewards: true, unknown: __VOTINGPLUGIN_CONTROL_REDACTED__}\n";
        assertEquals("{ProcessRewards: false, unknown: __VOTINGPLUGIN_CONTROL_REDACTED__}\n",
                GeneralSettingsDocument.patch(content, Map.of("ProcessRewards", false)));
    }

    @Test void usesCodePointMarksWhenTextBeforeASettingContainsNonBmpCharacters() {
        String content = "Message: '😀'\nProcessRewards: true # exact location follows emoji\n";
        assertEquals("Message: '😀'\nProcessRewards: false # exact location follows emoji\n",
                GeneralSettingsDocument.patch(content, Map.of("ProcessRewards", false)));
    }

    @Test void rejectsAliasOrAnchorInvolvementForAnEditedSetting() {
        String anchored = "ProcessRewards: &shared true\nOther: *shared\n";
        String aliased = "Other: &shared true\nProcessRewards: *shared\n";

        assertEquals(GeneralSettingsDocument.Status.UNSUPPORTED,
                GeneralSettingsDocument.fields(anchored).get("ProcessRewards").status());
        assertEquals(GeneralSettingsDocument.Status.UNSUPPORTED,
                GeneralSettingsDocument.fields(aliased).get("ProcessRewards").status());
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch(anchored, Map.of("ProcessRewards", false)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch(aliased, Map.of("ProcessRewards", false)));
    }

    @Test void rejectsDuplicateRootsMultipleDocumentsInvalidAndOversizedInputWithoutLeakingIt() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("ProcessRewards: true\nProcessRewards: false\n"));
        assertThrows(IllegalArgumentException.class, () -> GeneralSettingsDocument.fields("- ProcessRewards\n"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("ProcessRewards: true\n---\nProcessRewards: false\n"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("ProcessRewards: [\n"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("ProcessRewards: " + "x".repeat(512 * 1024)));
        assertTrue(!failure.getMessage().contains("x"));
    }
}
