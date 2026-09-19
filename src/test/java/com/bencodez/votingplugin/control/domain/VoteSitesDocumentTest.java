package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VoteSitesDocumentTest {
    private static final String SITE = "VoteSites:\n"
            + "  Alpha:\n"
            + "    Enabled: true\n"
            + "    Name: Alpha display\n"
            + "    ServiceSite: alpha-service\n"
            + "    VoteURL: https://example.test/vote\n"
            + "    VoteDelay: '24h'\n"
            + "    Priority: 7\n"
            + "    Hidden: false\n"
            + "    DisplayItem:\n"
            + "      Material: DIAMOND\n"
            + "      Amount: 2\n";

    @Test void inventoryUsesMappingKeyNotNameOrServiceAndExposesOnlyNineFields() {
        String content = "Header: retain-me\n" + SITE + "    Password: never-exposed\n"
                + "    Rewards: {}\n"
                + "  unsafe key!: \n"
                + "    Enabled: false\n"
                + "    Name: secret-name-is-an-allowed-field\n"
                + "    ServiceSite: beta\n"
                + "    VoteURL: https://example.test/b\n"
                + "    VoteDelay: 10\n"
                + "    Priority: 0\n"
                + "    Hidden: false\n"
                + "    DisplayItem:\n"
                + "      Material: STONE\n"
                + "      Amount: 1\n";

        VoteSitesDocument.Inventory inventory = VoteSitesDocument.inventory(content);

        assertEquals(List.of("Alpha", "unsafe key!"), inventory.sites().stream().map(VoteSitesDocument.Site::key).toList());
        VoteSitesDocument.Site alpha = inventory.sites().get(0);
        assertTrue(alpha.editable());
        assertFalse(alpha.rewardsConfigured());
        assertEquals(9, alpha.fields().size());
        assertFalse(alpha.fields().containsKey("Password"));
        assertEquals("Alpha display", alpha.fields().get("Name").value());
        assertEquals("alpha-service", alpha.fields().get("ServiceSite").value());
        assertEquals(7, alpha.fields().get("Priority").value());
        assertFalse(inventory.sites().get(1).editable());
        assertEquals(VoteSitesDocument.Status.UNSUPPORTED,
                inventory.sites().get(1).fields().get("VoteDelay").status());
        assertFalse(inventory.toString().contains("never-exposed"));
    }

    @Test void rewardsPresenceAndLegacyDelayAreObservedWithoutDisclosure() {
        String content = SITE + "    Rewards:\n      Commands:\n        - say hi\n";
        VoteSitesDocument.Site site = VoteSitesDocument.inventory(content).sites().get(0);
        assertTrue(site.rewardsConfigured());
        assertEquals(VoteSitesDocument.Status.AVAILABLE, site.fields().get("VoteDelay").status());

        String legacy = SITE.replace("VoteDelay: '24h'", "VoteDelay: 24");
        assertEquals(VoteSitesDocument.Status.UNSUPPORTED,
                VoteSitesDocument.inventory(legacy).sites().get(0).fields().get("VoteDelay").status());
    }

    @Test void editChangesOneScalarAndPreservesRewardsUnknownFieldsAndCommentsExactly() {
        String content = "# outside\n" + SITE + "    Unknown: keep # unknown comment\n"
                + "    Rewards:\n      Commands:\n        - 'secret command' # reward comment\n";
        String expected = content.replace("Priority: 7", "Priority: 8");
        assertEquals(expected, VoteSitesDocument.edit(content, "Alpha", Map.of("Priority", 8)));
    }

    @Test void editCanInsertMissingDirectAndNestedFieldsWithoutRebuildingDisplayItem() {
        String content = "VoteSites:\n  Alpha:\n    Enabled: true\n    DisplayItem:\n      Material: STONE\n      UnknownNested: preserve\n"
                + "    Rewards:\n      A: B\n";
        String edited = VoteSitesDocument.edit(content, "Alpha", Map.of("Name", "Inserted", "DisplayItem.Amount", 3));
        assertEquals("VoteSites:\n  Alpha:\n    Enabled: true\n    DisplayItem:\n      Material: STONE\n      UnknownNested: preserve\n"
                + "      Amount: 3\n    Rewards:\n      A: B\n    Name: \"Inserted\"\n", edited);
    }

    @Test void addRequiresFullTypedMapAndKeepsExistingDocument() {
        String content = "Top: keep\n" + SITE + "# trailing\n";
        String added = VoteSitesDocument.add(content, "Beta", complete("Beta", "beta"));
        assertTrue(added.startsWith("Top: keep\n" + SITE + "# trailing\n  \"Beta\":\n"));
        assertTrue(added.contains("    Name: \"Beta\"\n"));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.add(content, "Alpha", complete("B", "b")));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.add(content, "alpha", complete("B", "b")));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.add(content, "bad key!", complete("B", "b")));
        Map<String, Object> incomplete = new LinkedHashMap<>(complete("B", "b"));
        incomplete.remove("Hidden");
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.add(content, "Beta", incomplete));
    }

    @Test void removeIsExactAndRejectsMissingOrUnsafeIdentity() {
        String content = "VoteSites:\n  Alpha:\n    Enabled: true\n  Beta:\n    Enabled: false\n# retained\n";
        assertEquals("VoteSites:\n  Beta:\n    Enabled: false\n# retained\n", VoteSitesDocument.remove(content, "Alpha"));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.remove(content, "missing"));
        assertThrows(IllegalArgumentException.class,
                () -> VoteSitesDocument.remove("VoteSites:\n  bad key!:\n    Enabled: true\n", "bad key!"));
        String empty = VoteSitesDocument.remove("VoteSites:\n  Alpha:\n    Enabled: true\n", "Alpha");
        assertEquals("VoteSites:\n", empty);
        assertTrue(VoteSitesDocument.inventory(empty).sites().isEmpty());
        assertEquals(1, VoteSitesDocument.inventory(VoteSitesDocument.add(empty, "Beta", complete("Beta", "beta"))).sites().size());
    }

    @Test void invalidDocumentsFailClosed() {
        List<String> invalid = List.of(
                "VoteSites:\n  Alpha: {}\n  Alpha: {}\n",
                "VoteSites:\n  Alpha: &site\n    Enabled: true\n",
                "VoteSites:\n  Alpha: *missing\n",
                "VoteSites: { Alpha: {} }\n",
                "VoteSites:\n  Alpha: [x]\n",
                "VoteSites:\n  Alpha: {}\n---\nVoteSites: {}\n",
                "VoteSites:\n  Alpha: [\n");
        for (String content : invalid) assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.inventory(content));
        assertThrows(IllegalArgumentException.class,
                () -> VoteSitesDocument.inventory("VoteSites:\n  A:\n    Enabled: true\n" + "#".repeat(512 * 1024)));
        assertThrows(IllegalArgumentException.class,
                () -> VoteSitesDocument.inventory("VoteSites:\n  Alpha:\n    Enabled: true\n  alpha:\n    Enabled: false\n"));
        StringBuilder tooMany = new StringBuilder("VoteSites:\n");
        for (int i = 0; i <= 200; i++) tooMany.append("  Site").append(i).append(":\n    Enabled: true\n");
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.inventory(tooMany.toString()));
    }

    @Test void editRejectsUnknownWrongTypeRangeAndUnsupportedExistingFields() {
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.edit(SITE, "Alpha", Map.of("Unknown", true)));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.edit(SITE, "Alpha", Map.of("Enabled", "true")));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.edit(SITE, "Alpha", Map.of("DisplayItem.Amount", 0)));
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.edit(SITE, "Alpha", Map.of("VoteURL", "bad\nurl")));
        String unsupported = SITE.replace("VoteDelay: '24h'", "VoteDelay: 24");
        assertThrows(IllegalArgumentException.class,
                () -> VoteSitesDocument.edit(unsupported, "Alpha", Map.of("VoteDelay", "24h")));
        String lowerCase = SITE.replace("Enabled: true", "enabled: true");
        assertEquals(VoteSitesDocument.Status.UNSUPPORTED,
                VoteSitesDocument.inventory(lowerCase).sites().get(0).fields().get("Enabled").status());
        assertThrows(IllegalArgumentException.class,
                () -> VoteSitesDocument.edit(lowerCase, "Alpha", Map.of("Enabled", false)));
        String ambiguous = SITE.replace("Enabled: true", "Enabled: true\n    enabled: false");
        assertThrows(IllegalArgumentException.class, () -> VoteSitesDocument.inventory(ambiguous));
    }

    private static Map<String, Object> complete(String name, String service) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Enabled", true);
        fields.put("Name", name);
        fields.put("ServiceSite", service);
        fields.put("VoteURL", "https://example.test/" + service);
        fields.put("VoteDelay", "24h");
        fields.put("Priority", 1);
        fields.put("Hidden", false);
        fields.put("DisplayItem.Material", "STONE");
        fields.put("DisplayItem.Amount", 1);
        return fields;
    }
}
