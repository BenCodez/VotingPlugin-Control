package com.bencodez.votingplugin.control.http;

import com.bencodez.votingplugin.control.auth.CredentialStore;
import com.bencodez.votingplugin.control.domain.InMemoryNodeRegistry;
import com.bencodez.votingplugin.control.protocol.ControlIdentity;
import com.bencodez.votingplugin.control.protocol.BackendServerIdentity;
import com.bencodez.votingplugin.control.protocol.NodeStatus;
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
import java.nio.file.Files;
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
                credentials, "00000000-0000-0000-0000-000000000123");
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
        assertTrue(web.body().contains("data-tab=\"overview\""));
        assertTrue(web.body().contains("data-tab=\"network\""));
        assertTrue(web.body().contains("id=\"server-picker\""));
        assertTrue(web.body().contains("Full YAML"));
        assertTrue(web.body().contains("Comment support unknown"));
        assertTrue(web.body().contains("Sync site definitions across backends"));
        assertTrue(web.body().contains("Target-only sites and every reward section stay local"));
        assertTrue(web.body().contains("Load current values"));
        assertTrue(web.headers().firstValue("Content-Security-Policy").orElseThrow().contains("default-src 'self'"));
        HttpResponse<String> script = get("/app.js", null);
        assertEquals(200, script.statusCode());
		assertTrue(script.body().contains("offset=${offset}&limit=${PAGE_SIZE}"));
        assertTrue(script.body().contains("async function loadAllNodes()"));
        assertTrue(script.body().contains("MAX_REGISTRY_SCAN_ATTEMPTS"));
        assertTrue(script.body().contains("&revision=${revision}"));
        assertTrue(script.body().contains("enrollmentIds.has(backend.backendId)"));
        assertTrue(script.body().contains("Control enrollment unavailable"));
        assertTrue(script.body().contains("Comments preserved for every target"));
        assertTrue(script.body().contains("Backend topology is truncated"));
        assertTrue(script.body().contains("function resetServerConfigurationForms(status, preserveDirtyDrafts = false)"));
        assertTrue(script.body().contains("Network data is unavailable. Refresh and load current values before continuing."));
        assertTrue(script.body().contains("backendTopologyTruncated = false;"));
        assertTrue(script.body().contains("nextPage.addEventListener"));
        assertTrue(script.body().contains("result.configuration?.content != null"));
        assertTrue(script.body().contains("authenticationGeneration"));
        assertTrue(script.body().contains("if (loginInFlight) return"));
        assertTrue(script.body().contains("backendItemsTruncated"));
        assertTrue(script.body().contains("topologyComplete: !truncatedNodeIds.has(proxyId)"));
        assertTrue(script.body().contains("proxyReady: network.proxyReady"));
        assertTrue(script.body().contains("option.value = backend.backendId"));
        assertTrue(script.body().contains("return allNodeItems.filter(node => isProxy(node)"));
        assertTrue(script.body().contains("MAX_OPERATION_TARGETS = 100"));
        assertTrue(script.body().contains("proxyMethodNetworkSignature(refreshedNetwork)"));
        assertTrue(script.body().contains("proxyMethodCurrentSessionId !== (network.proxy?.sessionId || '')"));
        assertTrue(script.body().contains("sessionId !== proxyMethodNetwork().proxy?.sessionId"));
        assertTrue(script.body().contains("refreshedNetwork.proxy?.sessionId !== network.proxy.sessionId"));
        assertTrue(script.body().contains("if (approvedQuickPreview?.workflow === 'sync-vote-sites') approvedQuickPreview = null;"));
        assertTrue(script.body().contains("if (quickPreset.value !== 'sync-vote-sites') return;"));
        assertTrue(web.body().contains("Add a simple vote reward"));
        assertTrue(web.body().contains("First-run setup"));
        assertTrue(web.body().contains("Node enrollment"));
        assertTrue(script.body().contains("setupForm.addEventListener"));
        assertTrue(script.body().contains("loadEnrollments"));
        assertTrue(script.body().contains("enrollmentMutationInFlight"));
        assertTrue(script.body().contains("enrollmentRefreshRequested"));
        assertTrue(script.body().contains("await loadEnrollments()"));
        assertTrue(script.body().contains("enrollmentSubmit.disabled = true"));
        assertTrue(script.body().contains("filteredSelection.size !== selectedNodes.size"));
        assertTrue(script.body().contains("previewGeneration === inputGeneration"));
        assertTrue(script.body().contains("let configurationContentPresent = false;"));
        assertTrue(script.body().contains("const MAX_TRACE_EVENTS_PER_NODE = 100;"));
        assertTrue(script.body().contains("const traceAbortController = new AbortController();"));
        assertTrue(script.body().contains("await Promise.allSettled(candidates.map(async node => {"));
        assertTrue(script.body().contains("const response = await authorized(path, {...requestOptions, signal: options.signal});\n      ensureActive();"));
        assertTrue(script.body().contains("current.acceptedCapabilities.includes('data.inspect.v1')"));
        assertTrue(script.body().contains(
                "signal: traceAbortController.signal, contextCurrent, manageBusy: false"));
        assertTrue(script.body().contains("window.clearTimeout(deadlineTimer);"));
        assertFalse(script.body().contains("for (const node of candidates)"));
        assertTrue(script.body().contains("let configurationDraftNodeId = '';"));
        assertTrue(script.body().contains("let configurationDraftSessionId = '';"));
        assertTrue(script.body().contains("function fileDraftMatchesCurrentContext()"));
        assertTrue(script.body().contains("if (configurationDirty) {\n      text(fileOperationStatus, fileDraftStatus("),
                "Routine refresh must retain dirty drafts when the replacement has a different node role.");
        assertTrue(script.body().contains("const fileDraftReady = fileReady && fileDraftMatchesCurrentContext();"));
        assertTrue(script.body().contains(
                "previewFileConfiguration.disabled = !fileDraftReady || !configurationContentPresent;"));
        assertTrue(script.body().contains(
                "applyFileConfiguration.disabled = !fileDraftReady || !approvedFilePreview;"));
        assertTrue(script.body().contains(
                "configurationContent.value = document.content;\n          configurationContentPresent = true;"));
        assertTrue(script.body().contains(
                "resetServerContextValues('A selected server reconnected. Load current values before continuing.', true);"));
        assertTrue(script.body().contains(
                "configurationContent.addEventListener('input', () => {\n  if (!configurationDirty) {\n    configurationDraftNodeId = selectedServerId;"));
        assertTrue(script.body().contains("quickPresetNeedsRead() && !quickSetupValuesLoaded()"));
        assertTrue(script.body().contains("loadedQuickSetup.sessionId === nodeIndex.get(selectedServerId)?.sessionId"));
        assertTrue(script.body().contains("previousNodeIndex.get(selectedServerId)?.sessionId !== nodeIndex.get(selectedServerId)?.sessionId"));
        assertTrue(script.body().contains("sessionId !== nodeIndex.get(nodeId)?.sessionId"));
        assertTrue(script.body().contains("retained.sessionId === readSessionId"));
        assertTrue(script.body().contains("operation.results?.[proxyId]?.sessionId !== proxySessionId"));
        assertTrue(script.body().contains("confirmDiscardUnsavedConfiguration('switching servers')"));
        assertTrue(script.body().contains("voteId === voteTraceId.value.trim()"));
        assertTrue(script.body().contains("Pending offline votes"));
        assertTrue(script.body().contains("Additional VoteSite history was omitted"));
        assertTrue(script.body().contains("Node result limit reached; this trace is incomplete"));
        assertFalse(script.body().contains("This is the complete retained trace"));
        assertTrue(script.body().contains("const traceReady = authenticated && connectedInspectionNodes().length > 0"));
        assertTrue(script.body().contains("traceVote.disabled = !traceReady;"));
        assertTrue(script.body().contains("const retainedRoutingDraft = preserveDirtyDrafts && routingDirty;"));
        assertTrue(script.body().contains("routingDraftNodeId === selectedServerId"));
        assertTrue(script.body().contains("readConfiguration.disabled = !routingReadReady;"));
        assertTrue(script.body().contains("previewConfiguration.disabled = !routingDraftReady;"));
        assertTrue(script.body().contains("applyConfiguration.disabled = !routingDraftReady || !approvedPreview;"));
        assertTrue(script.body().contains("Your unsaved proxy-routing draft is retained"));
        assertTrue(script.body().contains("text(operationStatus, routingDraftStatus('The selected nodes changed during refresh."));
        assertTrue(script.body().contains("Your unsaved ${configurationFile.value} draft is retained"));
        assertTrue(script.body().contains("Discard unsaved routing changes and load current values?"));
        assertTrue(script.body().contains("Discard the unsaved ${configurationFile.value} draft and read/reload the current file for this server?"));
        assertTrue(script.body().contains("window.addEventListener('beforeunload'"));
        assertTrue(script.body().contains("loadedQuickSetup = {nodeId, sessionId, preset, selector}"));
        assertTrue(script.body().contains("configurationOperationsInFlight"));
        assertTrue(script.body().contains("if (selectedCapabilitiesChanged) {\n      approvedPreview = null;"));
        assertTrue(script.body().contains("approvedPreview.nodeIds.every"));
        assertTrue(script.body().contains("selectedCapabilitiesChanged"));
        assertTrue(script.body().contains("proxyFile ? !isProxy(restoreNode) : !isBackend(restoreNode)"));
        assertTrue(script.body().contains("discardAuthenticationState"));
        assertTrue(script.body().contains("text(operationStatus, '');"));
        assertTrue(script.body().contains("text(fileOperationStatus, '');"));
        assertTrue(script.body().contains("text(quickOperationStatus, '');"));
        assertTrue(script.body().contains("configurationForm.reset();"));
        assertTrue(script.body().contains("quickSetupForm.reset();"));
        assertTrue(script.body().contains("rewardSimulationForm.reset();"));
        assertTrue(script.body().contains("playerLookupForm.reset();"));
        assertTrue(script.body().contains("voteLogForm.reset();"));
        assertTrue(script.body().contains("voteTraceForm.reset();"));
        assertTrue(script.body().contains("siteResolutionForm.reset();"));
        assertTrue(script.body().contains("snapshotForm.reset();"));
        assertTrue(script.body().contains("voteLogFilter.disabled = true;"));
        assertTrue(script.body().contains("body.voteLoggingRestartSessions"));
        assertTrue(script.body().contains("retainedOperations.slice(0, MAX_OPERATION_HISTORY)"));
        assertTrue(script.body().contains("quickCommandSuggestions.replaceChildren();"));
        assertTrue(script.body().contains("Sign out could not be confirmed"));
        assertTrue(script.body().contains("result.success && result.configuration"));
        assertTrue(script.body().contains("Not enrolled in Control"));
        assertTrue(script.body().contains("Presence not available"));
        assertTrue(script.body().contains("No connected proxy reports this backend ID"));
        assertTrue(script.body().contains("'config.files.v1': 'Full configuration'"));
        assertTrue(script.body().contains("'config.file-comments.v1': 'Comments preserved'"));
        assertTrue(script.body().contains("'config.vote-sites-sync.v1': 'VoteSites sync'"));
        assertTrue(script.body().contains("'config.transport-test.v1': 'Communication test'"));
        assertTrue(script.body().contains("'config.proxy-method.v1': 'Proxy method'"));
        assertTrue(script.body().contains("preset: 'sync-vote-sites'"));
        assertTrue(script.body().contains("return allNodeItems.filter(node => isBackend(node)"));
        assertTrue(script.body().contains("A sync target became unavailable"));
        assertTrue(script.body().contains("MAX_SYNC_TARGETS = 100"));
        assertTrue(script.body().contains("The sync source became unavailable"));
        assertTrue(script.body().contains("sourceContent: source"));
        assertTrue(script.body().contains("preset: 'communication-test'"));
        assertTrue(script.body().contains("runTransportTest.addEventListener"));
        assertTrue(script.body().contains("preset: 'proxy-method'"));
        assertTrue(script.body().contains("proxyMethodButtons.forEach"));
        assertTrue(script.body().contains("handleEditorKeydown"));
        assertFalse(script.body().contains("'No backends reported.'"));
        assertFalse(script.body().contains("'No Bukkit plugin inventory reported.'"));
        HttpResponse<String> stylesheet = get("/app.css", null);
        assertEquals(200, stylesheet.statusCode());
        assertTrue(stylesheet.body().contains(".sidebar"));
        assertTrue(web.body().contains("id=\"primary-navigation\""));
        assertTrue(web.body().contains("id=\"attention-feed\""));
        assertTrue(web.body().contains("id=\"global-search-input\""));
        assertTrue(script.body().contains("async function refreshDashboard()"));
        assertTrue(script.body().contains("function dashboardIssues()"));
        assertTrue(script.body().contains("disconnected from Control"));
        assertTrue(script.body().contains("document.createElement('progress')"));
        assertTrue(script.body().contains("const countSource = Object.hasOwn(entry, 'count') ? entry.count : entry.votes;"));
        assertTrue(script.body().contains("Math.max(...services.map(service => service.count), 1)"));
        assertTrue(script.body().contains("track.value = service.count;"));
        assertFalse(script.body().contains("service.votes"));
        assertTrue(script.body().contains("dashboardInspectionStatus.voteSiteHealth = 'failed';"));
        assertTrue(script.body().contains("dashboardInspectionStatus.voteLog24h = 'failed';"));
        assertTrue(script.body().contains("dashboardInspectionStatus.voteLog30d = 'failed';"));
        assertTrue(script.body().contains("Some dashboard checks could not be verified"));
        assertTrue(script.body().contains("!current || hasWarning || hasIncompleteInspection ? 'Warning' : 'Healthy'"));
        assertTrue(script.body().contains("function normalizeDashboardCollection(value, maximum, normalize)"));
        assertTrue(script.body().contains("if (!Array.isArray(value)) return {items: [], incomplete: true};"));
        assertTrue(script.body().contains("normalizeDashboardVoteSiteHealth"));
        assertTrue(script.body().contains("normalizeDashboardVoteSummary"));
        assertTrue(script.body().contains("typeof value === 'number' && Number.isSafeInteger(value) && value >= 0"),
                "Dashboard counts must reject null, booleans, whitespace strings, and fractional values.");
        assertFalse(script.body().contains("const count = Number(value);"));
        assertFalse(script.body().contains("Number(dashboardOverview.configuredVoteSites) === 0"));
        assertTrue(script.body().contains("const siteCountsKnown = configured != null && enabled != null;"));
        assertTrue(script.body().contains("text(metricVoteSites, !siteCountsKnown ? '—'"));
        assertTrue(script.body().contains("voteSitesConfigured: configuredVoteSites == null ? null : configuredVoteSites > 0"));
        assertTrue(script.body().contains("voteSitesConfiguredKnown: configuredVoteSites != null"));
        int exactShortcut = script.body().indexOf("const exactShortcut = GLOBAL_PAGE_SHORTCUTS.get(normalized);");
        int fuzzySetting = script.body().indexOf("const setting = SETTINGS_SCHEMA.find");
        assertTrue(exactShortcut >= 0 && exactShortcut < fuzzySetting);
        assertTrue(script.body().contains("['rewards', {tab: 'quick-setup', scrollTarget: 'reward-builder-card'}]"));
        assertTrue(script.body().contains("['vote sites', {tab: 'data', scrollTarget: 'site-health-card'}]"));
        assertTrue(script.body().contains("['network doctor', {tab: 'network', scrollTarget: 'network-doctor-card'}]"));
        assertTrue(script.body().contains("['configuration compare', {tab: 'configurations', configView: 'compare'"));
        assertTrue(web.body().contains("data-tab=\"configurations\" data-config-shortcut=\"compare\""));
        assertTrue(script.body().contains("if (button.dataset.configShortcut) setConfigView(button.dataset.configShortcut);"));
        assertTrue(script.body().contains("if (setting) {\n    settingsFilter.value = query;"));
        int openWorkspace = script.body().indexOf("function openWorkspace(tab, scrollTarget = '', preset = '', navigationButton = null)");
        int presetBeforeTab = script.body().indexOf("quickPreset.value = preset;", openWorkspace);
        int activateAfterPreset = script.body().indexOf("setActiveTab(tab, true);", openWorkspace);
        assertTrue(openWorkspace >= 0 && presetBeforeTab > openWorkspace && activateAfterPreset > presetBeforeTab,
                "Nested shortcuts must establish their preset before tab autoload starts.");
        assertTrue(script.body().contains("if (autoLoadInFlight.has(tab)) {\n    autoLoadPending.add(tab);"));
        assertTrue(script.body().contains("if (autoLoadPending.delete(tab)) void autoLoadTab(tab);"),
                "A preset change during an older read must queue a fresh autoload.");
        assertTrue(script.body().contains("autoLoadPending.clear();"));
        int globalShortcut = script.body().indexOf("function openGlobalShortcut(destination)");
        int selectConfigView = script.body().indexOf("setConfigView(destination.configView);", globalShortcut);
        int openShortcutTab = script.body().indexOf("openWorkspace(destination.tab", globalShortcut);
        assertTrue(globalShortcut >= 0 && selectConfigView > globalShortcut && openShortcutTab > selectConfigView,
                "Nested search shortcuts must establish their subview before tab autoload starts.");
        assertFalse(script.body().contains(".style."));
        assertError(send("POST", "/", null, null), 405, "METHOD_NOT_ALLOWED");
        HttpResponse<String> health = get("/api/v1/health", null);
        assertEquals(200, health.statusCode());
        assertEquals("00000000-0000-0000-0000-000000000123",
                json.readTree(health.body()).get("launchId").asText());
        JsonNode operations = json.readTree(get("/api/v1/operations", adminToken).body());
        assertTrue(operations.get("items").isArray());
        assertTrue(operations.get("voteLoggingRestartSessions").isObject());
        assertError(get("/api/v1/health/anything", null), 404, "NOT_FOUND");
        assertError(get("/api/v1/nodes/register/anything", null), 404, "NOT_FOUND");
        HttpResponse<String> method = send("POST", "/api/v1/health", "{}", null);
        assertError(method, 405, "METHOD_NOT_ALLOWED");
        assertEquals("GET", method.headers().firstValue("Allow").orElseThrow());
        assertError(send("GET", "/api/v1/nodes/register", null, null), 405, "METHOD_NOT_ALLOWED");
        assertError(send("POST", "/api/v1/nodes/proxy-a/heartbeat", "{}", null), 405,
                "METHOD_NOT_ALLOWED");
    }

    @Test void nodePageBoundsBackendSummariesAcrossMaximumProxyPage() {
        java.util.List<BackendServerIdentity> backends = java.util.stream.IntStream.range(0, 300)
                .mapToObj(index -> new BackendServerIdentity("backend-" + index, "Backend " + index,
                        true, true, index)).toList();
        java.util.List<NodeStatus> nodes = java.util.stream.IntStream.range(0, 100).mapToObj(index ->
                new NodeStatus("proxy-" + index, UUID.randomUUID(), "Proxy " + index, "BUNGEECORD", "test", 1,
                        java.util.Set.of("presence.snapshot"), java.util.Set.of("presence.snapshot"),
                        java.util.Set.of(), backends, 1, java.time.Instant.EPOCH, java.time.Instant.EPOCH, true)).toList();

        ControlHttpServer.BackendPage page = ControlHttpServer.boundedNodePage(nodes);
        assertTrue(page.backendItemsTruncated());
        assertTrue(page.backendItemsReturned() <= ControlHttpServer.MAX_BACKENDS_PER_NODE_PAGE);
        assertEquals(100, page.items().size());
        assertEquals(100, page.backendItemsTruncatedNodeIds().size());
        assertTrue(page.items().stream().allMatch(node -> node.backends().size() == 40));
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
        assertEquals(1, listed.get("total").asInt());
        assertTrue(listed.has("registryRevision"));
    }

    @Test void nodePaginationRejectsAStaleRegistryRevision() throws Exception {
        assertEquals(201, send("POST", "/api/v1/nodes/register", registration(), nodeToken).statusCode());
        JsonNode first = json.readTree(get("/api/v1/nodes?offset=0&limit=1", adminToken).body());
        long revision = first.get("registryRevision").asLong();

        String secondToken = credentials.rotateNode("proxy-b");
        String secondRegistration = registration()
                .replace("proxy-a", "proxy-b")
                .replace(SESSION, "00000000-0000-0000-0000-000000000002");
        assertEquals(201, send("POST", "/api/v1/nodes/register", secondRegistration, secondToken).statusCode());

        assertError(get("/api/v1/nodes?offset=1&limit=1&revision=" + revision, adminToken),
                409, "REGISTRY_CHANGED");
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
                + proposal + ",\"changes\":[\"blockedServers changed\"],\"reloaded\":false,\"rolledBack\":false,"
                + "\"attemptId\":\"" + previewTask.get("attemptId").asText() + "\"}";
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

    @Test void inspectionRetryAndSnapshotRoutesAreEndToEnd() throws Exception {
        String capableRegistration = registration().replace("\"presence.snapshot\"]",
                "\"presence.snapshot\",\"data.inspect.v1\",\"config.files.v1\"]")
                .replace("\"platform\":\"VELOCITY\"", "\"platform\":\"BUKKIT\"");
        assertEquals(201, send("POST", "/api/v1/nodes/register", capableRegistration, nodeToken).statusCode());

        HttpResponse<String> inspectionQueued = send("POST", "/api/v1/inspections",
                "{\"nodeId\":\"proxy-a\",\"query\":{\"kind\":\"overview\",\"filters\":{}}}", adminToken);
        assertEquals(202, inspectionQueued.statusCode(), inspectionQueued.body());
        String inspectionId = json.readTree(inspectionQueued.body()).get("inspectionId").asText();
        JsonNode inspectionTask = json.readTree(send("POST", "/api/v1/nodes/proxy-a/inspections",
                "{\"sessionId\":\"" + SESSION + "\"}", nodeToken).body());
        assertEquals(inspectionId, inspectionTask.get("inspectionId").asText());
        String inspectionResult = "{\"sessionId\":\"" + SESSION + "\",\"success\":true,"
                + "\"code\":\"OK\",\"message\":\"current\",\"data\":{\"schemaVersion\":1,"
                + "\"kind\":\"overview\",\"generatedAt\":\"2026-08-30T00:00:00Z\","
                + "\"result\":{\"loggingEnabled\":true}},\"attemptId\":\""
                + inspectionTask.get("attemptId").asText() + "\"}";
        assertEquals(200, send("POST", "/api/v1/nodes/proxy-a/inspections/" + inspectionId + "/result",
                inspectionResult, nodeToken).statusCode());
        JsonNode completedInspection = json.readTree(get("/api/v1/inspections/" + inspectionId,
                adminToken).body());
        assertEquals("SUCCEEDED", completedInspection.get("state").asText());
        assertTrue(completedInspection.at("/result/data/result/loggingEnabled").asBoolean());

        String selector = "{\"domain\":\"file\",\"fileName\":\"Config.yml\"}";
        HttpResponse<String> readQueued = send("POST", "/api/v1/configuration/read",
                "{\"nodeIds\":[\"proxy-a\"],\"configuration\":" + selector + "}", adminToken);
        assertEquals(202, readQueued.statusCode(), readQueued.body());
        String readId = json.readTree(readQueued.body()).get("operationId").asText();
        JsonNode failedTask = json.readTree(send("POST", "/api/v1/nodes/proxy-a/operations",
                "{\"sessionId\":\"" + SESSION + "\"}", nodeToken).body());
        String failedResult = "{\"sessionId\":\"" + SESSION + "\",\"success\":false,"
                + "\"code\":\"READ_FAILED\",\"message\":\"failed\",\"changes\":[],"
                + "\"reloaded\":false,\"rolledBack\":false,\"attemptId\":\""
                + failedTask.get("attemptId").asText() + "\"}";
        assertEquals(200, send("POST", "/api/v1/nodes/proxy-a/operations/" + readId + "/result",
                failedResult, nodeToken).statusCode());

        HttpResponse<String> retryQueued = send("POST", "/api/v1/operations/" + readId + "/retry",
                null, adminToken);
        assertEquals(202, retryQueued.statusCode(), retryQueued.body());
        String retryId = json.readTree(retryQueued.body()).get("operationId").asText();
        assertNotEquals(readId, retryId);
        JsonNode retryTask = json.readTree(send("POST", "/api/v1/nodes/proxy-a/operations",
                "{\"sessionId\":\"" + SESSION + "\"}", nodeToken).body());
        assertEquals(retryId, retryTask.get("operationId").asText());
        String successfulResult = "{\"sessionId\":\"" + SESSION + "\",\"success\":true,"
                + "\"code\":\"OK\",\"message\":\"read\",\"revision\":\"" + "a".repeat(64) + "\","
                + "\"configuration\":{\"domain\":\"file\",\"fileName\":\"Config.yml\","
                + "\"content\":\"Feature: true\\n\"},\"changes\":[],\"reloaded\":false,"
                + "\"rolledBack\":false,\"attemptId\":\"" + retryTask.get("attemptId").asText() + "\"}";
        assertEquals(200, send("POST", "/api/v1/nodes/proxy-a/operations/" + retryId + "/result",
                successfulResult, nodeToken).statusCode());

        HttpResponse<String> snapshotCreated = send("POST", "/api/v1/snapshots",
                "{\"name\":\"Known good\",\"operationId\":\"" + retryId + "\"}", adminToken);
        assertEquals(201, snapshotCreated.statusCode(), snapshotCreated.body());
        String snapshotId = json.readTree(snapshotCreated.body()).get("snapshotId").asText();
        JsonNode snapshots = json.readTree(get("/api/v1/snapshots", adminToken).body());
        assertEquals(snapshotId, snapshots.at("/items/0/snapshotId").asText());
        JsonNode snapshot = json.readTree(get("/api/v1/snapshots/" + snapshotId, adminToken).body());
        assertEquals("Feature: true\n", snapshot.at("/documents/0/content").asText());
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

    @Test void firstRunWebSetupConsumesOneTimeCodeAndCreatesSession() throws Exception {
        Path setupFile = credentials.ensureWebSetupCode();
        String setupCode = java.nio.file.Files.readString(setupFile).trim();
        JsonNode setupState = json.readTree(get("/api/v1/auth/setup", null).body());
        assertTrue(setupState.get("required").asBoolean());
        assertEquals("web-setup-code.txt", setupState.get("codeFile").asText());

        assertAuthFailure(send("POST", "/api/v1/auth/setup",
                "{\"setupCode\":\"wrong\",\"password\":\"a-secure-web-password\"}", null));
        HttpResponse<String> completed = send("POST", "/api/v1/auth/setup",
                "{\"setupCode\":\"" + setupCode + "\",\"password\":\"a-secure-web-password\"}", null);
        assertEquals(200, completed.statusCode());
        assertTrue(completed.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly"));
        assertTrue(credentials.verifyWebPassword("a-secure-web-password"));
        assertFalse(java.nio.file.Files.exists(setupFile));
        assertFalse(json.readTree(get("/api/v1/auth/setup", null).body()).get("required").asBoolean());
        assertError(send("POST", "/api/v1/auth/setup",
                "{\"setupCode\":\"" + setupCode + "\",\"password\":\"a-different-web-password\"}", null),
                409, "SETUP_COMPLETE");
    }

    @Test void enrollmentApiCreatesListsRotatesAndRevokesNodeCredentials() throws Exception {
        assertAuthFailure(get("/api/v1/enrollments", null));
        JsonNode initial = json.readTree(get("/api/v1/enrollments", adminToken).body());
        assertEquals(java.util.List.of("proxy-a"),
                json.convertValue(initial.get("nodeIds"), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() { }));

        HttpResponse<String> created = send("POST", "/api/v1/enrollments",
                "{\"nodeId\":\"backend-lobby\"}", adminToken);
        assertEquals(201, created.statusCode());
        String credential = json.readTree(created.body()).get("credential").asText();
        assertTrue(credentials.verifyNode("backend-lobby", credential));
        assertFalse(java.nio.file.Files.readString(directory.resolve("credentials.json")).contains(credential));

        String rotated = json.readTree(send("POST", "/api/v1/enrollments",
                "{\"nodeId\":\"backend-lobby\"}", adminToken).body()).get("credential").asText();
        assertFalse(credentials.verifyNode("backend-lobby", credential));
        assertTrue(credentials.verifyNode("backend-lobby", rotated));
        assertEquals(200, send("DELETE", "/api/v1/enrollments/backend-lobby", null, adminToken).statusCode());
        assertFalse(credentials.verifyNode("backend-lobby", rotated));
        assertError(send("PUT", "/api/v1/enrollments", "{}", adminToken), 405, "METHOD_NOT_ALLOWED");
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

    @Test void repeatedPasswordFailuresAreThrottledBeforeMoreDerivations() throws Exception {
        credentials.setWebPassword("a-secure-web-password".toCharArray());
        for (int attempt = 0; attempt < 5; attempt++) {
            assertAuthFailure(send("POST", "/api/v1/auth/login",
                    "{\"password\":\"invalid-password-" + attempt + "\"}", null));
        }

        assertError(send("POST", "/api/v1/auth/login",
                "{\"password\":\"another-invalid-password\"}", null), 429, "AUTH_RATE_LIMITED");
    }

    @Test void passwordAdmissionPreventsOneClientFromOccupyingAllVerificationCapacity() {
        ControlHttpServer.PasswordAdmission admission = new ControlHttpServer.PasswordAdmission(2);
        assertTrue(admission.acquire("attacker"));
        assertTrue(admission.acquire("attacker"));
        assertFalse(admission.acquire("attacker"));
        assertTrue(admission.acquire("owner"));
        admission.release("attacker");
        assertTrue(admission.acquire("attacker"));
        admission.release("owner");
    }

    @Test void passwordFailureLimiterExpiresClearsAndBoundsClients() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        ControlHttpServer.PasswordFailureLimiter limiter =
                new ControlHttpServer.PasswordFailureLimiter(now::get, 2, 10, 2);
        assertTrue(limiter.allowAttempt("attacker"));
        limiter.recordFailure("attacker");
        assertTrue(limiter.allowAttempt("attacker"));
        limiter.recordFailure("attacker");
        assertFalse(limiter.allowAttempt("attacker"));
        limiter.clear("attacker");
        assertTrue(limiter.allowAttempt("attacker"));

        limiter.recordFailure("attacker");
        limiter.recordFailure("second");
        limiter.recordFailure("third");
        assertTrue(limiter.allowAttempt("attacker"));
        now.set(10);
        assertTrue(limiter.allowAttempt("second"));
    }

    @Test void trustedProxyUsesForwardedClientWhileDirectPeersCannotSpoofIt() {
        assertEquals("203.0.113.9", ControlHttpServer.forwardedPasswordClient("127.0.0.1", "203.0.113.9",
                java.util.Set.of("127.0.0.1")));
        assertEquals("198.51.100.4", ControlHttpServer.forwardedPasswordClient("198.51.100.4", "203.0.113.9",
                java.util.Set.of("127.0.0.1")));
        assertEquals("127.0.0.1", ControlHttpServer.forwardedPasswordClient("127.0.0.1", "not-an-address",
                java.util.Set.of("127.0.0.1")));
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
        assertError(send("POST", "/api/v1/inspections", "null", adminToken), 400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/inspections",
                "{\"query\":{\"kind\":\"overview\",\"filters\":{}}}", adminToken),
                400, "VALIDATION_ERROR");
        assertError(send("POST", "/api/v1/snapshots", "null", adminToken), 400, "VALIDATION_ERROR");
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

    @Test void snapshotStoreFailuresReturnStructuredErrorsAndServerRemainsUsable() throws Exception {
        var field = ControlHttpServer.class.getDeclaredField("configurationSnapshots");
        field.setAccessible(true);
        Object snapshots = field.get(server);
        var directoryField = snapshots.getClass().getDeclaredField("directory");
        directoryField.setAccessible(true);
        Path snapshotDirectory = (Path) directoryField.get(snapshots);
        UUID id = UUID.randomUUID();
        Path corrupted = snapshotDirectory.resolve(id + ".json");
        Files.writeString(corrupted, "{\"snapshotId\":\"" + id + "\",\"name\":\"x\"}");
        HttpResponse<String> response = get("/api/v1/snapshots/" + id, adminToken);
        assertError(response, 503, "SNAPSHOT_STORE_UNAVAILABLE");
        assertFalse(response.body().contains(snapshotDirectory.toString()));
        assertFalse(response.body().contains("Configuration snapshot is invalid"));
        assertEquals(200, get("/api/v1/health", null).statusCode());
        Files.deleteIfExists(corrupted);
        Files.createDirectory(corrupted);
        response = get("/api/v1/snapshots/" + id, adminToken);
        assertError(response, 503, "SNAPSHOT_STORE_UNAVAILABLE");
        assertFalse(response.body().contains(snapshotDirectory.toString()));
        Files.deleteIfExists(corrupted);
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
