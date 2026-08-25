package com.bencodez.votingplugin.control.http;

import com.bencodez.votingplugin.control.auth.CredentialStore;
import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.protocol.ControlIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ControlHttpServerTest {
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
        credentials = new CredentialStore(directory);
        nodeToken = credentials.rotateNode("proxy-a");
        adminToken = credentials.rotateAdmin();
        server = new ControlHttpServer(new InetSocketAddress("127.0.0.1", 0),
                new InMemoryNodeRegistry(Clock.systemUTC(), Duration.ofSeconds(90)),
                new ControlIdentity(UUID.fromString("00000000-0000-0000-0000-000000000099"), "test", 1),
                credentials);
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

    @Test void healthRouteIsExactUnknownRoutesAreStructuredAndMethodsAreIntentional() throws Exception {
        HttpResponse<String> web = get("/", null);
        assertEquals(200, web.statusCode());
        assertTrue(web.body().contains("VotingPlugin Control"));
        assertTrue(web.headers().firstValue("Content-Security-Policy").orElseThrow().contains("default-src 'self'"));
        HttpResponse<String> script = get("/app.js", null);
        assertEquals(200, script.statusCode());
        assertTrue(script.body().contains("offset=${pageOffset}&limit=${PAGE_SIZE}"));
        assertTrue(script.body().contains("nextPage.addEventListener"));
        assertTrue(script.body().contains("result.configuration?.content != null"));
        assertTrue(script.body().contains("authenticationGeneration"));
        assertTrue(script.body().contains("if (loginInFlight) return"));
        assertTrue(web.body().contains("Easy vote reward"));
        assertTrue(script.body().contains("filteredSelection.size !== selectedNodes.size"));
        assertTrue(script.body().contains("previewGeneration === inputGeneration"));
        assertTrue(script.body().contains("approvedPreview.nodeIds.every"));
        assertEquals(200, get("/app.css", null).statusCode());
        assertError(send("POST", "/", null, null), 405, "METHOD_NOT_ALLOWED");
        assertEquals(200, get("/api/v1/health", null).statusCode());
        assertError(get("/api/v1/health/anything", null), 404, "NOT_FOUND");
        assertError(get("/api/v1/nodes/register/anything", null), 404, "NOT_FOUND");
        HttpResponse<String> method = send("POST", "/api/v1/health", "{}", null);
        assertError(method, 405, "METHOD_NOT_ALLOWED");
        assertEquals("GET", method.headers().firstValue("Allow").orElseThrow());
        assertError(send("GET", "/api/v1/nodes/register", null, null), 405, "METHOD_NOT_ALLOWED");
        assertError(send("POST", "/api/v1/nodes/proxy-a/heartbeat", "{}", null), 405,
                "METHOD_NOT_ALLOWED");
    }

    @Test void validEnrollmentAuthenticatesRegistrationHeartbeatPresenceAndAdminListing() throws Exception {
        HttpResponse<String> registered = send("POST", "/api/v1/nodes/register", registration(), nodeToken);
        assertEquals(201, registered.statusCode());
        JsonNode node = json.readTree(registered.body()).get("node");
        assertEquals("proxy-a", node.get("nodeId").asText());
        assertEquals("presence.snapshot", node.get("acceptedCapabilities").get(0).asText());

        String heartbeat = "{\"sessionId\":\"" + SESSION + "\",\"protocolVersion\":1,"
                + "\"capabilities\":[\"discovery.read\"],\"requiredCapabilities\":[]}";
        assertEquals(200, send("PUT", "/api/v1/nodes/proxy-a/heartbeat", heartbeat, nodeToken).statusCode());
        String snapshot = "{\"sessionId\":\"" + SESSION + "\",\"protocolVersion\":1,\"sequence\":1,"
                + "\"backends\":[{\"backendId\":\"lobby\",\"displayName\":\"Lobby\","
                + "\"presenceKnown\":true,\"available\":true,\"playerCount\":3}]}";
        assertEquals(200, send("PUT", "/api/v1/nodes/proxy-a/presence", snapshot, nodeToken).statusCode());
        JsonNode listed = json.readTree(get("/api/v1/nodes?offset=0&limit=10", adminToken).body());
        assertEquals("lobby", listed.at("/items/0/backends/0/backendId").asText());
        assertTrue(listed.at("/items/0/detectedPlugins").toString().contains("LuckPerms"));
        assertTrue(listed.at("/items/0/online").asBoolean());
    }

    @Test void configurationPreviewApprovalAndRevisionCheckedApplyAreEndToEnd() throws Exception {
        String capableRegistration = registration().replace("\"presence.snapshot\"]",
                "\"presence.snapshot\",\"config.proxy-routing.v1\"]");
        assertEquals(201, send("POST", "/api/v1/nodes/register", capableRegistration, nodeToken).statusCode());
        String proposal = "{\"sendVotesToAllServers\":true,\"blockedServers\":[\"lobby\"]}";
        HttpResponse<String> queued = send("POST", "/api/v1/configuration/preview",
                "{\"nodeIds\":[\"proxy-a\"],\"configuration\":" + proposal + "}", adminToken);
        assertEquals(202, queued.statusCode());
        String previewId = json.readTree(queued.body()).get("operationId").asText();

        JsonNode previewTask = json.readTree(send("POST", "/api/v1/nodes/proxy-a/operations",
                "{\"sessionId\":\"" + SESSION + "\"}", nodeToken).body());
        assertEquals("PREVIEW", previewTask.get("type").asText());
        String previewResult = "{\"sessionId\":\"" + SESSION + "\",\"success\":true,\"code\":\"OK\","+
                "\"message\":\"valid\",\"revision\":\"" + "a".repeat(64) + "\",\"configuration\":"
                + proposal + ",\"changes\":[\"blockedServers changed\"],\"reloaded\":false,\"rolledBack\":false}";
        assertEquals(200, send("POST", "/api/v1/nodes/proxy-a/operations/" + previewId + "/result",
                previewResult, nodeToken).statusCode());

        JsonNode preview = json.readTree(get("/api/v1/operations/" + previewId, adminToken).body());
        String approval = preview.get("approvalToken").asText();
        HttpResponse<String> applyQueued = send("POST", "/api/v1/configuration/apply",
                "{\"previewOperationId\":\"" + previewId + "\",\"approvalToken\":\"" + approval + "\"}",
                adminToken);
        assertEquals(202, applyQueued.statusCode());
        String applyId = json.readTree(applyQueued.body()).get("operationId").asText();
        JsonNode applyTask = json.readTree(send("POST", "/api/v1/nodes/proxy-a/operations",
                "{\"sessionId\":\"" + SESSION + "\"}", nodeToken).body());
        assertEquals("APPLY", applyTask.get("type").asText());
        assertEquals("a".repeat(64), applyTask.get("expectedRevision").asText());
        assertError(send("POST", "/api/v1/configuration/apply",
                "{\"previewOperationId\":\"" + previewId + "\",\"approvalToken\":\"" + approval + "\"}",
                adminToken), 409, "APPROVAL_REQUIRED");
        assertEquals(applyId, applyTask.get("operationId").asText());
    }

    @Test void missingInvalidWrongNodeRevokedAndRotatedCredentialsFailWithoutDisclosure() throws Exception {
        assertAuthFailure(send("POST", "/api/v1/nodes/register", registration(), null));
        assertAuthFailure(send("POST", "/api/v1/nodes/register", registration(), "wrong"));
        String other = credentials.rotateNode("proxy-b");
        assertAuthFailure(send("POST", "/api/v1/nodes/register", registration(), other));
        credentials.revokeNode("proxy-a");
        assertAuthFailure(send("POST", "/api/v1/nodes/register", registration(), nodeToken));
        String rotated = credentials.rotateNode("proxy-a");
        assertEquals(201, send("POST", "/api/v1/nodes/register", registration(), rotated).statusCode());
        assertAuthFailure(get("/api/v1/nodes", nodeToken));
        assertEquals(200, get("/api/v1/nodes", adminToken).statusCode());
    }

    @Test void validCredentialsBypassSaturatedFailureLimiter() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertAuthFailure(get("/api/v1/nodes", "wrong-" + i));
        }
        assertError(get("/api/v1/nodes", "one-too-many"), 429, "AUTH_RATE_LIMITED");
        assertEquals(200, get("/api/v1/nodes", adminToken).statusCode());
        assertEquals(201, send("POST", "/api/v1/nodes/register", registration(), nodeToken).statusCode());
        credentials.setWebPassword("valid-owner-password".toCharArray());
        assertEquals(200, send("POST", "/api/v1/auth/login",
                "{\"password\":\"valid-owner-password\"}", null).statusCode());
    }

    @Test void webPasswordCreatesHttpOnlySessionRequiresCsrfAndLogsOut() throws Exception {
        credentials.setWebPassword("a-secure-web-password".toCharArray());
        assertAuthFailure(send("POST", "/api/v1/auth/login", "{\"password\":\"wrong-password-value\"}", null));

        HttpResponse<String> login = send("POST", "/api/v1/auth/login",
                "{\"password\":\"a-secure-web-password\"}", null);
        assertEquals(200, login.statusCode());
        String setCookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        String csrf = json.readTree(login.body()).get("csrfToken").asText();

        assertEquals(200, sendWithHeaders("GET", "/api/v1/auth/session", null,
                Map.of("Cookie", cookie)).statusCode());
        assertEquals(200, sendWithHeaders("GET", "/api/v1/nodes", null,
                Map.of("Cookie", cookie)).statusCode());
        assertError(sendWithHeaders("POST", "/api/v1/auth/logout", null,
                Map.of("Cookie", cookie)), 403, "CSRF_REQUIRED");
        HttpResponse<String> logout = sendWithHeaders("POST", "/api/v1/auth/logout", null,
                Map.of("Cookie", cookie, "X-CSRF-Token", csrf));
        assertEquals(200, logout.statusCode());
        assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        assertEquals(204, sendWithHeaders("GET", "/api/v1/auth/session", null, Map.of("Cookie", cookie)).statusCode());

        HttpResponse<String> secondLogin = send("POST", "/api/v1/auth/login",
                "{\"password\":\"a-secure-web-password\"}", null);
        String secondCookie = secondLogin.headers().firstValue("Set-Cookie").orElseThrow();
        secondCookie = secondCookie.substring(0, secondCookie.indexOf(';'));
        credentials.setWebPassword("a-rotated-web-password".toCharArray());
        assertEquals(204, sendWithHeaders("GET", "/api/v1/auth/session", null,
                Map.of("Cookie", secondCookie)).statusCode());
    }

    @Test void concurrentValidPasswordAttemptsQueueBehindVerificationLimit() throws Exception {
        credentials.setWebPassword("a-secure-web-password".toCharArray());
        java.util.List<java.util.concurrent.CompletableFuture<HttpResponse<String>>> attempts =
                java.util.stream.IntStream.range(0, 6).mapToObj(ignored ->
                        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return send("POST", "/api/v1/auth/login",
                                        "{\"password\":\"a-secure-web-password\"}", null);
                            } catch (Exception e) {
                                throw new java.util.concurrent.CompletionException(e);
                            }
                        })).toList();
        for (java.util.concurrent.CompletableFuture<HttpResponse<String>> attempt : attempts) {
            assertEquals(200, attempt.join().statusCode());
        }
    }

    @Test void queuedPasswordVerificationDoesNotBlockHealthOrAdminRequests() throws Exception {
        credentials.setWebPassword("a-secure-web-password".toCharArray());
        java.util.List<java.util.concurrent.CompletableFuture<HttpResponse<String>>> attempts =
                java.util.stream.IntStream.range(0, 8).mapToObj(index ->
                        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            try {
                                return send("POST", "/api/v1/auth/login",
                                        "{\"password\":\"invalid-password-" + index + "\"}", null);
                            } catch (Exception e) {
                                throw new java.util.concurrent.CompletionException(e);
                            }
                        })).toList();
        assertEquals(200, get("/api/v1/health", null).statusCode());
        assertEquals(200, get("/api/v1/nodes", adminToken).statusCode());
        for (java.util.concurrent.CompletableFuture<HttpResponse<String>> attempt : attempts) {
            assertEquals(401, attempt.join().statusCode());
        }
    }

    @Test void malformedEmptyNullDuplicateNestedOversizedAndLongPayloadsAreDeterministic() throws Exception {
        assertError(send("POST", "/api/v1/nodes/register", "{", nodeToken), 400, "MALFORMED_JSON");
        assertError(send("POST", "/api/v1/nodes/register", "", nodeToken), 400, "MALFORMED_JSON");
        assertError(send("POST", "/api/v1/nodes/register", "null", nodeToken), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/auth/login", "null", null), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/configuration/read", "null", adminToken), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/configuration/preview", "null", adminToken), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/configuration/apply", "null", adminToken), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/nodes/proxy-a/operations", "null", nodeToken), 400,
                "VALIDATION_ERROR");
        String duplicate = registration().replaceFirst("\\{", "{\"nodeId\":\"proxy-a\",");
        assertError(send("POST", "/api/v1/nodes/register", duplicate, nodeToken), 400, "MALFORMED_JSON");
        String nested = "[".repeat(25) + "0" + "]".repeat(25);
        assertError(send("POST", "/api/v1/nodes/register", nested, nodeToken), 400, "MALFORMED_JSON");
        String oversized = sendRaw("POST /api/v1/nodes/register HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\nContent-Type: application/json\r\n"
                + "Authorization: Bearer " + nodeToken + "\r\n"
                + "Content-Length: " + (ControlHttpServer.MAX_REQUEST_BYTES + 1) + "\r\n"
                + "Connection: close\r\n\r\n");
        assertTrue(oversized.startsWith("HTTP/1.1 413"), oversized);
        assertTrue(oversized.contains("REQUEST_TOO_LARGE"), oversized);
        assertError(send("POST", "/api/v1/nodes/register", registration().replace("Proxy A", "x".repeat(101)),
                nodeToken), 400, "VALIDATION_ERROR");
        HttpRequest invalidUtf8 = HttpRequest.newBuilder(base.resolve("/api/v1/nodes/register"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + nodeToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[] {(byte) 0xc3, (byte) 0x28})).build();
        assertError(client.send(invalidUtf8, HttpResponse.BodyHandlers.ofString()), 400, "MALFORMED_JSON");
    }

    @Test void unknownFieldsAreAdditivelyCompatibleButUnsupportedProtocolAndRequirementsAreExplicit() throws Exception {
        assertEquals(201, send("POST", "/api/v1/nodes/register",
                registration().replace("}", ",\"futureField\":{\"value\":true}}"), nodeToken).statusCode());
        credentials.rotateNode("proxy-a");
        nodeToken = credentials.rotateNode("proxy-a");
        assertError(send("POST", "/api/v1/nodes/register", registration().replace("\"protocolVersion\":1",
                "\"protocolVersion\":2"), nodeToken), 409, "UNSUPPORTED_PROTOCOL");
        assertError(send("POST", "/api/v1/nodes/register", registration().replace("\"protocolVersion\":1",
                "\"protocolVersion\":1.9"), nodeToken), 400, "MALFORMED_JSON");
        assertError(send("POST", "/api/v1/nodes/register", registration().replace("\"requiredCapabilities\":[]",
                "\"requiredCapabilities\":[\"future.required\"]"), nodeToken), 409,
                "INCOMPATIBLE_CAPABILITIES");
    }

    @Test void queryValidationAndShutdownAreBounded() throws Exception {
        assertError(get("/api/v1/nodes?limit=101", adminToken), 400, "VALIDATION_ERROR");
        assertError(get("/api/v1/nodes?limit=1&limit=2", adminToken), 400, "VALIDATION_ERROR");
        server.close();
        server = null;
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.isAlive() && "votingplugin-control-http".equals(thread.getName())));
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

    private String sendRaw(String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
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
