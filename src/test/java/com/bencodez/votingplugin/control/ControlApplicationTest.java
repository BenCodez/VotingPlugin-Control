package com.bencodez.votingplugin.control;

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

    @Test void startupConfigurationValidatesAllBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> ControlApplication.Configuration.from(java.util.Map.of("CONTROL_PORT", "70000")));
        assertThrows(IllegalArgumentException.class,
                () -> ControlApplication.Configuration.from(java.util.Map.of("CONTROL_OFFLINE_TIMEOUT_SECONDS", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> ControlApplication.Configuration.from(java.util.Map.of("CONTROL_REQUEST_TIMEOUT_SECONDS", "61")));
    }
}
