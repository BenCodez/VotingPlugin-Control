package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/** Stable fingerprint of the bounded public registry page used by a read-only report. */
public final class RegistrySnapshot {
    private RegistrySnapshot() { }

    public static String sha256(NodeRegistry.RegistryPage page) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            field(digest, Long.toString(page.revision()));
            field(digest, Integer.toString(page.total()));
            field(digest, Integer.toString(page.items().size()));
            page.items().stream().sorted(Comparator.comparing(NodeStatus::nodeId)).forEach(node -> {
                field(digest, node.nodeId());
                field(digest, node.sessionId() == null ? "" : node.sessionId().toString());
                field(digest, node.platform());
                field(digest, Boolean.toString(node.online()));
                field(digest, Integer.toString(node.acceptedCapabilities().size()));
                node.acceptedCapabilities().stream().sorted().forEach(capability -> field(digest, capability));
                var backends = node.backends().stream().sorted(Comparator.comparing(BackendServerIdentity::backendId)
                        .thenComparing(BackendServerIdentity::displayName)).toList();
                field(digest, Integer.toString(backends.size()));
                backends.forEach(backend -> {
                    field(digest, backend.backendId());
                    field(digest, backend.displayName());
                });
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void field(MessageDigest digest, String value) {
        String safe = value == null ? "" : value;
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        byte[] length = Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII);
        digest.update(length);
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }
}
