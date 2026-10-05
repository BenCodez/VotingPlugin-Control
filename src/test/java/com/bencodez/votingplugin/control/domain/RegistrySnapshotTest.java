package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RegistrySnapshotTest {
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void samePageIgnoresHeartbeatTimesAndOrdering() {
        NodeStatus first = node(SESSION, Set.of("b", "a"), List.of(new BackendServerIdentity("b", "B", true, true, 2)));
        NodeStatus second = new NodeStatus(first.nodeId(), first.sessionId(), first.displayName(), first.platform(),
                first.pluginVersion(), first.protocolVersion(), first.advertisedCapabilities(), first.acceptedCapabilities(),
                first.detectedPlugins(), List.of(new BackendServerIdentity("b", "B", false, false, 99)),
                first.snapshotSequence() + 1, Instant.ofEpochSecond(99),
                Instant.ofEpochSecond(100), first.online());
        assertEquals(RegistrySnapshot.sha256(page(first)), RegistrySnapshot.sha256(page(second)));
    }

    @Test void identityCapabilitiesAndTopologyChangesInvalidateEvenWithSameRevision() {
        NodeStatus base = node(SESSION, Set.of("a"), List.of(new BackendServerIdentity("b", "B", true, true, 1)));
        String fingerprint = RegistrySnapshot.sha256(page(base));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(new NodeRegistry.RegistryPage(List.of(base), 8, 1)));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(new NodeRegistry.RegistryPage(List.of(base), 7, 2)));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(page(node(SESSION, Set.of("a"),
                List.of(new BackendServerIdentity("new-backend", "B", true, true, 1))))));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(page(node(UUID.randomUUID(), Set.of("a"), base.backends()))));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(page(node(SESSION, Set.of("changed"), base.backends()))));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(page(node(SESSION, Set.of("a"),
                List.of(new BackendServerIdentity("b", "Renamed", true, true, 1))))));
        assertNotEquals(fingerprint, RegistrySnapshot.sha256(page(node("other", SESSION, Set.of("a"), base.backends()))));
    }

    private static NodeRegistry.RegistryPage page(NodeStatus node) {
        return new NodeRegistry.RegistryPage(List.of(node), 7, 1);
    }

    private static NodeStatus node(UUID session, Set<String> capabilities, List<BackendServerIdentity> backends) {
        return node("node", session, capabilities, backends);
    }

    private static NodeStatus node(String id, UUID session, Set<String> capabilities, List<BackendServerIdentity> backends) {
        return new NodeStatus(id, session, "Node", "BUKKIT", "test", 1, capabilities, capabilities,
                Set.of(), backends, 4, Instant.EPOCH, Instant.EPOCH, true);
    }
}
