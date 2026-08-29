package com.bencodez.votingplugin.control;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Filesystem durability helpers with explicit handling for unsupported directory channels. */
public final class DurableFiles {
    private DurableFiles() { }

    public static void forceDirectory(Path directory) throws IOException {
        if (!supportsDirectoryChannels(System.getProperty("os.name", ""))) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    static boolean supportsDirectoryChannels(String operatingSystem) {
        return !operatingSystem.toLowerCase(Locale.ROOT).contains("windows");
    }
}
