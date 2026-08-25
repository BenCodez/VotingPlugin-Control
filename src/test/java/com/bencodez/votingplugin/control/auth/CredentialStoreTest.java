package com.bencodez.votingplugin.control.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CredentialStoreTest {
    @TempDir Path directory;

    @Test void enrollmentIsBoundRotatableRevocableAndStoredAsVerifier() throws Exception {
        CredentialStore store = new CredentialStore(directory);
        String first = store.rotateNode("proxy-a");
        assertTrue(store.verifyNode("proxy-a", first));
        assertFalse(store.verifyNode("proxy-b", first));
        assertFalse(store.verifyNode("missing", "vpctl_node_wrong"));
        String persisted = Files.readString(directory.resolve("credentials.json"));
        assertFalse(persisted.contains(first));
        assertFalse(persisted.contains("vpctl_node_"));

        String rotated = store.rotateNode("proxy-a");
        assertFalse(store.verifyNode("proxy-a", first));
        assertTrue(store.verifyNode("proxy-a", rotated));
        store.revokeNode("proxy-a");
        assertFalse(store.verifyNode("proxy-a", rotated));
    }

    @Test void adminCredentialRotatesAndRawSecretIsNeverPersisted() throws Exception {
        CredentialStore store = new CredentialStore(directory);
        assertFalse(store.hasAdmin());
        String token = store.rotateAdmin();
        assertTrue(store.hasAdmin());
        assertTrue(store.verifyAdmin(token));
        assertFalse(Files.readString(directory.resolve("credentials.json")).contains(token));
        String replacement = store.rotateAdmin();
        assertFalse(store.verifyAdmin(token));
        assertTrue(store.verifyAdmin(replacement));
    }
}
