package com.bencodez.votingplugin.control;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ControlApplicationTest {
    @TempDir Path directory;

    @Test void identityIsStableAndConcurrentFirstStartupPublishesOneWinner() throws Exception {
        int count = 24;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        List<java.util.UUID> results = java.util.Collections.synchronizedList(new ArrayList<>());
        var executor = Executors.newFixedThreadPool(count);
        try {
            for (int i = 0; i < count; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        results.add(ControlApplication.loadIdentity(directory));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await();
        } finally {
            executor.shutdownNow();
        }
        Set<java.util.UUID> unique = new HashSet<>(results);
        assertEquals(1, unique.size());
        assertEquals(unique.iterator().next(), ControlApplication.loadIdentity(directory));
    }

    @Test void corruptIdentityFailsClosed() throws Exception {
        Files.writeString(directory.resolve("instance-id"), "not-a-uuid");
        assertThrows(java.io.IOException.class, () -> ControlApplication.loadIdentity(directory));
    }

    @Test void explicitAdminDirectoryDoesNotTouchConfiguredDefault() throws Exception {
        Path configuredDefault = directory.resolve("unwritable-default");
        Path explicit = directory.resolve("explicit");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ControlApplication.runOwnerCommand(new String[]{"admin-token", explicit.toString()},
                java.util.Map.of("CONTROL_DATA_DIR", configuredDefault.toString()), new PrintStream(output));

        assertFalse(Files.exists(configuredDefault));
        assertTrue(Files.exists(explicit.resolve("credentials.json")));
        assertTrue(output.toString().trim().startsWith("vpctl_admin_"));
    }

    @Test void webPasswordCommandReadsSecretOutOfBand() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        char[] supplied = "command-password-value".toCharArray();

        ControlApplication.runOwnerCommand(new String[]{"web-password", directory.toString()},
                java.util.Map.of(), new PrintStream(output), () -> supplied);

        assertTrue(new com.bencodez.votingplugin.control.auth.CredentialStore(directory)
                .verifyWebPassword("command-password-value"));
        assertTrue(output.toString().contains("WebUI password updated"));
        assertTrue(java.util.Arrays.equals(new char[supplied.length], supplied));
    }

    @Test void startupConfigurationValidatesAllBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> ControlApplication.Configuration.from(java.util.Map.of("CONTROL_PORT", "70000")));
        assertThrows(IllegalArgumentException.class,
                () -> ControlApplication.Configuration.from(java.util.Map.of("CONTROL_OFFLINE_TIMEOUT_SECONDS", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> ControlApplication.Configuration.from(java.util.Map.of("CONTROL_REQUEST_TIMEOUT_SECONDS", "61")));
    }
}
