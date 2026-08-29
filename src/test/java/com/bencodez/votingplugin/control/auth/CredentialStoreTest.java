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

    @Test void webPasswordUsesSaltedPbkdf2VerifierAndRotates() throws Exception {
        CredentialStore store = new CredentialStore(directory);
        assertFalse(store.hasWebPassword());
        char[] first = "a-long-local-password".toCharArray();
        store.setWebPassword(first);
        assertTrue(store.hasWebPassword());
        assertTrue(store.verifyWebPassword("a-long-local-password"));
        assertFalse(store.verifyWebPassword("not-the-password"));
        String persisted = Files.readString(directory.resolve("credentials.json"));
        assertFalse(persisted.contains("a-long-local-password"));
        assertTrue(persisted.contains("webPasswordSalt"));

        store.setWebPassword("a-different-long-password".toCharArray());
        assertFalse(store.verifyWebPassword("a-long-local-password"));
        assertTrue(store.verifyWebPassword("a-different-long-password"));
        assertThrows(IllegalArgumentException.class, () -> store.setWebPassword("too-short".toCharArray()));
    }

    @Test void firstRunSetupCodeIsOneTimeHashedAndCreatesTheWebPassword() throws Exception {
        CredentialStore store = new CredentialStore(directory);
        Path setupFile = store.ensureWebSetupCode();
        assertNotNull(setupFile);
        String code = Files.readString(setupFile).trim();
        assertTrue(code.startsWith("vpctl_setup_"));
        assertFalse(Files.readString(directory.resolve("credentials.json")).contains(code));
        assertEquals(setupFile, store.ensureWebSetupCode());
        assertEquals(code, Files.readString(setupFile).trim());

        assertFalse(store.completeWebSetup("vpctl_setup_wrong", "a-secure-browser-password".toCharArray()));
        assertTrue(store.completeWebSetup(code, "a-secure-browser-password".toCharArray()));
        assertFalse(Files.exists(setupFile));
        assertTrue(store.verifyWebPassword("a-secure-browser-password"));
        assertFalse(store.completeWebSetup(code, "a-different-browser-password".toCharArray()));
        assertNull(store.ensureWebSetupCode());
    }

    @Test void enrolledNodeIdsAreStableAndSorted() throws Exception {
        CredentialStore store = new CredentialStore(directory);
        store.rotateNode("proxy-z");
        store.rotateNode("backend-a");
        assertEquals(java.util.List.of("backend-a", "proxy-z"), store.enrolledNodeIds());
        store.revokeNode("backend-a");
        assertEquals(java.util.List.of("proxy-z"), store.enrolledNodeIds());
    }

    @Test void oversizedCredentialStoreFailsBeforeReadingItsContents() throws Exception {
        Files.write(directory.resolve("credentials.json"), new byte[2 * 1024 * 1024 + 1]);
        CredentialStore store = new CredentialStore(directory);

        assertThrows(java.io.IOException.class, store::hasAdmin);
        assertFalse(store.verifyAdmin("vpctl_admin_invalid"));
    }
}
