package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneralSettingsDocumentTest {
    @Test void backendFieldsExposeCuratedTypedValuesAndStatuses() {
        String content = "DebugLevel: extra\nOnlineMode: TRUE\nAutoCreateVoteSites: true\n"
                + "CountFakeVotes: 'true'\nBedrockPlayerPrefix: '.'\nDisableUpdateChecking: true\n"
                + "SecretToken: __VOTINGPLUGIN_CONTROL_REDACTED__\n";
        Map<String, GeneralSettingsDocument.Field> fields = GeneralSettingsDocument.fields(content);
        assertEquals(13, fields.size());
        assertEquals("EXTRA", fields.get("Debug").value());
        assertEquals(true, fields.get("OnlineMode").value());
        assertEquals(GeneralSettingsDocument.Status.UNSUPPORTED, fields.get("CountFakeVotes").status());
        assertEquals(".", fields.get("BedrockPlayerPrefix").value());
        assertEquals(false, fields.get("CheckForUpdates").value());
        assertEquals(GeneralSettingsDocument.Status.MISSING, fields.get("GiveDefaultPermission").status());
    }

    @Test void proxyProfileMapsNamesAndDebugWithoutInventingExtra() {
        String content = "Debug: true\nOnlineMode: false\nAllowUnJoined: true\nBedrockPlayerPrefix: '.'\n";
        Map<String, GeneralSettingsDocument.Field> fields = GeneralSettingsDocument.fields(content,
                GeneralSettingsDocument.Profile.PROXY);
        assertEquals(4, fields.size());
        assertEquals("INFO", fields.get("Debug").value());
        assertEquals(true, fields.get("AllowUnjoined").value());
        assertEquals("Debug: false\nOnlineMode: true\nAllowUnJoined: true\nBedrockPlayerPrefix: '_'\n",
                GeneralSettingsDocument.patch(content, GeneralSettingsDocument.Profile.PROXY,
                        Map.of("Debug", "NONE", "OnlineMode", true, "BedrockPlayerPrefix", "_")));
        assertEquals(content, GeneralSettingsDocument.patch(content, GeneralSettingsDocument.Profile.PROXY,
                Map.of("Debug", "EXTRA")));
    }

    @Test void patchChangesOnlySelectedScalarSpansAndPreservesSource() {
        String source = "# settings\nCountFakeVotes: TRUE # keep\nDisableUpdateChecking: false\n"
                + "BedrockPlayerPrefix: '.'\nUnknown: keep\nPassword: __VOTINGPLUGIN_CONTROL_REDACTED__\n";
        assertEquals("# settings\nCountFakeVotes: false # keep\nDisableUpdateChecking: true\n"
                        + "BedrockPlayerPrefix: 'x''y'\nUnknown: keep\nPassword: __VOTINGPLUGIN_CONTROL_REDACTED__\n",
                GeneralSettingsDocument.patch(source, Map.of("CountFakeVotes", false,
                        "CheckForUpdates", false, "BedrockPlayerPrefix", "x'y")));
    }

    @Test void patchIsStrictAndNeverInventsOrCoercesSettings() {
        HashMap<String, Object> nullValue = new HashMap<>();
        nullValue.put("CountFakeVotes", null);
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("Other: true\n", Map.of("CountFakeVotes", true)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("CountFakeVotes: 'true'\n", Map.of("CountFakeVotes", true)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("CountFakeVotes: true\n", Map.of("unknown", true)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("CountFakeVotes: true\n", nullValue));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("DebugLevel: INFO\n", Map.of("Debug", "DEV")));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch("BedrockPlayerPrefix: '.'\n",
                        Map.of("BedrockPlayerPrefix", "x".repeat(33))));
    }

    @Test void noChangeReturnsTextVerbatimAndUnicodeOffsetsRemainCorrect() {
        String content = "Message: '😀'\nCountFakeVotes: TRUE # exact location follows emoji\n";
        assertEquals(content, GeneralSettingsDocument.patch(content, Map.of("CountFakeVotes", true)));
        assertEquals("Message: '😀'\nCountFakeVotes: false # exact location follows emoji\n",
                GeneralSettingsDocument.patch(content, Map.of("CountFakeVotes", false)));
    }

    @Test void rejectsAliasDuplicateMultipleInvalidAndOversizedDocuments() {
        String anchored = "CountFakeVotes: &shared true\nOther: *shared\n";
        assertEquals(GeneralSettingsDocument.Status.UNSUPPORTED,
                GeneralSettingsDocument.fields(anchored).get("CountFakeVotes").status());
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.patch(anchored, Map.of("CountFakeVotes", false)));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("CountFakeVotes: true\nCountFakeVotes: false\n"));
        assertThrows(IllegalArgumentException.class, () -> GeneralSettingsDocument.fields("- CountFakeVotes\n"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("CountFakeVotes: true\n---\nCountFakeVotes: false\n"));
        assertThrows(IllegalArgumentException.class, () -> GeneralSettingsDocument.fields("CountFakeVotes: [\n"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> GeneralSettingsDocument.fields("CountFakeVotes: " + "x".repeat(512 * 1024)));
        assertTrue(!failure.getMessage().contains("x"));
    }
}
