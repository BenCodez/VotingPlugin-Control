package com.bencodez.votingplugin.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentAccessSettingsTest {
    @TempDir Path directory;

    @Test void corruptPreferenceFailsClosedEvenWithStartupOptIn() throws IOException {
        Path preference = directory.resolve("agent-access.v1");
        Files.writeString(preference, "true\nfalse\n");
        AgentAccessSettings settings = new AgentAccessSettings(directory, true);
        assertFalse(settings.available());
        assertFalse(settings.enabled());
        assertThrows(IOException.class, () -> settings.save(true));
        assertEquals("true\nfalse\n", Files.readString(preference));
    }

    @Test void failedTemporaryCleanupDoesNotPublishEnablement() throws IOException {
        try (var watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
            var settings = new AgentAccessSettings(directory, false, path -> {
                java.nio.file.WatchKey key;
                try {
                    key = watcher.poll(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
                if (key == null) throw new AssertionError("Expected temporary creation event");
                Path temporary = key.pollEvents().stream()
                        .map(event -> (Path) event.context())
                        .filter(name -> name.toString().endsWith(".tmp"))
                        .findFirst().orElseThrow();
                Path replacement = Files.createDirectory(path.resolve(temporary));
                Files.createFile(replacement.resolve("child"));
            });
            assertThrows(java.nio.file.DirectoryNotEmptyException.class, () -> settings.save(true));
            assertFalse(settings.enabled());
        }
    }

    @Test void symlinkAppearingAfterStartupCannotReplaceTarget() throws IOException {
        var settings = new AgentAccessSettings(directory, false);
        Path preference = directory.resolve("agent-access.v1");
        Path target = directory.resolve("unrelated");
        Files.writeString(target, "true\n");
        try {
            Files.createSymbolicLink(preference, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable: " + unavailable);
        }
        assertThrows(IOException.class, () -> settings.save(true));
        assertFalse(settings.enabled());
        assertTrue(Files.isSymbolicLink(preference));
        assertEquals("true\n", Files.readString(target));
        var restarted = new AgentAccessSettings(directory, true);
        assertFalse(restarted.available());
        assertFalse(restarted.enabled());
    }
}
