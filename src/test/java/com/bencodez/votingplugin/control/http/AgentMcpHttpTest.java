package com.bencodez.votingplugin.control.http;

import com.bencodez.votingplugin.control.auth.CredentialStore;
import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.mcp.StdioAdapter;
import com.bencodez.votingplugin.control.protocol.ControlIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AgentMcpHttpTest {
    private static final String SESSION = "00000000-0000-0000-0000-000000000001";
    @TempDir Path directory;
    private ControlHttpServer server;
    private CredentialStore credentials;
    private String nodeToken;
    private String adminToken;
    private HttpClient client;
    private ObjectMapper json;
    private URI base;

    @BeforeEach void start() throws Exception {
        start(true);
    }

    private void start(boolean agentEnabled) throws Exception {
        credentials = new CredentialStore(directory);
        nodeToken = credentials.rotateNode("proxy-a");
        adminToken = credentials.rotateAdmin();
        server = new ControlHttpServer(new InetSocketAddress("127.0.0.1", 0),
                new InMemoryNodeRegistry(Clock.systemUTC(), Duration.ofSeconds(90)),
                new ControlIdentity(UUID.fromString("00000000-0000-0000-0000-000000000099"), "test", 1),
                credentials, "00000000-0000-0000-0000-000000000123", agentEnabled);
        server.start();
        base = URI.create("http://127.0.0.1:" + server.port());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        json = new ObjectMapper();
    }

    @AfterEach void stop() {
        if (server != null) {
            server.close();
        }
    }
    @Test void defaultOffDeniesAgentRoutesAndPreservesOwnerApi() throws Exception {
        stop();
        start(false);
        assertError(send("POST", "/api/v1/agent/nodes", "{}", adminToken), 403, "AGENT_DISABLED");
        assertError(send("POST", "/api/v1/agent-admin/issue", "{}", adminToken), 403, "AGENT_DISABLED");
        assertEquals(200, get("/api/v1/nodes", adminToken).statusCode());
    }

    @Test void agentVoteSiteUsesScopedReadAndBrowserOnlyApproval() throws Exception {
        String fixturePassphrase = UUID.randomUUID().toString();
        credentials.setWebPassword(fixturePassphrase.toCharArray());
        var login = send("POST", "/api/v1/auth/login",
                json.writeValueAsString(Map.of("password", fixturePassphrase)), null);
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        var browser = Map.of("Cookie", cookie, "X-CSRF-Token", json.readTree(login.body()).path("csrfToken").asText());
        String issue = json.writeValueAsString(Map.of("nodeIds", java.util.List.of("proxy-a"),
                "actions", java.util.List.of("discover", "read", "preview", "status"), "minutes", 10));
        assertError(sendWithHeaders("POST", "/api/v1/agent-admin/issue", issue, Map.of("Cookie", cookie)),
                403, "CSRF_REQUIRED");
        assertAuthFailure(send("POST", "/api/v1/agent-admin/issue", issue, adminToken));
        var issued = sendWithHeaders("POST", "/api/v1/agent-admin/issue", issue, browser);
        assertEquals(201, issued.statusCode(), issued.body());
        var grant = json.readTree(issued.body());
        String token = grant.path("token").asText();
        String other = json.readTree(sendWithHeaders("POST", "/api/v1/agent-admin/issue", issue, browser).body())
                .path("token").asText();
        assertEquals(201, send("POST", "/api/v1/nodes/register", registration()
                .replace("VELOCITY", "BUKKIT").replace("presence.snapshot", "config.quick-setup.v1\",\"config.files.v1"), nodeToken).statusCode());
        assertAuthFailure(get("/api/v1/nodes", token));
        assertAuthFailure(send("POST", "/api/v1/agent/nodes", "{}", adminToken));
        assertError(send("POST", "/api/v1/agent/nodes", "{\"extra\":true}", token), 400, "MALFORMED_JSON");
        assertEquals("proxy-a", json.readTree(send("POST", "/api/v1/agent/nodes", "{}", token).body())
                .path("items").get(0).path("nodeId").asText());
        assertAuthFailure(send("POST", "/api/v1/agent/read",
                json.writeValueAsString(Map.of("nodeId", "somewhere-else", "name", "example")), token));
        var read = send("POST", "/api/v1/agent/read",
                json.writeValueAsString(Map.of("nodeId", "proxy-a", "name", "example")), token);
        assertEquals(200, read.statusCode(), read.body());
        String readId = json.readTree(read.body()).path("operationId").asText();
        completeAgentTask(readId, "READ", "a", "VoteSites:\n", java.util.List.of());
        String proposal = json.writeValueAsString(Map.of("nodeId", "proxy-a", "readOperationId", readId,
                "options", Map.of("name", "example", "serviceSite", "example", "voteUrl", "https://example.com/vote")));

        String staleId = json.readTree(send("POST", "/api/v1/agent/preview", proposal, token).body())
                .path("operationId").asText();
        completeAgentTask(staleId, "PREVIEW", "b", null, java.util.List.of("changed VoteSites.example.VoteURL"));
        assertFalse(json.readTree(get("/api/v1/operations/" + staleId, adminToken).body()).path("browserReviewReady").asBoolean());

        String partialId = json.readTree(send("POST", "/api/v1/agent/preview", proposal, token).body())
                .path("operationId").asText();
        completeAgentTask(partialId, "PREVIEW", "a", null, java.util.List.of("changed VoteSites.example.VoteURL"));
        var partial = json.readTree(get("/api/v1/operations/" + partialId, adminToken).body());
        assertEquals("DIFF_UNAVAILABLE", partial.path("results").path("proxy-a").path("code").asText());

        String previewId = json.readTree(send("POST", "/api/v1/agent/preview", proposal, token).body())
                .path("operationId").asText();
        completeAgentTask(previewId, "PREVIEW", "a", null, java.util.stream.Stream.of("Enabled", "Name", "Priority",
                "Hidden", "ServiceSite", "VoteURL", "VoteDelay", "DisplayItem.Material", "DisplayItem.Amount")
                .map(path -> "changed VoteSites.example." + path).toList());
        String selector = json.writeValueAsString(Map.of("operationId", previewId));
        var detail = json.readTree(get("/api/v1/operations/" + previewId, adminToken).body());
        assertTrue(detail.path("browserReviewReady").asBoolean());
        assertTrue(detail.path("approvalToken").isNull() || detail.path("approvalToken").isMissingNode());
        assertError(send("POST", "/api/v1/agent/status", selector, other), 404, "OPERATION_NOT_FOUND");
        assertFalse(send("POST", "/api/v1/agent/status", selector, token).body().contains("approvalToken"));
        assertAuthFailure(send("POST", "/api/v1/agent-admin/approve", selector, token));
        assertAuthFailure(send("POST", "/api/v1/agent-admin/approve", selector, adminToken));
        assertError(sendWithHeaders("POST", "/api/v1/agent-admin/approve", selector, Map.of("Cookie", cookie)),
                403, "CSRF_REQUIRED");
        assertEquals(400, send("POST", "/api/v1/configuration/apply", json.writeValueAsString(
                Map.of("previewOperationId", previewId, "approvalToken", "invalid")), adminToken).statusCode());
        var applied = sendWithHeaders("POST", "/api/v1/agent-admin/approve", selector, browser);
        assertEquals(202, applied.statusCode(), applied.body());
        String applyId = json.readTree(applied.body()).path("operationId").asText();
        assertEquals(applyId, json.readTree(send("POST", "/api/v1/agent/status", selector, token).body())
                .path("applyOperationId").asText());
        assertEquals(400, sendWithHeaders("POST", "/api/v1/agent-admin/approve", selector, browser).statusCode());
        completeAgentTask(applyId, "APPLY", "c", null, java.util.List.of("changed VoteSites.example.VoteURL"));
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"fixture","version":"1"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_nodes","arguments":{}}}
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_operation","arguments":%s}}
                """.formatted(selector);
        new StdioAdapter(base.toString(), token).run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), frames);
        var replies = frames.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(4, replies.size());
        assertEquals(12, json.readTree(replies.get(1)).path("result").path("tools").size());
        assertEquals("proxy-a", json.readTree(replies.get(2)).path("result").path("structuredContent")
                .path("items").get(0).path("nodeId").asText());
        assertEquals(applyId, json.readTree(replies.get(3)).path("result").path("structuredContent")
                .path("applyOperationId").asText());
        assertFalse(replies.get(3).contains("approvalToken"));
        String healthRequest = json.writeValueAsString(Map.of("nodeIds", java.util.List.of("proxy-a")));
        assertAuthFailure(send("POST", "/api/v1/agent/health", healthRequest, token));
        String healthGrant = json.writeValueAsString(Map.of("nodeIds", java.util.List.of("proxy-a"),
                "actions", java.util.List.of("health"), "minutes", 10));
        String healthCredential = json.readTree(sendWithHeaders("POST", "/api/v1/agent-admin/issue",
                healthGrant, browser).body()).path("token").asText();
        String healthFrames = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"fixture","version":"1"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_topology","arguments":{}}}
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_network_health","arguments":%s}}
                """.formatted(healthRequest);
        frames.reset();
        new StdioAdapter(base.toString(), healthCredential).run(
                new ByteArrayInputStream(healthFrames.getBytes(StandardCharsets.UTF_8)), frames);
        var healthReplies = frames.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(3, healthReplies.size());
        assertEquals("proxy-a", json.readTree(healthReplies.get(1)).path("result").path("structuredContent")
                .path("items").get(0).path("nodeId").asText());
        assertTrue(java.util.stream.StreamSupport.stream(json.readTree(healthReplies.get(2)).path("result")
                .path("structuredContent").path("checks").spliterator(), false)
                .anyMatch(check -> "UNKNOWN".equals(check.path("status").asText())));
        assertEquals(200, sendWithHeaders("POST", "/api/v1/agent-admin/revoke",
                json.writeValueAsString(Map.of("operationId", grant.path("id").asText())), browser).statusCode());
        assertAuthFailure(send("POST", "/api/v1/agent/status", selector, token));
    }

    private void completeAgentTask(String operationId, String type, String revision,
                                   String fileContent, java.util.List<String> changes) throws Exception {
        var task = json.readTree(send("POST", "/api/v1/nodes/proxy-a/operations",
                json.writeValueAsString(Map.of("sessionId", SESSION)), nodeToken).body());
        assertEquals(operationId, task.path("operationId").asText());
        assertEquals(type, task.path("type").asText());
        if ("APPLY".equals(type)) assertEquals("a".repeat(64), task.path("expectedRevision").asText());
        Object configuration = fileContent == null ? task.path("configuration")
                : Map.of("domain", "file", "fileName", "VoteSites.yml", "content", fileContent);
        var response = send("POST", "/api/v1/nodes/proxy-a/operations/" + operationId + "/result",
                json.writeValueAsString(Map.of("sessionId", SESSION, "success", true, "code", "OK", "message", "valid",
                        "revision", revision.repeat(64), "changes", changes, "configuration", configuration,
                        "attemptId", task.path("attemptId").asText())), nodeToken);
        assertEquals(200, response.statusCode(), response.body());
    }

    private String registration() {
        return "{\"nodeId\":\"proxy-a\",\"sessionId\":\"" + SESSION + "\",\"displayName\":\"Proxy A\","
                + "\"platform\":\"VELOCITY\",\"pluginVersion\":\"7.1.2\",\"protocolVersion\":1,"
                + "\"capabilities\":[\"presence.snapshot\"],\"requiredCapabilities\":[],"
                + "\"detectedPlugins\":[\"LuckPerms\",\"Essentials\"]}";
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        return sendWithHeaders(method, path, body,
                token == null ? Map.of() : Map.of("Authorization", "Bearer " + token));
    }

    private HttpResponse<String> sendWithHeaders(String method, String path, String body,
                                                  Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(3));
        headers.forEach(builder::header);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertAuthFailure(HttpResponse<String> response) throws Exception {
        assertError(response, 401, "UNAUTHORIZED");
        assertEquals("Authentication failed", json.readTree(response.body()).at("/error/message").asText());
        assertFalse(response.body().contains("proxy-a"));
        assertFalse(response.body().contains("credential"));
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json.readTree(response.body()).at("/error/code").asText(), response.body());
    }

}
