package com.bencodez.votingplugin.control.domain;

import com.bencodez.votingplugin.control.DurableFiles;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Persistent owner preference. The startup flag is used only before the first explicit save. */
public final class AgentAccessSettings {
    private final Path directory;
    private final Path file;
    private final DirectorySync directorySync;
    private volatile boolean enabled;
    private final boolean available;

    public AgentAccessSettings(Path directory, boolean startupDefault) {
        this(directory, startupDefault, DurableFiles::forceDirectory);
    }

    AgentAccessSettings(Path directory, boolean startupDefault, DirectorySync directorySync) {
        this.directory = directory;
        this.directorySync = directorySync;
        this.file = directory.resolve("agent-access.v1");
        boolean loaded = false;
        boolean readable = false;
        try {
            checkPath();
            loaded = Files.notExists(file, LinkOption.NOFOLLOW_LINKS) ? startupDefault : read();
            readable = true;
        } catch (IOException failure) {
            System.getLogger(AgentAccessSettings.class.getName()).log(System.Logger.Level.WARNING,
                    "Agent access preference is unavailable; MCP is disabled");
        }
        enabled = loaded;
        available = readable;
    }

    private boolean read() throws IOException {
        try (var input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            String value = new String(input.readNBytes(7), StandardCharsets.US_ASCII);
            return switch (value) {
                case "true\n" -> true;
                case "false\n" -> false;
                default -> throw new IOException("Invalid agent access preference");
            };
        }
    }

    public boolean available() { return available; }

    public boolean enabled() { return enabled; }

    void disableRuntime() { enabled = false; }

    @FunctionalInterface
    interface DirectorySync {
        void force(Path directory) throws IOException;
    }

    void save(boolean value) throws IOException {
        if (!available) throw new IOException("Agent access preference unavailable");
        checkPath();
        Path temporary = Files.createTempFile(directory, "agent-access-", ".tmp");
        try {
            Files.writeString(temporary, value + "\n", StandardCharsets.US_ASCII, StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            directorySync.force(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
        enabled = value;
    }

    private void checkPath() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.notExists(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe agent access preference path");
        }
    }
}
