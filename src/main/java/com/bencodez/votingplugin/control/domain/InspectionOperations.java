package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.InspectionQuery;
import com.bencodez.votingplugin.control.protocol.InspectionTask;
import com.bencodez.votingplugin.control.protocol.InspectionTaskResult;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Short-lived coordinator for typed read-only node inspections. */
public final class InspectionOperations {
    public static final int MAX_DATA_BYTES = 512 * 1024;
    private static final int MAX_INSPECTIONS = 100;
    private static final int MAX_MESSAGE_BYTES = 4096;
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
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
        return view(stored);
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
        }
        if (jsonBytes(result.data()) > MAX_DATA_BYTES || bytes(result.message()) > MAX_MESSAGE_BYTES) {
            throw invalid("inspection result exceeds retention limits");
        }
    }

    private static int jsonBytes(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null ? 0 : value.toString().getBytes(StandardCharsets.UTF_8).length;
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

        private StoredInspection(UUID id, String nodeId, UUID targetSession, InspectionQuery query, Instant createdAt) {
            this.id = id;
            this.nodeId = nodeId;
            this.targetSession = targetSession;
            this.query = query;
            this.createdAt = createdAt;
        }
    }
}
