package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import java.util.HashSet;
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
import java.util.function.Supplier;

public final class AgentOperations {
    private static final Set<String> ACTIONS = Set.of("discover", "read", "preview", "status", "health");
    private final Map<UUID, Grant> grants = new HashMap<>();
    private final Map<UUID, Instant> healthRefreshes = new HashMap<>();
    private final NodeRegistry registry;
    private final ConfigurationOperations operations;
    private final Clock clock;
    private final InspectionOperations inspections;
    private final SecureRandom random = new SecureRandom();

    public AgentOperations(NodeRegistry registry, ConfigurationOperations operations, Clock clock) {
        this(registry, operations, new InspectionOperations(registry, clock), clock);
    }

    public AgentOperations(NodeRegistry registry, ConfigurationOperations operations,
                           InspectionOperations inspections, Clock clock) {
        this.registry = registry;
        this.operations = operations;
        this.inspections = inspections;
        this.clock = clock;
    }

    public synchronized Issued issue(Issue request) {
        grants.values().removeIf(grant -> !clock.instant().isBefore(grant.expiresAt()));
        healthRefreshes.keySet().retainAll(grants.keySet());
        if (request == null || request.actions() == null || request.actions().isEmpty()
                || request.actions().size() > ACTIONS.size()
                || request.actions().stream().anyMatch(action -> action == null || !ACTIONS.contains(action))
                || request.minutes() == null || request.minutes() < 1 || request.minutes() > 1440
                || grants.size() >= 64) throw invalid("Invalid agent grant or grant limit reached");
        NodeStatus root = null;
        Set<String> nodes;
        if (request.proxyNodeId() != null) {
            if (request.nodeIds() != null && !request.nodeIds().isEmpty()) throw invalid("Choose one scope");
            validateNodeId(request.proxyNodeId());
            root = registry.find(request.proxyNodeId());
            if (!onlineProxy(root) || root.snapshotSequence() < 0) {
                throw invalid("An online enrolled proxy with a topology snapshot is required");
            }
            nodes = new HashSet<>();
            nodes.add(root.nodeId());
            for (var backend : root.backends()) {
                NodeStatus member = registry.find(backend.backendId());
                if (member != null && "BUKKIT".equals(member.platform())) nodes.add(member.nodeId());
            }
        } else {
            if (request.nodeIds() == null || request.nodeIds().isEmpty() || request.nodeIds().size() > 50) {
                throw invalid("Node scope requires 1 to 50 exact node IDs");
            }
            request.nodeIds().forEach(AgentOperations::validateNodeId);
            nodes = Set.copyOf(request.nodeIds());
        }
        UUID id = UUID.randomUUID();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = "vp-agent-" + id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expiry = clock.instant().plusSeconds(60L * request.minutes());
        operations.auditAgentGrant(id, root == null ? "ISSUED" : "NETWORK_ISSUED");
        grants.put(id, new Grant(id, nodes.stream().sorted().toList(), Set.copyOf(request.actions()),
                expiry, digest(token), root == null ? null : root.nodeId(), root == null ? null : root.sessionId()));
        return new Issued(id, token, expiry);
    }

    public synchronized List<GrantView> list() {
        return grants.values().stream().filter(grant -> clock.instant().isBefore(grant.expiresAt()))
                .map(grant -> new GrantView(grant.id(), grant.proxyNodeId() == null ? grant.nodes() : List.of(),
                        grant.actions(), grant.expiresAt(), grant.proxyNodeId(), grant.nodes().size())).toList();
    }

    public synchronized void revoke(UUID id) {
        operations.auditAgentGrant(id, "REVOKED");
        grants.remove(id);
        healthRefreshes.remove(id);
    }

    public synchronized void requireAccess(String token, String action) {
        authorize(token, action);
    }

    public synchronized Object discover(String token, Discovery request) {
        Grant grant = authorize(token, "discover");
        int offset = request.offset() == null ? 0 : request.offset();
        if (offset < 0 || offset > grant.nodes().size()) throw invalid("Invalid discovery offset");
        int end = Math.min(offset + 50, grant.nodes().size());
        return withStableScope(grant, () -> {
            Set<String> permitted = permittedNodes(grant);
            if (permitted.isEmpty()) return Map.of("items", List.of());
            var results = new LinkedHashMap<String, Object>();
            results.put("items", grant.nodes().subList(offset, end).stream().filter(permitted::contains)
                    .map(registry::find).filter(java.util.Objects::nonNull)
                    .map(node -> Map.of("nodeId", node.nodeId(), "platform", node.platform(), "online", node.online(),
                            "acceptedCapabilities", node.acceptedCapabilities())).toList());
            if (end < grant.nodes().size()) results.put("nextOffset", end);
            return results;
        });
    }

    public synchronized AgentHealth.Topology topology(String token, Discovery request) {
        Grant grant = authorize(token, "health");
        return registry.withStableTopology(() -> {
            List<String> scope = grant.nodes();
            int offset = request.offset() == null ? 0 : request.offset();
            if (offset < 0 || offset > scope.size()) throw invalid("Invalid topology offset");
            int end = Math.min(offset + 50, scope.size());
            Set<String> allowed = permittedNodes(grant);
            var rows = scope.subList(offset, end).stream().filter(allowed::contains).map(registry::find)
                    .filter(java.util.Objects::nonNull).map(node -> AgentHealth.topologyRow(
                            registry, node, allowed, clock.instant())).toList();
            return new AgentHealth.Topology(rows, !allowed.isEmpty() && end < scope.size() ? end : null);
        });
    }

    public synchronized AgentHealth.Report health(String token, HealthRequest request) {
        Grant grant = authorize(token, "health");
        List<String> requested = request.nodeIds();
        if (requested == null || requested.isEmpty() || requested.size() > 25
                || new HashSet<>(requested).size() != requested.size()) {
            throw invalid("Health requires 1 to 25 distinct target nodes");
        }
        requested.forEach(AgentOperations::validateNodeId);
        synchronized (inspections) {
            return registry.withStableTopology(() -> {
                Set<String> allowed = permittedNodes(grant);
                if (!allowed.containsAll(requested)) denied();
                return AgentHealth.report(registry, inspections, requested, clock.instant());
            });
        }
    }

    public synchronized Map<String, Object> refreshHealth(String token, RefreshHealth request) {
        Grant grant = authorize(token, "health");
        synchronized (inspections) {
            return registry.withStableTopology(() -> {
                requireNode(grant, request.nodeId());
                Instant now = clock.instant();
                Instant last = healthRefreshes.get(grant.id());
                if (last != null && now.isBefore(last.plusSeconds(2))) throw invalid("Wait before refreshing another node");
                var observation = inspections.refreshAgentHealth(request.nodeId());
                healthRefreshes.put(grant.id(), now);
                return Map.of("nodeId", request.nodeId(), "state", observation.state());
            });
        }
    }

    public synchronized Object read(String token, Read request) {
        Grant grant = authorize(token, "read");
        siteName(request.name());
        return withStableScope(grant, () -> {
            requireNode(grant, request.nodeId());
            return project(operations.createAgentVoteSite(grant.id(), request.nodeId(),
                    configuration(Map.of("name", request.name())), null));
        });
    }

    public synchronized Object preview(String token, Preview request) {
        Grant grant = authorize(token, "preview");
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
        return withStableScope(grant, () -> {
            requireNode(grant, request.nodeId());
            return project(operations.createAgentVoteSite(grant.id(), request.nodeId(), configuration(values),
                    parseId(request.readOperationId())));
        });
    }

    public synchronized Object status(String token, UUID id) {
        Grant grant = authorize(token, "status");
        return withStableScope(grant, () -> {
            var operation = operations.getAgentOperation(grant.id(), id);
            operation.nodeStates().keySet().forEach(nodeId -> requireNode(grant, nodeId));
            var projected = project(operation);
            if ("READ".equals(operation.type()) && "SUCCEEDED".equals(operation.state())) {
                projected.put("values", operations.agentReadValues(grant.id(), id));
            }
            if ("PREVIEW".equals(operation.type())) {
                UUID applyId = operations.agentApplyId(grant.id(), id);
                if (applyId != null) projected.put("applyOperationId", applyId);
            }
            return projected;
        });
    }

    public synchronized ConfigurationOperations.OperationView approve(UUID id) {
        var preview = operations.get(id);
        Grant grant = grants.get(preview.agentId());
        if (grant == null || !clock.instant().isBefore(grant.expiresAt())) throw invalid("Agent grant is unavailable");
        return withStableScope(grant, () -> {
            if (!permittedNodes(grant).containsAll(preview.nodeStates().keySet())) {
                throw invalid("Preview target is outside the current grant scope");
            }
            return operations.approveAgentPreview(id);
        });
    }

    private <T> T withStableScope(Grant grant, Supplier<T> action) {
        if (grant.proxyNodeId() == null) return action.get();
        // Configuration claims and completions already acquire the operations
        // monitor before entering the registry. Preserve that order.
        synchronized (operations) {
            return registry.withStableTopology(action);
        }
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

    private void requireNode(Grant grant, String id) {
        validateNodeId(id);
        if (!permittedNodes(grant).contains(id)) denied();
    }

    private Set<String> permittedNodes(Grant grant) {
        if (grant.proxyNodeId() == null) return Set.copyOf(grant.nodes());
        NodeStatus proxy = registry.find(grant.proxyNodeId());
        if (!onlineProxy(proxy) || !grant.proxySession().equals(proxy.sessionId())) return Set.of();
        Set<String> current = new HashSet<>();
        current.add(proxy.nodeId());
        for (var backend : proxy.backends()) {
            NodeStatus member = registry.find(backend.backendId());
            if (member != null && "BUKKIT".equals(member.platform())) current.add(member.nodeId());
        }
        current.retainAll(grant.nodes());
        return current;
    }

    private static boolean onlineProxy(NodeStatus node) {
        return node != null && node.online() && Set.of("BUNGEECORD", "VELOCITY").contains(node.platform());
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

    public record Issue(List<String> nodeIds, List<String> actions, Integer minutes, String proxyNodeId) { }
    public record Discovery(Integer offset) { }
    public record HealthRequest(List<String> nodeIds) { }
    public record RefreshHealth(String nodeId) { }
    public record Issued(UUID id, String token, Instant expiresAt) { }
    public record GrantView(UUID id, List<String> nodeIds, Set<String> actions, Instant expiresAt, String proxyNodeId, int nodeCount) { }
    public record Read(String nodeId, String name) { }
    public record Preview(String nodeId, String readOperationId, Site options) { }
    public record Site(String name, String serviceSite, String voteUrl, Boolean enabled, String displayName,
                       Integer priority, Boolean hidden, String voteDelay, String material) { }
    private record Grant(UUID id, List<String> nodes, Set<String> actions, Instant expiresAt, byte[] verifier,
                         String proxyNodeId, UUID proxySession) { }
}
