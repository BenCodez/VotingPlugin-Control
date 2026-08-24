package com.bencodez.votingplugin.control.http;

import com.bencodez.votingplugin.control.domain.*;
import com.bencodez.votingplugin.control.protocol.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class ControlHttpServer implements AutoCloseable {
    public static final int MAX_REQUEST_BYTES = 16 * 1024;
    private final HttpServer server;
    private final ObjectMapper json;
    private final NodeRegistry registry;
    private final ControlIdentity identity;
    private final ExecutorService executor;

    public ControlHttpServer(InetSocketAddress address, NodeRegistry registry, ControlIdentity identity) throws IOException {
        this.registry = Objects.requireNonNull(registry); this.identity = Objects.requireNonNull(identity);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        json.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(4096).build());
        server = HttpServer.create(address, 0);
        server.createContext("/api/v1/health", this::health);
        server.createContext("/api/v1/nodes", this::nodes);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }

    private void health(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        send(exchange, 200, Map.of("status", "ok", "time", Instant.now(), "identity", identity));
    }

    private void nodes(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/v1/nodes") && exchange.getRequestMethod().equals("GET")) {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                int offset = integer(query.getOrDefault("offset", "0"), "offset");
                int limit = integer(query.getOrDefault("limit", "50"), "limit");
                List<NodeStatus> nodes = registry.list(offset, limit);
                send(exchange, 200, Map.of("items", nodes, "offset", offset, "limit", limit));
                return;
            }
            if (path.equals("/api/v1/nodes/register")) {
                if (!method(exchange, "POST")) return;
                NodeRegistry.RegistrationResult result = registry.register(read(exchange, NodeRegistration.class));
                send(exchange, result.created() ? 201 : 200, Map.of("created", result.created(), "node", result.node()));
                return;
            }
            String prefix = "/api/v1/nodes/";
            if (path.startsWith(prefix) && path.endsWith("/heartbeat")) {
                if (!method(exchange, "PUT")) return;
                String encodedId = path.substring(prefix.length(), path.length() - "/heartbeat".length());
                String nodeId = URLDecoder.decode(encodedId, StandardCharsets.UTF_8);
                if (nodeId.contains("/")) throw invalid("nodeId path segment is invalid");
                send(exchange, 200, Map.of("node", registry.heartbeat(nodeId, read(exchange, Heartbeat.class))));
                return;
            }
            error(exchange, 404, "NOT_FOUND", "Endpoint not found", List.of());
        } catch (ValidationException e) {
            error(exchange, e.code().equals("NODE_NOT_FOUND") ? 404 : e.code().equals("UNSUPPORTED_PROTOCOL") ? 409
                            : e.code().equals("UNSUPPORTED_MEDIA_TYPE") ? 415 : 400,
                    e.code(), e.getMessage(), e.details());
        } catch (JsonProcessingException e) {
            error(exchange, 400, "MALFORMED_JSON", "Request body is not valid JSON", List.of());
        } catch (RequestTooLargeException e) {
            error(exchange, 413, "REQUEST_TOO_LARGE", "Request body exceeds " + MAX_REQUEST_BYTES + " bytes", List.of());
        } catch (NumberFormatException e) {
            error(exchange, 400, "VALIDATION_ERROR", "Query parameter must be an integer", List.of(e.getMessage()));
        }
    }

    private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json"))
            throw new ValidationException("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", List.of());
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null && Long.parseLong(length) > MAX_REQUEST_BYTES) throw new RequestTooLargeException();
        byte[] bytes;
        try (InputStream input = exchange.getRequestBody()) { bytes = input.readNBytes(MAX_REQUEST_BYTES + 1); }
        if (bytes.length > MAX_REQUEST_BYTES) throw new RequestTooLargeException();
        return json.readValue(bytes, type);
    }
    private boolean method(HttpExchange exchange, String expected) throws IOException {
        if (exchange.getRequestMethod().equals(expected)) return true;
        exchange.getResponseHeaders().set("Allow", expected);
        error(exchange, 405, "METHOD_NOT_ALLOWED", "Expected " + expected, List.of()); return false;
    }
    private void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = json.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }
    private void error(HttpExchange exchange, int status, String code, String message, List<String> details) throws IOException {
        send(exchange, status, Map.of("error", new ProtocolError(code, message, details)));
    }
    private static int integer(String value, String name) { try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw new NumberFormatException(name); } }
    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String item : raw.split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
    private static ValidationException invalid(String detail) { return new ValidationException("VALIDATION_ERROR", "Request validation failed", List.of(detail)); }
    private static final class RequestTooLargeException extends IOException { }
}
