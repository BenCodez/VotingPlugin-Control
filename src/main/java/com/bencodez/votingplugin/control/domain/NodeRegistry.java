package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
import java.util.List;

public interface NodeRegistry {
    RegistrationResult register(NodeRegistration registration);
    NodeStatus heartbeat(String nodeId, Heartbeat heartbeat);
    SnapshotResult replacePresence(String nodeId, PresenceSnapshot snapshot);
    List<NodeStatus> list(int offset, int limit);

    record RegistrationResult(NodeStatus node, boolean created) { }
    record SnapshotResult(NodeStatus node, boolean applied) { }
}
