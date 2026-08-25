package com.bencodez.votingplugin.control;

import com.bencodez.votingplugin.control.auth.CredentialStore;
import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.http.ControlHttpServer;
import com.bencodez.votingplugin.control.protocol.ControlIdentity;
import com.bencodez.votingplugin.control.protocol.Protocol;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class ControlApplication {
    private ControlApplication() { }

    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                runOwnerCommand(args);
            } else {
                runServer(System.getenv());
            }
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("VotingPlugin Control could not complete the requested operation. "
                    + "Verify the arguments, configuration, and data-directory permissions.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("VotingPlugin Control stopped because of an internal startup failure.");
            System.exit(1);
        }
    }

    private static void runOwnerCommand(String[] args) throws IOException {
        Path dataDirectory = args.length >= 3 ? requirePath(args[2])
                : requirePath(System.getenv().getOrDefault("CONTROL_DATA_DIR", "data"));
        CredentialStore credentials = new CredentialStore(dataDirectory);
        switch (args[0]) {
            case "enroll" -> {
                if (args.length < 2 || args.length > 3) {
                    throw new IllegalArgumentException("Usage: enroll <nodeId> [dataDirectory]");
                }
                System.out.println(credentials.rotateNode(args[1]));
            }
            case "revoke" -> {
                if (args.length < 2 || args.length > 3) {
                    throw new IllegalArgumentException("Usage: revoke <nodeId> [dataDirectory]");
                }
                credentials.revokeNode(args[1]);
                System.out.println("Enrollment revoked.");
            }
            case "admin-token" -> {
                if (args.length > 2) {
                    throw new IllegalArgumentException("Usage: admin-token [dataDirectory]");
                }
                Path selected = args.length == 2 ? requirePath(args[1]) : dataDirectory;
                System.out.println(new CredentialStore(selected).rotateAdmin());
            }
            default -> throw new IllegalArgumentException("Unknown command");
        }
    }

    static void runServer(Map<String, String> environment) throws Exception {
        Configuration configuration = Configuration.from(environment);
        CredentialStore credentials = new CredentialStore(configuration.dataDirectory());
        if (!configuration.address().getAddress().isLoopbackAddress() && !credentials.hasAdmin()) {
            throw new IllegalArgumentException("An admin credential is required for non-loopback binding");
        }
        // JDK HttpServer honors these bounds for stalled request and response bodies.
        System.setProperty("sun.net.httpserver.maxReqTime", Integer.toString(configuration.requestTimeoutSeconds()));
        System.setProperty("sun.net.httpserver.maxRspTime", Integer.toString(configuration.requestTimeoutSeconds()));
        ControlIdentity identity = new ControlIdentity(loadIdentity(configuration.dataDirectory()),
                VersionInfo.applicationVersion(), Protocol.VERSION);
        ControlHttpServer server = new ControlHttpServer(configuration.address(),
                new InMemoryNodeRegistry(Clock.systemUTC(), configuration.offlineTimeout()), identity, credentials);
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            shutdown.countDown();
        }, "votingplugin-control-shutdown"));
        server.start();
        System.out.printf("VotingPlugin Control %s listening on http://%s:%d (protocol v%d)%n",
                VersionInfo.applicationVersion(), configuration.address().getHostString(), server.port(),
                Protocol.VERSION);
        try {
            shutdown.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.close();
        }
    }

    /** Create-if-absent publication cannot replace a concurrent startup's winning identity. */
    static UUID loadIdentity(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("instance-id");
        UUID candidate = UUID.randomUUID();
        try {
            Files.writeString(file, candidate.toString(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return candidate;
        } catch (FileAlreadyExistsException e) {
            return readIdentity(file);
        }
    }

    private static UUID readIdentity(Path file) throws IOException {
        try {
            return UUID.fromString(Files.readString(file).trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("Control identity is invalid");
        }
    }

    private static Path requirePath(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid data path");
        }
        return Path.of(value);
    }

    record Configuration(InetSocketAddress address, Path dataDirectory, Duration offlineTimeout,
                         int requestTimeoutSeconds) {
        static Configuration from(Map<String, String> environment) throws IOException {
            String host = environment.getOrDefault("CONTROL_HOST", "127.0.0.1").trim();
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Invalid host");
            }
            InetAddress resolved = InetAddress.getByName(host);
            int port = boundedInteger(environment.getOrDefault("CONTROL_PORT", "8080"), "port", 0, 65535);
            int offlineSeconds = boundedInteger(environment.getOrDefault("CONTROL_OFFLINE_TIMEOUT_SECONDS", "90"),
                    "offline timeout", 1, 3600);
            int requestSeconds = boundedInteger(environment.getOrDefault("CONTROL_REQUEST_TIMEOUT_SECONDS", "10"),
                    "request timeout", 1, 60);
            return new Configuration(new InetSocketAddress(resolved, port),
                    requirePath(environment.getOrDefault("CONTROL_DATA_DIR", "data")),
                    Duration.ofSeconds(offlineSeconds), requestSeconds);
        }

        private static int boundedInteger(String value, String name, int minimum, int maximum) {
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid " + name);
            }
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException("Invalid " + name);
            }
            return parsed;
        }
    }
}
