package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.InspectionQuery;
import com.bencodez.votingplugin.control.protocol.InspectionTask;
import com.bencodez.votingplugin.control.protocol.InspectionTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Short-lived coordinator for typed read-only node inspections. */
public final class InspectionOperations {
    public static final int MAX_DATA_BYTES = 512 * 1024;
    private static final int MAX_INSPECTIONS = 100;
    private static final int MAX_MESSAGE_BYTES = 4096;
    private static final int MAX_PLAYER_ROWS = 100;
    private static final int MAX_PLAYER_COLUMN_VALUE_BYTES = 16 * 1024;
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern PLAYER_MONTH_TOTAL = Pattern.compile(
            "MonthTotal-(?:JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)-[0-9]{4}");
    private static final Pattern PLAYER_VOTE_SHOP_LIMIT = Pattern.compile("VoteShopLimit[A-Za-z0-9_-]{1,64}");
    private static final Set<String> PLAYER_STRING_COLUMNS = Set.of("UUID", "PlayerName", "LastOnline",
            "DayVoteStreakLastUpdate", "VoteRemindersLast");
    private static final Set<String> PLAYER_BOOLEAN_COLUMNS = Set.of("TopVoterIgnore", "Reminded",
            "DisableBroadcast", "CoolDownCheck");
    private static final Set<String> PLAYER_INTEGER_COLUMNS = Set.of("VotePartyVotes", "MonthTotal", "AllTimeTotal",
            "DailyTotal", "WeeklyTotal", "Points", "DayVoteStreak", "BestDayVoteStreak", "WeekVoteStreak",
            "BestWeekVoteStreak", "MonthVoteStreak", "BestMonthVoteStreak", "HighestDailyTotal",
            "HighestMonthlyTotal", "HighestWeeklyTotal", "LastMonthTotal", "LastWeeklyTotal", "LastDailyTotal",
            "AllSitesLast", "AlmostAllSitesLast");
    private static final Set<String> PLAYER_FOUND_FIELDS = Set.of("found", "uuid", "name", "lastOnline", "online",
            "totals", "points", "streaks", "lastVoteTime", "lastVotes", "lastVotesTruncated",
            "pendingOfflineVotes", "storageRowAvailable");
    private static final Set<String> PLAYER_OPTIONAL_STORAGE_FIELDS = Set.of("storage", "columns", "columnsTruncated");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration ACTIVE_RETENTION = Duration.ofMinutes(5);
    private static final Duration COMPLETE_RETENTION = Duration.ofMinutes(15);

    private final NodeRegistry registry;
    private final ConfigurationAuditLog audit;
    private final Clock clock;
    private final LinkedHashMap<UUID, StoredInspection> inspections = new LinkedHashMap<>();

    public InspectionOperations(NodeRegistry registry, ConfigurationAuditLog audit, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.audit = audit;
        this.clock = Objects.requireNonNull(clock);
    }

    public InspectionOperations(NodeRegistry registry, Clock clock) {
        this(registry, null, clock);
    }

    public synchronized InspectionView create(String nodeId, InspectionQuery query) {
        prune();
        if (query == null) throw invalid("inspection query is required");
        if (nodeId == null || !NODE_ID.matcher(nodeId).matches()) throw invalid("nodeId is invalid");
        NodeStatus node = registry.find(nodeId);
        if (node == null) throw new ValidationException("NODE_NOT_FOUND", "Node was not found", List.of(nodeId));
        if (!node.online() || !node.acceptedCapabilities().contains(InspectionQuery.CAPABILITY)) {
            throw new ValidationException("NODE_UNAVAILABLE", "Node cannot answer inspection queries", List.of(nodeId));
        }
        evictOldestCompletedAtCapacity();
        if (inspections.size() >= MAX_INSPECTIONS) {
            throw new ValidationException("OPERATION_LIMIT", "Too many retained inspections", List.of());
        }
        UUID id = UUID.randomUUID();
        StoredInspection stored = new StoredInspection(id, nodeId, node.sessionId(), query, clock.instant());
        inspections.put(id, stored);
        try {
            // Deliberately record only the query type. Player names and filter values are not audit metadata.
            append("INSPECTION_CREATED", id, nodeId, query.kind());
        } catch (RuntimeException failure) {
            inspections.remove(id);
            throw failure;
        }
        return view(stored);
    }

    public synchronized InspectionView get(UUID id) {
        prune();
        StoredInspection stored = inspections.get(id);
        if (stored == null) {
            throw new ValidationException("OPERATION_NOT_FOUND", "Inspection was not found", List.of());
        }
        InspectionView result = view(stored);
        if ("COMPLETE".equals(stored.state)) stored.terminalResultObserved = true;
        return result;
    }

    public synchronized InspectionTask claim(String nodeId, UUID sessionId) {
        return registry.withSession(nodeId, sessionId, node -> claimCurrentSession(nodeId, node));
    }

    private InspectionTask claimCurrentSession(String nodeId, NodeStatus node) {
        prune();
        Instant now = clock.instant();
        for (StoredInspection stored : inspections.values()) {
            if (!stored.nodeId.equals(nodeId) || "COMPLETE".equals(stored.state)) continue;
            if (!node.online() || !node.acceptedCapabilities().contains(InspectionQuery.CAPABILITY)) {
                completeUnavailable(stored, node.sessionId());
                continue;
            }
            if ("IN_PROGRESS".equals(stored.state) && stored.leasedAt != null
                    && now.isBefore(stored.leasedAt.plus(LEASE))) continue;
            String previousState = stored.state;
            Instant previousLease = stored.leasedAt;
            UUID previousAttempt = stored.attemptId;
            UUID previousTargetSession = stored.targetSession;
            UUID attempt = UUID.randomUUID();
            stored.state = "IN_PROGRESS";
            stored.leasedAt = now;
            stored.attemptId = attempt;
            stored.targetSession = node.sessionId();
            try {
                append("INSPECTION_CLAIMED", stored.id, nodeId, stored.query.kind());
            } catch (RuntimeException failure) {
                stored.state = previousState;
                stored.leasedAt = previousLease;
                stored.attemptId = previousAttempt;
                stored.targetSession = previousTargetSession;
                throw failure;
            }
            return new InspectionTask(stored.id, stored.query, attempt);
        }
        return null;
    }

    public synchronized InspectionView complete(UUID id, String nodeId, InspectionTaskResult result) {
        if (result == null) throw invalid("inspection result is required");
        return registry.withSession(nodeId, result.sessionId(), node -> completeCurrentSession(id, node, result));
    }

    private InspectionView completeCurrentSession(UUID id, NodeStatus node, InspectionTaskResult result) {
        prune();
        StoredInspection stored = inspections.get(id);
        if (stored == null || !stored.nodeId.equals(node.nodeId())) {
            throw new ValidationException("OPERATION_NOT_FOUND", "Inspection was not found", List.of());
        }
        if (!"IN_PROGRESS".equals(stored.state)) {
            if ("COMPLETE".equals(stored.state)) return view(stored);
            throw new ValidationException("TASK_NOT_CLAIMED", "Inspection was not claimed", List.of());
        }
        if (stored.leasedAt == null || !clock.instant().isBefore(stored.leasedAt.plus(LEASE))) {
            throw new ValidationException("TASK_LEASE_EXPIRED", "Inspection lease expired", List.of());
        }
        if (!Objects.equals(stored.targetSession, result.sessionId())) {
            throw new ValidationException("SESSION_MISMATCH",
                    "Inspection was claimed by another node session", List.of());
        }
        if (!Objects.equals(stored.attemptId, result.attemptId())) {
            throw new ValidationException("TASK_NOT_CLAIMED", "Inspection attempt does not match", List.of());
        }
        validateResult(result, stored.query.kind());
        InspectionTaskResult previousResult = stored.result;
        String previousState = stored.state;
        Instant previousLease = stored.leasedAt;
        UUID previousAttempt = stored.attemptId;
        stored.result = result;
        stored.state = "COMPLETE";
        stored.leasedAt = null;
        stored.attemptId = null;
        try {
            append("INSPECTION_COMPLETED", id, node.nodeId(), result.success() ? "SUCCESS" : safeCode(result.code()));
        } catch (RuntimeException failure) {
            stored.result = previousResult;
            stored.state = previousState;
            stored.leasedAt = previousLease;
            stored.attemptId = previousAttempt;
            throw failure;
        }
        return view(stored);
    }

    private void completeUnavailable(StoredInspection stored, UUID sessionId) {
        InspectionTaskResult previousResult = stored.result;
        String previousState = stored.state;
        Instant previousLease = stored.leasedAt;
        UUID previousAttempt = stored.attemptId;
        stored.result = new InspectionTaskResult(sessionId, false, "CAPABILITY_LOST",
                "Node no longer accepts inspection queries", null, null);
        stored.state = "COMPLETE";
        stored.leasedAt = null;
        stored.attemptId = null;
        try {
            append("INSPECTION_CANCELLED", stored.id, stored.nodeId, "CAPABILITY_LOST");
        } catch (RuntimeException failure) {
            stored.result = previousResult;
            stored.state = previousState;
            stored.leasedAt = previousLease;
            stored.attemptId = previousAttempt;
            throw failure;
        }
    }

    private static void validateResult(InspectionTaskResult result, String expectedKind) {
        if (result.message() == null || result.message().isBlank()) {
            throw invalid("inspection result message is required");
        }
        if (result.success() && (result.data() == null
                || result.code() != null && !"OK".equals(result.code()))) {
            throw invalid("successful inspection must contain data and an optional OK code");
        }
        if (!result.success() && (result.data() != null || result.code() == null
                || !result.code().matches("[A-Z][A-Z0-9_]{0,63}"))) {
            throw invalid("failed inspection result is invalid");
        }
        if (result.success() && (!result.data().isObject()
                || !result.data().path("schemaVersion").isIntegralNumber()
                || result.data().path("schemaVersion").intValue() != 1
                || !expectedKind.equals(result.data().path("kind").asText())
                || !result.data().path("generatedAt").isTextual()
                || !result.data().path("result").isObject())) {
            throw invalid("inspection data envelope is invalid");
        }
        if (result.success()) {
            try {
                Instant.parse(result.data().path("generatedAt").asText());
            } catch (java.time.format.DateTimeParseException failure) {
                throw invalid("inspection data generatedAt is invalid");
            }
            if ("player".equals(expectedKind) && !validPlayerResult(result.data().path("result"))) {
                throw invalid("player inspection data is invalid");
            }
        }
        if (jsonBytes(result.data()) > MAX_DATA_BYTES || bytes(result.message()) > MAX_MESSAGE_BYTES) {
            throw invalid("inspection result exceeds retention limits");
        }
    }

    private static int jsonBytes(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null ? 0 : value.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Player data is the only inspection result that includes values read from a storage row. The node is remote, so
     * enforce the same narrow column contract here before retaining a result for a browser GET.
     */
    private static boolean validPlayerResult(JsonNode value) {
        if (!value.isObject() || !value.path("found").isBoolean()) return false;
        if (!value.path("found").booleanValue()) {
            return exactFields(value, Set.of("found", "entity")) && value.path("entity").isTextual()
                    && "player".equals(value.path("entity").textValue());
        }
        if (!onlyFields(value, PLAYER_FOUND_FIELDS, PLAYER_OPTIONAL_STORAGE_FIELDS)
                || !hasFields(value, PLAYER_FOUND_FIELDS)
                || !canonicalUuid(value.path("uuid")) || !boundedText(value.path("name"), 16)
                || !PLAYER_NAME.matcher(value.path("name").textValue()).matches()
                || !nonNegativeLong(value.path("lastOnline")) || !value.path("online").isBoolean()
                || !validTotals(value.path("totals")) || !signedInt(value.path("points"))
                || !validStreaks(value.path("streaks")) || !nonNegativeLong(value.path("lastVoteTime"))
                || !validLastVotes(value.path("lastVotes")) || !value.path("lastVotesTruncated").isBoolean()
                || !nonNegativeInt(value.path("pendingOfflineVotes"))
                || value.path("pendingOfflineVotes").intValue() > 100_000
                || !value.path("storageRowAvailable").isBoolean()) {
            return false;
        }
        boolean storageAvailable = value.path("storageRowAvailable").booleanValue();
        if (!storageAvailable) {
            return !value.has("storage")
                    && (!value.has("columns") || value.path("columns").isArray() && value.path("columns").isEmpty())
                    && (!value.has("columnsTruncated") || !value.path("columnsTruncated").booleanValue());
        }
        return boundedText(value.path("storage"), 32) && !value.path("storage").textValue().isBlank()
                && validColumns(value.path("columns")) && value.path("columnsTruncated").isBoolean();
    }

    private static boolean validTotals(JsonNode value) {
        return exactFields(value, Set.of("daily", "weekly", "monthly", "allTime"))
                && nonNegativeInt(value.path("daily")) && nonNegativeInt(value.path("weekly"))
                && nonNegativeInt(value.path("monthly")) && nonNegativeInt(value.path("allTime"));
    }

    private static boolean validStreaks(JsonNode value) {
        return exactFields(value, Set.of("daily", "weekly", "monthly"))
                && nonNegativeInt(value.path("daily")) && nonNegativeInt(value.path("weekly"))
                && nonNegativeInt(value.path("monthly"));
    }

    private static boolean validLastVotes(JsonNode value) {
        if (!value.isArray() || value.size() > MAX_PLAYER_ROWS) return false;
        for (JsonNode row : value) {
            if (!exactFields(row, Set.of("siteKey", "displayName", "serviceSite", "time"))
                    || !boundedCharacters(row.path("siteKey"), 64) || !boundedCharacters(row.path("displayName"), 100)
                    || !boundedCharacters(row.path("serviceSite"), 64) || !nonNegativeLong(row.path("time"))) return false;
        }
        return true;
    }

    private static boolean validColumns(JsonNode value) {
        if (!value.isArray() || value.size() > MAX_PLAYER_ROWS) return false;
        Set<String> names = new HashSet<>();
        for (JsonNode column : value) {
            if (!exactFields(column, Set.of("name", "type", "value")) || !column.path("name").isTextual()
                    || !column.path("type").isTextual() || !column.path("value").isTextual()
                    || !boundedText(column.path("value"), MAX_PLAYER_COLUMN_VALUE_BYTES)
                    || !names.add(column.path("name").textValue())
                    || !validPlayerColumn(column.path("name").textValue(), column.path("type").textValue(),
                    column.path("value").textValue())) return false;
        }
        return true;
    }

    private static boolean validPlayerColumn(String name, String type, String rendered) {
        if (PLAYER_STRING_COLUMNS.contains(name) || runtimeStringColumn(name)) return "STRING".equals(type);
        if (PLAYER_BOOLEAN_COLUMNS.contains(name) || runtimeBooleanColumn(name)) {
            return "BOOLEAN".equals(type) && ("true".equals(rendered) || "false".equals(rendered))
                    || "STRING".equals(type) && rendered.matches("(?i:true|false)");
        }
        if (!(PLAYER_INTEGER_COLUMNS.contains(name) || runtimeIntegerColumn(name)
                || PLAYER_MONTH_TOTAL.matcher(name).matches() || PLAYER_VOTE_SHOP_LIMIT.matcher(name).matches())
                || !"INTEGER".equals(type) || !rendered.matches("-?(?:0|[1-9][0-9]*)")) return false;
        try {
            Integer.parseInt(rendered);
            return true;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    private static boolean runtimeStringColumn(String name) {
        if (!name.startsWith("CoolDownCheck") || !name.endsWith("_Sites")) return false;
        String middle = name.substring("CoolDownCheck".length(), name.length() - "_Sites".length());
        return middle.isEmpty() || middle.startsWith("_") && validRuntimeSuffix(middle.substring(1));
    }

    private static boolean runtimeBooleanColumn(String name) {
        if (!name.startsWith("CoolDownCheck")) return false;
        String suffix = name.substring("CoolDownCheck".length());
        return suffix.startsWith("_") && validRuntimeSuffix(suffix.substring(1));
    }

    private static boolean runtimeIntegerColumn(String name) {
        for (String prefix : List.of("AllSitesLast", "AlmostAllSitesLast")) {
            if (!name.startsWith(prefix)) continue;
            String suffix = name.substring(prefix.length());
            if (suffix.startsWith("_") && validRuntimeSuffix(suffix.substring(1))) return true;
        }
        return false;
    }

    private static boolean validRuntimeSuffix(String value) {
        return !value.isEmpty() && !hasControlCharacter(value);
    }

    private static boolean canonicalUuid(JsonNode value) {
        if (!boundedText(value, 36)) return false;
        try {
            return UUID.fromString(value.textValue()).toString().equals(value.textValue());
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static boolean boundedText(JsonNode value, int maximumBytes) {
        return value.isTextual() && bytes(value.textValue()) <= maximumBytes && !hasControlCharacter(value.textValue());
    }

    private static boolean boundedCharacters(JsonNode value, int maximumCharacters) {
        return value.isTextual() && value.textValue().length() <= maximumCharacters
                && !hasControlCharacter(value.textValue());
    }

    private static boolean hasControlCharacter(String value) {
        return value.codePoints().anyMatch(character -> character <= 0x1f || character >= 0x7f && character <= 0x9f);
    }

    private static boolean nonNegativeLong(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0;
    }

    private static boolean nonNegativeInt(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0;
    }

    private static boolean signedInt(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt();
    }

    private static boolean exactFields(JsonNode value, Set<String> expected) {
        return value.isObject() && value.size() == expected.size() && hasFields(value, expected)
                && onlyFields(value, expected, Set.of());
    }

    private static boolean hasFields(JsonNode value, Set<String> expected) {
        return expected.stream().allMatch(value::has);
    }

    private static boolean onlyFields(JsonNode value, Set<String> required, Set<String> optional) {
        Iterator<String> fields = value.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!required.contains(field) && !optional.contains(field)) return false;
        }
        return true;
    }

    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String safeCode(String value) {
        return value == null ? "FAILED" : value;
    }

    private InspectionView view(StoredInspection stored) {
        String state = "COMPLETE".equals(stored.state)
                ? stored.result != null && stored.result.success() ? "SUCCEEDED" : "FAILED" : "RUNNING";
        return new InspectionView(stored.id, stored.nodeId, stored.query, state, stored.createdAt, stored.result);
    }

    private void prune() {
        Instant activeCutoff = clock.instant().minus(ACTIVE_RETENTION);
        Instant completeCutoff = clock.instant().minus(COMPLETE_RETENTION);
        Iterator<Map.Entry<UUID, StoredInspection>> iterator = inspections.entrySet().iterator();
        while (iterator.hasNext()) {
            StoredInspection stored = iterator.next().getValue();
            Instant cutoff = "COMPLETE".equals(stored.state) ? completeCutoff : activeCutoff;
            boolean leased = stored.leasedAt != null && clock.instant().isBefore(stored.leasedAt.plus(LEASE));
            if (!leased && stored.createdAt.isBefore(cutoff)) {
                append("INSPECTION_EXPIRED", stored.id, stored.nodeId, stored.query.kind());
                iterator.remove();
            }
        }
    }

    private void evictOldestCompletedAtCapacity() {
        if (inspections.size() < MAX_INSPECTIONS) return;
        Iterator<Map.Entry<UUID, StoredInspection>> iterator = inspections.entrySet().iterator();
        while (iterator.hasNext()) {
            StoredInspection stored = iterator.next().getValue();
            if (!"COMPLETE".equals(stored.state) || !stored.terminalResultObserved) continue;
            append("INSPECTION_EVICTED", stored.id, stored.nodeId, stored.query.kind());
            iterator.remove();
            return;
        }
    }

    private void append(String action, UUID id, String nodeId, String outcome) {
        if (audit != null) audit.append(action, id, nodeId, outcome);
    }

    private static ValidationException invalid(String detail) {
        return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail));
    }

    public record InspectionView(UUID inspectionId, String nodeId, InspectionQuery query, String state,
                                 Instant createdAt, InspectionTaskResult result) { }

    private static final class StoredInspection {
        private final UUID id;
        private final String nodeId;
        private UUID targetSession;
        private final InspectionQuery query;
        private final Instant createdAt;
        private String state = "QUEUED";
        private Instant leasedAt;
        private UUID attemptId;
        private InspectionTaskResult result;
        private boolean terminalResultObserved;

        private StoredInspection(UUID id, String nodeId, UUID targetSession, InspectionQuery query, Instant createdAt) {
            this.id = id;
            this.nodeId = nodeId;
            this.targetSession = targetSession;
            this.query = query;
            this.createdAt = createdAt;
        }
    }
}
