package com.bencodez.votingplugin.control.http;

import com.bencodez.votingplugin.control.auth.CredentialStore;
import com.bencodez.votingplugin.control.auth.WebSessionStore;
import com.bencodez.votingplugin.control.domain.NodeRegistry;
import com.bencodez.votingplugin.control.domain.ConfigurationOperations;
import com.bencodez.votingplugin.control.domain.ValidationException;
import com.bencodez.votingplugin.control.protocol.ConfigurationRequests;
import com.bencodez.votingplugin.control.protocol.ConfigurationTask;
import com.bencodez.votingplugin.control.protocol.ConfigurationTaskResult;
import com.bencodez.votingplugin.control.protocol.ControlIdentity;
import com.bencodez.votingplugin.control.protocol.Heartbeat;
import com.bencodez.votingplugin.control.protocol.ManagedConfiguration;
import com.bencodez.votingplugin.control.protocol.NodeRegistration;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
import com.bencodez.votingplugin.control.protocol.PresenceSnapshot;
import com.bencodez.votingplugin.control.protocol.ProtocolError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded HTTP adapter. Node writes are enrolled; node listings require the local admin credential. */
public final class ControlHttpServer implements AutoCloseable {
    // Covers the worst-case JSON encoding of the 512 KiB managed-configuration limit plus its result envelope.
    public static final int MAX_REQUEST_BYTES = 4 * 1024 * 1024;
    private static final String HEALTH = "/api/v1/health";
    private static final String NODES = "/api/v1/nodes";
    private static final String REGISTER = "/api/v1/nodes/register";
    private static final String CONFIGURATION = "/api/v1/configuration";
    private static final String OPERATIONS = "/api/v1/operations";
    private static final String AUTH_LOGIN = "/api/v1/auth/login";
    private static final String AUTH_SESSION = "/api/v1/auth/session";
    private static final String AUTH_LOGOUT = "/api/v1/auth/logout";
    private static final String AUTH_SETUP = "/api/v1/auth/setup";
    private static final String ENROLLMENTS = "/api/v1/enrollments";
    private static final String SESSION_COOKIE = "vpctl_session";
    private static final Map<String, WebResource> WEB_RESOURCES = Map.of(
            "/", new WebResource("/web/index.html", "text/html; charset=utf-8"),
            "/index.html", new WebResource("/web/index.html", "text/html; charset=utf-8"),
            "/app.js", new WebResource("/web/app.js", "text/javascript; charset=utf-8"),
            "/app.css", new WebResource("/web/app.css", "text/css; charset=utf-8"));
    private static final int MAX_AUTH_FAILURES_PER_MINUTE = 100;
    private static final int MAX_PASSWORD_ATTEMPTS_PER_CLIENT = 8;
    private static final int MAX_PASSWORD_FAILURES_PER_CLIENT = 5;
    private static final int MAX_PASSWORD_FAILURE_CLIENTS = 4096;
    static final int MAX_BACKENDS_PER_NODE_PAGE = 4096;
    private static final int MAX_BACKENDS_PER_NODE_SUMMARY = 256;

    private final HttpServer server;
    private final ObjectMapper json;
    private final NodeRegistry registry;
    private final ControlIdentity identity;
    private final CredentialStore credentials;
    private final ConfigurationOperations configurationOperations;
    private final ThreadPoolExecutor executor;
    private final ThreadPoolExecutor passwordExecutor;
    private final PasswordAdmission passwordAdmission = new PasswordAdmission(MAX_PASSWORD_ATTEMPTS_PER_CLIENT);
    private final PasswordFailureLimiter passwordFailureLimiter;
    private final AuthFailureLimiter authLimiter;
    private final WebSessionStore webSessions;
    private final boolean secureCookies;
    private final Set<String> trustedProxyAddresses;
    private final String launchId;

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                             CredentialStore credentials) throws IOException {
        this(address, registry, identity, credentials, (String) null);
    }

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                             CredentialStore credentials, String launchId) throws IOException {
        this(address, registry, identity, credentials,
                new ConfigurationOperations(registry, new com.bencodez.votingplugin.control.domain.ConfigurationAuditLog(
                        java.nio.file.Files.createTempDirectory("votingplugin-control-test-audit"), Clock.systemUTC()),
                        Clock.systemUTC()), Clock.systemUTC(), System::nanoTime, false, Set.of(), launchId);
    }

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                             CredentialStore credentials, ConfigurationOperations configurationOperations)
            throws IOException {
        this(address, registry, identity, credentials, configurationOperations, Clock.systemUTC(), System::nanoTime,
                false, Set.of(), null);
    }

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                             CredentialStore credentials, ConfigurationOperations configurationOperations,
                             boolean secureCookies) throws IOException {
        this(address, registry, identity, credentials, configurationOperations, Clock.systemUTC(), System::nanoTime,
                secureCookies, Set.of(), null);
    }

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                             CredentialStore credentials, ConfigurationOperations configurationOperations,
                             boolean secureCookies, Set<String> trustedProxyAddresses) throws IOException {
        this(address, registry, identity, credentials, configurationOperations, Clock.systemUTC(), System::nanoTime,
                secureCookies, trustedProxyAddresses, null);
    }

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                             CredentialStore credentials, ConfigurationOperations configurationOperations,
                             boolean secureCookies, Set<String> trustedProxyAddresses, String launchId)
            throws IOException {
        this(address, registry, identity, credentials, configurationOperations, Clock.systemUTC(), System::nanoTime,
                secureCookies, trustedProxyAddresses, launchId);
    }

    ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity,
                      CredentialStore credentials, ConfigurationOperations configurationOperations, Clock clock,
                      java.util.function.LongSupplier nanoTime, boolean secureCookies,
                      Set<String> trustedProxyAddresses, String launchId) throws IOException {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.configurationOperations = Objects.requireNonNull(configurationOperations, "configurationOperations");
        this.secureCookies = secureCookies;
        this.trustedProxyAddresses = Set.copyOf(Objects.requireNonNull(trustedProxyAddresses, "trustedProxyAddresses"));
        this.launchId = launchId;
        Objects.requireNonNull(clock, "clock");
        json = new ObjectMapper();
        json.findAndRegisterModules();
        json.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        json.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        json.getFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
        json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(20).maxStringLength(ManagedConfiguration.MAX_CONTENT + 4096)
                .maxNumberLength(64).build());
        authLimiter = new AuthFailureLimiter(nanoTime, MAX_AUTH_FAILURES_PER_MINUTE, TimeUnit.MINUTES.toNanos(1));
        passwordFailureLimiter = new PasswordFailureLimiter(nanoTime, MAX_PASSWORD_FAILURES_PER_CLIENT,
                TimeUnit.MINUTES.toNanos(1), MAX_PASSWORD_FAILURE_CLIENTS);
        webSessions = new WebSessionStore(clock);
        server = HttpServer.create(address, 32);
        server.createContext("/", this::handle);
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "votingplugin-control-http");
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), threads, new ThreadPoolExecutor.AbortPolicy());
        ThreadFactory passwordThreads = runnable -> {
            Thread thread = new Thread(runnable, "votingplugin-control-password");
            thread.setDaemon(true);
            return thread;
        };
        passwordExecutor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), passwordThreads, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        passwordExecutor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
            passwordExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            configurationOperations.close();
        } catch (IOException ignored) {
            // The server is already stopped; startup validation will detect any later audit problem.
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        boolean deferred = false;
        try {
            route(exchange);
        } catch (DeferredResponseException ignored) {
            deferred = true;
        } catch (AuthenticationException e) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            error(exchange, e.rateLimited ? 429 : 401, e.rateLimited ? "AUTH_RATE_LIMITED" : "UNAUTHORIZED",
                    e.rateLimited ? "Too many authentication failures" : "Authentication failed", List.of());
        } catch (CsrfException e) {
            error(exchange, 403, "CSRF_REQUIRED", "A valid CSRF token is required", List.of());
        } catch (ValidationException e) {
            int status = switch (e.code()) {
                case "NODE_NOT_FOUND", "OPERATION_NOT_FOUND" -> 404;
                case "UNSUPPORTED_PROTOCOL", "INCOMPATIBLE_CAPABILITIES", "SESSION_MISMATCH",
                        "PREVIEW_INCOMPLETE", "APPROVAL_REQUIRED", "NODE_UNAVAILABLE", "OPERATION_LIMIT",
                        "REGISTRY_LIMIT", "TASK_NOT_CLAIMED", "TASK_LEASE_EXPIRED", "SETUP_COMPLETE" -> 409;
                case "UNSUPPORTED_MEDIA_TYPE" -> 415;
                default -> 400;
            };
            error(exchange, status, e.code(), e.getMessage(), e.details());
        } catch (JsonProcessingException | CharacterCodingException e) {
            error(exchange, 400, "MALFORMED_JSON", "Request body is not valid JSON", List.of());
        } catch (RequestTooLargeException e) {
            error(exchange, 413, "REQUEST_TOO_LARGE", "Request body exceeds " + MAX_REQUEST_BYTES + " bytes",
                    List.of());
        } catch (IllegalArgumentException e) {
            error(exchange, 400, "VALIDATION_ERROR", "Request validation failed", List.of());
        } catch (ResponseCompleteException ignored) {
            // The route already sent its intentional response (for example, a 405).
        } catch (RuntimeException e) {
            error(exchange, 500, "INTERNAL_ERROR", "Request could not be completed", List.of());
        } finally {
            if (!deferred) exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        WebResource resource = WEB_RESOURCES.get(path);
        if (resource != null) {
            requireMethod(exchange, "GET");
            sendResource(exchange, resource);
            return;
        }
        if (HEALTH.equals(path)) {
            requireMethod(exchange, "GET");
            Map<String, Object> health = new HashMap<>();
            health.put("status", "ok");
            health.put("time", Instant.now());
            health.put("identity", identity);
            if (launchId != null) health.put("launchId", launchId);
            send(exchange, 200, health);
            return;
        }
        if (AUTH_SETUP.equals(path)) {
            if ("GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, Map.of("required", !credentials.hasWebPassword(),
                        "codeFile", "web-setup-code.txt"));
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                error(exchange, 405, "METHOD_NOT_ALLOWED", "Method is not allowed",
                        List.of("allowed=GET", "allowed=POST"));
                throw new ResponseCompleteException();
            }
            if (credentials.hasWebPassword()) {
                throw new ValidationException("SETUP_COMPLETE", "First-run WebUI setup is already complete",
                        List.of());
            }
            SetupRequest request = read(exchange, SetupRequest.class);
            requireRequest(request);
            String client = passwordClient(exchange);
            if (!passwordFailureLimiter.allowAttempt(client) || !passwordAdmission.acquire(client)) {
                throw new AuthenticationException(true);
            }
            try {
                passwordExecutor.execute(() -> {
                    try {
                        handleWebSetup(exchange, request, client);
                    } finally {
                        passwordAdmission.release(client);
                    }
                });
            } catch (RejectedExecutionException e) {
                passwordAdmission.release(client);
                throw new AuthenticationException(true);
            }
            throw new DeferredResponseException();
        }
        if (AUTH_LOGIN.equals(path)) {
            requireMethod(exchange, "POST");
            PasswordRequest request = read(exchange, PasswordRequest.class);
            requireRequest(request);
            String client = passwordClient(exchange);
            if (!passwordFailureLimiter.allowAttempt(client)) {
                throw new AuthenticationException(true);
            }
            if (!passwordAdmission.acquire(client)) {
                throw new AuthenticationException(true);
            }
            try {
                passwordExecutor.execute(() -> {
                    try {
                        handlePasswordLogin(exchange, request, client);
                    } finally {
                        passwordAdmission.release(client);
                    }
                });
            } catch (RejectedExecutionException e) {
                passwordAdmission.release(client);
                throw new AuthenticationException(true);
            }
            throw new DeferredResponseException();
        }
        if (AUTH_SESSION.equals(path)) {
            requireMethod(exchange, "GET");
            WebSessionStore.Session session = webSessions.authenticate(cookie(exchange, SESSION_COOKIE),
                    credentials.webPasswordRevision());
            if (session == null) {
                noContent(exchange);
                return;
            }
            send(exchange, 200, Map.of("csrfToken", session.csrfToken()));
            return;
        }
        if (AUTH_LOGOUT.equals(path)) {
            requireMethod(exchange, "POST");
            WebSessionStore.Session session = authenticateWebSession(exchange, true);
            webSessions.remove(session.id());
            setSessionCookie(exchange, "", true);
            send(exchange, 200, Map.of("loggedOut", true));
            return;
        }
        if (ENROLLMENTS.equals(path)) {
            if ("GET".equals(exchange.getRequestMethod())) {
                authenticateAdmin(exchange, false);
                send(exchange, 200, Map.of("nodeIds", credentials.enrolledNodeIds()));
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                authenticateAdmin(exchange, true);
                EnrollmentRequest request = read(exchange, EnrollmentRequest.class);
                requireRequest(request);
                String credential = credentials.rotateNode(request.nodeId());
                send(exchange, 201, Map.of("nodeId", request.nodeId(), "credential", credential));
                return;
            }
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            error(exchange, 405, "METHOD_NOT_ALLOWED", "Method is not allowed",
                    List.of("allowed=GET", "allowed=POST"));
            throw new ResponseCompleteException();
        }
        if (path != null && path.startsWith(ENROLLMENTS + "/")) {
            String remainder = path.substring((ENROLLMENTS + "/").length());
            if (!remainder.contains("/")) {
                requireMethod(exchange, "DELETE");
                authenticateAdmin(exchange, true);
                String nodeId = decodePathSegment(remainder);
                credentials.revokeNode(nodeId);
                send(exchange, 200, Map.of("nodeId", nodeId, "revoked", true));
                return;
            }
        }
        if (NODES.equals(path)) {
            requireMethod(exchange, "GET");
            authenticateAdmin(exchange, false);
            Map<String, String> query = query(uri.getRawQuery());
            int offset = integer(query.getOrDefault("offset", "0"), "offset");
            int limit = integer(query.getOrDefault("limit", "50"), "limit");
            BackendPage page = boundedNodePage(registry.list(offset, limit));
            send(exchange, 200, Map.of("items", page.items(), "offset", offset, "limit", limit,
                    "backendItemsReturned", page.backendItemsReturned(),
                    "backendItemsTruncatedNodeIds", page.backendItemsTruncatedNodeIds(),
                    "backendItemsTruncated", page.backendItemsTruncated()));
            return;
        }
        if (REGISTER.equals(path)) {
            requireMethod(exchange, "POST");
            NodeRegistration registration = read(exchange, NodeRegistration.class);
            if (registration == null) {
                throw new ValidationException("VALIDATION_ERROR", "Request validation failed",
                        List.of("registration is required"));
            }
            authenticateNode(exchange, registration.nodeId());
            NodeRegistry.RegistrationResult result = registry.register(registration);
            send(exchange, result.created() ? 201 : 200,
                    Map.of("created", result.created(), "node", result.node(), "identity", identity));
            return;
        }
        if ((CONFIGURATION + "/read").equals(path)) {
            requireMethod(exchange, "POST");
            authenticateAdmin(exchange, true);
            ConfigurationRequests.Read request = read(exchange, ConfigurationRequests.Read.class);
            requireRequest(request);
            send(exchange, 202, configurationOperations.createRead(request.nodeIds(), request.configuration()));
            return;
        }
        if ((CONFIGURATION + "/preview").equals(path)) {
            requireMethod(exchange, "POST");
            authenticateAdmin(exchange, true);
            ConfigurationRequests.Preview request = read(exchange, ConfigurationRequests.Preview.class);
            requireRequest(request);
            send(exchange, 202, configurationOperations.createPreview(request.nodeIds(), request.configuration()));
            return;
        }
        if ((CONFIGURATION + "/apply").equals(path)) {
            requireMethod(exchange, "POST");
            authenticateAdmin(exchange, true);
            ConfigurationRequests.Apply request = read(exchange, ConfigurationRequests.Apply.class);
            requireRequest(request);
            send(exchange, 202, configurationOperations.createApply(request.previewOperationId(), request.approvalToken()));
            return;
        }
        if (path != null && path.startsWith(OPERATIONS + "/")) {
            String remainder = path.substring((OPERATIONS + "/").length());
            if (!remainder.contains("/")) {
                requireMethod(exchange, "GET");
                authenticateAdmin(exchange, false);
                send(exchange, 200, configurationOperations.get(UUID.fromString(remainder)));
                return;
            }
        }

        String prefix = NODES + "/";
        if (path != null && path.startsWith(prefix)) {
            String remainder = path.substring(prefix.length());
            String[] segments = remainder.split("/", -1);
            if (segments.length == 2 && ("heartbeat".equals(segments[1]) || "presence".equals(segments[1])
                    || "operations".equals(segments[1]))) {
                String nodeId = decodePathSegment(segments[0]);
                if ("heartbeat".equals(segments[1])) {
                    requireMethod(exchange, "PUT");
                    authenticateNode(exchange, nodeId);
                    send(exchange, 200, Map.of("node", registry.heartbeat(nodeId, read(exchange, Heartbeat.class))));
                } else if ("presence".equals(segments[1])) {
                    requireMethod(exchange, "PUT");
                    authenticateNode(exchange, nodeId);
                    NodeRegistry.SnapshotResult result = registry.replacePresence(nodeId,
                            read(exchange, PresenceSnapshot.class));
                    send(exchange, 200, Map.of("applied", result.applied(), "node", result.node()));
                } else {
                    requireMethod(exchange, "POST");
                    authenticateNode(exchange, nodeId);
                    ConfigurationRequests.Claim claim = read(exchange, ConfigurationRequests.Claim.class);
                    requireRequest(claim);
                    ConfigurationTask task = configurationOperations.claim(nodeId, claim.sessionId());
                    if (task == null) {
                        noContent(exchange);
                    } else {
                        send(exchange, 200, task);
                    }
                }
                return;
            }
            if (segments.length == 4 && "operations".equals(segments[1]) && "result".equals(segments[3])) {
                String nodeId = decodePathSegment(segments[0]);
                requireMethod(exchange, "POST");
                authenticateNode(exchange, nodeId);
                ConfigurationTaskResult result = read(exchange, ConfigurationTaskResult.class);
                send(exchange, 200, configurationOperations.complete(UUID.fromString(segments[2]), nodeId, result));
                return;
            }
        }
        error(exchange, 404, "NOT_FOUND", "Endpoint not found", List.of());
    }

    private void handleWebSetup(HttpExchange exchange, SetupRequest request, String client) {
        char[] password = request.password() == null ? null : request.password().toCharArray();
        try {
            String revision = credentials.completeWebSetup(request.setupCode(), password);
            if (revision == null) {
                passwordFailureLimiter.recordFailure(client);
                authenticationFailed();
            }
            passwordFailureLimiter.clear(client);
            webSessions.remove(cookie(exchange, SESSION_COOKIE));
            WebSessionStore.Session session = webSessions.create(revision);
            setSessionCookie(exchange, session.id(), false);
            send(exchange, 200, Map.of("csrfToken", session.csrfToken(), "setupComplete", true));
        } catch (AuthenticationException e) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            try {
                error(exchange, e.rateLimited ? 429 : 401,
                        e.rateLimited ? "AUTH_RATE_LIMITED" : "UNAUTHORIZED",
                        e.rateLimited ? "Too many authentication failures" : "Authentication failed", List.of());
            } catch (IOException ignored) {
                // The client disconnected while first-run setup was completing.
            }
        } catch (IllegalArgumentException e) {
            try {
                error(exchange, 400, "VALIDATION_ERROR", "Request validation failed", List.of());
            } catch (IOException ignored) {
                // The client disconnected while first-run setup was completing.
            }
        } catch (IOException | RuntimeException e) {
            try {
                error(exchange, 500, "INTERNAL_ERROR", "Request could not be completed", List.of());
            } catch (IOException ignored) {
                // The client disconnected while first-run setup was completing.
            }
        } finally {
            if (password != null) java.util.Arrays.fill(password, '\0');
            exchange.close();
        }
    }

    private void handlePasswordLogin(HttpExchange exchange, PasswordRequest request, String client) {
        try {
            String credentialRevision = credentials.authenticateWebPassword(request.password());
            if (credentialRevision == null) {
                passwordFailureLimiter.recordFailure(client);
                authenticationFailed();
            }
            passwordFailureLimiter.clear(client);
            webSessions.remove(cookie(exchange, SESSION_COOKIE));
            WebSessionStore.Session session = webSessions.create(credentialRevision);
            setSessionCookie(exchange, session.id(), false);
            send(exchange, 200, Map.of("csrfToken", session.csrfToken()));
        } catch (AuthenticationException e) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            try {
                error(exchange, e.rateLimited ? 429 : 401,
                        e.rateLimited ? "AUTH_RATE_LIMITED" : "UNAUTHORIZED",
                        e.rateLimited ? "Too many authentication failures" : "Authentication failed", List.of());
            } catch (IOException ignored) {
                // The client disconnected while password verification was in progress.
            }
        } catch (IOException | RuntimeException e) {
            try {
                error(exchange, 500, "INTERNAL_ERROR", "Request could not be completed", List.of());
            } catch (IOException ignored) {
                // The client disconnected while password verification was in progress.
            }
        } finally {
            exchange.close();
        }
    }

    static BackendPage boundedNodePage(List<NodeStatus> nodes) {
        if (nodes.isEmpty()) return new BackendPage(List.of(), 0, false, List.of());
        int perNode = Math.min(MAX_BACKENDS_PER_NODE_SUMMARY,
                Math.max(1, MAX_BACKENDS_PER_NODE_PAGE / nodes.size()));
        int returned = 0;
        boolean truncated = false;
        java.util.ArrayList<String> truncatedNodeIds = new java.util.ArrayList<>();
        java.util.ArrayList<NodeStatus> items = new java.util.ArrayList<>(nodes.size());
        for (NodeStatus node : nodes) {
            int count = Math.min(perNode, node.backends().size());
            returned += count;
            truncated |= count < node.backends().size();
            if (count < node.backends().size()) truncatedNodeIds.add(node.nodeId());
            items.add(new NodeStatus(node.nodeId(), node.sessionId(), node.displayName(), node.platform(),
                    node.pluginVersion(), node.protocolVersion(), node.advertisedCapabilities(),
                    node.acceptedCapabilities(), node.detectedPlugins(), node.backends().subList(0, count),
                    node.snapshotSequence(), node.lastSeen(), node.lastAuthenticatedUpdate(), node.online()));
        }
        return new BackendPage(List.copyOf(items), returned, truncated, List.copyOf(truncatedNodeIds));
    }

    private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new ValidationException("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", List.of());
        }
        List<String> lengths = exchange.getRequestHeaders().get("Content-Length");
        if (lengths != null && lengths.size() > 1) {
            throw new IllegalArgumentException("Ambiguous Content-Length");
        }
        if (lengths != null && !lengths.isEmpty()) {
            long length;
            try {
                length = Long.parseLong(lengths.get(0));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid Content-Length");
            }
            if (length < 0) {
                throw new IllegalArgumentException("Invalid Content-Length");
            }
            if (length > MAX_REQUEST_BYTES) {
                throw new RequestTooLargeException();
            }
        }
        byte[] bytes;
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_REQUEST_BYTES) {
                    throw new RequestTooLargeException();
                }
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        return json.readValue(text, type);
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new ValidationException("VALIDATION_ERROR", "Request validation failed",
                    List.of("request body must be a JSON object"));
        }
    }

    private void authenticateNode(HttpExchange exchange, String nodeId) {
        authenticate(exchange, token -> credentials.verifyNode(nodeId, token));
    }

    private void authenticateAdmin(HttpExchange exchange, boolean csrfRequired) {
        if (credentials.verifyAdmin(bearer(exchange))) {
            return;
        }
        authenticateWebSession(exchange, csrfRequired);
    }

    private void authenticate(HttpExchange exchange, java.util.function.Predicate<String> verifier) {
        String token = bearer(exchange);
        if (verifier.test(token)) {
            return;
        }
        authenticationFailed();
    }

    private WebSessionStore.Session authenticateWebSession(HttpExchange exchange, boolean csrfRequired) {
        WebSessionStore.Session session = webSessions.authenticate(cookie(exchange, SESSION_COOKIE),
                credentials.webPasswordRevision());
        if (session == null) {
            authenticationFailed();
        }
        if (csrfRequired && !constantTimeEquals(session.csrfToken(), singleHeader(exchange, "X-CSRF-Token"))) {
            throw new CsrfException();
        }
        return session;
    }

    private void authenticationFailed() {
        if (!authLimiter.allowAttempt()) throw new AuthenticationException(true);
        authLimiter.recordFailure();
        throw new AuthenticationException(false);
    }

    private static String bearer(HttpExchange exchange) {
        List<String> values = exchange.getRequestHeaders().get("Authorization");
        if (values == null || values.size() != 1) {
            return null;
        }
        String value = values.get(0);
        return value.regionMatches(true, 0, "Bearer ", 0, 7) && value.length() > 7
                ? value.substring(7) : null;
    }

    private String passwordClient(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        if (remote == null) return "unknown";
        String peer = remote.getAddress() == null ? remote.getHostString() : remote.getAddress().getHostAddress();
        return forwardedPasswordClient(peer, singleHeader(exchange, "X-Forwarded-For"), trustedProxyAddresses);
    }

    static String forwardedPasswordClient(String peer, String forwardedFor, Set<String> trustedProxies) {
        if (!trustedProxies.contains(peer) || forwardedFor == null || forwardedFor.isBlank()) return peer;
        String selected = peer;
        String[] chain = forwardedFor.split(",", -1);
        for (int index = chain.length - 1; index >= 0; index--) {
            String address = canonicalIpLiteral(chain[index]);
            if (address == null) return peer;
            selected = address;
            if (!trustedProxies.contains(address)) return address;
        }
        return selected;
    }

    private static String canonicalIpLiteral(String value) {
        String candidate = value == null ? "" : value.trim();
        boolean ipv4 = candidate.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean ipv6 = candidate.indexOf(':') >= 0 && candidate.matches("[0-9A-Fa-f:.]+");
        if (!ipv4 && !ipv6) return null;
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (IOException e) {
            return null;
        }
    }

    private static String cookie(HttpExchange exchange, String name) {
        String found = null;
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String header : headers) {
            for (String item : header.split(";")) {
                String[] pair = item.trim().split("=", 2);
                if (pair.length == 2 && name.equals(pair[0])) {
                    if (found != null) return null;
                    found = pair[1];
                }
            }
        }
        return found;
    }

    private static String singleHeader(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        return values != null && values.size() == 1 ? values.get(0) : null;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] first = expected == null ? new byte[32] : expected.getBytes(StandardCharsets.UTF_8);
        byte[] second = actual == null ? new byte[32] : actual.getBytes(StandardCharsets.UTF_8);
        return expected != null && actual != null && MessageDigest.isEqual(first, second);
    }

    private void setSessionCookie(HttpExchange exchange, String value, boolean expired) {
        String cookie = SESSION_COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict"
                + (secureCookies || exchange instanceof HttpsExchange ? "; Secure" : "")
                + (expired ? "; Max-Age=0" : "");
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    private void requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (expected.equals(exchange.getRequestMethod())) {
            return;
        }
        exchange.getResponseHeaders().set("Allow", expected);
        error(exchange, 405, "METHOD_NOT_ALLOWED", "Method is not allowed", List.of("allowed=" + expected));
        throw new ResponseCompleteException();
    }

    private void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = json.writeValueAsBytes(value);
        sendBytes(exchange, status, "application/json; charset=utf-8", body);
    }

    private void noContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(204, -1);
    }

    private void sendResource(HttpExchange exchange, WebResource resource) throws IOException {
        byte[] body;
        try (InputStream input = ControlHttpServer.class.getResourceAsStream(resource.classpath())) {
            if (input == null) {
                throw new IOException("Web resource is unavailable");
            }
            body = input.readAllBytes();
        }
        sendBytes(exchange, 200, resource.contentType(), body);
    }

    private void sendBytes(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; connect-src 'self'; img-src 'self'; object-src 'none'; "
                        + "base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void error(HttpExchange exchange, int status, String code, String message, List<String> details)
            throws IOException {
        send(exchange, status, Map.of("error", new ProtocolError(code, message, details)));
    }

    private static int integer(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split("&", -1)) {
            String[] pair = item.split("=", 2);
            String key = decodeQuery(pair[0]);
            String value = decodeQuery(pair.length == 2 ? pair[1] : "");
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate query parameter");
            }
        }
        return result;
    }

    private static String decodeQuery(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid query encoding");
        }
    }

    private static String decodePathSegment(String value) {
        String decoded;
        try {
            decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid path encoding");
        }
        if (decoded.isBlank() || decoded.contains("/") || decoded.contains("\\")) {
            throw new IllegalArgumentException("Invalid node path");
        }
        return decoded;
    }

    @SuppressWarnings("serial")
    private static final class RequestTooLargeException extends IOException { }
    @SuppressWarnings("serial")
    private static final class AuthenticationException extends RuntimeException {
        private final boolean rateLimited;
        private AuthenticationException(boolean rateLimited) {
            this.rateLimited = rateLimited;
        }
    }
    @SuppressWarnings("serial")
    private static final class CsrfException extends RuntimeException { }
    @SuppressWarnings("serial")
    private static final class ResponseCompleteException extends RuntimeException { }
    @SuppressWarnings("serial")
    private static final class DeferredResponseException extends RuntimeException { }

    private record WebResource(String classpath, String contentType) { }
    private record PasswordRequest(String password) { }
    private record SetupRequest(String setupCode, String password) { }
    private record EnrollmentRequest(String nodeId) { }
    record BackendPage(List<NodeStatus> items, int backendItemsReturned, boolean backendItemsTruncated,
                       List<String> backendItemsTruncatedNodeIds) { }

    static final class AuthFailureLimiter {
        private final java.util.function.LongSupplier nanoTime;
        private final int maximum;
        private final long windowNanos;
        private long windowStarted;
        private int failures;

        AuthFailureLimiter(java.util.function.LongSupplier nanoTime, int maximum, long windowNanos) {
            this.nanoTime = nanoTime;
            this.maximum = maximum;
            this.windowNanos = windowNanos;
            this.windowStarted = nanoTime.getAsLong();
        }

        synchronized boolean allowAttempt() {
            resetExpired();
            return failures < maximum;
        }

        synchronized void recordFailure() {
            resetExpired();
            if (failures < maximum) {
                failures++;
            }
        }

        private void resetExpired() {
            long now = nanoTime.getAsLong();
            if (now - windowStarted >= windowNanos || now < windowStarted) {
                windowStarted = now;
                failures = 0;
            }
        }
    }

    static final class PasswordAdmission {
        private final int maximumPerClient;
        private final Map<String, Integer> inFlight = new HashMap<>();

        PasswordAdmission(int maximumPerClient) {
            this.maximumPerClient = maximumPerClient;
        }

        synchronized boolean acquire(String client) {
            int current = inFlight.getOrDefault(client, 0);
            if (current >= maximumPerClient) return false;
            inFlight.put(client, current + 1);
            return true;
        }

        synchronized void release(String client) {
            Integer current = inFlight.get(client);
            if (current == null || current <= 1) {
                inFlight.remove(client);
            } else {
                inFlight.put(client, current - 1);
            }
        }
    }

    static final class PasswordFailureLimiter {
        private final java.util.function.LongSupplier nanoTime;
        private final int maximumFailures;
        private final long windowNanos;
        private final int maximumClients;
        private final LinkedHashMap<String, FailureWindow> failures = new LinkedHashMap<>(16, 0.75f, true);

        PasswordFailureLimiter(java.util.function.LongSupplier nanoTime, int maximumFailures,
                               long windowNanos, int maximumClients) {
            this.nanoTime = nanoTime;
            this.maximumFailures = maximumFailures;
            this.windowNanos = windowNanos;
            this.maximumClients = maximumClients;
        }

        synchronized boolean allowAttempt(String client) {
            FailureWindow current = current(client);
            return current == null || current.failures() < maximumFailures;
        }

        synchronized void recordFailure(String client) {
            long now = nanoTime.getAsLong();
            FailureWindow current = current(client);
            if (current == null) {
                while (failures.size() >= maximumClients) {
                    var oldest = failures.entrySet().iterator();
                    if (!oldest.hasNext()) break;
                    oldest.next();
                    oldest.remove();
                }
                failures.put(client, new FailureWindow(now, 1));
            } else if (current.failures() < maximumFailures) {
                failures.put(client, new FailureWindow(current.started(), current.failures() + 1));
            }
        }

        synchronized void clear(String client) {
            failures.remove(client);
        }

        private FailureWindow current(String client) {
            FailureWindow current = failures.get(client);
            if (current == null) return null;
            long now = nanoTime.getAsLong();
            if (now < current.started() || now - current.started() >= windowNanos) {
                failures.remove(client);
                return null;
            }
            return current;
        }

        private record FailureWindow(long started, int failures) { }
    }
}
