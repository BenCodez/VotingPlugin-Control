package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneralSettingsOperationsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);
    private static final String REDACTED = "__VOTINGPLUGIN_CONTROL_REDACTED__";

    @TempDir Path directory;

    @Test void readUsesThatBackendDocumentAndRevision() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            String document = "# keep\nProcessRewards: true\nToken: " + REDACTED + "\n";
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", document, revision('a'));

            ConfigurationOperations.SettingsState state = fixture.operations.generalSettingsState(read.operationId(),
                    "backend-a");
            assertEquals(read.operationId(), state.readOperationId());
            assertEquals(fixture.session("backend-a"), state.sessionId());
            assertEquals(revision('a'), state.revision());
            assertEquals(GeneralSettingsDocument.Status.AVAILABLE, state.fields().get("ProcessRewards").status());
            assertEquals(Boolean.TRUE, state.fields().get("ProcessRewards").value());
            assertEquals(GeneralSettingsDocument.Status.MISSING,
                    state.fields().get("AutoCreateVoteSites").status());
            assertNull(state.fields().get("AutoCreateVoteSites").value());
        }
    }

    @Test void voteSitesUseIndependentRetainedReadsAndKeepEachTargetsUnknownAndRewardsContent() throws Exception {
        try (Fixture fixture = fixture("backend-a", "backend-b")) {
            String first = "VoteSites:\n  Alpha:\n    Enabled: false\n    Priority: 5\n"
                    + "    Rewards:\n      Commands:\n        - 'private A'\n    Unknown: A\n";
            String second = "VoteSites:\n  Alpha:\n    Enabled: true\n    Priority: 2\n"
                    + "    Rewards:\n      Commands:\n        - 'private B'\n    Unknown: B\n";
            ConfigurationOperations.OperationView read = fixture.operations.createRead(List.of("backend-a", "backend-b"),
                    ManagedConfiguration.file("VoteSites.yml", null));
            completeFileRead(fixture, read, "backend-a", first, revision('a'), "VoteSites.yml");
            read = completeFileRead(fixture, read, "backend-b", second, revision('b'), "VoteSites.yml");
            assertEquals(false, fixture.operations.voteSitesState(read.operationId(), "backend-a")
                    .sites().get(0).fields().get("Enabled").value());
            assertEquals(true, fixture.operations.voteSitesState(read.operationId(), "backend-b")
                    .sites().get(0).fields().get("Enabled").value());
            ConfigurationOperations.OperationView firstPreview = fixture.operations.createVoteSitesPreview(
                    read.operationId(), "backend-a", "EDIT", "Alpha", Map.of("Priority", 20));
            ConfigurationOperations.OperationView secondPreview = fixture.operations.createVoteSitesPreview(
                    read.operationId(), "backend-b", "EDIT", "Alpha", Map.of("Priority", 20));
            ConfigurationTask firstTask = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            ConfigurationTask secondTask = fixture.operations.claim("backend-b", fixture.session("backend-b"));
            assertEquals(first.replace("Priority: 5", "Priority: 20"), firstTask.configuration().content());
            assertEquals(second.replace("Priority: 2", "Priority: 20"), secondTask.configuration().content());
            assertEquals(revision('a'), firstTask.expectedRevision());
            assertEquals(revision('b'), secondTask.expectedRevision());
            complete(fixture, firstPreview.operationId(), "backend-a", firstTask, revision('a'));
            complete(fixture, secondPreview.operationId(), "backend-b", secondTask, revision('b'));
        }
    }

    @Test void rewardPreviewUsesEachTargetsOwnRetainedDocumentAndIsRevisionBound() throws Exception {
        try (Fixture fixture = fixture("backend-a", "backend-b")) {
            String first = "VoteSites:\n  Alpha:\n    Rewards:\n      Commands:\n      - 'say A'\n"
                    + "      AdvancedPriority:\n        Rare:\n          Chance: 20\n    Private: keep-A\n";
            String second = first.replace("say A", "say B").replace("keep-A", "keep-B");
            ConfigurationOperations.OperationView read = fixture.operations.createRead(List.of("backend-a", "backend-b"),
                    ManagedConfiguration.file("VoteSites.yml", null));
            completeFileRead(fixture, read, "backend-a", first, revision('a'), "VoteSites.yml");
            read = completeFileRead(fixture, read, "backend-b", second, revision('b'), "VoteSites.yml");
            assertEquals(List.of("say A"), fixture.operations.rewardsState(read.operationId(), "backend-a", "VoteSites.yml")
                    .scopes().get(0).fields().get("Commands"));
            assertEquals(List.of("say B"), fixture.operations.rewardsState(read.operationId(), "backend-b", "VoteSites.yml")
                    .scopes().get(0).fields().get("Commands"));
            ConfigurationOperations.OperationView a = fixture.operations.createRewardsPreview(read.operationId(), "backend-a",
                    "VoteSites.yml", "VoteSites.Alpha.Rewards", "APPEND_LIST_ENTRY", "Commands", "say new");
            ConfigurationOperations.OperationView b = fixture.operations.createRewardsPreview(read.operationId(), "backend-b",
                    "VoteSites.yml", "VoteSites.Alpha.Rewards", "APPEND_LIST_ENTRY", "Commands", "say new");
            ConfigurationTask taskA = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            ConfigurationTask taskB = fixture.operations.claim("backend-b", fixture.session("backend-b"));
            assertEquals(first.replace("      - 'say A'\n", "      - 'say A'\n      - \"say new\"\n"), taskA.configuration().content());
            assertEquals(second.replace("      - 'say B'\n", "      - 'say B'\n      - \"say new\"\n"), taskB.configuration().content());
            assertEquals(revision('a'), taskA.expectedRevision());
            assertEquals(revision('b'), taskB.expectedRevision());
            complete(fixture, a.operationId(), "backend-a", taskA, revision('a'));
            complete(fixture, b.operationId(), "backend-b", taskB, revision('b'));
            UUID retainedReadId = read.operationId();
            assertThrows(ValidationException.class, () -> fixture.operations.createRewardsPreview(
                    retainedReadId, "backend-a", "SpecialRewards.yml", "VoteParty.Rewards", "REMOVE_REWARD", null, null));
        }
    }

    @Test void namedRewardFilesRequireV1CapabilityAndPatchOnlyRetainedRoot() throws Exception {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(CLOCK, Duration.ofMinutes(2));
        UUID oldSession = UUID.randomUUID();
        UUID newSession = UUID.randomUUID();
        registry.register(new NodeRegistration("old", oldSession, "Old", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        registry.register(new NodeRegistration("new", newSession, "New", "BUKKIT", "test", 1,
                Set.of(ConfigurationOperations.FILE_CAPABILITY, ManagedConfiguration.REWARD_FILE_CAPABILITY), Set.of()));
        try (ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(directory.resolve("named-audit"), CLOCK), CLOCK)) {
            String name = "Rewards/StandardVote.yml";
            assertEquals("NODE_UNAVAILABLE", assertThrows(ValidationException.class,
                    () -> operations.createRead(List.of("old"), ManagedConfiguration.file(name, null))).code());
            String source = "# keep\nCommands:\n- 'say first'\nItems:\n  Diamond:\n    CustomModelData: 123\n";
            ConfigurationOperations.OperationView read = operations.createRead(List.of("new"), ManagedConfiguration.file(name, null));
            ConfigurationTask task = operations.claim("new", newSession);
            read = operations.complete(read.operationId(), "new", new ConfigurationTaskResult(newSession,
                    true, "OK", "read", revision('a'), ManagedConfiguration.file(name, source), List.of(),
                    false, false, task.attemptId()));
            UUID readId = read.operationId();
            assertEquals("$", operations.rewardsState(read.operationId(), "new", name).scopes().get(0).path());
            ConfigurationOperations.OperationView preview = operations.createRewardsPreview(read.operationId(), "new",
                    name, "$", "APPEND_LIST_ENTRY", "Commands", "say second");
            ConfigurationTask proposal = operations.claim("new", newSession);
            assertEquals(revision('a'), proposal.expectedRevision());
            assertEquals(source.replace("- 'say first'\n", "- 'say first'\n- \"say second\"\n"),
                    proposal.configuration().content());
            assertNull(preview.configuration().content());
            assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class,
                    () -> operations.rewardsState(readId, "new", "Rewards/../Config.yml")).code());
        }
    }

    @Test void voteSitesRejectStaleRevisionAndDisposeExactPreviewBeforeAnyApply() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            String source = "VoteSites:\n  Alpha:\n    Enabled: false\n";
            ConfigurationOperations.OperationView read = fixture.operations.createRead(List.of("backend-a"),
                    ManagedConfiguration.file("VoteSites.yml", null));
            read = completeFileRead(fixture, read, "backend-a", source, revision('a'), "VoteSites.yml");
            ConfigurationOperations.OperationView stale = fixture.operations.createVoteSitesPreview(
                    read.operationId(), "backend-a", "EDIT", "Alpha", Map.of("Enabled", true));
            ConfigurationTask task = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            complete(fixture, stale.operationId(), "backend-a", task, revision('b'));
            assertEquals("STALE_REVISION", fixture.operations.get(stale.operationId()).results().get("backend-a").code());
            assertEquals("PREVIEW_INCOMPLETE", assertThrows(ValidationException.class,
                    () -> fixture.operations.createApply(stale.operationId(), "no-token")).code());

            ConfigurationOperations.OperationView preview = fixture.operations.createVoteSitesPreview(
                    read.operationId(), "backend-a", "EDIT", "Alpha", Map.of("Enabled", true));
            ConfigurationTask valid = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            preview = complete(fixture, preview.operationId(), "backend-a", valid, revision('a'));
            UUID id = preview.operationId(); String token = preview.approvalToken();
            assertNotNull(token);
            assertEquals("APPROVAL_REQUIRED", assertThrows(ValidationException.class,
                    () -> fixture.operations.discardVoteSitesPreview(id, "wrong-token")).code());
            fixture.operations.discardVoteSitesPreview(id, token);
            assertEquals("APPROVAL_REQUIRED", assertThrows(ValidationException.class,
                    () -> fixture.operations.createApply(id, token)).code());
            assertNull(fixture.operations.claim("backend-a", fixture.session("backend-a")));
        }
    }

    @Test void previewsArePerTargetAndOnlyChangeRequestedScalarText() throws Exception {
        try (Fixture fixture = fixture("backend-a", "backend-b")) {
            String first = "# first comment\nProcessRewards: true # retain\nUnknown: first\nToken: " + REDACTED + "\n";
            String second = "# second comment\nAutoCreateVoteSites: false # retain\nUnknown: second\nPassword: " + REDACTED + "\n";
            ConfigurationOperations.OperationView read = fixture.operations.createRead(List.of("backend-a", "backend-b"),
                    ManagedConfiguration.file("Config.yml", null));
            completeRead(fixture, read, "backend-a", first, revision('a'));
            read = completeRead(fixture, read, "backend-b", second, revision('b'));

            ConfigurationOperations.OperationView firstPreview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationOperations.OperationView secondPreview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-b", Map.of("AutoCreateVoteSites", true));
            ConfigurationTask firstTask = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            ConfigurationTask secondTask = fixture.operations.claim("backend-b", fixture.session("backend-b"));

            assertEquals("# first comment\nProcessRewards: false # retain\nUnknown: first\nToken: " + REDACTED + "\n",
                    firstTask.configuration().content());
            assertEquals("# second comment\nAutoCreateVoteSites: true # retain\nUnknown: second\nPassword: "
                    + REDACTED + "\n", secondTask.configuration().content());
            assertFalse(firstTask.configuration().content().contains("second"));
            assertFalse(secondTask.configuration().content().contains("first"));
            complete(fixture, firstPreview.operationId(), "backend-a", firstTask, revision('c'));
            complete(fixture, secondPreview.operationId(), "backend-b", secondTask, revision('d'));
        }
    }

    @Test void rejectsUnknownNonBooleanMissingAndWrongTypeEdits() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a",
                    "ProcessRewards: true\nExtraAllSitesCheck: 'false'\n", revision('a'));
            assertValidation(() -> fixture.operations.createGeneralSettingsPreview(read.operationId(), "backend-a",
                    Map.<String, Object>of("UnknownSetting", true)));
            assertValidation(() -> fixture.operations.createGeneralSettingsPreview(read.operationId(), "backend-a",
                    Map.<String, Object>of("ProcessRewards", "false")));
            assertValidation(() -> fixture.operations.createGeneralSettingsPreview(read.operationId(), "backend-a",
                    Map.<String, Object>of("DisableUpdateChecking", false)));
            assertValidation(() -> fixture.operations.createGeneralSettingsPreview(read.operationId(), "backend-a",
                    Map.<String, Object>of("ExtraAllSitesCheck", false)));
        }
    }

    @Test void rejectsUnsupportedCapabilityProxyTargetAndReconnectedReadTarget() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            fixture.registry.heartbeat("backend-a", new Heartbeat(fixture.session("backend-a"), 1, Set.of(), Set.of()));
            assertEquals("NODE_UNAVAILABLE", assertThrows(ValidationException.class,
                    () -> fixture.operations.generalSettingsState(read.operationId(), "backend-a")).code());
        }
        try (Fixture fixture = fixture("proxy-a")) {
            assertEquals("INVALID_TARGET", assertThrows(ValidationException.class,
                    () -> fixture.operations.createRead(List.of("proxy-a"), ManagedConfiguration.file("Config.yml", null))).code());
        }
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            UUID replacement = UUID.randomUUID();
            fixture.registry.register(new NodeRegistration("backend-a", replacement, "backend-a", "BUKKIT", "test", 1,
                    Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
            assertEquals("TARGET_CHANGED", assertThrows(ValidationException.class,
                    () -> fixture.operations.generalSettingsState(read.operationId(), "backend-a")).code());
        }
    }

    @Test void stalePreviewRevisionCannotBeApprovedForApply() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            ConfigurationOperations.OperationView preview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationTask task = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            assertEquals(revision('a'), task.expectedRevision());
            preview = complete(fixture, preview.operationId(), "backend-a", task, revision('b'));

            assertEquals("STALE_REVISION", preview.results().get("backend-a").code());
            assertNull(preview.approvalToken());
            UUID stalePreviewId = preview.operationId();
            assertEquals("PREVIEW_INCOMPLETE", assertThrows(ValidationException.class,
                    () -> fixture.operations.createApply(stalePreviewId, "unused")).code());
        }
    }

    @Test void boundPreviewCreatesExactSingleUseRevisionBoundApply() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a",
                    "# retain\nProcessRewards: true\nUnknown: value\n", revision('a'));
            ConfigurationOperations.OperationView preview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationTask previewTask = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            String proposal = "# retain\nProcessRewards: false\nUnknown: value\n";
            assertEquals(proposal, previewTask.configuration().content());
            preview = complete(fixture, preview.operationId(), "backend-a", previewTask, revision('a'));
            assertNotNull(preview.approvalToken());
            UUID approvedPreviewId = preview.operationId();
            String approvalToken = preview.approvalToken();

            ConfigurationOperations.OperationView apply = fixture.operations.createApply(approvedPreviewId, approvalToken);
            ConfigurationTask applyTask = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            assertEquals("APPLY", applyTask.type());
            assertEquals(revision('a'), applyTask.expectedRevision());
            assertEquals(proposal, applyTask.configuration().content());
            assertEquals("APPROVAL_REQUIRED", assertThrows(ValidationException.class,
                    () -> fixture.operations.createApply(approvedPreviewId, approvalToken)).code());
            apply = complete(fixture, apply.operationId(), "backend-a", applyTask, revision('c'));
            assertEquals("SUCCEEDED", apply.state());
            assertNull(apply.results().get("backend-a").configuration().content());
        }
    }

    @Test void pendingBoundPreviewIsNotEvictedByFileRetention() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            ConfigurationOperations.OperationView bound = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationTask boundTask = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            bound = complete(fixture, bound.operationId(), "backend-a", boundTask, revision('a'));
            UUID protectedId = bound.operationId();

            for (int index = 0; index < 16; index++) {
                ConfigurationOperations.OperationView extra = fixture.operations.createPreview(List.of("backend-a"),
                        ManagedConfiguration.file("Config.yml", "Other: " + index + "\n"));
                ConfigurationTask task = fixture.operations.claim("backend-a", fixture.session("backend-a"));
                complete(fixture, extra.operationId(), "backend-a", task, revision((char) ('a' + index % 6)));
            }

            ConfigurationOperations.OperationView retained = fixture.operations.get(protectedId);
            assertEquals("SUCCEEDED", retained.state());
            assertNotNull(retained.approvalToken());
        }
    }

    @Test void abandonedVisualApprovalIsRevokedWithoutSchedulingAWriteAndCanBeEvicted() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            ConfigurationOperations.OperationView preview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationTask task = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            preview = complete(fixture, preview.operationId(), "backend-a", task, revision('a'));
            UUID id = preview.operationId(); String token = preview.approvalToken();
            assertEquals("APPROVAL_REQUIRED", assertThrows(ValidationException.class,
                    () -> fixture.operations.discardGeneralSettingsPreview(id, "wrong-token")).code());
            assertNotNull(fixture.operations.get(id).approvalToken());
            assertNull(fixture.operations.discardGeneralSettingsPreview(id, token).approvalToken());
            assertNull(fixture.operations.claim("backend-a", fixture.session("backend-a")));
            assertEquals("APPROVAL_REQUIRED", assertThrows(ValidationException.class,
                    () -> fixture.operations.createApply(id, token)).code());
            for (int index = 0; index < 17; index++) read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            assertEquals("OPERATION_NOT_FOUND", assertThrows(ValidationException.class,
                    () -> fixture.operations.get(id)).code());
        }
    }

    @Test void staleVisualPreviewCannotBeRetriedWithAnOldDocumentAtANewRevision() throws Exception {
        try (Fixture fixture = fixture("backend-a")) {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a", "ProcessRewards: true\n", revision('a'));
            ConfigurationOperations.OperationView preview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationTask task = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            complete(fixture, preview.operationId(), "backend-a", task, revision('b'));
            UUID id = preview.operationId();
            assertEquals("PREVIEW_REQUIRED", assertThrows(ValidationException.class,
                    () -> fixture.operations.retry(id)).code());
        }
    }

    @Test void journalAndAuditNeverPersistReadOrProposalSecrets() throws Exception {
        Path journalDirectory = directory.resolve("journal");
        Path auditDirectory = directory.resolve("audit");
        String secret = "DO_NOT_PERSIST_GENERAL_SETTINGS_SECRET";
        Fixture fixture = fixture(journalDirectory, auditDirectory, "backend-a");
        try {
            ConfigurationOperations.OperationView read = read(fixture, "backend-a",
                    "ProcessRewards: true\nUnknownSecret: " + secret + "\n", revision('a'));
            ConfigurationOperations.OperationView preview = fixture.operations.createGeneralSettingsPreview(
                    read.operationId(), "backend-a", Map.of("ProcessRewards", false));
            ConfigurationTask task = fixture.operations.claim("backend-a", fixture.session("backend-a"));
            complete(fixture, preview.operationId(), "backend-a", task, revision('b'));
        } finally {
            fixture.close();
        }
        assertFalse(Files.readString(journalDirectory.resolve("configuration-operations.json")).contains(secret));
        assertFalse(Files.readString(auditDirectory.resolve("configuration-audit.jsonl")).contains(secret));
    }

    private ConfigurationOperations.OperationView read(Fixture fixture, String nodeId, String content, String revision) {
        ConfigurationOperations.OperationView read = fixture.operations.createRead(List.of(nodeId),
                ManagedConfiguration.file("Config.yml", null));
        return completeRead(fixture, read, nodeId, content, revision);
    }

    private ConfigurationOperations.OperationView completeRead(Fixture fixture, ConfigurationOperations.OperationView read,
            String nodeId, String content, String revision) {
        ConfigurationTask task = fixture.operations.claim(nodeId, fixture.session(nodeId));
        assertEquals("READ", task.type());
        assertNull(task.expectedRevision());
        return fixture.operations.complete(read.operationId(), nodeId, new ConfigurationTaskResult(fixture.session(nodeId),
                true, "OK", "read", revision, ManagedConfiguration.file("Config.yml", content), List.of(), false, false,
                task.attemptId()));
    }

    private ConfigurationOperations.OperationView completeFileRead(Fixture fixture, ConfigurationOperations.OperationView read,
            String nodeId, String content, String revision, String file) {
        ConfigurationTask task = fixture.operations.claim(nodeId, fixture.session(nodeId));
        assertEquals("READ", task.type());
        return fixture.operations.complete(read.operationId(), nodeId, new ConfigurationTaskResult(fixture.session(nodeId),
                true, "OK", "read", revision, ManagedConfiguration.file(file, content), List.of(), false, false,
                task.attemptId()));
    }

    private ConfigurationOperations.OperationView complete(Fixture fixture, UUID operationId, String nodeId,
            ConfigurationTask task, String revision) {
        return fixture.operations.complete(operationId, nodeId, new ConfigurationTaskResult(fixture.session(nodeId), true,
                "OK", "valid", revision, task.configuration(), List.of(), false, false, task.attemptId()));
    }

    private static void assertValidation(Runnable action) {
        assertEquals("VALIDATION_ERROR", assertThrows(ValidationException.class, action::run).code());
    }

    private Fixture fixture(String... nodeIds) throws Exception {
        return fixture(null, null, nodeIds);
    }

    private Fixture fixture(Path journalDirectory, Path auditDirectory, String... nodeIds) throws Exception {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(CLOCK, Duration.ofMinutes(2));
        for (String nodeId : nodeIds) {
            String platform = nodeId.startsWith("proxy") ? "VELOCITY" : "BUKKIT";
            registry.register(new NodeRegistration(nodeId, UUID.randomUUID(), nodeId, platform, "test", 1,
                    Set.of(ConfigurationOperations.FILE_CAPABILITY), Set.of()));
        }
        Path auditRoot = auditDirectory == null ? directory.resolve(UUID.randomUUID().toString()) : auditDirectory;
        ConfigurationAuditLog audit = new ConfigurationAuditLog(auditRoot, CLOCK);
        ConfigurationOperationJournal journal = journalDirectory == null ? null
                : new ConfigurationOperationJournal(journalDirectory, CLOCK);
        ConfigurationOperations operations = journal == null
                ? new ConfigurationOperations(registry, audit, CLOCK)
                : new ConfigurationOperations(registry, audit, CLOCK, journal);
        return new Fixture(registry, operations);
    }

    private static String revision(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static final class Fixture implements AutoCloseable {
        private final InMemoryNodeRegistry registry;
        private final ConfigurationOperations operations;

        private Fixture(InMemoryNodeRegistry registry, ConfigurationOperations operations) {
            this.registry = registry;
            this.operations = operations;
        }

        private UUID session(String nodeId) {
            return registry.find(nodeId).sessionId();
        }

        @Override public void close() throws Exception {
            operations.close();
        }
    }
}
