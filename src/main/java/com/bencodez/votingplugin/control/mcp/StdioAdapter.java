package com.bencodez.votingplugin.control.mcp;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class StdioAdapter {
    private static final int MAX_REQUEST = 16 * 1024;
    private static final int MAX_RESULT = 32 * 1024;
    private static final List<String> VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static {
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16).maxStringLength(MAX_RESULT).maxNumberLength(64).build());
    }
    private static final Map<String, Tool> TOOLS = tools();

    private final URI origin;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private String version;
    private boolean ready;

    public StdioAdapter(String origin, String token) {
        if (origin == null || token == null || !token.matches("vp-agent-[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Configure the Control origin and scoped credential");
        }
        this.origin = URI.create(origin);
        String host = this.origin.getHost();
        if (host == null || this.origin.getRawUserInfo() != null || this.origin.getRawQuery() != null
                || this.origin.getRawFragment() != null || !Set.of("", "/").contains(this.origin.getRawPath())
                || !("https".equals(this.origin.getScheme()) || ("http".equals(this.origin.getScheme())
                && Set.of("127.0.0.1", "[::1]").contains(host)))) {
            throw new IllegalArgumentException("Use an HTTPS Control origin or a literal loopback HTTP origin");
        }
        this.token = token;
    }

    public void run(InputStream source, OutputStream output) throws IOException, InterruptedException {
        BufferedInputStream input = new BufferedInputStream(source);
        byte[] line;
        while ((line = readFrame(input)) != null) {
            Object reply = dispatch(line);
            if (reply == null) continue;
            output.write(JSON.writeValueAsBytes(reply));
            output.write('\n');
            output.flush();
        }
    }

    private Object dispatch(byte[] line) throws InterruptedException {
        JsonNode request;
        try {
            request = JSON.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(line)).toString());
        } catch (IOException failure) {
            return error(JSON.nullNode(), -32700, "Invalid JSON");
        }
        JsonNode id = request == null ? JSON.nullNode() : request.path("id");
        if (request == null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()
                || !(id.isMissingNode() || id.isTextual() || id.isIntegralNumber())) {
            return error(JSON.nullNode(), -32600, "Invalid request");
        }
        String method = request.path("method").asText();
        if (id.isMissingNode()) {
            if (version != null && "notifications/initialized".equals(method)) ready = true;
            return null;
        }
        JsonNode params = request.path("params");
        return switch (method) {
            case "initialize" -> initialize(id, params);
            case "ping" -> reply(id, "result", Map.of());
            case "tools/list" -> ready ? reply(id, "result", Map.of("tools",
                    TOOLS.values().stream().map(Tool::descriptionJson).toList()))
                    : error(id, -32002, "Initialize MCP first");
            case "tools/call" -> ready ? call(id, params) : error(id, -32002, "Initialize MCP first");
            default -> error(id, -32601, "Method not found");
        };
    }

    private Object initialize(JsonNode id, JsonNode params) {
        if (version != null || !params.path("protocolVersion").isTextual()) {
            return error(id, -32602, "Invalid initialization");
        }
        String requested = params.path("protocolVersion").asText();
        version = VERSIONS.contains(requested) ? requested : VERSIONS.get(0);
        return reply(id, "result", Map.of(
                "protocolVersion", version,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "votingplugin-control", "version", "1.0.0")));
    }

    private Object call(JsonNode id, JsonNode params) throws InterruptedException {
        Tool tool = TOOLS.get(params.path("name").asText());
        if (tool == null) return error(id, -32602, "Unknown tool");
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : JSON.createObjectNode();
        if (!arguments.isObject()) return error(id, -32602, "Invalid arguments");
        try {
            HttpRequest request = HttpRequest.newBuilder(origin.resolve("/api/v1/agent/" + tool.route()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(arguments))).build();
            HttpResponse<byte[]> response = request(request);
            if (response.statusCode() != 200) {
                String code = JSON.readTree(response.body()).path("error").path("code").asText("");
                return toolError(id, "Control rejected the request (" + response.statusCode()
                        + (code.matches("[A-Z_]{1,48}") ? ", " + code : "") + ").");
            }
            JsonNode result = JSON.readTree(response.body());
            if (!validResult(tool.route(), result)) return toolError(id, "Invalid Control response.");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content", List.of(Map.of("type", "text", "text", JSON.writeValueAsString(result))));
            if (version.compareTo("2025-06-18") >= 0) body.put("structuredContent", result);
            return reply(id, "result", body);
        } catch (IOException | RuntimeException failure) {
            return toolError(id, "Control request failed. Check the configured origin and operation status.");
        }
    }

    private static boolean validResult(String route, JsonNode result) {
        if ("topology".equals(route)) return validTopology(result);
        if ("health".equals(route)) return validHealth(result);
        if ("health-refresh".equals(route)) {
            return fields(result, Set.of("nodeId", "state")) && nodeId(result.path("nodeId"))
                    && Set.of("RUNNING", "SUCCEEDED", "FAILED").contains(result.path("state").asText());
        }
        if ("nodes".equals(route)) {
            JsonNode items = result.path("items");
            if (!fields(result, Set.of("items", "nextOffset")) || !items.isArray() || items.size() > 50) return false;
            if (result.has("nextOffset") && (!result.path("nextOffset").isIntegralNumber()
                    || !result.path("nextOffset").canConvertToInt() || result.path("nextOffset").intValue() < 1
                    || result.path("nextOffset").intValue() > 4097)) return false;
            for (JsonNode node : items) {
                if (!fields(node, Set.of("nodeId", "platform", "online", "acceptedCapabilities"))
                        || !node.path("nodeId").asText().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                        || !node.path("nodeId").isTextual() || !node.path("platform").isTextual()
                        || !node.path("online").isBoolean() || !node.path("acceptedCapabilities").isArray()) {
                    return false;
                }
                for (JsonNode capability : node.path("acceptedCapabilities")) {
                    if (!capability.isTextual()) return false;
                }
            }
            return true;
        }
        if (!fields(result, Set.of("operationId", "applyOperationId", "type", "state", "nodes",
                "browserReviewReady", "reviewPath", "results", "values"))
                || !canonicalId(result.path("operationId"))
                || !Set.of("READ", "PREVIEW", "APPLY").contains(result.path("type").asText())
                || !Set.of("RUNNING", "SUCCEEDED", "COMPLETED_WITH_ERRORS").contains(result.path("state").asText())
                || !result.path("browserReviewReady").isBoolean()
                || !result.path("nodes").isObject() || result.path("nodes").size() != 1
                || !result.path("results").isObject() || result.path("results").size() > 1) return false;
        if (result.has("applyOperationId") && !canonicalId(result.path("applyOperationId"))) return false;
        if (result.has("reviewPath") && !result.path("reviewPath").asText().equals(
                "/#agent-review/" + result.path("operationId").asText())) return false;
        var nodes = result.path("nodes").fields();
        var node = nodes.next();
        if (!node.getKey().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !Set.of("QUEUED", "IN_PROGRESS", "COMPLETE").contains(node.getValue().asText())) return false;
        if (result.has("values") && !validValues(result)) return false;
        if (result.path("results").isEmpty()) return true;
        JsonNode details = result.path("results").path(node.getKey());
        return fields(details, Set.of("code", "success", "rolledBack", "reloaded"))
                && details.path("code").asText().matches("[A-Z][A-Z0-9_]{0,63}")
                && details.path("success").isBoolean() && details.path("rolledBack").isBoolean()
                && details.path("reloaded").isBoolean();
    }

    private static boolean validTopology(JsonNode result) {
        JsonNode items = result.path("items");
        if (!fields(result, Set.of("items", "nextOffset")) || !items.isArray() || items.size() > 50) return false;
        if (result.hasNonNull("nextOffset") && (!result.path("nextOffset").isIntegralNumber()
                || !result.path("nextOffset").canConvertToInt() || result.path("nextOffset").intValue() < 1
                || result.path("nextOffset").intValue() > 4097)) return false;
        for (JsonNode row : items) {
            if (!fields(row, Set.of("nodeId", "platform", "online", "topologyObservedAt", "topologyFresh",
                    "backendIds", "linksTruncated")) || !nodeId(row.path("nodeId"))
                    || !Set.of("VELOCITY", "BUNGEECORD", "BUKKIT").contains(row.path("platform").asText())
                    || !row.path("online").isBoolean() || !row.path("topologyFresh").isBoolean()
                    || !row.path("linksTruncated").isBoolean()
                    || row.hasNonNull("topologyObservedAt") && !timestamp(row.path("topologyObservedAt"))
                    || !nodeList(row.path("backendIds"), 6)) return false;
        }
        return true;
    }

    private static boolean validHealth(JsonNode result) {
        JsonNode checks = result.path("checks");
        if (!fields(result, Set.of("generatedAt", "truncated", "checks")) || !timestamp(result.path("generatedAt"))
                || !result.path("truncated").isBoolean() || !checks.isArray() || checks.size() > 75) return false;
        for (JsonNode check : checks) {
            if (!fields(check, Set.of("id", "status", "affectedNodes", "restartRequired"))
                    || !check.path("id").asText().matches("[a-zA-Z][a-zA-Z0-9.-]{0,95}")
                    || !Set.of("PASS", "WARNING", "FAIL", "UNKNOWN", "INFO").contains(check.path("status").asText())
                    || !check.path("restartRequired").isBoolean() || !nodeList(check.path("affectedNodes"), 3)) return false;
        }
        return true;
    }

    private static boolean nodeList(JsonNode nodes, int maximum) {
        if (!nodes.isArray() || nodes.size() > maximum) return false;
        for (JsonNode node : nodes) if (!nodeId(node)) return false;
        return true;
    }

    private static boolean nodeId(JsonNode node) {
        return node.isTextual() && node.asText().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private static boolean timestamp(JsonNode node) {
        if (!node.isTextual() || node.asText().length() > 40) return false;
        try {
            java.time.Instant.parse(node.asText());
            return true;
        } catch (java.time.format.DateTimeParseException failure) {
            return false;
        }
    }

    private static boolean validValues(JsonNode result) {
        JsonNode values = result.path("values");
        if (!"READ".equals(result.path("type").asText()) || !fields(values, Set.of("name", "exists",
                "serviceSite", "voteUrl", "enabled", "displayName", "priority", "hidden", "voteDelay", "material",
                "displayItem.amount", "sendVotesToAllServers", "blockedServers"))) {
            return false;
        }
        for (JsonNode value : values) if (!value.isTextual()) return false;
        return true;
    }

    private static boolean fields(JsonNode object, Set<String> permitted) {
        if (!object.isObject()) return false;
        var fields = object.fieldNames();
        while (fields.hasNext()) {
            if (!permitted.contains(fields.next())) return false;
        }
        return true;
    }

    private static boolean canonicalId(JsonNode id) {
        return id.isTextual() && id.asText().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private HttpResponse<byte[]> request(HttpRequest request) throws IOException, InterruptedException {
        var pending = client.sendAsync(request, ignored -> new BoundedBody());
        try {
            return pending.get(8, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Control request failed", failure);
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    private static byte[] readFrame(InputStream input) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1 && next != '\n') {
            if (frame.size() == MAX_REQUEST) throw new IOException("MCP frame exceeds the input limit");
            frame.write(next);
        }
        if (next == -1) return null;
        return frame.toByteArray();
    }

    private static Object toolError(JsonNode id, String text) {
        return reply(id, "result", Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", text))));
    }

    private static Object error(JsonNode id, int code, String message) {
        return reply(id, "error", Map.of("code", code, "message", message));
    }

    private static Object reply(JsonNode id, String field, Object body) {
        return Map.of("jsonrpc", "2.0", "id", id, field, body);
    }

    private static Map<String, Tool> tools() {
        Map<String, Tool> tools = new LinkedHashMap<>();
        Map<String, Object> node = Map.of("type", "string", "pattern", "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
        Map<String, Object> name = Map.of("type", "string", "pattern", "^[A-Za-z0-9_-]{1,64}$");
        Map<String, Object> id = Map.of("type", "string", "pattern",
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        Map<String, Object> flag = Map.of("type", "boolean");
        Map<String, Object> text = Map.of("type", "string", "minLength", 1, "maxLength", 200);
        tools.put("list_nodes", new Tool("list_nodes", "nodes", "List authorized enrolled Control nodes.",
                object(Map.of("offset", Map.of("type", "integer", "minimum", 0, "maximum", 4097)), List.of())));
        tools.put("get_topology", new Tool("get_topology", "topology", "Read scoped node topology and observation freshness.",
                object(Map.of("offset", Map.of("type", "integer", "minimum", 0, "maximum", 4097)), List.of())));
        tools.put("get_network_health", new Tool("get_network_health", "health", "Read scoped Network Doctor checks from current evidence.",
                object(Map.of("nodeIds", Map.of("type", "array", "items", node, "minItems", 1,
                        "maxItems", 25, "uniqueItems", true)), List.of("nodeIds"))));
        tools.put("refresh_node_health", new Tool("refresh_node_health", "health-refresh",
                "Queue the read-only Network Doctor observation for an authorized node.",
                object(Map.of("nodeId", node), List.of("nodeId"))));
        tools.put("read_site_policy", new Tool("read_site_policy", "site-policy-read",
                "Read automatic vote-site creation policy on one authorized backend.",
                object(Map.of("nodeId", node), List.of("nodeId"))));
        tools.put("preview_site_policy", new Tool("preview_site_policy", "site-policy-preview",
                "Preview automatic vote-site creation policy for browser approval.",
                object(Map.of("nodeId", node, "readOperationId", id, "enabled", flag),
                        List.of("nodeId", "readOperationId", "enabled"))));
        tools.put("check_transport", new Tool("check_transport", "transport",
                "Run a non-vote status probe between an authorized proxy and backend.",
                object(Map.of("proxyNodeId", node, "backendNodeId", node), List.of("proxyNodeId", "backendNodeId"))));
        tools.put("read_proxy_routing", new Tool("read_proxy_routing", "routing-read",
                "Read routing settings on one authorized proxy.",
                object(Map.of("nodeId", node), List.of("nodeId"))));
        tools.put("preview_proxy_routing", new Tool("preview_proxy_routing", "routing-preview",
                "Preview routing settings for browser approval on one authorized proxy.",
                object(Map.of("nodeId", node, "readOperationId", id, "sendVotesToAllServers", flag,
                        "blockedServers", Map.of("type", "array", "items", node, "maxItems", 256, "uniqueItems", true)),
                        List.of("nodeId", "readOperationId", "sendVotesToAllServers", "blockedServers"))));
        tools.put("read_setup", new Tool("read_setup", "read", "Read one named backend vote site.",
                object(Map.of("nodeId", node, "name", name), List.of("nodeId", "name"))));
        tools.put("preview_setup", new Tool("preview_setup", "preview",
                "Preview a named backend vote site. The operator reviews and applies in Control.",
                object(Map.of("nodeId", node, "readOperationId", id, "options", object(Map.of(
                        "name", name, "serviceSite", text, "voteUrl", Map.of("type", "string", "maxLength", 500),
                        "enabled", flag, "displayName", text, "priority", Map.of("type", "integer",
                                "minimum", 0, "maximum", 100), "hidden", flag, "voteDelay", text, "material", text),
                        List.of("name", "serviceSite", "voteUrl"))), List.of("nodeId", "readOperationId", "options"))));
        tools.put("get_operation", new Tool("get_operation", "status", "Track a scoped setup operation.",
                object(Map.of("operationId", id), List.of("operationId"))));
        return java.util.Collections.unmodifiableMap(tools);
    }

    private static Map<String, Object> object(Map<String, ?> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    private record Tool(String name, String route, String description, Map<String, Object> schema) {
        Map<String, Object> descriptionJson() {
            return Map.of("name", name, "description", description, "inputSchema", schema);
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long size;

        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        public void onNext(List<ByteBuffer> buffers) {
            size += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (size > MAX_RESULT) {
                subscription.cancel();
                delegate.onError(new IOException("Control response exceeds the result limit"));
                return;
            }
            delegate.onNext(buffers);
        }

        public void onError(Throwable failure) { delegate.onError(failure); }

        public void onComplete() { delegate.onComplete(); }
    }
}
