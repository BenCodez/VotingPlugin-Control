package com.bencodez.votingplugin.control;

import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.http.ControlHttpServer;
import com.bencodez.votingplugin.control.protocol.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;

public final class ControlApplication {
    private static final String VERSION = "0.1.0-SNAPSHOT";
    private ControlApplication() { }
    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("CONTROL_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("CONTROL_PORT", "8080"));
        Path dataDirectory = Path.of(System.getenv().getOrDefault("CONTROL_DATA_DIR", "data"));
        ControlIdentity identity = new ControlIdentity(loadIdentity(dataDirectory), VERSION, Protocol.VERSION);
        ControlHttpServer server = new ControlHttpServer(new InetSocketAddress(host, port),
                new InMemoryNodeRegistry(Clock.systemUTC(), Duration.ofSeconds(90)), identity);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.printf("VotingPlugin Control %s listening on http://%s:%d (protocol v%d)%n", VERSION, host, server.port(), Protocol.VERSION);
    }
    static UUID loadIdentity(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("instance-id");
        if (Files.exists(file)) return UUID.fromString(Files.readString(file).trim());
        UUID id = UUID.randomUUID();
        Path temporary = Files.createTempFile(directory, "instance-id-", ".tmp");
        Files.writeString(temporary, id.toString(), StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
        catch (FileAlreadyExistsException e) { Files.deleteIfExists(temporary); return UUID.fromString(Files.readString(file).trim()); }
        return id;
    }
}
