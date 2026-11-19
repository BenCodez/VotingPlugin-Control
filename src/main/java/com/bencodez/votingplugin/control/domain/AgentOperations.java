package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AgentOperations {
    private static final Set<String> ACTIONS = Set.of("discover", "read", "preview", "status");
    private final Map<UUID, Grant> grants = new HashMap<>();
    private final NodeRegistry registry;
    private final ConfigurationOperations operations;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AgentOperations(NodeRegistry registry, ConfigurationOperations operations, Clock clock) {
        this.registry = registry;
        this.operations = operations;
        this.clock = clock;
    }

    public synchronized Issued issue(Issue request) {
        grants.values().removeIf(grant -> !clock.instant().isBefore(grant.expiresAt()));
        if (request == null || request.nodeIds() == null || request.nodeIds().isEmpty()
                || request.nodeIds().size() > 50 || request.actions() == null || request.actions().isEmpty()
                || request.actions().size() > ACTIONS.size() || request.actions().stream().anyMatch(action -> action == null || !ACTIONS.contains(action)) || request.minutes() == null || request.minutes() < 1
                || request.minutes() > 1440 || grants.size() >= 64) throw invalid("Invalid agent grant or grant limit reached");
        request.nodeIds().forEach(AgentOperations::validateNodeId);
        UUID id = UUID.randomUUID();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = "vp-agent-" + id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expiry = clock.instant().plusSeconds(60L * request.minutes());
        operations.auditAgentGrant(id, "ISSUED");
        grants.put(id, new Grant(id, Set.copyOf(request.nodeIds()), Set.copyOf(request.actions()), expiry, digest(token)));
        return new Issued(id, token, expiry);
    }

    public synchronized List<GrantView> list() {
        return grants.values().stream().filter(grant -> clock.instant().isBefore(grant.expiresAt()))
                .map(grant -> new GrantView(grant.id(), grant.nodes(), grant.actions(), grant.expiresAt())).toList();
    }

    public synchronized void revoke(UUID id) {
        operations.auditAgentGrant(id, "REVOKED");
        grants.remove(id);
    }

    public synchronized void requireAccess(String token, String action) {
        authorize(token, action);
    }

    public synchronized Object discover(String token) {
        Grant grant = authorize(token, "discover");
        return Map.of("items", grant.nodes().stream().sorted().map(registry::find)
                .filter(java.util.Objects::nonNull)
                .map(node -> Map.of("nodeId", node.nodeId(), "platform", node.platform(), "online", node.online(),
                        "acceptedCapabilities", node.acceptedCapabilities())).toList());
    }

    public synchronized Object read(String token, Read request) {
        Grant grant = authorize(token, "read");
        requireNode(grant, request.nodeId());
        siteName(request.name());
        return project(operations.createAgentVoteSite(grant.id(), request.nodeId(),
                configuration(Map.of("name", request.name())), null));
    }

    public synchronized Object preview(String token, Preview request) {
        Grant grant = authorize(token, "preview");
        requireNode(grant, request.nodeId());
        if (request.readOperationId() == null || request.options() == null) throw invalid("Read and options are required");
        Site site = request.options();
        siteName(site.name());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", site.name());
        values.put("serviceSite", bounded(site.serviceSite(), 200));
        if (!AgentSetup.safeUrl(site.voteUrl())) throw invalid("Invalid vote URL");
        values.put("voteUrl", site.voteUrl());
        put(values, "displayName", site.displayName());
        put(values, "voteDelay", site.voteDelay());
        put(values, "material", site.material());
        if (site.enabled() != null) values.put("enabled", site.enabled().toString());
        if (site.hidden() != null) values.put("hidden", site.hidden().toString());
        if (site.priority() != null) {
            if (site.priority() < 0 || site.priority() > 100) throw invalid("Invalid priority");
            values.put("priority", site.priority().toString());
        }
        return project(operations.createAgentVoteSite(grant.id(), request.nodeId(), configuration(values),
                parseId(request.readOperationId())));
    }

    public synchronized Object status(String token, UUID id) {
        Grant grant = authorize(token, "status");
        var operation = operations.getAgentOperation(grant.id(), id);
        var projected = project(operation);
        if ("READ".equals(operation.type()) && "SUCCEEDED".equals(operation.state())) {
            projected.put("values", operations.agentReadValues(grant.id(), id));
        }
        if ("PREVIEW".equals(operation.type())) {
            UUID applyId = operations.agentApplyId(grant.id(), id);
            if (applyId != null) projected.put("applyOperationId", applyId);
        }
        return projected;
    }

    public synchronized ConfigurationOperations.OperationView approve(UUID id) {
        var preview = operations.get(id);
        Grant grant = grants.get(preview.agentId());
        if (grant == null || !clock.instant().isBefore(grant.expiresAt())) throw invalid("Agent grant is unavailable");
        return operations.approveAgentPreview(id);
    }

    private static Map<String, Object> project(ConfigurationOperations.OperationView operation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", operation.operationId());
        result.put("type", operation.type());
        result.put("state", operation.state());
        result.put("nodes", operation.nodeStates());
        result.put("browserReviewReady", operation.browserReviewReady());
        if ("PREVIEW".equals(operation.type())) result.put("reviewPath", "/#agent-review/" + operation.operationId());
        Map<String, Object> results = new LinkedHashMap<>();
        operation.results().forEach((node, value) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", value.code());
            item.put("success", value.success());
            item.put("rolledBack", value.rolledBack());
            item.put("reloaded", value.reloaded());
            results.put(node, item);
        });
        result.put("results", results);
        return result;
    }

    private Grant authorize(String token, String action) {
        if (token == null || !token.matches("vp-agent-[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")) return denied();
        UUID id;
        try {
            id = UUID.fromString(token.substring(9, 45));
        } catch (IllegalArgumentException e) {
            return denied();
        }
        Grant grant = grants.get(id);
        if (grant == null || !clock.instant().isBefore(grant.expiresAt())
                || !MessageDigest.isEqual(grant.verifier(), digest(token)) || !grant.actions().contains(action)) {
            return denied();
        }
        return grant;
    }

    private static Grant denied() {
        throw new SecurityException("Agent access denied");
    }

    private static void requireNode(Grant grant, String id) {
        validateNodeId(id);
        if (!grant.nodes().contains(id)) denied();
    }

    public static UUID parseId(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw invalid("Invalid operation ID");
        }
        return UUID.fromString(id);
    }

    private static void validateNodeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw invalid("Invalid node ID");
    }

    private static void siteName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) throw invalid("Invalid site name");
    }

    private static String bounded(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) throw invalid("Invalid site option");
        return value;
    }

    private static void put(Map<String, String> values, String field, String value) {
        if (value != null) values.put(field, bounded(value, 200));
    }

    private static ManagedConfiguration configuration(Map<String, String> options) {
        return new ManagedConfiguration("quick-setup", null, List.of(), null, null, "vote-site", options);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Issue(List<String> nodeIds, List<String> actions, Integer minutes) { }
    public record Issued(UUID id, String token, Instant expiresAt) { }
    public record GrantView(UUID id, Set<String> nodeIds, Set<String> actions, Instant expiresAt) { }
    public record Read(String nodeId, String name) { }
    public record Preview(String nodeId, String readOperationId, Site options) { }
    public record Site(String name, String serviceSite, String voteUrl, Boolean enabled, String displayName,
                       Integer priority, Boolean hidden, String voteDelay, String material) { }
    private record Grant(UUID id, Set<String> nodes, Set<String> actions, Instant expiresAt, byte[] verifier) { }
}
