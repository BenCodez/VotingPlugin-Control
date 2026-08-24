package com.bencodez.votingplugin.control.http;

import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.protocol.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ControlHttpServerTest {
    private ControlHttpServer server;
    private HttpClient client;
    private ObjectMapper json;
    private URI base;

    @BeforeEach void start() throws Exception {
        server = new ControlHttpServer(new InetSocketAddress("127.0.0.1", 0),
                new InMemoryNodeRegistry(Clock.systemUTC(), Duration.ofSeconds(90)),
                new ControlIdentity(UUID.fromString("00000000-0000-0000-0000-000000000001"), "test", 1));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.port());
        client = HttpClient.newHttpClient();
        json = new ObjectMapper();
    }
    @AfterEach void stop() { server.close(); }

    @Test void healthIsVersionedAndHealthy() throws Exception {
        HttpResponse<String> response = get("/api/v1/health");
        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals("ok", body.get("status").asText());
        assertEquals(1, body.at("/identity/protocolVersion").asInt());
    }

    @Test void registrationIsCreatedThenUpdatedAndListed() throws Exception {
        String first = "{\"nodeId\":\"proxy-a\",\"displayName\":\"Proxy A\",\"platform\":\"VELOCITY\",\"pluginVersion\":\"6.20\",\"protocolVersion\":1,\"capabilities\":[\"status.read\"]}";
        assertEquals(201, send("POST", "/api/v1/nodes/register", first).statusCode());
        String update = first.replace("Proxy A", "Main Proxy");
        HttpResponse<String> updated = send("POST", "/api/v1/nodes/register", update);
        assertEquals(200, updated.statusCode());
        assertFalse(json.readTree(updated.body()).get("created").asBoolean());
        JsonNode listed = json.readTree(get("/api/v1/nodes?limit=10").body());
        assertEquals(1, listed.get("items").size());
        assertEquals("Main Proxy", listed.at("/items/0/displayName").asText());
    }

    @Test void heartbeatUpdatesLastSeenAndCapabilities() throws Exception {
        register("proxy-a", "[\"status.read\"]");
        HttpResponse<String> response = send("PUT", "/api/v1/nodes/proxy-a/heartbeat",
                "{\"protocolVersion\":1,\"capabilities\":[\"servers.list\"]}");
        assertEquals(200, response.statusCode());
        JsonNode node = json.readTree(response.body()).get("node");
        assertEquals("servers.list", node.get("capabilities").get(0).asText());
        assertNotNull(Instant.parse(node.get("lastSeen").asText()));
    }

    @Test void malformedPayloadInvalidIdAndOversizedBodyReturnStructuredErrors() throws Exception {
        assertError(send("POST", "/api/v1/nodes/register", "{"), 400, "MALFORMED_JSON");
        String invalid = "{\"nodeId\":\"bad/id\",\"displayName\":\"x\",\"platform\":\"OTHER\",\"pluginVersion\":\"1\",\"protocolVersion\":1}";
        assertError(send("POST", "/api/v1/nodes/register", invalid), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/nodes/register", " ".repeat(ControlHttpServer.MAX_REQUEST_BYTES + 1)), 413, "REQUEST_TOO_LARGE");
    }

    @Test void ignoresUnknownJsonFieldsForForwardCompatibility() throws Exception {
        String payload = "{\"nodeId\":\"future-proxy\",\"displayName\":\"Future\",\"platform\":\"BUNGEECORD\",\"pluginVersion\":\"1\",\"protocolVersion\":1,\"futureField\":{\"value\":true}}";
        assertEquals(201, send("POST", "/api/v1/nodes/register", payload).statusCode());
    }

    @Test void rejectsInvalidCapabilitiesAndProtocolVersions() throws Exception {
        assertError(register("proxy-a", "[\"INVALID CAPABILITY\"]"), 400, "VALIDATION_ERROR");
        String payload = "{\"nodeId\":\"proxy-a\",\"displayName\":\"A\",\"platform\":\"OTHER\",\"pluginVersion\":\"1\",\"protocolVersion\":2}";
        assertError(send("POST", "/api/v1/nodes/register", payload), 409, "UNSUPPORTED_PROTOCOL");
    }

    private HttpResponse<String> register(String id, String capabilities) throws Exception {
        return send("POST", "/api/v1/nodes/register", "{\"nodeId\":\"" + id + "\",\"displayName\":\"A\",\"platform\":\"OTHER\",\"pluginVersion\":\"1\",\"protocolVersion\":1,\"capabilities\":" + capabilities + "}");
    }
    private HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(base.resolve(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(code, json.readTree(response.body()).at("/error/code").asText());
    }
}
