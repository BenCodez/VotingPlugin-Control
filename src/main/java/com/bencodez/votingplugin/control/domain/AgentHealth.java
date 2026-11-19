package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AgentHealth {
    private static final Duration TOPOLOGY_MAX_AGE = Duration.ofMinutes(5);
    private static final int MAX_CHECKS = 75;
    private static final int MAX_LINKS = 6;

    private AgentHealth() { }

    static Node topologyRow(NodeRegistry registry, NodeStatus node, Set<String> allowed, Instant now) {
        Instant observedAt = registry.topologyObservedAt(node.nodeId());
        boolean current = fresh(node, observedAt, now);
        var links = current ? node.backends().stream().map(BackendServerIdentity::backendId)
                .filter(allowed::contains).limit(MAX_LINKS + 1L).toList() : List.<String>of();
        return new Node(node.nodeId(), node.platform(), node.online(), observedAt == null ? null : observedAt.toString(),
                current, links.stream().limit(MAX_LINKS).toList(), links.size() > MAX_LINKS);
    }

    static Report report(NodeRegistry registry, InspectionOperations inspections, List<String> requested, Instant now) {
        Set<String> included = new LinkedHashSet<>(requested);
        var nodes = requested.stream().map(registry::find).filter(java.util.Objects::nonNull).toList();
        boolean incomplete = nodes.size() != requested.size() || nodes.stream().anyMatch(node ->
                !"BUKKIT".equals(node.platform()) && (!fresh(node, registry.topologyObservedAt(node.nodeId()), now)
                        || node.backends().stream().anyMatch(backend -> !included.contains(backend.backendId()))));
        var projected = nodes.stream().map(node -> new NodeStatus(node.nodeId(), node.sessionId(), node.displayName(),
                node.platform(), node.pluginVersion(), node.protocolVersion(), node.advertisedCapabilities(),
                node.acceptedCapabilities(), node.detectedPlugins(),
                fresh(node, registry.topologyObservedAt(node.nodeId()), now) ? node.backends().stream()
                        .filter(backend -> included.contains(backend.backendId())).toList() : List.of(),
                node.snapshotSequence(), node.lastSeen(), node.lastAuthenticatedUpdate(), node.online())).toList();
        var evidence = inspections.networkEvidence(projected.stream()
                .filter(node -> "BUKKIT".equals(node.platform())
                        || fresh(node, registry.topologyObservedAt(node.nodeId()), now)).toList());
        var evaluation = NetworkDoctor.evaluate(projected, evidence, now, incomplete);
        var checks = evaluation.checks().stream().filter(check -> included.containsAll(check.affectedNodes()))
                .limit(MAX_CHECKS).map(check -> new Check(check.id(), check.status().name(), check.affectedNodes().stream().limit(3).toList(),
                        check.restartRequired())).toList();
        return new Report(now.toString(), evaluation.truncated() || evaluation.checks().size() > checks.size()
                || evaluation.checks().stream().anyMatch(check -> check.affectedNodes().size() > 3), checks);
    }

    static boolean fresh(NodeStatus node, Instant observed, Instant now) {
        return node.online() && node.acceptedCapabilities().contains("presence.snapshot") && observed != null
                && !observed.isAfter(now) && observed.isAfter(now.minus(TOPOLOGY_MAX_AGE));
    }

    public record Node(String nodeId, String platform, boolean online, String topologyObservedAt,
                       boolean topologyFresh, List<String> backendIds, boolean linksTruncated) { }
    public record Topology(List<Node> items, Integer nextOffset) { }
    public record Check(String id, String status, List<String> affectedNodes, boolean restartRequired) { }
    public record Report(String generatedAt, boolean truncated, List<Check> checks) { }
}
