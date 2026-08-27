package com.bencodez.votingplugin.control;

import com.bencodez.votingplugin.control.auth.CredentialStore;
import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.domain.ConfigurationAuditLog;
import com.bencodez.votingplugin.control.domain.ConfigurationOperations;
import com.bencodez.votingplugin.control.http.ControlHttpServer;
import com.bencodez.votingplugin.control.protocol.ControlIdentity;
import com.bencodez.votingplugin.control.protocol.Protocol;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class ControlApplication {
    private static final Object IDENTITY_LOCK = new Object();

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
        runOwnerCommand(args, System.getenv(), System.out, ControlApplication::readWebPassword);
    }

    static void runOwnerCommand(String[] args, Map<String, String> environment, PrintStream output)
            throws IOException {
        runOwnerCommand(args, environment, output,
                () -> { throw new IllegalArgumentException("A console is required to read the WebUI password"); });
    }

    static void runOwnerCommand(String[] args, Map<String, String> environment, PrintStream output,
                                PasswordReader passwordReader) throws IOException {
        switch (args[0]) {
            case "enroll" -> {
                if (args.length < 2 || args.length > 3) {
                    throw new IllegalArgumentException("Usage: enroll <nodeId> [dataDirectory]");
                }
                Path selected = ownerDataDirectory(args.length == 3 ? args[2] : null, environment);
                output.println(new CredentialStore(selected).rotateNode(args[1]));
            }
            case "revoke" -> {
                if (args.length < 2 || args.length > 3) {
                    throw new IllegalArgumentException("Usage: revoke <nodeId> [dataDirectory]");
                }
                Path selected = ownerDataDirectory(args.length == 3 ? args[2] : null, environment);
                new CredentialStore(selected).revokeNode(args[1]);
                output.println("Enrollment revoked.");
            }
            case "admin-token" -> {
                if (args.length > 2) {
                    throw new IllegalArgumentException("Usage: admin-token [dataDirectory]");
                }
                Path selected = ownerDataDirectory(args.length == 2 ? args[1] : null, environment);
                output.println(new CredentialStore(selected).rotateAdmin());
            }
            case "web-password" -> {
                if (args.length > 2) {
                    throw new IllegalArgumentException("Usage: web-password [dataDirectory]");
                }
                Path selected = ownerDataDirectory(args.length == 2 ? args[1] : null, environment);
                char[] password = passwordReader.read();
                try {
                    new CredentialStore(selected).setWebPassword(password);
                } finally {
                    if (password != null) java.util.Arrays.fill(password, '\0');
                }
                output.println("WebUI password updated. Existing browser sessions are now invalid.");
            }
            default -> throw new IllegalArgumentException("Unknown command");
        }
    }

    private static char[] readWebPassword() {
        java.io.Console console = System.console();
        if (console == null) {
            throw new IllegalArgumentException("The web-password command requires an interactive console");
        }
        char[] first = console.readPassword("New WebUI password: ");
        char[] second = console.readPassword("Confirm WebUI password: ");
        if (first == null || second == null || !java.util.Arrays.equals(first, second)) {
            if (first != null) java.util.Arrays.fill(first, '\0');
            if (second != null) java.util.Arrays.fill(second, '\0');
            throw new IllegalArgumentException("WebUI passwords did not match");
        }
        java.util.Arrays.fill(second, '\0');
        return first;
    }

    private static Path ownerDataDirectory(String explicit, Map<String, String> environment) {
        return requirePath(explicit != null ? explicit : environment.getOrDefault("CONTROL_DATA_DIR", "data"));
    }

    static void runServer(Map<String, String> environment) throws Exception {
        Configuration configuration = Configuration.from(environment);
        CredentialStore credentials = new CredentialStore(configuration.dataDirectory());
        if (!configuration.address().getAddress().isLoopbackAddress()
                && !credentials.hasAdmin() && !credentials.hasWebPassword()) {
            throw new IllegalArgumentException("An admin token or WebUI password is required for non-loopback binding");
        }
        // JDK HttpServer honors these bounds for stalled request and response bodies.
        System.setProperty("sun.net.httpserver.maxReqTime", Integer.toString(configuration.requestTimeoutSeconds()));
        System.setProperty("sun.net.httpserver.maxRspTime", Integer.toString(configuration.requestTimeoutSeconds()));
        ControlIdentity identity = new ControlIdentity(loadIdentity(configuration.dataDirectory()),
                VersionInfo.applicationVersion(), Protocol.VERSION);
        Clock clock = Clock.systemUTC();
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry(clock, configuration.offlineTimeout());
        ConfigurationOperations operations = new ConfigurationOperations(registry,
                new ConfigurationAuditLog(configuration.dataDirectory(), clock), clock);
        ControlHttpServer server = new ControlHttpServer(configuration.address(), registry, identity, credentials,
                operations, configuration.secureCookies(), configuration.trustedProxyAddresses(),
                configuration.launchId());
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

    /** A process lock keeps the first identity private until its complete contents are readable. */
    static UUID loadIdentity(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("instance-id");
        Path lockFile = directory.resolve("instance-id.lock");
        synchronized (IDENTITY_LOCK) {
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                if (Files.exists(file)) {
                    return readIdentity(file);
                }
                UUID candidate = UUID.randomUUID();
                Path temporary = Files.createTempFile(directory, "instance-id-", ".temporary");
                try {
                    Files.writeString(temporary, candidate.toString(), StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    try (FileChannel identity = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                        identity.force(true);
                    }
                    try {
                        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(temporary, file);
                    }
                    DurableFiles.forceDirectory(directory);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return candidate;
            }
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
                         int requestTimeoutSeconds, boolean secureCookies, Set<String> trustedProxyAddresses,
                         String launchId) {
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
            boolean secureCookies = strictBoolean(environment.getOrDefault("CONTROL_SECURE_COOKIE", "false"),
                    "secure cookie");
            Set<String> trustedProxies = trustedProxyAddresses(
                    environment.getOrDefault("CONTROL_TRUSTED_PROXY_ADDRESSES", ""));
            String launchId = launchId(environment.getOrDefault("CONTROL_LAUNCH_ID", ""));
            return new Configuration(new InetSocketAddress(resolved, port),
                    requirePath(environment.getOrDefault("CONTROL_DATA_DIR", "data")),
                    Duration.ofSeconds(offlineSeconds), requestSeconds, secureCookies, trustedProxies, launchId);
        }

        private static String launchId(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return UUID.fromString(value.trim()).toString();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid launch ID");
            }
        }

        private static Set<String> trustedProxyAddresses(String value) throws IOException {
            if (value == null || value.isBlank()) return Set.of();
            Set<String> result = new LinkedHashSet<>();
            for (String item : value.split(",", -1)) {
                String candidate = item.trim();
                boolean ipv4 = candidate.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
                boolean ipv6 = candidate.indexOf(':') >= 0 && candidate.matches("[0-9A-Fa-f:.]+");
                if (!ipv4 && !ipv6) throw new IllegalArgumentException("Invalid trusted proxy address");
                result.add(InetAddress.getByName(candidate).getHostAddress());
            }
            return Set.copyOf(result);
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

        private static boolean strictBoolean(String value, String name) {
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    @FunctionalInterface
    interface PasswordReader {
        char[] read();
    }
}
