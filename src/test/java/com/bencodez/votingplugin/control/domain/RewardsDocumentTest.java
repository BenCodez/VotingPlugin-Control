package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RewardsDocumentTest {
    private static final String SOURCE = "VoteSites:\n  Alpha:\n    Name: Alpha\n    Rewards:\n"
            + "      Commands:\n      - 'say one'\n      - 'say two'\n"
            + "      Messages:\n        Player: 'hello'\n"
            + "      Items:\n        diamond:\n          Material: DIAMOND\n          Amount: 2\n          CustomModelData: 123\n"
            + "      AdvancedPriority:\n        Rare:\n          Chance: 20\n          Commands:\n          - 'say rare'\n"
            + "      Unknown: preserve # important\n"
            + "  Beta:\n    Name: Beta\n    Rewards:\n      Commands:\n      - 'say beta'\n"
            + "EverySiteReward: {}\n";

    @Test void inventoryShowsExistingAndAdvancedStructureWithoutCopyingUnknownValues() {
        List<RewardsDocument.Scope> scopes = RewardsDocument.inventory(SOURCE, "VoteSites.yml");
        assertEquals(3, scopes.size());
        assertEquals("VoteSites.Alpha.Rewards", scopes.get(0).path());
        assertEquals(List.of("say one", "say two"), scopes.get(0).fields().get("Commands"));
        assertTrue(scopes.get(0).advancedKeys().contains("AdvancedPriority"));
        assertTrue(scopes.get(0).advancedKeys().contains("Items"));
        assertTrue(scopes.get(0).structurePaths().contains("Items.diamond.CustomModelData"));
        assertFalse(scopes.get(0).toString().contains("123"));
    }

    @Test void inventoryRejectsStructurePathsThatWouldExpandBeyondTheBoundedTypedResponse() {
        String key = "x".repeat(129);
        assertThrows(IllegalArgumentException.class,
                () -> RewardsDocument.inventory("VoteSites:\n  Alpha:\n    Rewards:\n      " + key + ": value\n", "VoteSites.yml"));

        StringBuilder source = new StringBuilder("VoteSites:\n  Alpha:\n    Rewards:\n      " + "p".repeat(120) + ":\n");
        for (int index = 0; index < 120; index++) {
            source.append("        ").append("c".repeat(125)).append(String.format("%03d", index)).append(": value\n");
        }
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.inventory(source.toString(), "VoteSites.yml"));
    }

    @Test void discoveredRewardScopePathsUseTheSameResponseBoundsAsTheirStructure() {
        String key = "n".repeat(128);
        String overlongPath = key + ":\n  " + key + ":\n    " + key + ":\n      " + key + ":\n        Rewards: {}\n";
        assertThrows(IllegalArgumentException.class,
                () -> RewardsDocument.inventory(overlongPath, "SpecialRewards.yml"));

        StringBuilder cumulative = new StringBuilder();
        for (int index = 0; index < 50; index++) {
            String suffix = String.format("%03d", index);
            cumulative.append("a".repeat(125)).append(suffix).append(":\n  ")
                    .append("b".repeat(125)).append(suffix).append(":\n    ")
                    .append("c".repeat(125)).append(suffix).append(":\n      Rewards: {}\n");
        }
        assertThrows(IllegalArgumentException.class,
                () -> RewardsDocument.inventory(cumulative.toString(), "SpecialRewards.yml"));
    }

    @Test void appendAndRemoveCommandsKeepDifferentTargetDocumentsIndependent() {
        String a = RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say three"));
        assertTrue(a.contains("- \"say three\""));
        assertTrue(a.contains("CustomModelData: 123"));
        assertTrue(a.contains("Unknown: preserve # important"));
        String b = RewardsDocument.patch(SOURCE.replace("say one", "say other"), "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say three"));
        assertTrue(b.contains("say other"));
        assertFalse(a.contains("say other"));
        String removed = RewardsDocument.patch(a, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say two"));
        assertFalse(removed.contains("say two"));
        assertTrue(removed.contains("say one"));
    }

    @Test void scalarEditPreservesNestedRewardAndOtherSite() {
        String changed = RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("SET_SCALAR", "Messages.Player", "new hello"));
        assertEquals(SOURCE.replace("Player: 'hello'", "Player: \"new hello\""), changed);
        assertTrue(changed.contains("say beta"));
        assertTrue(changed.contains("AdvancedPriority"));
    }

    @Test void explicitReplaceAndLastEntryRemovalHaveBoundedNonAmbiguousSemantics() {
        String replaced = RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REPLACE_LIST", "Commands", List.of("say replacement")));
        assertTrue(replaced.contains("Commands:\n      - \"say replacement\"\n      Messages:"));
        assertTrue(replaced.contains("CustomModelData: 123"));
        String single = SOURCE.replace("      - 'say two'\n", "");
        String removed = RewardsDocument.patch(single, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one"));
        assertTrue(removed.contains("Commands: []\n      Messages:"));
        String withComment = SOURCE.replace("      - 'say one'", "      - 'say one' # keep");
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(withComment, "VoteSites.yml",
                "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("REPLACE_LIST", "Commands", List.of("changed"))));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(withComment, "VoteSites.yml",
                "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one")));
        String hashInCommand = SOURCE.replace("      - 'say one'", "      - 'say # one'");
        assertFalse(RewardsDocument.patch(hashInCommand, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say # one")).contains("say # one"));
        String replacedHash = RewardsDocument.patch(hashInCommand, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REPLACE_LIST", "Commands", List.of("say changed")));
        assertTrue(replacedHash.contains("- \"say changed\""));
    }

    @Test void multilineAndFlowCommandsRemainAdvancedAndCannotBePartiallyRemoved() {
        String multiline = SOURCE.replace("      - 'say one'\n", "      - >\n        say one\n        continued\n");
        String path = "VoteSites.Alpha.Rewards";
        RewardsDocument.Scope scope = RewardsDocument.inventory(multiline, "VoteSites.yml").get(0);
        assertFalse(scope.fields().containsKey("Commands"));
        assertTrue(scope.advancedKeys().contains("Commands"));
        for (RewardsDocument.Edit edit : List.of(
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one continued\n"),
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say three"),
                new RewardsDocument.Edit("REPLACE_LIST", "Commands", List.of("say replacement")))) {
            assertThrows(IllegalArgumentException.class,
                    () -> RewardsDocument.patch(multiline, "VoteSites.yml", path, edit));
        }
        String changed = RewardsDocument.patch(multiline, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Money", 5));
        assertTrue(changed.contains("      - >\n        say one\n        continued\n"));

        String flow = SOURCE.replace("Commands:\n      - 'say one'\n      - 'say two'", "Commands: ['say one', 'say two']");
        assertTrue(RewardsDocument.inventory(flow, "VoteSites.yml").get(0).advancedKeys().contains("Commands"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(flow, "VoteSites.yml", path,
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one")));
        String escaped = SOURCE.replace("      - 'say one'", "      - \"say one\\ncontinued\"");
        assertEquals(List.of("say one\ncontinued", "say two"),
                RewardsDocument.inventory(escaped, "VoteSites.yml").get(0).fields().get("Commands"));
        assertFalse(RewardsDocument.patch(escaped, "VoteSites.yml", path,
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one\ncontinued")).contains("say one"));

        String multilineMessage = SOURCE.replace("Player: 'hello'", "Player: >\n          hello\n          continued");
        RewardsDocument.Scope messageScope = RewardsDocument.inventory(multilineMessage, "VoteSites.yml").get(0);
        assertFalse(messageScope.fields().containsKey("Messages.Player"));
        assertTrue(messageScope.advancedKeys().contains("Messages.Player"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(multilineMessage, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Messages.Player", "replacement")));

        String multilineMoney = SOURCE.replace("      Unknown: preserve # important", "      Money: !!int >-\n        10\n      Unknown: preserve # important");
        RewardsDocument.Scope moneyScope = RewardsDocument.inventory(multilineMoney, "VoteSites.yml").get(0);
        assertFalse(moneyScope.fields().containsKey("Money"));
        assertTrue(moneyScope.advancedKeys().contains("Money"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(multilineMoney, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Money", 20)));
        String multilineAmount = SOURCE.replace("          Amount: 2", "          Amount: !!int >-\n            2");
        assertFalse(RewardsDocument.inventory(multilineAmount, "VoteSites.yml").get(0).fields().containsKey("Items.diamond.Amount"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(multilineAmount, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Amount", 3)));
    }

    @Test void emptyCommandsCanBeRepopulatedAfterExplicitRemovalOrReplacement() {
        String path = "VoteSites.Alpha.Rewards";
        String empty = SOURCE.replace("      Commands:\n      - 'say one'\n      - 'say two'\n", "      Commands: []\n");
        assertEquals(List.of(), RewardsDocument.inventory(empty, "VoteSites.yml").get(0).fields().get("Commands"));
        String appended = RewardsDocument.patch(empty, "VoteSites.yml", path,
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say restored"));
        assertEquals(List.of("say restored"), RewardsDocument.inventory(appended, "VoteSites.yml").get(0).fields().get("Commands"));
        assertTrue(appended.contains("CustomModelData: 123"));
        String replaced = RewardsDocument.patch(empty, "VoteSites.yml", path,
                new RewardsDocument.Edit("REPLACE_LIST", "Commands", List.of("say first", "say second")));
        assertEquals(List.of("say first", "say second"), RewardsDocument.inventory(replaced, "VoteSites.yml").get(0).fields().get("Commands"));
        assertEquals(empty, RewardsDocument.patch(empty, "VoteSites.yml", path,
                new RewardsDocument.Edit("REPLACE_LIST", "Commands", List.of())));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(empty, "VoteSites.yml", path,
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say restored")));
        String commented = empty.replace("Commands: []", "Commands: [] # keep");
        String restoredWithNote = RewardsDocument.patch(commented, "VoteSites.yml", path,
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say restored"));
        assertTrue(restoredWithNote.contains("Commands:  # keep\n      - \"say restored\""));
        assertEquals(List.of("say restored"), RewardsDocument.inventory(restoredWithNote, "VoteSites.yml")
                .get(0).fields().get("Commands"));
        String spaced = empty.replace("Commands: []", "Commands: [ ]");
        assertEquals(List.of("say restored"), RewardsDocument.inventory(RewardsDocument.patch(spaced,
                "VoteSites.yml", path, new RewardsDocument.Edit("REPLACE_LIST", "Commands",
                        List.of("say restored"))), "VoteSites.yml").get(0).fields().get("Commands"));
        String removedLast = RewardsDocument.patch(SOURCE.replace("      - 'say two'\n", ""), "VoteSites.yml", path,
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one"));
        assertEquals(List.of("say restored"), RewardsDocument.inventory(RewardsDocument.patch(removedLast,
                "VoteSites.yml", path, new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say restored")),
                "VoteSites.yml").get(0).fields().get("Commands"));
    }

    @Test void explicitCreateOnlyWhenAbsentAndUnicodeSourceMarksRemainCorrect() {
        String source = "# 😀\nVoteSites:\n  Alpha:\n    Name: Alpha\n  Beta:\n    Name: Beta\n";
        String created = RewardsDocument.patch(source, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("CREATE_REWARD", "Commands", "say 😀"));
        assertTrue(created.contains("    Rewards:\n      Commands:\n      - \"say 😀\"\n  Beta:"));
        assertTrue(created.startsWith("# 😀\n"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(created, "VoteSites.yml",
                "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("CREATE_REWARD", "Commands", "again")));
    }

    @Test void exactRemovalLeavesSiteAndOtherRewards() {
        String changed = RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_REWARD", null, null));
        assertTrue(changed.contains("Alpha:\n    Name: Alpha"));
        assertFalse(changed.contains("say one"));
        assertTrue(changed.contains("say beta"));
    }

    @Test void removingRewardPreservesTrailingNotesBeforeSiblingAndEof() {
        String source = "VoteSites:\n  Alpha:\n    Rewards:\n      Commands:\n      - 'say one'\n"
                + "      # internal note\n      Money: 1\n      # operator note\n\n    Hidden: true\n";
        String removed = RewardsDocument.patch(source, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_REWARD", null, null));
        assertEquals("VoteSites:\n  Alpha:\n      # operator note\n\n    Hidden: true\n", removed);
        String eof = source.substring(0, source.indexOf("    Hidden:"));
        String removedAtEof = RewardsDocument.patch(eof, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_REWARD", null, null));
        assertEquals("VoteSites:\n  Alpha:\n      # operator note\n\n", removedAtEof);
    }

    @Test void rejectsTraversalAmbiguousYamlAndDuplicateCommands() {
        RewardsDocument.Scope nullReward = RewardsDocument.inventory(
                "VoteSites:\n  Alpha:\n    Rewards: null\n", "VoteSites.yml").get(0);
        assertEquals("UNSUPPORTED", nullReward.status());
        assertFalse(nullReward.editable());
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites...Rewards",
                new RewardsDocument.Edit("REMOVE_REWARD", null, null)));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "not there")));
        String duplicate = SOURCE.replace("say two", "say one");
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(duplicate, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("REMOVE_LIST_ENTRY", "Commands", "say one")));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.inventory("A: 1\nA: 2\n", "SpecialRewards.yml"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.inventory("A: &x {B: 1}\nC: *x\n", "SpecialRewards.yml"));
    }

    @Test void escapesSupportedWhitespaceAndRejectsInvalidYamlControlCharacters() {
        String changed = RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say\tplayer"));
        assertTrue(changed.contains("- \"say\\tplayer\""));
        assertEquals(List.of("say one", "say two", "say\tplayer"),
                RewardsDocument.inventory(changed, "VoteSites.yml").get(0).fields().get("Commands"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml",
                "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say\u0001player")));
    }

    @Test void malformedEditPayloadsFailAsValidationErrors() {
        String path = "VoteSites.Alpha.Rewards";
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", path,
                new RewardsDocument.Edit(null, "Commands", "say no")));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", null, "say no")));
        String withoutCommands = SOURCE.replace("      Commands:\n      - 'say one'\n      - 'say two'\n", "");
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(withoutCommands, "VoteSites.yml", path,
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", 42)));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(withoutCommands, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Commands", "say wrong shape")));
        String added = RewardsDocument.patch(withoutCommands, "VoteSites.yml", path,
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say valid"));
        assertEquals(List.of("say valid"), RewardsDocument.inventory(added, "VoteSites.yml").get(0).fields().get("Commands"));
    }

    @Test void missingFieldEditsDoNotCreateCaseFoldedDuplicateKeys() {
        String path = "VoteSites.Alpha.Rewards";
        String lowerMoney = SOURCE.replace("      Unknown: preserve # important", "      money: 1\n      Unknown: preserve # important");
        assertTrue(RewardsDocument.inventory(lowerMoney, "VoteSites.yml").get(0).advancedKeys().contains("Money"));
        assertFalse(RewardsDocument.inventory(lowerMoney, "VoteSites.yml").get(0).fields().containsKey("Money"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(lowerMoney, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Money", 2)));
        String lowerCommands = SOURCE.replace("Commands:\n      - 'say one'\n      - 'say two'", "commands:\n      - 'say one'");
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(lowerCommands, "VoteSites.yml", path,
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say two")));
        String lowerReward = "VoteSites:\n  Alpha:\n    rewards: {}\n";
        assertEquals("UNSUPPORTED", RewardsDocument.inventory(lowerReward, "VoteSites.yml").get(0).status());
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(lowerReward, "VoteSites.yml", path,
                new RewardsDocument.Edit("CREATE_REWARD", "Commands", "say new")));
        String named = "chance: 5\n";
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(named, "Rewards/StandardVote.yml", "$",
                new RewardsDocument.Edit("SET_SCALAR", "Chance", 10)));
        String added = RewardsDocument.patch(named, "Rewards/StandardVote.yml", "$",
                new RewardsDocument.Edit("SET_SCALAR", "Money", 2));
        assertEquals("2", RewardsDocument.inventory(added, "Rewards/StandardVote.yml").get(0).fields().get("Money"));
    }

    @Test void namedRewardEditsPatchOnlyRootFieldsAndPreserveUnknownChildren() {
        String source = "# owner note\nCommands:\n- 'say first'\nItems:\n  diamond:\n    Material: DIAMOND\n"
                + "    Amount: 1\n    CustomModelData: 912\nCondition:\n  FutureKey: preserve\n";
        String file = "Rewards/StandardVote.yml";
        RewardsDocument.Scope root = RewardsDocument.inventory(source, file).get(0);
        assertEquals("$", root.path());
        assertEquals(List.of("say first"), root.fields().get("Commands"));
        String appended = RewardsDocument.patch(source, file, "$",
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say second"));
        assertTrue(appended.contains("- \"say second\""));
        assertTrue(appended.contains("CustomModelData: 912"));
        assertTrue(appended.contains("FutureKey: preserve"));
        String scalar = RewardsDocument.patch(appended, file, "$",
                new RewardsDocument.Edit("SET_SCALAR", "Money", 2));
        assertTrue(scalar.contains("Money: 2\n"));
        assertTrue(scalar.startsWith("# owner note\n"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(source, file, "$",
                new RewardsDocument.Edit("REMOVE_REWARD", null, null)));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.inventory(source, "Rewards/../Config.yml"));
    }

    @Test void namedRewardMissingFieldsAreInsertedBeforeExplicitDocumentEnd() {
        String source = "# owner note\nCommands:\n- 'say first'\n# trailing note\n...\n";
        String file = "Rewards/StandardVote.yml";
        String added = RewardsDocument.patch(source, file, "$", new RewardsDocument.Edit("SET_SCALAR", "Money", 10));
        assertTrue(added.indexOf("Money: 10") < added.indexOf("..."));
        assertTrue(added.contains("# trailing note"));
        assertEquals("10", RewardsDocument.inventory(added, file).get(0).fields().get("Money"));

        String withoutCommands = "# owner note\nMoney: 2\n...\n";
        String appended = RewardsDocument.patch(withoutCommands, file, "$",
                new RewardsDocument.Edit("APPEND_LIST_ENTRY", "Commands", "say added"));
        assertTrue(appended.indexOf("Commands:") < appended.indexOf("..."));
        assertEquals(List.of("say added"), RewardsDocument.inventory(appended, file).get(0).fields().get("Commands"));
    }

    @Test void itemLeafEditsPreserveMetadataCommentsAndOtherItems() {
        String source = "Items:\n  diamond: # keep identity\n    Material: DIAMOND\n    Amount: 1\n"
                + "    Name: '&bVote diamond'\n    Lore:\n    - first\n    Enchantments:\n      sharpness: 5\n"
                + "    CustomModelData: 912\n  future_item:\n    Material: FUTURE_MATERIAL\n    Amount: 2\n    FutureMetadata: preserve\n";
        String material = RewardsDocument.patch(source, "Rewards/StandardVote.yml", "$",
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Material", "NETHERITE_INGOT"));
        assertTrue(material.contains("Material: \"NETHERITE_INGOT\""));
        assertTrue(material.contains("Name: '&bVote diamond'"));
        assertTrue(material.contains("sharpness: 5"));
        assertTrue(material.contains("CustomModelData: 912"));
        assertTrue(material.contains("FutureMetadata: preserve"));
        String amount = RewardsDocument.patch(material, "Rewards/StandardVote.yml", "$",
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Amount", 2));
        assertTrue(amount.contains("Amount: 2"));
        assertTrue(amount.contains("future_item:"));
        RewardsDocument.Scope scope = RewardsDocument.inventory(amount, "Rewards/StandardVote.yml").get(0);
        assertEquals("NETHERITE_INGOT", scope.fields().get("Items.diamond.Material"));
        assertEquals("2", scope.fields().get("Items.diamond.Amount"));
    }

    @Test void numericRewardEditsRejectQuotedOrAdvancedCurrentScalars() {
        String path = "VoteSites.Alpha.Rewards";
        String quoted = SOURCE.replace("      Unknown: preserve # important\n",
                "      Money: '10'\n      Chance: '25'\n      Unknown: preserve # important\n");
        RewardsDocument.Scope scope = RewardsDocument.inventory(quoted, "VoteSites.yml").get(0);
        assertTrue(scope.advancedKeys().contains("Money"));
        assertTrue(scope.advancedKeys().contains("Chance"));
        for (String field : List.of("Money", "Chance")) {
            assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(quoted, "VoteSites.yml", path,
                    new RewardsDocument.Edit("SET_SCALAR", field, 20)));
        }
        String numeric = quoted.replace("Money: '10'", "Money: 10").replace("Chance: '25'", "Chance: 25.5");
        String changed = RewardsDocument.patch(numeric, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Chance", 30));
        assertTrue(changed.contains("Chance: 30\n"));
        assertTrue(changed.contains("Money: 10\n"));
    }

    @Test void messageEditsRejectNonstringsClassifiedAsAdvanced() {
        String source = SOURCE.replace("Player: 'hello'", "Player: true\n        Broadcast: 42");
        RewardsDocument.Scope scope = RewardsDocument.inventory(source, "VoteSites.yml").get(0);
        assertTrue(scope.advancedKeys().contains("Messages.Player"));
        assertTrue(scope.advancedKeys().contains("Messages.Broadcast"));
        for (String field : List.of("Messages.Player", "Messages.Broadcast")) {
            assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(source, "VoteSites.yml",
                    "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("SET_SCALAR", field, "safe text")));
        }
        String changed = RewardsDocument.patch(SOURCE, "VoteSites.yml", "VoteSites.Alpha.Rewards",
                new RewardsDocument.Edit("SET_SCALAR", "Messages.Player", "new hello"));
        assertTrue(changed.contains("Player: \"new hello\""));
    }

    @Test void flowMessagesAreAdvancedWhileBlockMessagesRemainEditable() {
        String flow = SOURCE.replace("Messages:\n        Player: 'hello'", "Messages: {Player: 'hello'}");
        RewardsDocument.Scope scope = RewardsDocument.inventory(flow, "VoteSites.yml").get(0);
        assertFalse(scope.fields().containsKey("Messages.Player"));
        assertTrue(scope.advancedKeys().contains("Messages"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(flow, "VoteSites.yml",
                "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("SET_SCALAR", "Messages.Player", "new hello")));

        String named = "Messages: {Player: 'hello'}\n";
        RewardsDocument.Scope namedScope = RewardsDocument.inventory(named, "Rewards/StandardVote.yml").get(0);
        assertFalse(namedScope.fields().containsKey("Messages.Player"));
        assertTrue(namedScope.advancedKeys().contains("Messages"));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(named, "Rewards/StandardVote.yml", "$",
                new RewardsDocument.Edit("SET_SCALAR", "Messages.Player", "new hello")));
        assertEquals("new hello", RewardsDocument.inventory(RewardsDocument.patch(SOURCE, "VoteSites.yml",
                "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("SET_SCALAR", "Messages.Player", "new hello")),
                "VoteSites.yml").get(0).fields().get("Messages.Player"));
    }

    @Test void mappedCommandsStayAdvancedAndCannotBeEditedThroughTypedPreview() {
        String mapped = SOURCE.replace("Commands:\n      - 'say one'\n      - 'say two'",
                "Commands:\n        Console: 'say one'\n        Player: 42");
        RewardsDocument.Scope scope = RewardsDocument.inventory(mapped, "VoteSites.yml").get(0);
        assertFalse(scope.fields().containsKey("Commands"));
        assertTrue(scope.advancedKeys().contains("Commands"));
        for (String field : List.of("Commands.Console", "Commands.Player")) {
            assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(mapped, "VoteSites.yml",
                    "VoteSites.Alpha.Rewards", new RewardsDocument.Edit("SET_SCALAR", field, "new command")));
        }
        String named = "Commands:\n  Console: 'say one'\n";
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(named, "Rewards/StandardVote.yml", "$",
                new RewardsDocument.Edit("SET_SCALAR", "Commands.Console", "new command")));
        assertEquals(List.of("say one", "say two"),
                RewardsDocument.inventory(SOURCE, "VoteSites.yml").get(0).fields().get("Commands"));
    }

    @Test void itemEditingFailsClosedForUnsupportedOrUnsafeShapes() {
        String path = "VoteSites.Alpha.Rewards";
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Amount", 65)));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Material", "not-a-material")));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Amount", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(SOURCE, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "AdvancedPriority.Rare.Items.diamond.Amount", 2)));
        String missing = SOURCE.replace("          Amount: 2\n", "");
        assertThrows(IllegalArgumentException.class, () -> RewardsDocument.patch(missing, "VoteSites.yml", path,
                new RewardsDocument.Edit("SET_SCALAR", "Items.diamond.Amount", 2)));
    }
}
