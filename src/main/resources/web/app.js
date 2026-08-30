'use strict';

const health = document.querySelector('#health');
const setupCard = document.querySelector('#setup-card');
const setupForm = document.querySelector('#setup-form');
const setupCode = document.querySelector('#setup-code');
const setupPassword = document.querySelector('#setup-password');
const setupConfirmPassword = document.querySelector('#setup-confirm-password');
const setupMessage = document.querySelector('#setup-message');
const authCard = document.querySelector('#auth-card');
const form = document.querySelector('#auth-form');
const passwordInput = document.querySelector('#password');
const loginButton = form.querySelector('button[type="submit"]');
const logout = document.querySelector('#logout');
const message = document.querySelector('#message');
const welcome = document.querySelector('#welcome');
const appShell = document.querySelector('#app-shell');
const serverPickerLabel = document.querySelector('#server-picker-label');
const serverPicker = document.querySelector('#server-picker');
const tabButtons = [...document.querySelectorAll('[data-tab]')];
const tabPanels = [...document.querySelectorAll('[data-panel]')];
const configViewButtons = [...document.querySelectorAll('[data-config-view]')];
const configViewPanels = [...document.querySelectorAll('[data-config-panel]')];
const metricNodes = document.querySelector('#metric-nodes');
const metricOnline = document.querySelector('#metric-online');
const metricBackends = document.querySelector('#metric-backends');
const metricIssues = document.querySelector('#metric-issues');
const selectedServerName = document.querySelector('#selected-server-name');
const selectedServerState = document.querySelector('#selected-server-state');
const selectedServerSummary = document.querySelector('#selected-server-summary');
const selectedServerCapabilities = document.querySelector('#selected-server-capabilities');
const configurationContext = document.querySelector('#configuration-context');
const commentPreservationState = document.querySelector('#comment-preservation-state');
const topology = document.querySelector('#topology');
const nodes = document.querySelector('#nodes');
const refresh = document.querySelector('#refresh');
const previousPage = document.querySelector('#previous-page');
const nextPage = document.querySelector('#next-page');
const pageNumber = document.querySelector('#page-number');
const sendAll = document.querySelector('#send-all');
const blockedServers = document.querySelector('#blocked-servers');
const readConfiguration = document.querySelector('#read-configuration');
const previewConfiguration = document.querySelector('#preview-configuration');
const applyConfiguration = document.querySelector('#apply-configuration');
const operationStatus = document.querySelector('#operation-status');
const configurationForm = document.querySelector('#configuration-form');
const configurationFile = document.querySelector('#configuration-file');
const configurationContent = document.querySelector('#configuration-content');
const editorPosition = document.querySelector('#editor-position');
const readFileConfiguration = document.querySelector('#read-file-configuration');
const previewFileConfiguration = document.querySelector('#preview-file-configuration');
const applyFileConfiguration = document.querySelector('#apply-file-configuration');
const fileOperationStatus = document.querySelector('#file-operation-status');
const fileConfigurationForm = document.querySelector('#file-configuration-form');
const quickSetupForm = document.querySelector('#quick-setup-form');
const quickPreset = document.querySelector('#quick-preset');
const quickName = document.querySelector('#quick-name');
const quickMethod = document.querySelector('#quick-method');
const quickSiteDisplayName = document.querySelector('#quick-site-display-name');
const quickService = document.querySelector('#quick-service');
const quickUrl = document.querySelector('#quick-url');
const quickDelay = document.querySelector('#quick-delay');
const quickSitePriority = document.querySelector('#quick-site-priority');
const quickSiteMaterial = document.querySelector('#quick-site-material');
const quickSiteEnabled = document.querySelector('#quick-site-enabled');
const quickSiteHidden = document.querySelector('#quick-site-hidden');
const detectedPlugins = document.querySelector('#detected-plugins');
const quickRewardScope = document.querySelector('#quick-reward-scope');
const quickCommand = document.querySelector('#quick-command');
const quickMessage = document.querySelector('#quick-message');
const quickCommandSuggestions = document.querySelector('#quick-command-suggestions');
const quickProcessRewards = document.querySelector('#quick-process-rewards');
const quickAutoSites = document.querySelector('#quick-auto-sites');
const quickAutoSitesOnly = document.querySelector('#quick-auto-sites-only');
const quickVoteLoggingEnabled = document.querySelector('#quick-vote-logging-enabled');
const quickVoteLoggingDays = document.querySelector('#quick-vote-logging-days');
const quickVoteLoggingMainMysql = document.querySelector('#quick-vote-logging-main-mysql');
const quickExtraCheck = document.querySelector('#quick-extra-check');
const quickCountFake = document.querySelector('#quick-count-fake');
const quickHideSiteWarning = document.querySelector('#quick-hide-site-warning');
const quickDisableUpdates = document.querySelector('#quick-disable-updates');
const quickPartyVotes = document.querySelector('#quick-party-votes');
const quickPartyCommand = document.querySelector('#quick-party-command');
const quickPartyBroadcast = document.querySelector('#quick-party-broadcast');
const quickPartyAll = document.querySelector('#quick-party-all');
const quickPartyOnline = document.querySelector('#quick-party-online');
const readQuickSetup = document.querySelector('#read-quick-setup');
const previewQuickSetup = document.querySelector('#preview-quick-setup');
const applyQuickSetup = document.querySelector('#apply-quick-setup');
const quickOperationStatus = document.querySelector('#quick-operation-status');
const voteSitesSource = document.querySelector('#vote-sites-source');
const voteSitesTargets = document.querySelector('#vote-sites-targets');
const voteSitesSyncCapability = document.querySelector('#vote-sites-sync-capability');
const transportTestProxy = document.querySelector('#transport-test-proxy');
const transportTestBackend = document.querySelector('#transport-test-backend');
const transportTestCapability = document.querySelector('#transport-test-capability');
const runTransportTest = document.querySelector('#run-transport-test');
const transportTestStatus = document.querySelector('#transport-test-status');
const proxyMethodProxy = document.querySelector('#proxy-method-proxy');
const proxyMethodCapability = document.querySelector('#proxy-method-capability');
const proxyMethodButtons = [...document.querySelectorAll('[data-proxy-method]')];
const readProxyMethod = document.querySelector('#read-proxy-method');
const proxyMethodCurrent = document.querySelector('#proxy-method-current');
const proxyMethodStatus = document.querySelector('#proxy-method-status');
const enrollmentCard = document.querySelector('#enrollment-card');
const enrollmentForm = document.querySelector('#enrollment-form');
const enrollmentSubmit = enrollmentForm.querySelector('button[type="submit"]');
const enrollmentNodeId = document.querySelector('#enrollment-node-id');
const enrollmentCredential = document.querySelector('#enrollment-credential');
const enrollmentList = document.querySelector('#enrollment-list');
const enrollmentMessage = document.querySelector('#enrollment-message');
const refreshEnrollments = document.querySelector('#refresh-enrollments');
const networkDoctorCapability = document.querySelector('#network-doctor-capability');
const runNetworkDoctor = document.querySelector('#run-network-doctor');
const downloadNetworkDiagnostics = document.querySelector('#download-network-diagnostics');
const networkDoctorResults = document.querySelector('#network-doctor-results');
const driftFile = document.querySelector('#drift-file');
const driftCapability = document.querySelector('#drift-capability');
const runDriftCheck = document.querySelector('#run-drift-check');
const driftResults = document.querySelector('#drift-results');
const snapshotForm = document.querySelector('#snapshot-form');
const snapshotName = document.querySelector('#snapshot-name');
const createSnapshot = document.querySelector('#create-snapshot');
const refreshSnapshots = document.querySelector('#refresh-snapshots');
const snapshotList = document.querySelector('#snapshot-list');
const snapshotStatus = document.querySelector('#snapshot-status');
const refreshSetupChecklist = document.querySelector('#refresh-setup-checklist');
const setupChecklist = document.querySelector('#setup-checklist');
const setupChecklistStatus = document.querySelector('#setup-checklist-status');
const autoSitesEnabled = document.querySelector('#auto-sites-enabled');
const autoSitesState = document.querySelector('#auto-sites-state');
const autoSitesTargetCount = document.querySelector('#auto-sites-target-count');
const selectAllAutoSitesTargets = document.querySelector('#select-all-auto-sites-targets');
const loadAutoSites = document.querySelector('#load-auto-sites');
const previewAutoSites = document.querySelector('#preview-auto-sites');
const applyAutoSites = document.querySelector('#apply-auto-sites');
const autoSitesStatus = document.querySelector('#auto-sites-status');
const voteLoggingEnabled = document.querySelector('#vote-logging-enabled');
const voteLoggingDays = document.querySelector('#vote-logging-days');
const voteLoggingMainMysql = document.querySelector('#vote-logging-main-mysql');
const voteLoggingState = document.querySelector('#vote-logging-state');
const loadVoteLogging = document.querySelector('#load-vote-logging');
const previewVoteLogging = document.querySelector('#preview-vote-logging');
const applyVoteLogging = document.querySelector('#apply-vote-logging');
const voteLoggingStatus = document.querySelector('#vote-logging-status');
const profileName = document.querySelector('#profile-name');
const profilePicker = document.querySelector('#profile-picker');
const saveProfile = document.querySelector('#save-profile');
const loadProfile = document.querySelector('#load-profile');
const deleteProfile = document.querySelector('#delete-profile');
const profileStatus = document.querySelector('#profile-status');
const rewardSimulationForm = document.querySelector('#reward-simulation-form');
const rewardScope = document.querySelector('#reward-scope');
const rewardSiteLabel = document.querySelector('#reward-site-label');
const rewardSite = document.querySelector('#reward-site');
const rewardChance = document.querySelector('#reward-chance');
const rewardMoney = document.querySelector('#reward-money');
const rewardCommands = document.querySelector('#reward-commands');
const rewardMessages = document.querySelector('#reward-messages');
const rewardBroadcasts = document.querySelector('#reward-broadcasts');
const rewardPermissions = document.querySelector('#reward-permissions');
const rewardItems = document.querySelector('#reward-items');
const rewardOnlineOnly = document.querySelector('#reward-online-only');
const simulateReward = document.querySelector('#simulate-reward');
const previewReward = document.querySelector('#preview-reward');
const applyReward = document.querySelector('#apply-reward');
const copyRewardToSetup = document.querySelector('#copy-reward-to-setup');
const rewardSimulationCapability = document.querySelector('#reward-simulation-capability');
const rewardSimulationResult = document.querySelector('#reward-simulation-result');
const settingsFilter = document.querySelector('#settings-filter');
const settingsCatalog = document.querySelector('#settings-catalog');
const refreshDataOverview = document.querySelector('#refresh-data-overview');
const dataOverview = document.querySelector('#data-overview');
const playerLookupForm = document.querySelector('#player-lookup-form');
const playerLookup = document.querySelector('#player-lookup');
const lookupPlayer = document.querySelector('#lookup-player');
const playerResult = document.querySelector('#player-result');
const loadSiteHealth = document.querySelector('#load-site-health');
const siteHealthResult = document.querySelector('#site-health-result');
const loadVoteLogSummary = document.querySelector('#load-vote-log-summary');
const voteLogSummaryResult = document.querySelector('#vote-log-summary-result');
const voteLogForm = document.querySelector('#vote-log-form');
const voteLogFilterType = document.querySelector('#vote-log-filter-type');
const voteLogFilter = document.querySelector('#vote-log-filter');
const voteLogEvent = document.querySelector('#vote-log-event');
const voteLogDays = document.querySelector('#vote-log-days');
const voteLogLimit = document.querySelector('#vote-log-limit');
const searchVoteLog = document.querySelector('#search-vote-log');
const voteLogResult = document.querySelector('#vote-log-result');
const voteTraceForm = document.querySelector('#vote-trace-form');
const voteTraceId = document.querySelector('#vote-trace-id');
const traceVote = document.querySelector('#trace-vote');
const voteTraceResult = document.querySelector('#vote-trace-result');
const siteResolutionForm = document.querySelector('#site-resolution-form');
const siteResolutionService = document.querySelector('#site-resolution-service');
const siteResolutionDisabled = document.querySelector('#site-resolution-disabled');
const resolveSite = document.querySelector('#resolve-site');
const siteResolutionResult = document.querySelector('#site-resolution-result');
const operationHistory = document.querySelector('#operation-history');
const clearOperationHistory = document.querySelector('#clear-operation-history');
const PAGE_SIZE = 100;
const MAX_CONFIGURATION_TARGETS = 100;
const MAX_SYNC_TARGETS = 100;
const MAX_OPERATION_TARGETS = 100;
const MAX_REGISTRY_SCAN_ATTEMPTS = 3;
let authenticated = false;
let csrfToken = '';
let pageOffset = 0;
let selectedNodes = new Set();
let selectedServerId = '';
let visibleNodeItems = [];
let allNodeItems = [];
let nodeIndex = new Map();
let enrollmentIds = new Set();
let enrollmentsLoaded = false;
let backendTopologyTruncated = false;
let backendTopologyTruncatedNodeIds = new Set();
let approvedPreview = null;
let approvedFilePreview = null;
let approvedQuickPreview = null;
let loadedQuickSetup = null;
let voteSitesSourceId = '';
let voteSitesTargetIds = new Set();
let voteSitesTargetsInitialized = false;
let transportTestProxyId = '';
let transportTestBackendId = '';
let proxyMethodProxyId = '';
let proxyMethodCurrentFor = '';
let proxyMethodCurrentSessionId = '';
let proxyMethodCurrentValue = '';
let nodeCapabilities = new Map();
let nodePlugins = new Map();
let inputGeneration = 0;
let authenticationGeneration = 0;
let loginInFlight = false;
let setupRequired = false;
let enrollmentInFlight = false;
let enrollmentRefreshRequested = false;
let enrollmentMutationInFlight = false;
let configurationOperationsInFlight = 0;
let proxyMethodWorkflowInFlight = false;
const FILE_READ_CACHE_TTL_MS = 30_000;
const MAX_FILE_READ_CACHE_ENTRIES = 12;
const MAX_OPERATION_HISTORY = 50;
const SETUP_PROFILE_KEY = 'votingplugin-control.setup-profiles.v1';
let fileReadCache = new Map();
let lastFileReadOperation = null;
let configurationContentPresent = false;
let inspectionInFlight = false;
let lastDiagnostics = null;
let lastOverview = null;
let operationHistoryItems = [];
let dedicatedSetupApprovals = new Map();
let pendingDetectedVoteSite = null;
let voteLoggingRestartPending = new Map();

function text(element, value) {
  element.textContent = value;
  return element;
}

const SETTINGS_SCHEMA = Object.freeze([
  {key: 'AutoCreateVoteSites', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Create a VoteSites.yml entry when an unknown service votes.'},
  {key: 'ProcessRewards', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Run configured vote rewards on this backend.'},
  {key: 'VoteLogging.Enabled', file: 'Config.yml', type: 'boolean', defaultValue: 'false', effect: 'Store supported vote events in MySQL for searches and traces.', afterApply: 'Backend restart required'},
  {key: 'VoteLogging.PurgeDays', file: 'Config.yml', type: 'integer -1 or 1–3650', defaultValue: '30', effect: 'Retention window for vote-log rows; -1 disables automatic purging.'},
  {key: 'VoteLogging.UseMainMySQL', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Reuse the main MySQL connection for vote logging.', afterApply: 'Backend restart required'},
  {key: 'CountFakeVotes', file: 'Config.yml', type: 'boolean', defaultValue: 'true', effect: 'Include explicitly generated test votes in totals.'},
  {key: 'ExtraAllSitesCheck', file: 'Config.yml', type: 'boolean', defaultValue: 'false', effect: 'Add duplicate protection for all-sites rewards.'},
  {key: 'UseBungeecord', file: 'BungeeSettings.yml', type: 'boolean', defaultValue: 'false', effect: 'Run this node as a proxy-connected backend.'},
  {key: 'BungeeMethod', file: 'BungeeSettings.yml', type: 'enum', defaultValue: 'PLUGINMESSAGING', effect: 'Select the proxy transport.'},
  {key: 'VoteSites.<site>.Enabled', file: 'VoteSites.yml', type: 'boolean', defaultValue: 'true', effect: 'Allow a configured site to resolve and reward votes.'},
  {key: 'VoteSites.<site>.ServiceSite', file: 'VoteSites.yml', type: 'text ≤200', defaultValue: '', effect: 'Match the service name supplied by the vote listener.'},
  {key: 'VoteParty.VotesRequired', file: 'SpecialRewards.yml', type: 'integer 1–100000', defaultValue: '20', effect: 'Number of votes required to trigger a vote party.'}
]);

function inspectionCapableNode() {
  const node = nodeIndex.get(selectedServerId);
  return node?.online && node.acceptedCapabilities.includes('data.inspect.v1') ? node : null;
}

function boundedLines(value, maximum = 20) {
  const lines = value.split(/\r?\n/).map(item => item.trim()).filter(Boolean);
  if (lines.length > maximum) throw new Error(`At most ${maximum} reward lines are allowed.`);
  return lines;
}

function pruneFileReadCache() {
  const cutoff = Date.now() - FILE_READ_CACHE_TTL_MS;
  for (const [key, value] of fileReadCache) if (value.loadedAt < cutoff) fileReadCache.delete(key);
  while (fileReadCache.size > MAX_FILE_READ_CACHE_ENTRIES) fileReadCache.delete(fileReadCache.keys().next().value);
}

function cachedFile(key) {
  pruneFileReadCache();
  const value = fileReadCache.get(key);
  if (!value) return null;
  fileReadCache.delete(key);
  fileReadCache.set(key, value);
  return value;
}

function cacheFile(key, content, operationId) {
  if (typeof content !== 'string') return;
  pruneFileReadCache();
  fileReadCache.delete(key);
  fileReadCache.set(key, {content, operationId, loadedAt: Date.now()});
  pruneFileReadCache();
}

function renderJsonResult(element, value, emptyMessage = 'No data returned.') {
  element.replaceChildren();
  if (value == null) {
    text(element, emptyMessage);
    return;
  }
  const pre = document.createElement('pre');
  pre.className = 'json-result';
  text(pre, JSON.stringify(value, null, 2));
  element.append(pre);
}

function renderSiteHealthResult(value) {
  renderJsonResult(siteHealthResult, value);
  const services = Array.isArray(value?.detectedUnconfiguredServices)
    ? value.detectedUnconfiguredServices.slice(0, 20) : [];
  if (services.length === 0) return;
  const actions = document.createElement('div');
  actions.className = 'detected-actions';
  actions.append(text(document.createElement('strong'), 'Create a reviewed VoteSites entry:'));
  services.forEach(service => {
    const button = text(document.createElement('button'), String(service));
    button.type = 'button';
    button.className = 'secondary compact';
    button.addEventListener('click', () => {
      const key = String(service).replace(/[^A-Za-z0-9_-]/g, '-').replace(/-+/g, '-').slice(0, 64) || 'vote-site';
      quickPreset.value = 'vote-site';
      quickName.value = key;
      quickSiteDisplayName.value = String(service).slice(0, 200);
      quickService.value = String(service).slice(0, 200);
      pendingDetectedVoteSite = {nodeId: selectedServerId, key, service: String(service).slice(0, 200)};
      selectedNodes = new Set(selectedServerId ? [selectedServerId] : []);
      loadedQuickSetup = null;
      updateQuickFields();
      clearApprovals();
      renderNodeViews();
      updatePluginSuggestions();
      setActiveTab('quick-setup', true);
      text(quickOperationStatus, 'Detected service copied into the VoteSite setup. Load the generated key to confirm it is unused, complete the URL and delay, then preview before creating it.');
      document.querySelector('#quick-setup-card').scrollIntoView({behavior: 'smooth', block: 'start'});
    });
    actions.append(button);
  });
  siteHealthResult.append(actions);
}

function downloadJson(name, value) {
  const blob = new Blob([JSON.stringify(value, null, 2)], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function runInspection(kind, filters = {}, statusElement = null) {
  const node = inspectionCapableNode();
  if (!node) throw new Error('Choose a connected backend with data inspection support.');
  if (inspectionInFlight) throw new Error('Another read-only inspection is still running.');
  const nodeId = node.nodeId;
  const sessionId = node.sessionId;
  const requestAuthenticationGeneration = authenticationGeneration;
  inspectionInFlight = true;
  updateExtendedButtons();
  if (statusElement) text(statusElement, `Queued ${kind} inspection…`);
  try {
    const boundedFilters = {};
    Object.entries(filters).forEach(([key, value]) => {
      const serialized = String(value);
      const maximum = kind === 'reward-simulation' && key === 'proposal' ? 64 * 1024 : 500;
      const size = new TextEncoder().encode(serialized).length;
      if (size > maximum) throw new Error(`${key} exceeds the bounded inspection limit.`);
      boundedFilters[key] = serialized;
    });
    let inspection = await authorized('/api/v1/inspections', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({nodeId, query: {kind, filters: boundedFilters}})
    });
    if (statusElement) text(statusElement, `Running ${kind} inspection on ${nodeId}…`);
    const deadline = Date.now() + 180_000;
    while (inspection.state === 'RUNNING') {
      if (Date.now() >= deadline) throw new Error('Inspection is still running after three minutes. Check node connectivity and try again.');
      await new Promise(resolve => window.setTimeout(resolve, 1000));
      inspection = await authorized(`/api/v1/inspections/${inspection.inspectionId}`);
    }
    if (requestAuthenticationGeneration !== authenticationGeneration
        || nodeId !== selectedServerId || sessionId !== nodeIndex.get(nodeId)?.sessionId) {
      throw new Error('The selected server changed or reconnected while the inspection ran. Run it again.');
    }
    if (inspection.state !== 'SUCCEEDED' || !inspection.result?.success) {
      throw new Error(inspection.result?.message || inspection.result?.code || 'Inspection failed.');
    }
    let envelope = inspection.result.data;
    if (typeof envelope === 'string') {
      try { envelope = JSON.parse(envelope); } catch (_) { throw new Error('The node returned malformed inspection data.'); }
    }
    if (envelope?.schemaVersion !== 1 || envelope.kind !== kind || !Object.hasOwn(envelope, 'result')) {
      throw new Error('The node returned an unsupported inspection schema.');
    }
    return envelope;
  } finally {
    inspectionInFlight = false;
    updateExtendedButtons();
  }
}

function operationPhase(operation) {
  if (operation.recovered && operation.state !== 'RUNNING') return `Recovered history · ${operation.state}`;
  if (operation.state === 'RUNNING') return 'Queued or running';
  if (operation.type === 'PREVIEW' && operation.state === 'SUCCEEDED') return 'Preview ready for approval';
  if (operation.type === 'APPLY' && operation.state === 'SUCCEEDED') return 'Applied and verified';
  if (operation.state === 'COMPLETED_WITH_ERRORS') return 'Completed with failed targets';
  return operation.state;
}

function rememberOperation(operation) {
  const summary = {...operation, results: Object.fromEntries(Object.entries(operation.results || {}).map(([nodeId, result]) =>
    [nodeId, result ? {...result, configuration: null} : result]))};
  const existing = operationHistoryItems.findIndex(item => item.operationId === operation.operationId);
  if (existing >= 0) operationHistoryItems[existing] = summary;
  else operationHistoryItems.unshift(summary);
  operationHistoryItems = operationHistoryItems.slice(0, MAX_OPERATION_HISTORY);
  renderOperationHistory();
}

function renderOperationHistory() {
  operationHistory.replaceChildren();
  if (operationHistoryItems.length === 0) {
    text(operationHistory, 'No retained configuration operations.');
    return;
  }
  operationHistoryItems.forEach(operation => {
    const item = document.createElement('article');
    item.className = 'result-item';
    const heading = document.createElement('div');
    heading.className = 'section-title';
    const identity = document.createElement('div');
    identity.append(text(document.createElement('strong'), `${operation.type} · ${operationPhase(operation)}`));
    identity.append(text(document.createElement('small'), `${operation.operationId}${operation.sourceOperationId
      ? ` · retry of ${operation.sourceOperationId}` : ''}${operation.recovered ? ' · recovered after restart' : ''}`));
    heading.append(identity);
    const actions = document.createElement('div');
    actions.className = 'operation-actions';
    const alreadyRetried = operationHistoryItems.some(item => item.sourceOperationId === operation.operationId);
    if (operation.retryable && !alreadyRetried) {
      const retry = text(document.createElement('button'), 'Retry failed targets');
      retry.type = 'button';
      retry.className = 'secondary compact';
      retry.addEventListener('click', async () => {
        retry.disabled = true;
        try {
          const retried = await authorized(`/api/v1/operations/${operation.operationId}/retry`, {method: 'POST'});
          const completed = await waitForOperation(retried, operationStatus);
          if (completed.type === 'APPLY') {
            fileReadCache.clear();
            lastFileReadOperation = null;
            approvedPreview = null;
            approvedFilePreview = null;
            approvedQuickPreview = null;
            dedicatedSetupApprovals.clear();
            inputGeneration++;
            updateConfigurationButtons();
            updateExtendedButtons();
          }
          setActiveTab('activity', true);
        } catch (error) {
          text(message, error.code === 'PREVIEW_REQUIRED'
            ? 'That apply needs a fresh preview because the failed targets may have changed.' : error.message);
        } finally { retry.disabled = false; }
      });
      actions.append(retry);
    }
    if (operation.type === 'PREVIEW' && operation.state === 'SUCCEEDED' && operation.approvalToken
        && operation.configuration?.domain === 'quick-setup'
        && !['proxy-method', 'reward-builder', 'sync-vote-sites'].includes(operation.configuration?.preset)) {
      const approve = text(document.createElement('button'), 'Approve this preview');
      approve.type = 'button';
      approve.className = 'compact';
      approve.addEventListener('click', async () => {
        if (!window.confirm('Apply this exact completed preview? Review every listed node change before continuing.')) return;
        approve.disabled = true;
        try {
          const applied = await startConfigurationOperation('/api/v1/configuration/apply', {
            previewOperationId: operation.operationId, approvalToken: operation.approvalToken
          }, operationStatus);
          if (applied.state === 'SUCCEEDED') {
            fileReadCache.clear();
            lastFileReadOperation = null;
            updateExtendedButtons();
          }
          await loadOperationHistory();
        } catch (error) { text(message, error.message); }
        finally { approve.disabled = false; }
      });
      actions.append(approve);
    }
    if (actions.childElementCount > 0) heading.append(actions);
    const detail = document.createElement('pre');
    text(detail, operationSummary(operation));
    item.append(heading, detail);
    operationHistory.append(item);
  });
}

async function loadOperationHistory() {
  if (!authenticated) return;
  try {
    const body = await authorized('/api/v1/operations');
    const retainedOperations = Array.isArray(body.items) ? body.items : [];
    operationHistoryItems = retainedOperations.slice(0, MAX_OPERATION_HISTORY).map(operation =>
      ({...operation, results: Object.fromEntries(Object.entries(operation.results || {}).map(([nodeId, result]) =>
        [nodeId, result ? {...result, configuration: null} : result]))}));
    const pendingRestarts = new Map();
    const restartSessions = body.voteLoggingRestartSessions;
    if (restartSessions && typeof restartSessions === 'object' && !Array.isArray(restartSessions)) {
      Object.entries(restartSessions).slice(0, 10_000).forEach(([nodeId, sessionId]) => {
        if (typeof sessionId === 'string') pendingRestarts.set(nodeId, sessionId);
      });
    } else {
      retainedOperations.forEach(operation => {
        if (operation.type !== 'APPLY' || operation.configuration?.preset !== 'vote-logging') return;
        Object.entries(operation.results || {}).forEach(([nodeId, result]) => {
          if (result?.success && !pendingRestarts.has(nodeId)) {
            pendingRestarts.set(nodeId, result.sessionId || 'unknown');
          }
        });
      });
    }
    voteLoggingRestartPending = pendingRestarts;
    renderOperationHistory();
    updateSetupChecklist();
  } catch (error) {
    text(operationHistory, error.message || 'Operation history could not be loaded.');
  }
}

function renderSettingsCatalog() {
  const query = settingsFilter.value.trim().toLowerCase();
  const rows = SETTINGS_SCHEMA.filter(setting => Object.values(setting).join(' ').toLowerCase().includes(query));
  settingsCatalog.replaceChildren(...rows.map(setting => {
    const row = document.createElement('tr');
    [setting.key, setting.file, setting.type, setting.defaultValue || '—', setting.effect,
      setting.afterApply || (setting.file === 'BungeeSettings.yml'
        ? 'Connector/runtime may restart' : 'VotingPlugin reload')].forEach(value => {
      row.append(text(document.createElement('td'), value));
    });
    return row;
  }));
}

function readProfiles() {
  try {
    const value = JSON.parse(localStorage.getItem(SETUP_PROFILE_KEY) || '{}');
    const safe = Object.create(null);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      Object.entries(value).slice(0, 20).forEach(([name, profile]) => {
        if (name.length <= 60 && profile && typeof profile === 'object' && !Array.isArray(profile)) safe[name] = profile;
      });
    }
    return safe;
  } catch (_) { return Object.create(null); }
}

function writeProfiles(profiles) {
  const serialized = JSON.stringify(profiles);
  if (serialized.length > 1024 * 1024) throw new Error('Setup profiles exceed the 1 MiB browser-local limit.');
  localStorage.setItem(SETUP_PROFILE_KEY, serialized);
}

function currentProfileValues() {
  return {
    version: 1, preset: quickPreset.value, name: quickName.value, method: quickMethod.value,
    siteDisplayName: quickSiteDisplayName.value, service: quickService.value, url: quickUrl.value,
    delay: quickDelay.value, priority: quickSitePriority.value, material: quickSiteMaterial.value,
    siteEnabled: quickSiteEnabled.checked, siteHidden: quickSiteHidden.checked,
    rewardScope: quickRewardScope.value, command: quickCommand.value, playerMessage: quickMessage.value,
    processRewards: quickProcessRewards.checked, autoSites: quickAutoSites.checked,
    extraCheck: quickExtraCheck.checked, countFake: quickCountFake.checked,
    hideWarning: quickHideSiteWarning.checked, disableUpdates: quickDisableUpdates.checked,
    partyVotes: quickPartyVotes.value, partyCommand: quickPartyCommand.value,
    partyBroadcast: quickPartyBroadcast.value, partyAll: quickPartyAll.checked, partyOnline: quickPartyOnline.checked,
    autoSitesOnly: quickAutoSitesOnly.checked, voteLogging: quickVoteLoggingEnabled.checked,
    voteLoggingDays: quickVoteLoggingDays.value, voteLoggingMainMysql: quickVoteLoggingMainMysql.checked,
    rewardBuilder: {scope: rewardScope.value, site: rewardSite.value, chance: rewardChance.value,
      money: rewardMoney.value, commands: rewardCommands.value, messages: rewardMessages.value,
      broadcasts: rewardBroadcasts.value, permissions: rewardPermissions.value, items: rewardItems.value,
      onlineOnly: rewardOnlineOnly.checked}
  };
}

function populateProfilePicker() {
  const profiles = readProfiles();
  const current = profilePicker.value;
  const placeholder = text(document.createElement('option'), 'Choose a profile');
  placeholder.value = '';
  profilePicker.replaceChildren(placeholder, ...Object.keys(profiles).sort().map(name => {
    const option = text(document.createElement('option'), name);
    option.value = name;
    return option;
  }));
  profilePicker.value = Object.hasOwn(profiles, current) ? current : '';
  loadProfile.disabled = !profilePicker.value;
  deleteProfile.disabled = !profilePicker.value;
}

async function loadHealth() {
  try {
    const response = await fetch('/api/v1/health', {cache: 'no-store'});
    if (!response.ok) throw new Error('health');
    const body = await response.json();
    text(health, `Online · ${body.identity.applicationVersion} · protocol ${body.identity.protocolVersion}`);
    health.classList.add('online');
  } catch (_) {
    text(health, 'Unavailable');
    health.classList.remove('online');
  }
}

async function loadSetupState() {
  try {
    const response = await fetch('/api/v1/auth/setup', {cache: 'no-store'});
    if (!response.ok) throw new Error('First-run setup status is unavailable.');
    const body = await response.json();
    setupRequired = Boolean(body.required);
    setupCard.hidden = !setupRequired;
    authCard.hidden = setupRequired;
    if (setupRequired) {
      enrollmentCard.hidden = true;
      text(setupMessage, `Enter the one-time code from ${body.codeFile} inside the configured hosted data directory.`);
    }
    return setupRequired;
  } catch (error) {
    setupRequired = false;
    setupCard.hidden = true;
    authCard.hidden = false;
    text(message, error.message || 'First-run setup status is unavailable.');
    return false;
  }
}

function applyAuthenticatedSession(body) {
  authenticated = true;
  csrfToken = body.csrfToken;
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  loadedQuickSetup = null;
  selectedNodes.clear();
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  proxyMethodProxyId = '';
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentValue = '';
  fileReadCache.clear();
  lastFileReadOperation = null;
  lastDiagnostics = null;
  lastOverview = null;
  operationHistoryItems = [];
  dedicatedSetupApprovals.clear();
  voteLoggingRestartPending.clear();
  pendingDetectedVoteSite = null;
  configurationContent.value = '';
  configurationContentPresent = false;
  inputGeneration++;
  logout.hidden = false;
  authCard.hidden = true;
  welcome.hidden = true;
  appShell.hidden = false;
  serverPickerLabel.hidden = false;
  enrollmentCard.hidden = false;
  pageOffset = 0;
  renderOperationHistory();
  populateProfilePicker();
  setActiveTab(tabFromHash());
}

function isProxy(node) {
  return ['VELOCITY', 'BUNGEECORD'].includes(String(node.platform).toUpperCase()) ||
    node.acceptedCapabilities.includes('config.proxy-routing.v1');
}

function isBackend(node) {
  return !isProxy(node);
}

function roleLabel(node) {
  return isProxy(node) ? 'Proxy' : 'Backend';
}

function platformLabel(platform) {
  const normalized = String(platform || '').toUpperCase();
  if (normalized === 'BUKKIT') return 'Bukkit';
  if (normalized === 'BUNGEECORD') return 'BungeeCord';
  if (normalized === 'VELOCITY') return 'Velocity';
  return platform || 'Unspecified platform';
}

function friendlyCapability(capability) {
  return ({
    'config.files.v1': 'Full configuration',
    'config.file-comments.v1': 'Comments preserved',
    'config.vote-sites-sync.v1': 'VoteSites sync',
    'config.transport-test.v1': 'Communication test',
    'config.proxy-method.v1': 'Proxy method',
    'config.quick-setup.v1': 'Setup assistant',
    'config.proxy-routing.v1': 'Proxy routing',
    'data.inspect.v1': 'Read-only data inspection'
  })[capability];
}

function managedCapabilities(node) {
  return node.acceptedCapabilities.map(friendlyCapability).filter(Boolean);
}

function proxyReportsFor(backendId) {
  return allNodeItems.filter(node => isProxy(node) && node.online).filter(proxy =>
    (Array.isArray(proxy.backends) ? proxy.backends : []).some(backend => backend.backendId === backendId));
}

function backendCard(backend, reporterOnline) {
  const item = document.createElement('li');
  const title = text(document.createElement('strong'), backend.displayName);
  const details = document.createElement('div');
  details.className = 'backend-state';
  const registered = nodeIndex.get(backend.backendId);
  details.append(text(document.createElement('span'), !enrollmentsLoaded
    ? 'Control enrollment unavailable'
    : enrollmentIds.has(backend.backendId)
    ? `Enrolled in Control · ${registered ? (registered.online ? 'Control connected' : 'Control disconnected') : 'not registered'}`
    : 'Not enrolled in Control'));
  if (!reporterOnline) {
    details.append(text(document.createElement('span'), 'Presence stale · reporting proxy disconnected'));
  } else if (backend.presenceKnown) {
    details.append(text(document.createElement('span'), backend.available
      ? `Minecraft reachable · ${backend.playerCount} ${backend.playerCount === 1 ? 'player' : 'players'}`
      : 'Minecraft unavailable'));
  } else {
    details.append(text(document.createElement('span'), 'Presence not available'));
  }
  details.append(text(document.createElement('span'), backend.backendId));
  item.append(title, details);
  return item;
}

function nodeCard(node) {
  const article = document.createElement('article');
  article.className = `node${selectedServerId === node.nodeId ? ' selected' : ''}`;
  article.dataset.nodeId = node.nodeId;
  const header = document.createElement('div');
  header.className = 'node-header';
  const identity = document.createElement('div');
  const title = text(document.createElement('h3'), node.displayName);
  identity.append(title, text(document.createElement('span'), node.nodeId));
  identity.lastElementChild.className = 'node-id';
  const state = text(document.createElement('span'), node.online ? 'Control connected' : 'Control disconnected');
  state.className = `pill ${node.online ? 'online' : 'offline'}`;
  header.append(identity, state);

  const meta = document.createElement('div');
  meta.className = 'node-meta';
  [roleLabel(node), platformLabel(node.platform), `VotingPlugin ${node.pluginVersion}`,
    ...managedCapabilities(node)].forEach(value => {
    const pill = text(document.createElement('span'), value);
    pill.className = 'pill neutral';
    meta.append(pill);
  });

  const plugins = Array.isArray(node.detectedPlugins) ? node.detectedPlugins : [];
  let detail;
  if (isBackend(node)) {
    detail = text(document.createElement('p'), plugins.length
      ? `Detected plugins: ${plugins.join(', ')}` : 'Plugin inventory is not available for this backend.');
  } else {
    const backends = Array.isArray(node.backends) ? node.backends : [];
    detail = text(document.createElement('p'), `${backends.length} configured ${backends.length === 1 ? 'backend' : 'backends'} reported.`);
  }
  detail.className = 'node-detail';

  const selector = document.createElement('label');
  selector.className = 'node-select';
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  const controllable = ['config.proxy-routing.v1', 'config.files.v1', 'config.quick-setup.v1']
    .some(capability => node.acceptedCapabilities.includes(capability));
  checkbox.disabled = !node.online || !controllable || node.nodeId === selectedServerId;
  checkbox.checked = selectedNodes.has(node.nodeId);
  if (node.nodeId === selectedServerId) selector.title = 'The primary server remains included in configuration changes.';
  checkbox.addEventListener('change', () => {
    if (checkbox.checked && selectedNodes.size >= MAX_CONFIGURATION_TARGETS) {
      checkbox.checked = false;
      text(operationStatus, `At most ${MAX_CONFIGURATION_TARGETS} servers can be configured at once.`);
      return;
    }
    if (checkbox.checked) selectedNodes.add(node.nodeId); else selectedNodes.delete(node.nodeId);
    approvedPreview = null;
    approvedFilePreview = null;
    approvedQuickPreview = null;
    dedicatedSetupApprovals.clear();
    inputGeneration++;
    updatePluginSuggestions();
    renderSelectedServer();
    updateConfigurationButtons();
    updateExtendedButtons();
  });
  selector.append(checkbox, document.createTextNode('Include in configuration changes'));

  const list = document.createElement('ul');
  list.className = 'node-backends';
  if (isProxy(node)) {
    const backends = Array.isArray(node.backends) ? node.backends : [];
    if (backends.length === 0) {
      list.append(text(document.createElement('li'), 'No configured backends reported by this proxy.'));
    } else {
      backends.forEach(backend => list.append(backendCard(backend, node.online)));
    }
  } else {
    const proxies = proxyReportsFor(node.nodeId);
    list.append(text(document.createElement('li'), proxies.length
      ? `Reported by ${proxies.map(proxy => proxy.displayName).join(', ')}.`
      : 'No connected proxy reports this backend ID.'));
  }
  article.append(header, meta, detail, list, selector);
  return article;
}

function tabFromHash() {
  const requested = window.location.hash.replace(/^#/, '');
  return tabPanels.some(panel => panel.dataset.panel === requested) ? requested : 'overview';
}

function setActiveTab(tab, updateHash = false) {
  if (!tabPanels.some(panel => panel.dataset.panel === tab)) tab = 'overview';
  tabButtons.forEach(button => button.setAttribute('aria-selected', String(button.dataset.tab === tab)));
  tabPanels.forEach(panel => { panel.hidden = panel.dataset.panel !== tab; });
  if (updateHash && window.location.hash !== `#${tab}`) window.history.replaceState(null, '', `#${tab}`);
}

function setConfigView(view) {
  if (!configViewPanels.some(panel => panel.dataset.configPanel === view)) view = 'easy';
  configViewButtons.forEach(button => button.classList.toggle('active', button.dataset.configView === view));
  configViewPanels.forEach(panel => { panel.hidden = panel.dataset.configPanel !== view; });
}

function chooseDefaultServer(items) {
  return items.find(node => isBackend(node) && node.online) || items.find(node => isBackend(node)) ||
    items.find(node => node.online) || items[0] || null;
}

function renderServerPicker() {
  const previousValue = selectedServerId;
  const ordered = [...allNodeItems].sort((left, right) => {
    const roleOrder = Number(isProxy(left)) - Number(isProxy(right));
    return roleOrder || left.displayName.localeCompare(right.displayName);
  });
  const placeholder = text(document.createElement('option'), ordered.length ? 'Choose a server' : 'No servers registered');
  placeholder.value = '';
  serverPicker.replaceChildren(placeholder, ...ordered.map(node => {
    const option = text(document.createElement('option'),
      `${node.displayName} · ${roleLabel(node)} · ${node.online ? 'connected' : 'disconnected'}`);
    option.value = node.nodeId;
    return option;
  }));
  if (!nodeIndex.has(previousValue)) {
    selectedServerId = chooseDefaultServer(ordered)?.nodeId || '';
    if (previousValue) {
      loadedQuickSetup = null;
      resetServerConfigurationForms('The selected server is no longer available. Read the replacement server before previewing changes.');
    }
  }
  serverPicker.value = selectedServerId;
}

function renderSelectedServer() {
  const selected = nodeIndex.get(selectedServerId);
  selectedServerCapabilities.replaceChildren();
  if (!selected) {
    text(selectedServerName, 'Choose a VotingPlugin server');
    text(selectedServerState, 'No selection');
    selectedServerState.className = 'pill neutral';
    text(selectedServerSummary, 'Use the server picker above to keep configuration and setup actions focused on one node.');
    text(configurationContext, 'Choose a backend from the server picker to work with its VotingPlugin configuration.');
    text(commentPreservationState, 'Comment support unknown');
    commentPreservationState.className = 'pill warning';
    return;
  }
  text(selectedServerName, selected.displayName);
  text(selectedServerState, selected.online ? 'Control connected' : 'Control disconnected');
  selectedServerState.className = `pill ${selected.online ? 'online' : 'offline'}`;
  const relationships = isBackend(selected) ? proxyReportsFor(selected.nodeId) : [];
  const relationshipText = relationships.length
    ? ` Reported by ${relationships.map(proxy => proxy.displayName).join(', ')}.` : '';
  text(selectedServerSummary,
    `${roleLabel(selected)} · ${platformLabel(selected.platform)} · VotingPlugin ${selected.pluginVersion}.${relationshipText}`);
  text(configurationContext,
    `${selected.displayName} (${selected.nodeId}) · ${roleLabel(selected)} · ${selected.online ? 'Control connected' : 'Control disconnected'}`);
  const fileTargets = targets('config.files.v1');
  const preservesComments = fileTargets.length > 0 && fileTargets.every(nodeId =>
    nodeCapabilities.get(nodeId)?.includes('config.file-comments.v1'));
  text(commentPreservationState, preservesComments ? 'Comments preserved for every target' : 'Comments not guaranteed for every target');
  commentPreservationState.className = `pill ${preservesComments ? 'online' : 'warning'}`;
  const capabilities = managedCapabilities(selected);
  if (capabilities.length === 0) capabilities.push('Discovery only');
  capabilities.forEach(value => {
    const pill = text(document.createElement('span'), value);
    pill.className = 'pill';
    selectedServerCapabilities.append(pill);
  });
}

function topologyLink(backend, reporterOnline) {
  const registered = nodeIndex.get(backend.backendId);
  const link = document.createElement('span');
  link.className = `topology-link ${registered?.online ? 'online' : 'warning'}`;
  link.append(text(document.createElement('strong'), backend.displayName));
  link.append(text(document.createElement('small'), !enrollmentsLoaded
    ? 'Control enrollment unavailable'
    : enrollmentIds.has(backend.backendId)
    ? `Enrolled · Control ${registered ? (registered.online ? 'connected' : 'disconnected') : 'not registered'}`
    : 'Not enrolled in Control'));
  if (!reporterOnline) {
    link.append(text(document.createElement('small'), 'Presence stale · reporting proxy disconnected'));
  } else if (backend.presenceKnown) {
    link.append(text(document.createElement('small'), backend.available
      ? `Minecraft reachable · ${backend.playerCount} ${backend.playerCount === 1 ? 'player' : 'players'}`
      : 'Minecraft unavailable'));
  } else {
    link.append(text(document.createElement('small'), 'Presence not available'));
  }
  return link;
}

function renderTopology() {
  const proxies = allNodeItems.filter(isProxy);
  topology.replaceChildren();
  topology.classList.toggle('empty', proxies.length === 0);
  if (proxies.length === 0) {
    text(topology, 'No proxy nodes have registered with Control.');
    return;
  }
  proxies.forEach(proxy => {
    const row = document.createElement('article');
    row.className = 'topology-row';
    const proxyIdentity = document.createElement('div');
    proxyIdentity.className = 'topology-proxy';
    proxyIdentity.append(text(document.createElement('strong'), proxy.displayName));
    proxyIdentity.append(text(document.createElement('small'),
      `${platformLabel(proxy.platform)} proxy · Control ${proxy.online ? 'connected' : 'disconnected'}`));
    const backendList = document.createElement('div');
    backendList.className = 'topology-backends';
    const backends = Array.isArray(proxy.backends) ? proxy.backends : [];
    if (backends.length === 0) {
      backendList.append(text(document.createElement('small'), 'No configured backends reported by this proxy.'));
    } else {
      backends.forEach(backend => backendList.append(topologyLink(backend, proxy.online)));
    }
    row.append(proxyIdentity, backendList);
    topology.append(row);
  });
  if (backendTopologyTruncated) {
    const warning = text(document.createElement('p'), 'Backend topology is truncated; some proxy relationships are not shown.');
    warning.className = 'warning-text';
    topology.prepend(warning);
  }
}

function renderMetrics() {
  const backendIds = new Set(allNodeItems.filter(isProxy).flatMap(node =>
    (Array.isArray(node.backends) ? node.backends : []).map(backend => backend.backendId)));
  const issueIds = new Set(allNodeItems.filter(node => !node.online).map(node => node.nodeId));
  backendIds.forEach(backendId => { if (!nodeIndex.has(backendId)) issueIds.add(backendId); });
  if (enrollmentsLoaded) {
    backendIds.forEach(backendId => { if (!enrollmentIds.has(backendId)) issueIds.add(backendId); });
  }
  allNodeItems.filter(node => isProxy(node) && node.online).forEach(proxy =>
    (Array.isArray(proxy.backends) ? proxy.backends : []).forEach(backend => {
      if (backend.presenceKnown && !backend.available) issueIds.add(backend.backendId);
    }));
  text(metricNodes, allNodeItems.length);
  text(metricOnline, allNodeItems.filter(node => node.online).length);
  text(metricBackends, backendTopologyTruncated ? `${backendIds.size}+` : backendIds.size);
  const issueCount = issueIds.size;
  text(metricIssues, backendTopologyTruncated ? `${issueCount}+` : issueCount);
}

function syncSourceCandidates() {
  return allNodeItems.filter(node => isBackend(node) && node.online &&
    node.acceptedCapabilities.includes('config.files.v1') &&
    node.acceptedCapabilities.includes('config.file-comments.v1'));
}

function syncTargetCandidates() {
  return allNodeItems.filter(node => isBackend(node) && node.online &&
    node.acceptedCapabilities.includes('config.vote-sites-sync.v1'));
}

function selectedVoteSitesTargets() {
  const capable = new Set(syncTargetCandidates().map(node => node.nodeId));
  return [...voteSitesTargetIds].filter(nodeId => capable.has(nodeId) && nodeId !== voteSitesSourceId);
}

function invalidateVoteSitesSyncPreview(message) {
  if (approvedQuickPreview?.workflow === 'sync-vote-sites') approvedQuickPreview = null;
  if (quickPreset.value !== 'sync-vote-sites') return;
  inputGeneration++;
  text(quickOperationStatus, message);
}

function renderVoteSitesSync() {
  const sources = syncSourceCandidates();
  const targetsAvailable = syncTargetCandidates();
  const previousSourceId = voteSitesSourceId;
  if (!sources.some(node => node.nodeId === voteSitesSourceId)) {
    voteSitesSourceId = sources.find(node => node.nodeId === selectedServerId)?.nodeId || sources[0]?.nodeId || '';
  }
  if (previousSourceId && previousSourceId !== voteSitesSourceId) {
    invalidateVoteSitesSyncPreview('The sync source became unavailable. Read the replacement source and preview again.');
  }
  const targetIds = new Set(targetsAvailable.map(node => node.nodeId));
  const retainedTargets = new Set([...voteSitesTargetIds].filter(nodeId =>
    targetIds.has(nodeId) && nodeId !== voteSitesSourceId));
  if (retainedTargets.size !== voteSitesTargetIds.size) {
    invalidateVoteSitesSyncPreview('A sync target became unavailable. Preview again before syncing.');
  }
  voteSitesTargetIds = retainedTargets;
  if (!voteSitesTargetsInitialized && sources.length > 0) {
    voteSitesTargetIds = new Set(targetsAvailable.map(node => node.nodeId)
      .filter(nodeId => nodeId !== voteSitesSourceId).slice(0, MAX_SYNC_TARGETS));
    voteSitesTargetsInitialized = true;
  }

  const placeholder = text(document.createElement('option'), sources.length ? 'Choose a source' : 'No readable backends');
  placeholder.value = '';
  voteSitesSource.replaceChildren(placeholder, ...sources.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  voteSitesSource.value = voteSitesSourceId;

  voteSitesTargets.replaceChildren();
  const choices = targetsAvailable.filter(node => node.nodeId !== voteSitesSourceId);
  voteSitesTargets.classList.toggle('empty', choices.length === 0);
  if (choices.length === 0) {
    text(voteSitesTargets, targetsAvailable.length > 0
      ? 'Choose a different source or enroll another backend.' : 'No sync-capable target backends are connected.');
  } else {
    choices.forEach(node => {
      const label = document.createElement('label');
      label.className = 'target-option';
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = voteSitesTargetIds.has(node.nodeId);
      checkbox.addEventListener('change', () => {
        if (checkbox.checked && voteSitesTargetIds.size >= MAX_SYNC_TARGETS) {
          checkbox.checked = false;
          text(quickOperationStatus, `A sync operation supports at most ${MAX_SYNC_TARGETS} targets.`);
          return;
        }
        if (checkbox.checked) voteSitesTargetIds.add(node.nodeId); else voteSitesTargetIds.delete(node.nodeId);
        approvedQuickPreview = null;
        inputGeneration++;
        text(quickOperationStatus, 'Targets changed. Read the source and preview again before syncing.');
        updateConfigurationButtons();
      });
      label.append(checkbox, document.createTextNode(`${node.displayName} · ${node.nodeId}`));
      voteSitesTargets.append(label);
    });
  }
  const readyCount = targetsAvailable.length;
  text(voteSitesSyncCapability, readyCount > 0
    ? `${readyCount} sync-capable ${readyCount === 1 ? 'backend' : 'backends'}` : 'Waiting for capable backends');
  voteSitesSyncCapability.className = `pill ${readyCount > 0 ? 'online' : 'neutral'}`;
}

function transportTestProxies() {
  return allNodeItems.filter(node => isProxy(node) && node.online &&
    node.acceptedCapabilities.includes('config.transport-test.v1'));
}

function renderTransportTest() {
  const proxies = transportTestProxies();
  if (!proxies.some(node => node.nodeId === transportTestProxyId)) {
    transportTestProxyId = proxies.find(node => node.nodeId === selectedServerId)?.nodeId || proxies[0]?.nodeId || '';
    transportTestBackendId = '';
  }
  const proxy = proxies.find(node => node.nodeId === transportTestProxyId);
  const reported = Array.isArray(proxy?.backends) ? proxy.backends : [];
  const backendChoices = new Map(reported.map(backend => [backend.backendId, backend]));
  allNodeItems.filter(isBackend).forEach(node => {
    if (!backendChoices.has(node.nodeId)) backendChoices.set(node.nodeId,
      {backendId: node.nodeId, displayName: node.displayName});
  });
  const backends = [...backendChoices.values()];
  if (!backends.some(backend => backend.backendId === transportTestBackendId)) {
    transportTestBackendId = backends[0]?.backendId || '';
  }

  const proxyPlaceholder = text(document.createElement('option'), proxies.length ? 'Choose a proxy' : 'No capable proxies');
  proxyPlaceholder.value = '';
  transportTestProxy.replaceChildren(proxyPlaceholder, ...proxies.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  transportTestProxy.value = transportTestProxyId;

  const backendPlaceholder = text(document.createElement('option'), backends.length ? 'Choose a backend' : 'No backends reported');
  backendPlaceholder.value = '';
  transportTestBackend.replaceChildren(backendPlaceholder, ...backends.map(backend => {
    const option = text(document.createElement('option'), `${backend.displayName} · ${backend.backendId}`);
    option.value = backend.backendId;
    return option;
  }));
  transportTestBackend.value = transportTestBackendId;

  text(transportTestCapability, proxies.length > 0
    ? `${proxies.length} test-capable ${proxies.length === 1 ? 'proxy' : 'proxies'}` : 'Waiting for a capable proxy');
  transportTestCapability.className = `pill ${proxies.length > 0 ? 'online' : 'neutral'}`;
}

function proxyMethodCandidates() {
  return allNodeItems.filter(node => isProxy(node) && node.online &&
    node.acceptedCapabilities.includes('config.proxy-method.v1'));
}

function proxyMethodNetworkFor(items, truncatedNodeIds, proxyId) {
  const index = new Map(items.map(node => [node.nodeId, node]));
  const proxy = index.get(proxyId);
  const proxyReady = Boolean(proxy?.online && proxy.acceptedCapabilities.includes('config.proxy-method.v1'));
  const reported = Array.isArray(proxy?.backends) ? proxy.backends : [];
  const backends = reported.map(backend => index.get(backend.backendId)).filter(Boolean);
  const unavailable = reported.filter(backend => {
    const node = index.get(backend.backendId);
    return !node || !isBackend(node) || !node.online || !node.acceptedCapabilities.includes('config.proxy-method.v1');
  });
  return {proxy, proxyReady, reported, backends, unavailable, topologyComplete: !truncatedNodeIds.has(proxyId),
    nodeIds: proxy ? [proxy.nodeId, ...backends.map(node => node.nodeId)] : []};
}

function proxyMethodNetwork() {
  return proxyMethodNetworkFor(allNodeItems, backendTopologyTruncatedNodeIds, proxyMethodProxyId);
}

function proxyMethodNetworkSignature(network) {
  return JSON.stringify({proxyReady: network.proxyReady, topologyComplete: network.topologyComplete, nodeIds: [...network.nodeIds].sort(),
    unavailable: network.unavailable.map(backend => backend.backendId).sort()});
}

function renderProxyMethod() {
  const proxies = proxyMethodCandidates();
  if (!proxies.some(node => node.nodeId === proxyMethodProxyId)) {
    proxyMethodProxyId = proxies.find(node => node.nodeId === selectedServerId)?.nodeId || proxies[0]?.nodeId || '';
  }
  const placeholder = text(document.createElement('option'), proxies.length ? 'Choose a proxy' : 'No capable proxies');
  placeholder.value = '';
  proxyMethodProxy.replaceChildren(placeholder, ...proxies.map(node => {
    const option = text(document.createElement('option'), `${node.displayName} · ${node.nodeId}`);
    option.value = node.nodeId;
    return option;
  }));
  proxyMethodProxy.value = proxyMethodProxyId;
  const network = proxyMethodNetwork();
  if (proxyMethodCurrentFor !== proxyMethodProxyId
      || proxyMethodCurrentSessionId !== (network.proxy?.sessionId || '')) {
    proxyMethodCurrentFor = '';
    proxyMethodCurrentSessionId = '';
    proxyMethodCurrentValue = '';
  }
  const ready = network.proxyReady && network.topologyComplete && network.reported.length > 0 &&
    network.nodeIds.length <= MAX_OPERATION_TARGETS && network.unavailable.length === 0;
  const description = !network.proxyReady ? 'Waiting for a connected, capable proxy'
    : !network.topologyComplete ? 'Backend topology is truncated; switching disabled'
    : network.nodeIds.length > MAX_OPERATION_TARGETS ? `Network exceeds the ${MAX_OPERATION_TARGETS}-node operation limit`
    : network.reported.length === 0 ? 'No backends reported'
    : network.unavailable.length > 0 ? `${network.unavailable.length} backends unavailable`
    : `${network.nodeIds.length} nodes ready`;
  text(proxyMethodCapability, description);
  proxyMethodCapability.className = `pill ${ready ? 'online' : 'neutral'}`;
  text(proxyMethodCurrent, proxyMethodCurrentValue ? `Active: ${proxyMethodCurrentValue}` : 'Active method unknown');
  proxyMethodCurrent.className = `pill ${proxyMethodCurrentValue ? 'online' : 'neutral'}`;
  proxyMethodButtons.forEach(button => {
    const active = button.dataset.proxyMethod === proxyMethodCurrentValue;
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', String(active));
  });
}

function updateSetupChecklist(overview = lastOverview) {
  const node = nodeIndex.get(selectedServerId);
  const loggingRestartPending = voteLoggingRestartRequired();
  const steps = [...setupChecklist.querySelectorAll('li')];
  const states = [
    Boolean(node?.online && isBackend(node)),
    Boolean(overview && typeof overview.proxyMode === 'boolean'),
    Boolean(overview && Number.isFinite(Number(overview.configuredVoteSites))),
    Boolean(overview?.processRewards),
    Boolean(overview?.dataStorage && !loggingRestartPending
      && (!overview.voteLoggingEnabled || overview.voteLogReadable === true)),
    Boolean(overview && (!overview.proxyMode || allNodeItems.some(item => isProxy(item) && item.online
      && item.acceptedCapabilities.includes('config.transport-test.v1'))))
  ];
  steps.forEach((step, index) => {
    step.classList.toggle('complete', states[index]);
    text(step.querySelector('.step-state'), states[index] ? '✓' : String(index + 1));
  });
  const complete = states.filter(Boolean).length;
  const loggingStatus = loggingRestartPending
    ? ' Vote logging configuration was saved, but it is not considered live until this backend restarts and reconnects.'
    : overview?.voteLoggingEnabled === false
    ? ' Vote logging is optional and currently disabled.'
    : overview?.voteLoggingEnabled && overview.voteLogReadable !== true
    ? ' Vote logging is enabled but its MySQL table is not readable.' : '';
  text(setupChecklistStatus, `${complete} of ${states.length} readiness checks complete.${loggingStatus}`);
}

function updateExtendedButtons() {
  const node = nodeIndex.get(selectedServerId);
  const inspectionReady = authenticated && Boolean(inspectionCapableNode()) && !inspectionInFlight;
  const backendTargets = backendQuickTargets();
  const allQuickBackends = allNodeItems.filter(item => isBackend(item) && item.online
    && item.acceptedCapabilities.includes('config.quick-setup.v1')).slice(0, MAX_CONFIGURATION_TARGETS);
  const quickReady = authenticated && Boolean(node?.online && isBackend(node)
    && node.acceptedCapabilities.includes('config.quick-setup.v1')) && backendTargets.length > 0
    && configurationOperationsInFlight === 0;
  const fileTargets = targets('config.files.v1');
  const driftReady = authenticated && fileTargets.length >= 2 && configurationOperationsInFlight === 0;
  runNetworkDoctor.disabled = !inspectionReady;
  downloadNetworkDiagnostics.disabled = !lastDiagnostics;
  refreshSetupChecklist.disabled = !inspectionReady;
  refreshDataOverview.disabled = !inspectionReady;
  lookupPlayer.disabled = !inspectionReady;
  loadSiteHealth.disabled = !inspectionReady;
  loadVoteLogSummary.disabled = !inspectionReady;
  searchVoteLog.disabled = !inspectionReady;
  traceVote.disabled = !inspectionReady;
  resolveSite.disabled = !inspectionReady;
  simulateReward.disabled = !inspectionReady;
  previewReward.disabled = !quickReady;
  applyReward.disabled = !quickReady || !dedicatedSetupApprovals.get('reward-builder');
  loadAutoSites.disabled = !quickReady;
  previewAutoSites.disabled = !quickReady;
  applyAutoSites.disabled = !quickReady || !dedicatedSetupApprovals.get('auto-create-vote-sites');
  selectAllAutoSitesTargets.disabled = !authenticated || allQuickBackends.length === 0
    || configurationOperationsInFlight > 0;
  text(autoSitesTargetCount, `${backendTargets.length} selected ${backendTargets.length === 1 ? 'backend' : 'backends'}`);
  loadVoteLogging.disabled = !quickReady;
  previewVoteLogging.disabled = !quickReady;
  applyVoteLogging.disabled = !quickReady || !dedicatedSetupApprovals.get('vote-logging');
  runDriftCheck.disabled = !driftReady;
  createSnapshot.disabled = !lastFileReadOperation;
  const inspectionMessage = inspectionReady ? 'Read-only inspection available' : 'Choose an inspection-capable node';
  text(networkDoctorCapability, inspectionMessage);
  networkDoctorCapability.className = `pill ${inspectionReady ? 'online' : 'neutral'}`;
  const rewardReady = inspectionReady && quickReady;
  text(rewardSimulationCapability, rewardReady ? 'Simulation and preview/apply available'
    : inspectionReady ? 'Simulation available; configuration write unavailable' : 'Choose a capable node');
  rewardSimulationCapability.className = `pill ${rewardReady ? 'online' : 'neutral'}`;
  text(driftCapability, driftReady ? `${fileTargets.length} selected nodes ready` : 'Select at least two readable nodes');
  driftCapability.className = `pill ${driftReady ? 'online' : 'neutral'}`;
  updateSetupChecklist();
}

function renderNodeViews() {
  nodes.replaceChildren();
  nodes.classList.toggle('empty', visibleNodeItems.length === 0);
  if (visibleNodeItems.length === 0) {
    text(nodes, pageOffset === 0 ? 'No VotingPlugin nodes have registered yet.' : 'No nodes on this page.');
  } else {
    visibleNodeItems.forEach(node => nodes.append(nodeCard(node)));
  }
  renderMetrics();
  renderTopology();
  renderSelectedServer();
  renderVoteSitesSync();
  renderTransportTest();
  renderProxyMethod();
  updateExtendedButtons();
}

function resetServerConfigurationForms(status) {
  configurationForm.reset();
  configurationContent.value = '';
  configurationContentPresent = false;
  text(operationStatus, status);
  text(fileOperationStatus, status);
  updateEditorPosition();
  clearApprovals();
}

function selectPrimaryServer(nodeId) {
  if (nodeId && !nodeIndex.has(nodeId)) return;
  selectedServerId = nodeId;
  serverPicker.value = nodeId;
  selectedNodes.clear();
  if (nodeId) selectedNodes.add(nodeId);
  dedicatedSetupApprovals.clear();
  pendingDetectedVoteSite = null;
  lastFileReadOperation = null;
  lastDiagnostics = null;
  lastOverview = null;
  downloadNetworkDiagnostics.disabled = true;
  text(networkDoctorResults, 'Server changed. Run Network Doctor again.');
  text(dataOverview, 'Server changed. Refresh the overview.');
  text(playerResult, 'No player queried on this server.');
  text(siteHealthResult, 'No health query run on this server.');
  text(voteLogSummaryResult, 'No vote-log summary loaded on this server.');
  text(voteLogResult, 'No event search run on this server.');
  text(voteTraceResult, 'No vote traced on this server.');
  text(siteResolutionResult, 'No service tested on this server.');
  text(rewardSimulationResult, 'Server changed. Simulate or preview the reward again.');
  text(autoSitesState, 'Not loaded');
  autoSitesState.className = 'pill neutral';
  text(voteLoggingState, 'Not loaded');
  voteLoggingState.className = 'pill neutral';
  text(autoSitesStatus, 'Server changed. Load the current value.');
  text(voteLoggingStatus, 'Server changed. Load the current values.');
  loadedQuickSetup = null;
  resetServerConfigurationForms('Server changed. Read this server before previewing changes.');
  const preset = quickPreset.value;
  quickSetupForm.reset();
  quickPreset.value = preset;
  updateQuickFields();
  text(quickOperationStatus, preset === 'sync-vote-sites'
    ? 'Server context changed. Confirm the VoteSites source and targets.'
    : 'Server changed. Load its current values before editing an existing setup.');
  updatePluginSuggestions();
  renderNodeViews();
}

function updateConfigurationButtons(busy = configurationOperationsInFlight > 0 || proxyMethodWorkflowInFlight) {
  const primaryCapabilities = nodeCapabilities.get(selectedServerId) || [];
  const routingReady = authenticated && primaryCapabilities.includes('config.proxy-routing.v1') &&
    targets('config.proxy-routing.v1').length > 0 && !busy;
  const fileReady = authenticated && primaryCapabilities.includes('config.files.v1') &&
    targets('config.files.v1').length > 0 && !busy;
  const syncSelected = quickPreset.value === 'sync-vote-sites';
  const quickReady = authenticated && !busy && (syncSelected
    ? Boolean(voteSitesSourceId && selectedVoteSitesTargets().length > 0)
    : primaryCapabilities.includes('config.quick-setup.v1') && targets('config.quick-setup.v1').length > 0);
  readConfiguration.disabled = !routingReady;
  previewConfiguration.disabled = !routingReady;
  applyConfiguration.disabled = !routingReady || !approvedPreview;
  readFileConfiguration.disabled = !fileReady;
  previewFileConfiguration.disabled = !fileReady || !configurationContentPresent;
  applyFileConfiguration.disabled = !fileReady || !approvedFilePreview;
  readQuickSetup.disabled = !quickReady || !quickPresetReadable();
  previewQuickSetup.disabled = !quickReady || (quickPresetNeedsRead() && !quickSetupValuesLoaded());
  applyQuickSetup.disabled = !quickReady || !approvedQuickPreview;
  runTransportTest.disabled = !authenticated || !transportTestProxyId || !transportTestBackendId || busy;
  const methodNetwork = proxyMethodNetwork();
  const methodReady = authenticated && methodNetwork.proxyReady && methodNetwork.topologyComplete && methodNetwork.reported.length > 0 &&
    methodNetwork.nodeIds.length <= MAX_OPERATION_TARGETS && methodNetwork.unavailable.length === 0 && !busy;
  proxyMethodButtons.forEach(button => { button.disabled = !methodReady; });
  readProxyMethod.disabled = !authenticated || !methodNetwork.proxyReady || busy;
}

function targets(capability) {
  return [...selectedNodes].filter(node => nodeCapabilities.get(node)?.includes(capability));
}

function backendQuickTargets() {
  return targets('config.quick-setup.v1').filter(nodeId => nodeIndex.has(nodeId) && isBackend(nodeIndex.get(nodeId)));
}

function clearApprovals() {
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  dedicatedSetupApprovals.clear();
  inputGeneration++;
  updateConfigurationButtons();
}

function updateEditorPosition() {
  const beforeCursor = configurationContent.value.slice(0, configurationContent.selectionStart);
  const lines = beforeCursor.split('\n');
  text(editorPosition, `Line ${lines.length}, Column ${lines.at(-1).length + 1}`);
}

function handleEditorKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    if (!previewFileConfiguration.disabled) previewFileConfiguration.click();
    return;
  }
  if (event.key !== 'Tab') return;
  event.preventDefault();
  const start = configurationContent.selectionStart;
  const end = configurationContent.selectionEnd;
  if (event.shiftKey) {
    const lineStart = configurationContent.value.lastIndexOf('\n', start - 1) + 1;
    const removable = configurationContent.value.slice(lineStart, lineStart + 2).match(/^ {1,2}/)?.[0].length || 0;
    if (removable > 0) {
      configurationContent.setRangeText('', lineStart, lineStart + removable, 'preserve');
      configurationContent.setSelectionRange(Math.max(lineStart, start - removable), Math.max(lineStart, end - removable));
    }
  } else {
    configurationContent.setRangeText('  ', start, end, 'end');
  }
  configurationContent.dispatchEvent(new Event('input', {bubbles: true}));
  updateEditorPosition();
}

function updateQuickFields() {
  document.querySelectorAll('.quick-fields').forEach(group => {
    group.hidden = !group.dataset.presets.split(' ').includes(quickPreset.value);
  });
  quickName.closest('.quick-fields').hidden = !['proxy-backend', 'vote-site', 'easy-reward'].includes(quickPreset.value);
  const sync = quickPreset.value === 'sync-vote-sites';
  readQuickSetup.hidden = !quickPresetReadable();
  previewQuickSetup.textContent = sync ? 'Read source and preview sync' : 'Preview changes';
  applyQuickSetup.textContent = sync ? 'Approve and sync' : 'Approve and apply';
  updateConfigurationButtons();
}

function quickPresetReadable() {
  return quickPresetNeedsRead()
    && (quickPreset.value !== 'vote-site' || quickName.value.trim().length > 0);
}

function quickPresetNeedsRead() {
  return ['proxy-backend', 'vote-site', 'common-settings', 'vote-party',
    'auto-create-vote-sites', 'vote-logging'].includes(quickPreset.value);
}

function quickSetupValuesLoaded() {
  return loadedQuickSetup?.nodeId === selectedServerId
    && loadedQuickSetup.sessionId === nodeIndex.get(selectedServerId)?.sessionId
    && loadedQuickSetup.preset === quickPreset.value
    && loadedQuickSetup.selector === JSON.stringify(quickReadOptions());
}

function updatePluginSuggestions() {
  const plugins = new Set(targets('config.quick-setup.v1').flatMap(node => nodePlugins.get(node) || [])
    .map(name => name.toLowerCase()));
  const names = [...plugins].sort();
  text(detectedPlugins, names.length ? `Detected on selected nodes: ${names.join(', ')}`
    : 'No plugin inventory is available for the selected nodes; generic commands are still available.');
  const suggestions = [
    ['give %player% diamond 1', 'Minecraft item'],
    ['xp add %player% 5 levels', 'Minecraft experience']
  ];
  if ([...plugins].some(name => name === 'essentials' || name === 'essentialsx')) {
    suggestions.push(['eco give %player% 100', 'Essentials economy']);
  }
  if (plugins.has('cmi')) suggestions.push(['money give %player% 100', 'CMI economy']);
  if (plugins.has('luckperms')) {
    suggestions.push(['lp user %player% permission set example.permission true', 'LuckPerms permission']);
  }
  quickCommandSuggestions.replaceChildren(...suggestions.map(([value, label]) => {
    const option = document.createElement('option');
    option.value = value;
    option.label = label;
    return option;
  }));
}

async function authorized(path, options = {}) {
  const requestGeneration = authenticationGeneration;
  const method = (options.method || 'GET').toUpperCase();
  const response = await fetch(path, {
    cache: 'no-store',
    credentials: 'same-origin',
    ...options,
    headers: {...(options.headers || {}), ...(method === 'GET' ? {} : {'X-CSRF-Token': csrfToken})}
  });
  const body = response.status === 204 ? null : await response.json();
  if (response.status === 401 && requestGeneration === authenticationGeneration) {
    discardAuthenticationState('Session expired. Sign in again.');
  }
  if (requestGeneration !== authenticationGeneration) {
    throw new Error('Authentication changed while the request was running. Try again.');
  }
  if (!response.ok) {
    const error = new Error(body?.error?.message || `Control request failed (${response.status}).`);
    error.code = body?.error?.code || '';
    throw error;
  }
  return body;
}

function discardAuthenticationState(reason) {
  authenticationGeneration++;
  authenticated = false;
  csrfToken = '';
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  loadedQuickSetup = null;
  inputGeneration++;
  logout.hidden = true;
  appShell.hidden = true;
  serverPickerLabel.hidden = true;
  welcome.hidden = false;
  authCard.hidden = setupRequired;
  enrollmentCard.hidden = true;
  enrollmentCredential.value = '';
  enrollmentList.replaceChildren(text(document.createElement('li'), 'Authenticate to manage enrollments.'));
  text(enrollmentMessage, '');
  selectedNodes.clear();
  voteSitesSourceId = '';
  voteSitesTargetIds.clear();
  voteSitesTargetsInitialized = false;
  transportTestProxyId = '';
  transportTestBackendId = '';
  proxyMethodProxyId = '';
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentValue = '';
  fileReadCache.clear();
  lastFileReadOperation = null;
  lastDiagnostics = null;
  lastOverview = null;
  operationHistoryItems = [];
  dedicatedSetupApprovals.clear();
  voteLoggingRestartPending.clear();
  pendingDetectedVoteSite = null;
  selectedServerId = '';
  visibleNodeItems = [];
  allNodeItems = [];
  enrollmentIds.clear();
  enrollmentsLoaded = false;
  backendTopologyTruncated = false;
  backendTopologyTruncatedNodeIds = new Set();
  nodeIndex.clear();
  nodeCapabilities.clear();
  nodePlugins.clear();
  configurationForm.reset();
  fileConfigurationForm.reset();
  configurationContentPresent = false;
  quickSetupForm.reset();
  rewardSimulationForm.reset();
  playerLookupForm.reset();
  voteLogForm.reset();
  voteTraceForm.reset();
  siteResolutionForm.reset();
  snapshotForm.reset();
  rewardSiteLabel.hidden = false;
  copyRewardToSetup.disabled = true;
  voteLogFilter.disabled = true;
  voteLogFilter.required = false;
  voteLogFilter.placeholder = '';
  updateQuickFields();
  quickCommandSuggestions.replaceChildren();
  text(detectedPlugins, 'Authenticate to inspect detected plugins.');
  text(operationStatus, '');
  text(fileOperationStatus, '');
  text(quickOperationStatus, '');
  text(transportTestStatus, '');
  text(proxyMethodStatus, '');
  text(networkDoctorResults, 'Choose a connected backend with read-only data inspection.');
  text(dataOverview, 'Choose an inspection-capable backend.');
  text(playerResult, 'No player queried.');
  text(siteHealthResult, 'No health query run.');
  text(voteLogSummaryResult, 'No vote-log summary loaded.');
  text(voteLogResult, 'No event search run.');
  text(voteTraceResult, 'No vote traced.');
  text(siteResolutionResult, 'No service tested.');
  text(rewardSimulationResult, 'Add an action, then simulate it safely.');
  text(driftResults, 'Authenticate and choose two or more readable nodes.');
  text(snapshotList, 'Authenticate to view manual snapshots.');
  text(snapshotStatus, '');
  renderOperationHistory();
  nodes.replaceChildren();
  nodes.classList.add('empty');
  text(nodes, 'Authenticate to view the network.');
  serverPicker.replaceChildren(text(document.createElement('option'), 'Choose a server'));
  serverPicker.firstElementChild.value = '';
  renderMetrics();
  renderTopology();
  renderSelectedServer();
  text(message, reason);
  updateConfigurationButtons();
  updateExtendedButtons();
}

function proposal() {
  return {
    sendVotesToAllServers: sendAll.checked,
    blockedServers: blockedServers.value.split(/\r?\n/).map(value => value.trim()).filter(Boolean)
  };
}

function rememberVoteLoggingRestart(operation) {
  if (operation.type !== 'APPLY' || operation.configuration?.preset !== 'vote-logging') return;
  Object.entries(operation.results || {}).forEach(([nodeId, result]) => {
    const runtimeChanged = Array.isArray(result?.changes) && result.changes.some(change =>
      /VoteLogging runtime restart required|VoteLogging\.(Enabled|UseMainMySQL)\b/.test(change));
    if (result?.success && runtimeChanged) voteLoggingRestartPending.set(nodeId,
      result.sessionId || nodeIndex.get(nodeId)?.sessionId || 'unknown');
  });
}

function voteLoggingRestartRequired(nodeId = selectedServerId) {
  const appliedSession = voteLoggingRestartPending.get(nodeId);
  if (!appliedSession) return false;
  const currentSession = nodeIndex.get(nodeId)?.sessionId;
  if (currentSession && appliedSession !== 'unknown' && currentSession !== appliedSession) {
    voteLoggingRestartPending.delete(nodeId);
    return false;
  }
  return true;
}

function operationSummary(operation) {
  const lines = [`${operation.type} · ${operation.state} · ${operation.operationId}`];
  const voteLoggingChange = operation.configuration?.preset === 'vote-logging';
  Object.entries(operation.nodeStates).forEach(([node, state]) => {
    const result = operation.results[node];
    const successLabel = operation.type === 'READ' ? 'values read'
      : operation.type === 'PREVIEW' ? 'preview ready'
      : voteLoggingChange ? 'configuration saved; backend restart required'
      : result?.reloaded ? 'saved and reloaded' : 'applied';
    lines.push(`${result?.success ? '✓' : result ? '✗' : '…'} ${node}: ${result
      ? `${result.success ? successLabel : result.code} — ${result.message}` : state.toLowerCase()}`);
    if (result?.changes?.length) result.changes.forEach(change => lines.push(`  ${change}`));
    if (result?.rolledBack) lines.push('  NOT SAVED — the previous file was restored because reload failed');
  });
  if (operation.configuration?.preset === 'sync-vote-sites') {
    const sites = new Set(Object.values(operation.results).flatMap(result => result.changes || [])
      .map(change => change.match(/VoteSites\.([A-Za-z0-9_-]+)/)?.[1]).filter(Boolean));
    lines.push(`${sites.size || 'No'} site ${sites.size === 1 ? 'definition' : 'definitions'} ${operation.type === 'PREVIEW' ? 'would change' : 'changed'}.`);
    lines.push('Rewards and target-only sites remain local to each backend.');
  }
  if (voteLoggingChange && operation.type !== 'READ') {
    lines.push(operation.type === 'PREVIEW'
      ? 'Applying this preview requires restarting each changed backend; a plugin reload does not activate a new vote-log connection.'
      : 'Restart every successfully changed backend before treating the vote-logging runtime as live.');
  }
  return lines.join('\n');
}

async function waitForOperation(operation, statusElement = operationStatus) {
  text(statusElement, operationSummary(operation));
  rememberOperation(operation);
  while (operation.state === 'RUNNING') {
    await new Promise(resolve => window.setTimeout(resolve, 1500));
    operation = await authorized(`/api/v1/operations/${operation.operationId}`);
    text(statusElement, operationSummary(operation));
    rememberOperation(operation);
  }
  rememberVoteLoggingRestart(operation);
  if (operation.type === 'APPLY' && Object.values(operation.results || {}).some(result => result?.success)) {
    fileReadCache.clear();
    lastFileReadOperation = null;
    lastOverview = null;
    lastDiagnostics = null;
    updateExtendedButtons();
  }
  return operation;
}

async function startConfigurationOperation(path, body, statusElement = operationStatus) {
  if (path.endsWith('/apply')) {
    approvedPreview = null;
    approvedFilePreview = null;
    approvedQuickPreview = null;
    dedicatedSetupApprovals.clear();
    inputGeneration++;
  }
  configurationOperationsInFlight++;
  updateConfigurationButtons();
  updateExtendedButtons();
  try {
    return await waitForOperation(await authorized(path, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
    }), statusElement);
  } finally {
    configurationOperationsInFlight--;
    updateConfigurationButtons();
    updateExtendedButtons();
  }
}

async function loadEnrollments() {
  if (!authenticated) return;
  if (enrollmentInFlight) {
    enrollmentRefreshRequested = true;
    return;
  }
  enrollmentInFlight = true;
  refreshEnrollments.disabled = true;
  try {
    const body = await authorized('/api/v1/enrollments');
    enrollmentIds = new Set(Array.isArray(body.nodeIds) ? body.nodeIds : []);
    enrollmentsLoaded = true;
    if (allNodeItems.length) renderNodeViews();
    enrollmentList.replaceChildren();
    if (!Array.isArray(body.nodeIds) || body.nodeIds.length === 0) {
      enrollmentList.append(text(document.createElement('li'), 'No nodes are enrolled yet.'));
      return;
    }
    body.nodeIds.forEach(nodeId => {
      const item = document.createElement('li');
      item.className = 'enrollment-item';
      item.append(text(document.createElement('code'), nodeId));
      const revoke = text(document.createElement('button'), 'Revoke');
      revoke.type = 'button';
      revoke.addEventListener('click', async () => {
        if (enrollmentMutationInFlight || !window.confirm(`Revoke the credential for ${nodeId}?`)) return;
        enrollmentMutationInFlight = true;
        revoke.disabled = true;
        enrollmentSubmit.disabled = true;
        try {
          await authorized(`/api/v1/enrollments/${encodeURIComponent(nodeId)}`, {method: 'DELETE'});
          enrollmentCredential.value = '';
          text(enrollmentMessage, `${nodeId} was revoked.`);
          await loadEnrollments();
        } catch (error) {
          text(enrollmentMessage, error.message);
        } finally {
          enrollmentMutationInFlight = false;
          enrollmentSubmit.disabled = false;
        }
      });
      item.append(revoke);
      enrollmentList.append(item);
    });
  } catch (error) {
    enrollmentsLoaded = false;
    if (allNodeItems.length) renderNodeViews();
    text(enrollmentMessage, error.message || 'Enrollments could not be loaded.');
  } finally {
    enrollmentInFlight = false;
    refreshEnrollments.disabled = false;
    const refreshAgain = enrollmentRefreshRequested && authenticated;
    enrollmentRefreshRequested = false;
    if (refreshAgain) await loadEnrollments();
  }
}

async function loadAllNodes() {
  for (let attempt = 0; attempt < MAX_REGISTRY_SCAN_ATTEMPTS; attempt++) {
    const items = [];
    const pageMetadata = new Map();
    const ids = new Set();
    const truncatedNodeIds = new Set();
    let revision = null;
    let total = null;
    let truncated = false;
    try {
      for (let offset = 0; ; offset += PAGE_SIZE) {
        const expected = revision == null ? '' : `&revision=${revision}`;
        const page = await authorized(`/api/v1/nodes?offset=${offset}&limit=${PAGE_SIZE}${expected}`);
        if (revision == null) {
          revision = page.registryRevision;
          total = page.total;
        } else if (page.registryRevision !== revision || page.total !== total) {
          throw Object.assign(new Error('Node registry changed during pagination.'), {code: 'REGISTRY_CHANGED'});
        }
        if (page.items.some(node => ids.has(node.nodeId))) {
          throw Object.assign(new Error('Node registry changed during pagination.'), {code: 'REGISTRY_CHANGED'});
        }
        page.items.forEach(node => ids.add(node.nodeId));
        items.push(...page.items);
        truncated ||= Boolean(page.backendItemsTruncated);
        (page.backendItemsTruncatedNodeIds || []).forEach(nodeId => truncatedNodeIds.add(nodeId));
        pageMetadata.set(offset, {
          backendItemsReturned: page.backendItemsReturned,
          backendItemsTruncated: Boolean(page.backendItemsTruncated)
        });
        if (items.length === total) return {items, truncated, truncatedNodeIds, pageMetadata};
        if (page.items.length === 0 || items.length > total) {
          throw Object.assign(new Error('Node registry changed during pagination.'), {code: 'REGISTRY_CHANGED'});
        }
      }
    } catch (error) {
      if (error.code !== 'REGISTRY_CHANGED' || attempt + 1 === MAX_REGISTRY_SCAN_ATTEMPTS) throw error;
    }
  }
  throw new Error('Node registry could not be loaded consistently. Try refreshing again.');
}

async function loadNodes() {
  if (!authenticated) return;
  refresh.disabled = true;
  previousPage.disabled = true;
  nextPage.disabled = true;
  text(message, 'Loading…');
  try {
    const registry = await loadAllNodes();
    const previousNodeIndex = nodeIndex;
    visibleNodeItems = registry.items.slice(pageOffset, pageOffset + PAGE_SIZE);
    allNodeItems = registry.items;
    backendTopologyTruncated = registry.truncated;
    backendTopologyTruncatedNodeIds = registry.truncatedNodeIds;
    nodeIndex = new Map(registry.items.map(node => [node.nodeId, node]));
    const primarySessionChanged = selectedServerId && previousNodeIndex.get(selectedServerId)?.sessionId
      && previousNodeIndex.get(selectedServerId)?.sessionId !== nodeIndex.get(selectedServerId)?.sessionId;
    const selectedSessionChanged = primarySessionChanged || [...selectedNodes].some(node => previousNodeIndex.get(node)?.sessionId
      && previousNodeIndex.get(node)?.sessionId !== nodeIndex.get(node)?.sessionId);
    if (selectedSessionChanged) {
      dedicatedSetupApprovals.clear();
      lastOverview = null;
      lastDiagnostics = null;
      lastFileReadOperation = null;
      fileReadCache.clear();
      configurationContent.value = '';
      configurationContentPresent = false;
      approvedFilePreview = null;
      inputGeneration++;
      text(dataOverview, 'A selected server reconnected. Refresh the overview.');
      text(networkDoctorResults, 'A selected server reconnected. Run Network Doctor again.');
      text(playerResult, 'A selected server reconnected. Run the lookup again.');
      text(siteHealthResult, 'A selected server reconnected. Load health again.');
      text(voteLogSummaryResult, 'A selected server reconnected. Load the summary again.');
      text(voteLogResult, 'A selected server reconnected. Run the search again.');
      text(voteTraceResult, 'A selected server reconnected. Trace the vote again.');
      text(siteResolutionResult, 'A selected server reconnected. Test the service again.');
      text(rewardSimulationResult, 'A selected server reconnected. Simulate or preview the reward again.');
    }
    if (loadedQuickSetup?.nodeId === selectedServerId
        && previousNodeIndex.get(selectedServerId)?.sessionId !== nodeIndex.get(selectedServerId)?.sessionId) {
      loadedQuickSetup = null;
      approvedQuickPreview = null;
      inputGeneration++;
      text(quickOperationStatus, 'The selected server reconnected. Load its current values again before previewing changes.');
    }
    const previousCapabilities = nodeCapabilities;
    nodeCapabilities = new Map(registry.items.map(node => [node.nodeId, node.online ? node.acceptedCapabilities : []]));
    nodePlugins = new Map(registry.items.map(node => [node.nodeId, node.online && Array.isArray(node.detectedPlugins)
      ? node.detectedPlugins : []]));
    const selectedCapabilitiesChanged = [...selectedNodes].some(node =>
      ['config.proxy-routing.v1', 'config.files.v1', 'config.quick-setup.v1', 'data.inspect.v1'].some(capability =>
        Boolean(previousCapabilities.get(node)?.includes(capability)) !==
          Boolean(nodeCapabilities.get(node)?.includes(capability))));
    if (selectedCapabilitiesChanged) {
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      dedicatedSetupApprovals.clear();
      lastOverview = null;
      lastDiagnostics = null;
      inputGeneration++;
      text(operationStatus, 'A selected node changed capabilities during refresh. Preview again before apply.');
    }
    const invalidRoutingApproval = approvedPreview && !approvedPreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.proxy-routing.v1'));
    const invalidFileApproval = approvedFilePreview && !approvedFilePreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.files.v1'));
    const invalidQuickApproval = approvedQuickPreview && approvedQuickPreview.workflow !== 'sync-vote-sites' &&
      !approvedQuickPreview.nodeIds.every(node =>
      nodeCapabilities.get(node)?.includes('config.quick-setup.v1'));
    const invalidVoteSitesApproval = approvedQuickPreview?.workflow === 'sync-vote-sites' &&
      (!approvedQuickPreview.nodeIds.every(node =>
        nodeCapabilities.get(node)?.includes('config.vote-sites-sync.v1')) ||
       !nodeCapabilities.get(approvedQuickPreview.sourceId)?.includes('config.file-comments.v1'));
    if (invalidRoutingApproval || invalidFileApproval || invalidQuickApproval || invalidVoteSitesApproval) {
      if (invalidRoutingApproval) approvedPreview = null;
      if (invalidFileApproval) approvedFilePreview = null;
      if (invalidQuickApproval) approvedQuickPreview = null;
      if (invalidVoteSitesApproval) approvedQuickPreview = null;
      dedicatedSetupApprovals.clear();
      inputGeneration++;
      text(operationStatus, 'A preview target went offline or lost the required capability. Preview again before apply.');
    }
    const visibleIds = new Set(registry.items.filter(node => node.online && node.acceptedCapabilities.some(value => value.startsWith('config.')))
      .map(node => node.nodeId));
    const filteredSelection = new Set([...selectedNodes].filter(node => visibleIds.has(node)));
    renderServerPicker();
    if (selectedServerId && visibleIds.has(selectedServerId)) {
      while (!filteredSelection.has(selectedServerId) && filteredSelection.size >= MAX_CONFIGURATION_TARGETS) {
        const removable = [...filteredSelection].find(node => node !== selectedServerId);
        if (!removable) break;
        filteredSelection.delete(removable);
      }
      filteredSelection.add(selectedServerId);
    }
    if (filteredSelection.size !== selectedNodes.size ||
        [...filteredSelection].some(node => !selectedNodes.has(node))) {
      approvedPreview = null;
      approvedFilePreview = null;
      approvedQuickPreview = null;
      dedicatedSetupApprovals.clear();
      inputGeneration++;
      text(operationStatus, 'The selected nodes changed during refresh. Preview again before apply.');
    }
    selectedNodes = filteredSelection;
    renderNodeViews();
    updatePluginSuggestions();
    updateConfigurationButtons();
    const first = visibleNodeItems.length === 0 ? 0 : pageOffset + 1;
    const last = pageOffset + visibleNodeItems.length;
    const pageMeta = registry.pageMetadata.get(pageOffset);
    const backendLimit = pageMeta?.backendItemsTruncated
      ? ` Backend summaries are limited to ${pageMeta.backendItemsReturned} entries on this page.` : '';
    text(message, visibleNodeItems.length === 0 ? 'No nodes on this page.'
      : `Showing nodes ${first}–${last}.${backendLimit}`);
    text(pageNumber, `Page ${Math.floor(pageOffset / PAGE_SIZE) + 1}`);
    previousPage.disabled = pageOffset === 0;
    nextPage.disabled = pageOffset + visibleNodeItems.length >= registry.items.length;
  } catch (error) {
    visibleNodeItems = [];
    allNodeItems = [];
    backendTopologyTruncated = false;
    nodeIndex.clear();
    nodeCapabilities.clear();
    nodePlugins.clear();
    selectedNodes.clear();
    selectedServerId = '';
    dedicatedSetupApprovals.clear();
    lastOverview = null;
    lastDiagnostics = null;
    lastFileReadOperation = null;
    fileReadCache.clear();
    resetServerConfigurationForms('Network data is unavailable. Refresh before editing.');
    text(quickOperationStatus, 'Network data is unavailable. Refresh before editing.');
    renderServerPicker();
    renderNodeViews();
    updatePluginSuggestions();
    updateConfigurationButtons();
    text(nodes, 'Network data is unavailable.');
    text(dataOverview, 'Network data is unavailable.');
    text(networkDoctorResults, 'Network data is unavailable.');
    text(message, error.message || 'Control request failed.');
  } finally {
    refresh.disabled = false;
  }
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginInFlight) return;
  loginInFlight = true;
  loginButton.disabled = true;
  logout.disabled = true;
  const loginGeneration = ++authenticationGeneration;
  const password = passwordInput.value;
  passwordInput.value = '';
  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST', cache: 'no-store', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify({password})
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'Authentication failed.');
    if (loginGeneration !== authenticationGeneration) return;
    applyAuthenticatedSession(body);
    await Promise.all([loadEnrollments(), loadNodes(), loadOperationHistory(), loadSnapshots()]);
  } catch (error) {
    text(message, error.message || 'Authentication failed.');
  } finally {
    loginInFlight = false;
    loginButton.disabled = false;
    logout.disabled = false;
  }
});

logout.addEventListener('click', async () => {
  if (loginInFlight) return;
  loginInFlight = true;
  const logoutGeneration = ++authenticationGeneration;
  loginButton.disabled = true;
  logout.disabled = true;
  try {
    await authorized('/api/v1/auth/logout', {method: 'POST'});
    if (logoutGeneration === authenticationGeneration) discardAuthenticationState('Signed out.');
  } catch (_) {
    if (logoutGeneration === authenticationGeneration) {
      text(message, 'Sign out could not be confirmed. Check your connection and try again.');
    }
  } finally {
    loginInFlight = false;
    loginButton.disabled = false;
    logout.disabled = false;
  }
});

async function restoreSession() {
  const restoreGeneration = authenticationGeneration;
  try {
    const response = await fetch('/api/v1/auth/session', {cache: 'no-store', credentials: 'same-origin'});
    if (!response.ok || response.status === 204) return;
    const body = await response.json();
    if (restoreGeneration !== authenticationGeneration) return;
    applyAuthenticatedSession(body);
    await Promise.all([loadEnrollments(), loadNodes(), loadOperationHistory(), loadSnapshots()]);
  } catch (_) { /* The login form remains available. */ }
}

setupForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (loginInFlight) return;
  if (setupPassword.value !== setupConfirmPassword.value) {
    text(setupMessage, 'The new passwords do not match.');
    return;
  }
  loginInFlight = true;
  const setupGeneration = ++authenticationGeneration;
  const request = {setupCode: setupCode.value, password: setupPassword.value};
  setupCode.value = '';
  setupPassword.value = '';
  setupConfirmPassword.value = '';
  try {
    const response = await fetch('/api/v1/auth/setup', {
      method: 'POST', cache: 'no-store', credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request)
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.error?.message || 'First-run setup failed.');
    if (setupGeneration !== authenticationGeneration) return;
    setupRequired = false;
    setupCard.hidden = true;
    authCard.hidden = false;
    applyAuthenticatedSession(body);
    text(message, 'First-run setup completed.');
    await Promise.all([loadEnrollments(), loadNodes(), loadOperationHistory(), loadSnapshots()]);
  } catch (error) {
    text(setupMessage, error.message || 'First-run setup failed.');
  } finally {
    loginInFlight = false;
  }
});

enrollmentForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (!authenticated || enrollmentMutationInFlight) return;
  enrollmentMutationInFlight = true;
  enrollmentSubmit.disabled = true;
  const nodeId = enrollmentNodeId.value.trim();
  enrollmentCredential.value = '';
  try {
    const body = await authorized('/api/v1/enrollments', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({nodeId})
    });
    enrollmentCredential.value = body.credential;
    text(enrollmentMessage,
      `Credential created for ${body.nodeId}. Copy it now; rotating again immediately invalidates this value.`);
    await loadEnrollments();
  } catch (error) {
    text(enrollmentMessage, error.message || 'The credential could not be created.');
  } finally {
    enrollmentMutationInFlight = false;
    enrollmentSubmit.disabled = false;
  }
});
refreshEnrollments.addEventListener('click', loadEnrollments);

readConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  const readAuthenticationGeneration = authenticationGeneration;
  const readInputGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {nodeIds: [selectedServerId]});
    const retained = Object.values(operation.results).find(result => result.success && result.configuration);
    if (retained && authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration) {
      sendAll.checked = retained.configuration.sendVotesToAllServers;
      blockedServers.value = retained.configuration.blockedServers.join('\n');
      approvedPreview = null;
      inputGeneration++;
      updateConfigurationButtons();
    }
  } catch (error) { text(operationStatus, error.message); }
});

previewConfiguration.addEventListener('click', async () => {
  approvedPreview = null;
  const previewGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.proxy-routing.v1'), configuration: proposal()
    });
    if (operation.state === 'SUCCEEDED' && operation.approvalToken
        && previewGeneration === inputGeneration) {
      approvedPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.proxy-routing.v1')};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(operationStatus, 'The targets or proposal changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(operationStatus, error.message); }
});

applyConfiguration.addEventListener('click', async () => {
  if (!approvedPreview || !window.confirm('Apply this exact preview to every selected proxy? Each node may still reject a stale revision.')) return;
  const approval = approvedPreview;
  approvedPreview = null;
  inputGeneration++;
  try {
    await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    });
  } catch (error) { text(operationStatus, error.message); }
});
[sendAll, blockedServers].forEach(field => field.addEventListener('input', () => {
  if (approvedPreview) text(operationStatus, 'The proposal changed. Preview it again before apply.');
  approvedPreview = null;
  inputGeneration++;
  updateConfigurationButtons();
}));

readFileConfiguration.addEventListener('click', async () => {
  approvedFilePreview = null;
  const readAuthenticationGeneration = authenticationGeneration;
  const readInputGeneration = inputGeneration;
  const selectedFile = configurationFile.value;
  const selectedNode = nodeIndex.get(selectedServerId);
  const cacheKey = `${selectedServerId}|${selectedNode?.sessionId || ''}|${selectedFile}`;
  const cached = cachedFile(cacheKey);
  if (cached) {
    configurationContent.value = cached.content;
    configurationContentPresent = true;
    lastFileReadOperation = {operationId: cached.operationId};
    updateEditorPosition();
    text(fileOperationStatus, `Cached read · ${selectedServerId} · ${selectedFile}\nLoaded instantly; cache expires after 30 seconds. Preview still checks the live revision.`);
    inputGeneration++;
    updateConfigurationButtons();
    updateExtendedButtons();
    return;
  }
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [selectedServerId],
      configuration: {domain: 'file', fileName: selectedFile}
    }, fileOperationStatus);
    const contentResult = Object.values(operation.results).find(result =>
      result.success && result.configuration?.content != null);
    if (contentResult && authenticated && readAuthenticationGeneration === authenticationGeneration
        && readInputGeneration === inputGeneration && selectedFile === configurationFile.value) {
      configurationContent.value = contentResult.configuration.content;
      configurationContentPresent = true;
      lastFileReadOperation = {operationId: operation.operationId};
      cacheFile(cacheKey, contentResult.configuration.content, operation.operationId);
      updateEditorPosition();
      text(fileOperationStatus, operationSummary(operation));
      inputGeneration++;
      updateConfigurationButtons();
      updateExtendedButtons();
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

previewFileConfiguration.addEventListener('click', async () => {
  approvedFilePreview = null;
  const previewGeneration = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.files.v1'),
      configuration: {domain: 'file', fileName: configurationFile.value, content: configurationContent.value}
    }, fileOperationStatus);
    text(fileOperationStatus, operationSummary(operation));
    if (operation.state === 'SUCCEEDED' && operation.approvalToken && previewGeneration === inputGeneration) {
      approvedFilePreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.files.v1')};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(fileOperationStatus, 'The targets or file changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

applyFileConfiguration.addEventListener('click', async () => {
  if (!approvedFilePreview || !window.confirm(`Apply this exact ${configurationFile.value} preview to every selected Bukkit node?`)) return;
  const approval = approvedFilePreview;
  approvedFilePreview = null;
  inputGeneration++;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, fileOperationStatus);
    text(fileOperationStatus, operationSummary(operation));
    if (operation.state === 'SUCCEEDED') {
      fileReadCache.clear();
      lastFileReadOperation = null;
      updateExtendedButtons();
    }
  } catch (error) { text(fileOperationStatus, error.message); }
});

function quickOptions() {
  if (quickPreset.value === 'standalone') return {};
  if (quickPreset.value === 'proxy-backend') return {server: quickName.value.trim(), method: quickMethod.value};
  if (quickPreset.value === 'vote-site') return {
      name: quickName.value.trim(), displayName: quickSiteDisplayName.value.trim() || quickName.value.trim(),
      serviceSite: quickService.value.trim(), voteUrl: quickUrl.value.trim(), voteDelay: quickDelay.value.trim(),
      priority: quickSitePriority.value, material: quickSiteMaterial.value.trim(),
      enabled: String(quickSiteEnabled.checked), hidden: String(quickSiteHidden.checked)
    };
  if (quickPreset.value === 'easy-reward') return {scope: quickRewardScope.value,
    name: quickName.value.trim(), command: quickCommand.value.trim(), message: quickMessage.value.trim()};
  if (quickPreset.value === 'auto-create-vote-sites') return {enabled: String(quickAutoSitesOnly.checked)};
  if (quickPreset.value === 'vote-logging') return {enabled: String(quickVoteLoggingEnabled.checked),
    purgeDays: validatedPurgeDays(quickVoteLoggingDays), useMainMySQL: String(quickVoteLoggingMainMysql.checked)};
  if (quickPreset.value === 'common-settings') return {
    processRewards: String(quickProcessRewards.checked), autoCreateVoteSites: String(quickAutoSites.checked),
    extraAllSitesCheck: String(quickExtraCheck.checked), countFakeVotes: String(quickCountFake.checked),
    disableNoServiceSiteMessage: String(quickHideSiteWarning.checked),
    disableUpdateChecking: String(quickDisableUpdates.checked)
  };
  return {votesRequired: quickPartyVotes.value, command: quickPartyCommand.value.trim(),
    broadcast: quickPartyBroadcast.value.trim(), giveAllPlayers: String(quickPartyAll.checked),
    onlineOnly: String(quickPartyOnline.checked)};
}

function quickReadOptions() {
  return quickPreset.value === 'vote-site' ? {name: quickName.value.trim()} : {};
}

function populateQuickState(options) {
  if (quickPreset.value === 'proxy-backend') {
    quickName.value = options.server || '';
    quickMethod.value = options.method || 'PLUGINMESSAGING';
  } else if (quickPreset.value === 'vote-site') {
    quickSiteDisplayName.value = options.displayName || quickName.value.trim();
    quickService.value = options.serviceSite || '';
    quickUrl.value = options.voteUrl || '';
    quickDelay.value = options.voteDelay || '24h';
    quickSitePriority.value = options.priority || '5';
    quickSiteMaterial.value = options.material || 'DIAMOND';
    quickSiteEnabled.checked = options.enabled !== 'false';
    quickSiteHidden.checked = options.hidden === 'true';
  } else if (quickPreset.value === 'common-settings') {
    quickProcessRewards.checked = options.processRewards === 'true';
    quickAutoSites.checked = options.autoCreateVoteSites === 'true';
    quickExtraCheck.checked = options.extraAllSitesCheck === 'true';
    quickCountFake.checked = options.countFakeVotes === 'true';
    quickHideSiteWarning.checked = options.disableNoServiceSiteMessage === 'true';
    quickDisableUpdates.checked = options.disableUpdateChecking === 'true';
  } else if (quickPreset.value === 'auto-create-vote-sites') {
    quickAutoSitesOnly.checked = options.enabled === 'true';
  } else if (quickPreset.value === 'vote-logging') {
    quickVoteLoggingEnabled.checked = options.enabled === 'true';
    quickVoteLoggingDays.value = options.purgeDays || '30';
    quickVoteLoggingMainMysql.checked = options.useMainMySQL !== 'false';
  } else if (quickPreset.value === 'vote-party') {
    quickPartyVotes.value = options.votesRequired || '20';
    quickPartyBroadcast.value = options.broadcast || '';
    quickPartyAll.checked = options.giveAllPlayers === 'true';
    quickPartyOnline.checked = options.onlineOnly !== 'false';
    quickPartyCommand.value = '';
  }
}

readQuickSetup.addEventListener('click', async () => {
  if (!quickPresetReadable()) return;
  approvedQuickPreview = null;
  loadedQuickSetup = null;
  const preset = quickPreset.value;
  const nodeId = selectedServerId;
  const sessionId = nodeIndex.get(nodeId)?.sessionId;
  const selector = JSON.stringify(quickReadOptions());
  const generation = inputGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [selectedServerId],
      configuration: {domain: 'quick-setup', preset, options: quickReadOptions()}
    }, quickOperationStatus);
    const result = Object.values(operation.results).find(item =>
      item.success && item.configuration?.preset === preset && item.configuration?.options);
    if (!result) throw new Error('The selected backend did not return guided settings. Update VotingPlugin on that node.');
    if (generation !== inputGeneration || preset !== quickPreset.value || nodeId !== selectedServerId
        || sessionId !== nodeIndex.get(nodeId)?.sessionId
        || selector !== JSON.stringify(quickReadOptions())) {
      text(quickOperationStatus, 'The server or setup changed while reading. Load the current values again.');
      return;
    }
    const detected = preset === 'vote-site' && pendingDetectedVoteSite?.nodeId === nodeId
      && pendingDetectedVoteSite.key === quickName.value.trim() ? pendingDetectedVoteSite : null;
    populateQuickState(result.configuration.options);
    if (detected && result.configuration.options.exists === 'false') {
      quickSiteDisplayName.value = detected.service;
      quickService.value = detected.service;
    }
    if (detected) pendingDetectedVoteSite = null;
    loadedQuickSetup = {nodeId, sessionId, preset, selector};
    inputGeneration++;
    const suffix = preset === 'vote-site' && result.configuration.options.exists === 'false'
      ? ` This site key does not exist yet; the form is ready to create it.${detected ? ' The detected service was retained.' : ''}`
      : preset === 'vote-site' && detected
      ? ' The generated key already exists, so its current values were kept; choose a different key for the detected service.'
      : preset === 'vote-party' && Number(result.configuration.options.rewardCommandCount || 0) > 0
      ? ` ${result.configuration.options.rewardCommandCount} existing reward command(s) will be preserved.` : '';
    text(quickOperationStatus, `Current values loaded from ${Object.keys(operation.results).find(id => operation.results[id] === result)}.${suffix}`);
    updateConfigurationButtons();
  } catch (error) { text(quickOperationStatus, error.message); }
});

previewQuickSetup.addEventListener('click', async () => {
  approvedQuickPreview = null;
  const previewGeneration = inputGeneration;
  try {
    if (quickPreset.value === 'sync-vote-sites') {
      const sourceId = voteSitesSourceId;
      const nodeIds = selectedVoteSitesTargets();
      const read = await startConfigurationOperation('/api/v1/configuration/read', {
        nodeIds: [sourceId], configuration: {domain: 'file', fileName: 'VoteSites.yml'}
      }, quickOperationStatus);
      const source = Object.values(read.results).find(result =>
        result.success && result.configuration?.content != null)?.configuration?.content;
      if (source == null) throw new Error('The source backend did not return VoteSites.yml.');
      if (previewGeneration !== inputGeneration || sourceId !== voteSitesSourceId) {
        text(quickOperationStatus, 'The source or targets changed while reading. Preview again.');
        return;
      }
      const preview = await startConfigurationOperation('/api/v1/configuration/preview', {
        nodeIds,
        configuration: {domain: 'quick-setup', preset: 'sync-vote-sites', options: {sourceContent: source}}
      }, quickOperationStatus);
      text(quickOperationStatus, operationSummary(preview));
      if (preview.state === 'SUCCEEDED' && preview.approvalToken && previewGeneration === inputGeneration) {
        approvedQuickPreview = {workflow: 'sync-vote-sites', operationId: preview.operationId,
          approvalToken: preview.approvalToken, nodeIds, sourceId};
        updateConfigurationButtons();
      }
      return;
    }
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: targets('config.quick-setup.v1'),
      configuration: {domain: 'quick-setup', preset: quickPreset.value, options: quickOptions()}
    }, quickOperationStatus);
    text(quickOperationStatus, operationSummary(operation));
    if (operation.state === 'SUCCEEDED' && operation.approvalToken && previewGeneration === inputGeneration) {
      approvedQuickPreview = {operationId: operation.operationId, approvalToken: operation.approvalToken,
        nodeIds: targets('config.quick-setup.v1')};
      updateConfigurationButtons();
    } else if (previewGeneration !== inputGeneration) {
      text(quickOperationStatus, 'The targets or setup changed while previewing. Preview again before apply.');
    }
  } catch (error) { text(quickOperationStatus, error.message); }
});

applyQuickSetup.addEventListener('click', async () => {
  const sync = approvedQuickPreview?.workflow === 'sync-vote-sites';
  const confirmation = sync
    ? 'Sync the previewed site definitions to every target? Rewards and target-only sites remain unchanged.'
    : quickPreset.value === 'vote-logging'
    ? 'Apply this exact vote-logging change to every selected Bukkit node? Restart every changed backend afterward; a plugin reload does not activate the new runtime connection.'
    : 'Apply this exact guided change to every selected Bukkit node?';
  if (!approvedQuickPreview || !window.confirm(confirmation)) return;
  const approval = approvedQuickPreview;
  approvedQuickPreview = null;
  inputGeneration++;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, quickOperationStatus);
    text(quickOperationStatus, operationSummary(operation));
  } catch (error) { text(quickOperationStatus, error.message); }
});

voteSitesSource.addEventListener('change', () => {
  voteSitesSourceId = voteSitesSource.value;
  voteSitesTargetIds.delete(voteSitesSourceId);
  voteSitesTargetsInitialized = true;
  approvedQuickPreview = null;
  inputGeneration++;
  renderVoteSitesSync();
  text(quickOperationStatus, 'Source changed. Read it and preview every target before syncing.');
  updateConfigurationButtons();
});

transportTestProxy.addEventListener('change', () => {
  transportTestProxyId = transportTestProxy.value;
  transportTestBackendId = '';
  renderTransportTest();
  text(transportTestStatus, 'Choose a backend, then run a live non-vote communication test.');
  updateConfigurationButtons();
});

transportTestBackend.addEventListener('change', () => {
  transportTestBackendId = transportTestBackend.value;
  text(transportTestStatus, 'Ready to test the active VotingPlugin transport.');
  updateConfigurationButtons();
});

runTransportTest.addEventListener('click', async () => {
  const proxyId = transportTestProxyId;
  const server = transportTestBackendId;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [proxyId],
      configuration: {domain: 'quick-setup', preset: 'communication-test', options: {server}}
    }, transportTestStatus);
    if (proxyId !== transportTestProxyId || server !== transportTestBackendId) {
      text(transportTestStatus, 'The proxy or backend changed while testing. Run the test again.');
      return;
    }
    text(transportTestStatus, operationSummary(operation));
  } catch (error) { text(transportTestStatus, error.message); }
});

readProxyMethod.addEventListener('click', async () => {
  const proxyId = proxyMethodProxyId;
  const sessionId = proxyMethodNetwork().proxy?.sessionId;
  if (!proxyId) return;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [proxyId],
      configuration: {domain: 'quick-setup', preset: 'proxy-method', options: {method: 'PLUGINMESSAGING'}}
    }, proxyMethodStatus);
    const result = operation.results[proxyId];
    const method = result?.success ? result.configuration?.options?.method : '';
    if (!method) throw new Error('The proxy did not return its active communication method.');
    if (proxyId !== proxyMethodProxyId || sessionId !== proxyMethodNetwork().proxy?.sessionId) return;
    proxyMethodCurrentFor = proxyId;
    proxyMethodCurrentSessionId = sessionId;
    proxyMethodCurrentValue = method;
    renderProxyMethod();
    text(proxyMethodStatus, `Active method on ${proxyId}: ${method}`);
  } catch (error) { text(proxyMethodStatus, error.message); }
});

proxyMethodProxy.addEventListener('change', () => {
  proxyMethodProxyId = proxyMethodProxy.value;
  proxyMethodCurrentFor = '';
  proxyMethodCurrentSessionId = '';
  proxyMethodCurrentValue = '';
  renderProxyMethod();
  const network = proxyMethodNetwork();
  text(proxyMethodStatus, network.unavailable.length > 0
    ? `Cannot switch yet. Enroll, update, and connect: ${network.unavailable.map(backend => backend.displayName).join(', ')}.`
    : 'Choose a method to preflight every node before applying.');
  updateConfigurationButtons();
});

proxyMethodButtons.forEach(button => button.addEventListener('click', async () => {
  const method = button.dataset.proxyMethod;
  const network = proxyMethodNetwork();
  if (!network.proxyReady || !network.topologyComplete || network.reported.length === 0 ||
      network.nodeIds.length > MAX_OPERATION_TARGETS || network.unavailable.length > 0 || proxyMethodWorkflowInFlight) return;
  proxyMethodWorkflowInFlight = true;
  updateConfigurationButtons();
  try {
    const preview = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds: network.nodeIds,
      configuration: {domain: 'quick-setup', preset: 'proxy-method', options: {method}}
    }, proxyMethodStatus);
    if (preview.state !== 'SUCCEEDED' || !preview.approvalToken) return;
    if (!window.confirm(`Switch ${network.nodeIds.length} VotingPlugin nodes to ${method}? ` +
        'The proxy runtime will restart after Control records the result.')) return;
    const refreshedRegistry = await loadAllNodes();
    const refreshedNetwork = proxyMethodNetworkFor(refreshedRegistry.items, refreshedRegistry.truncatedNodeIds,
      proxyMethodProxyId);
    if (proxyMethodProxyId !== network.proxy.nodeId ||
        refreshedNetwork.proxy?.sessionId !== network.proxy.sessionId ||
        proxyMethodNetworkSignature(refreshedNetwork) !== proxyMethodNetworkSignature(network) ||
        refreshedNetwork.nodeIds.length > MAX_OPERATION_TARGETS) {
      text(proxyMethodStatus, 'The complete proxy topology changed while preflighting. Refresh and choose the method again.');
      return;
    }
    const applied = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: preview.operationId, approvalToken: preview.approvalToken
    }, proxyMethodStatus);
    if (applied.state === 'SUCCEEDED') {
      proxyMethodCurrentFor = network.proxy.nodeId;
      proxyMethodCurrentSessionId = network.proxy.sessionId;
      proxyMethodCurrentValue = method;
      renderProxyMethod();
    }
    const nextStep = applied.state === 'SUCCEEDED'
      ? 'Reconnect the proxy if needed, then run the communication test.'
      : 'No network-wide method change was committed. Fix the failed nodes, refresh the active method, and preview again.';
    text(proxyMethodStatus, `${operationSummary(applied)}\n${nextStep}`);
  } catch (error) {
    text(proxyMethodStatus, error.message);
  } finally {
    proxyMethodWorkflowInFlight = false;
    updateConfigurationButtons();
  }
}));

function dedicatedSetupOptions(preset) {
  if (preset === 'auto-create-vote-sites') return {enabled: String(autoSitesEnabled.checked)};
  return {enabled: String(voteLoggingEnabled.checked), purgeDays: validatedPurgeDays(voteLoggingDays),
    useMainMySQL: String(voteLoggingMainMysql.checked)};
}

function validatedPurgeDays(field) {
  const value = Number(field.value);
  if (!Number.isInteger(value) || value !== -1 && (value < 1 || value > 3650)) {
    throw new Error('Vote-log purge days must be -1 or an integer from 1 to 3650; 0 is not valid.');
  }
  return String(value);
}

function dedicatedSetupElements(preset) {
  return preset === 'auto-create-vote-sites'
    ? {status: autoSitesStatus, state: autoSitesState}
    : {status: voteLoggingStatus, state: voteLoggingState};
}

async function loadDedicatedSetup(preset) {
  dedicatedSetupApprovals.delete(preset);
  const elements = dedicatedSetupElements(preset);
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds: [selectedServerId], configuration: {domain: 'quick-setup', preset, options: {}}
    }, elements.status);
    const options = operation.results[selectedServerId]?.configuration?.options;
    if (!options) throw new Error('The selected backend did not return this setup. Update VotingPlugin on that node.');
    if (preset === 'auto-create-vote-sites') {
      autoSitesEnabled.checked = options.enabled === 'true';
      text(autoSitesState, autoSitesEnabled.checked ? 'Enabled on primary' : 'Disabled on primary');
    } else {
      voteLoggingEnabled.checked = options.enabled === 'true';
      voteLoggingDays.value = options.purgeDays || '30';
      voteLoggingMainMysql.checked = options.useMainMySQL !== 'false';
      text(voteLoggingState, voteLoggingEnabled.checked ? 'Enabled on primary' : 'Disabled on primary');
    }
    elements.state.className = `pill ${options.enabled === 'true' ? 'online' : 'neutral'}`;
  } catch (error) { text(elements.status, error.message); }
  updateExtendedButtons();
}

async function previewDedicatedSetup(preset) {
  dedicatedSetupApprovals.delete(preset);
  const elements = dedicatedSetupElements(preset);
  try {
    const nodeIds = backendQuickTargets();
    const options = dedicatedSetupOptions(preset);
    const signature = JSON.stringify({nodeIds, options});
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds, configuration: {domain: 'quick-setup', preset, options}
    }, elements.status);
    if (signature !== JSON.stringify({nodeIds: backendQuickTargets(), options: dedicatedSetupOptions(preset)})) {
      text(elements.status, 'The target scope or setup value changed while previewing. Preview again.');
    } else if (operation.state === 'SUCCEEDED' && operation.approvalToken) {
      dedicatedSetupApprovals.set(preset, {operationId: operation.operationId,
        approvalToken: operation.approvalToken, nodeIds});
    }
  } catch (error) { text(elements.status, error.message); }
  updateExtendedButtons();
}

async function applyDedicatedSetup(preset) {
  const approval = dedicatedSetupApprovals.get(preset);
  const restart = preset === 'vote-logging'
    ? ' Restart every changed backend afterward; a plugin reload does not activate the new runtime connection.' : '';
  if (!approval || !window.confirm(`Apply the exact ${preset} preview to every selected Bukkit node?${restart}`)) return;
  dedicatedSetupApprovals.delete(preset);
  const elements = dedicatedSetupElements(preset);
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, elements.status);
    if (operation.state === 'SUCCEEDED') {
      fileReadCache.clear();
      if (preset === 'auto-create-vote-sites') {
        text(elements.state, autoSitesEnabled.checked ? 'Enabled on selected' : 'Disabled on selected');
        elements.state.className = `pill ${autoSitesEnabled.checked ? 'online' : 'neutral'}`;
      } else {
        text(elements.state, 'Saved; restart required');
        elements.state.className = 'pill neutral';
      }
      lastOverview = null;
    }
  } catch (error) { text(elements.status, error.message); }
  updateExtendedButtons();
}

loadAutoSites.addEventListener('click', () => loadDedicatedSetup('auto-create-vote-sites'));
previewAutoSites.addEventListener('click', () => previewDedicatedSetup('auto-create-vote-sites'));
applyAutoSites.addEventListener('click', () => applyDedicatedSetup('auto-create-vote-sites'));
selectAllAutoSitesTargets.addEventListener('click', () => {
  const available = allNodeItems.filter(node => isBackend(node) && node.online
    && node.acceptedCapabilities.includes('config.quick-setup.v1'));
  const candidates = available
    .sort((left, right) => Number(right.nodeId === selectedServerId) - Number(left.nodeId === selectedServerId))
    .slice(0, MAX_CONFIGURATION_TARGETS);
  selectedNodes = new Set(candidates.map(node => node.nodeId));
  dedicatedSetupApprovals.clear();
  approvedPreview = null;
  approvedFilePreview = null;
  approvedQuickPreview = null;
  inputGeneration++;
  renderNodeViews();
  updatePluginSuggestions();
  updateConfigurationButtons();
  text(autoSitesStatus, `${candidates.length} online ${candidates.length === 1 ? 'backend is' : 'backends are'} selected${available.length > candidates.length
    ? ` (limited to ${MAX_CONFIGURATION_TARGETS} per operation)` : ''}. Choose enabled or disabled, then preview every target.`);
});
loadVoteLogging.addEventListener('click', () => loadDedicatedSetup('vote-logging'));
previewVoteLogging.addEventListener('click', () => previewDedicatedSetup('vote-logging'));
applyVoteLogging.addEventListener('click', () => applyDedicatedSetup('vote-logging'));
[autoSitesEnabled, voteLoggingEnabled, voteLoggingDays, voteLoggingMainMysql].forEach(field => {
  field.addEventListener('input', () => {
    dedicatedSetupApprovals.delete(field === autoSitesEnabled ? 'auto-create-vote-sites' : 'vote-logging');
    updateExtendedButtons();
  });
});

async function refreshOverview(target = dataOverview) {
  try {
    const envelope = await runInspection('overview', {}, target);
    lastOverview = {...(lastOverview || {}), ...envelope.result};
    renderJsonResult(target, envelope.result);
    updateSetupChecklist(lastOverview);
  } catch (error) { text(target, error.message); }
}

refreshSetupChecklist.addEventListener('click', async () => {
  try {
    const envelope = await runInspection('diagnostics', {}, setupChecklistStatus);
    lastOverview = envelope.result;
    updateSetupChecklist(envelope.result);
  } catch (error) { text(setupChecklistStatus, error.message); }
});
refreshDataOverview.addEventListener('click', () => refreshOverview(dataOverview));

runNetworkDoctor.addEventListener('click', async () => {
  downloadNetworkDiagnostics.disabled = true;
  lastDiagnostics = null;
  try {
    const diagnostics = await runInspection('diagnostics', {}, networkDoctorResults);
    lastOverview = diagnostics.result;
    const node = nodeIndex.get(selectedServerId);
    const checks = {
      controlConnected: Boolean(node?.online),
      configurationHealthy: diagnostics.result.configurationHealthy,
      votifierDetected: diagnostics.result.votifierDetected,
      voteSitesConfigured: Number(diagnostics.result.configuredVoteSites) > 0,
      processRewards: diagnostics.result.processRewards,
      voteLoggingEnabled: diagnostics.result.voteLoggingEnabled,
      topologyReported: isBackend(node) ? proxyReportsFor(node.nodeId).length > 0 || !diagnostics.result.proxyMode : true
    };
    lastDiagnostics = {
      schemaVersion: 1, generatedAt: new Date().toISOString(), selectedNodeId: selectedServerId,
      checks, node: diagnostics.result,
      control: {application: 'VotingPlugin Control', registeredNodes: allNodeItems.length,
        nodes: allNodeItems.slice(0, 100).map(item => ({nodeId: item.nodeId, displayName: item.displayName,
          role: roleLabel(item), online: item.online, pluginVersion: item.pluginVersion}))}
    };
    renderJsonResult(networkDoctorResults, lastDiagnostics);
    updateSetupChecklist(diagnostics.result);
    downloadNetworkDiagnostics.disabled = false;
  } catch (error) { text(networkDoctorResults, error.message); }
});

downloadNetworkDiagnostics.addEventListener('click', () => {
  if (lastDiagnostics) downloadJson(`votingplugin-diagnostics-${selectedServerId || 'node'}.json`, lastDiagnostics);
});

runDriftCheck.addEventListener('click', async () => {
  const nodeIds = targets('config.files.v1');
  const selectedFile = driftFile.value;
  const requestAuthenticationGeneration = authenticationGeneration;
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/read', {
      nodeIds, configuration: {domain: 'file', fileName: selectedFile}
    }, driftResults);
    if (requestAuthenticationGeneration !== authenticationGeneration) {
      throw new Error('Authentication changed while the drift check ran. Run it again.');
    }
    const rows = nodeIds.map(nodeId => {
      const result = operation.results[nodeId];
      return {nodeId, success: Boolean(result?.success), revision: result?.revision || null,
        content: result?.configuration?.content ?? null, error: result?.success ? null : result?.message || result?.code};
    });
    const comparable = rows.filter(row => row.success && typeof row.content === 'string');
    const contentNotRetained = rows.filter(row => row.success && typeof row.content !== 'string')
      .map(row => row.nodeId);
    const groups = new Map();
    comparable.forEach(row => {
      const key = row.revision || row.content;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(row.nodeId);
    });
    lastFileReadOperation = comparable.length > 0 ? {operationId: operation.operationId} : null;
    comparable.forEach(row => {
      const session = nodeIndex.get(row.nodeId)?.sessionId || '';
      cacheFile(`${row.nodeId}|${session}|${selectedFile}`, row.content, operation.operationId);
    });
    const baseline = comparable[0];
    const differences = comparable.filter(row => row !== baseline).map(row => {
      const left = baseline.content.split('\n');
      const right = row.content.split('\n');
      const changes = [];
      for (let index = 0; index < Math.max(left.length, right.length) && changes.length < 50; index++) {
        if (left[index] !== right[index]) changes.push({line: index + 1,
          baseline: String(left[index] ?? '').slice(0, 200), target: String(right[index] ?? '').slice(0, 200)});
      }
      return {baselineNode: baseline.nodeId, targetNode: row.nodeId, changes,
        truncated: changes.length === 50};
    });
    renderJsonResult(driftResults, {fileName: selectedFile, driftDetected: groups.size > 1,
      warning: contentNotRetained.length > 0
        ? 'Some successful file bodies exceeded Control’s 8 MiB aggregate retention bound. Compare fewer targets in batches.' : null,
      contentNotRetained,
      revisionGroups: [...groups.entries()].map(([revision, nodes]) => ({revision, nodes})),
      nodes: rows.map(({content, ...row}) => ({...row, contentBytes: content == null ? 0 : new Blob([content]).size})),
      differences});
  } catch (error) { text(driftResults, error.message); }
  updateExtendedButtons();
});

async function loadSnapshots() {
  try {
    const body = await authorized('/api/v1/snapshots');
    snapshotList.replaceChildren();
    if (!Array.isArray(body.items) || body.items.length === 0) {
      text(snapshotList, 'No snapshots saved yet.');
      return;
    }
    body.items.forEach(snapshot => {
      const item = document.createElement('article');
      item.className = 'result-item';
      const detail = document.createElement('div');
      detail.append(text(document.createElement('strong'), snapshot.name));
      detail.append(text(document.createElement('small'), `${new Date(snapshot.createdAt).toLocaleString()} · ${snapshot.documents.length} document(s)`));
      const restore = text(document.createElement('button'), 'Load for restore preview');
      restore.type = 'button';
      restore.className = 'secondary compact';
      restore.addEventListener('click', async () => {
        restore.disabled = true;
        try {
          const full = await authorized(`/api/v1/snapshots/${snapshot.snapshotId}`);
          const document = full.documents.find(value => value.nodeId === selectedServerId) || full.documents[0];
          if (!document) throw new Error('This snapshot has no restorable document.');
          if (!nodeCapabilities.get(selectedServerId)?.includes('config.files.v1')) {
            throw new Error('Choose a connected file-capable Bukkit node before restoring.');
          }
          configurationFile.value = document.fileName;
          configurationContent.value = document.content;
          configurationContentPresent = true;
          lastFileReadOperation = null;
          updateEditorPosition();
          approvedFilePreview = null;
          inputGeneration++;
          setActiveTab('configurations', true);
          setConfigView('yaml');
          text(fileOperationStatus, `Loaded snapshot “${full.name}” from ${document.nodeId}. Preview the complete file, review the exact changes, then approve to restore it to the selected targets.`);
          updateConfigurationButtons();
          updateExtendedButtons();
        } catch (error) { text(snapshotStatus, error.message); }
        finally { restore.disabled = false; }
      });
      item.append(detail, restore);
      snapshotList.append(item);
    });
  } catch (error) { text(snapshotStatus, error.message); }
}

snapshotForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (!lastFileReadOperation) return;
  try {
    const created = await authorized('/api/v1/snapshots', {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({name: snapshotName.value.trim(), operationId: lastFileReadOperation.operationId})
    });
    snapshotName.value = '';
    text(snapshotStatus, `Saved snapshot “${created.name}”.`);
    await loadSnapshots();
  } catch (error) { text(snapshotStatus, error.message); }
});
refreshSnapshots.addEventListener('click', loadSnapshots);

playerLookupForm.addEventListener('submit', async event => {
  event.preventDefault();
  const value = playerLookup.value.trim();
  const filter = /^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(value) ? {uuid: value} : {name: value};
  try { renderJsonResult(playerResult, (await runInspection('player', filter, playerResult)).result); }
  catch (error) { text(playerResult, error.message); }
});

loadSiteHealth.addEventListener('click', async () => {
  try { renderSiteHealthResult((await runInspection('vote-site-health', {days: '30'}, siteHealthResult)).result); }
  catch (error) { text(siteHealthResult, error.message); }
});

loadVoteLogSummary.addEventListener('click', async () => {
  try { renderJsonResult(voteLogSummaryResult,
    (await runInspection('vote-log-summary', {days: '30'}, voteLogSummaryResult)).result); }
  catch (error) { text(voteLogSummaryResult, error.message); }
});

voteLogFilterType.addEventListener('change', () => {
  voteLogFilter.disabled = !voteLogFilterType.value;
  voteLogFilter.required = Boolean(voteLogFilterType.value);
  voteLogFilter.placeholder = voteLogFilterType.value ? `Exact ${voteLogFilterType.value}` : '';
});
voteLogForm.addEventListener('submit', async event => {
  event.preventDefault();
  const filters = {days: voteLogDays.value, limit: voteLogLimit.value};
  if (voteLogFilterType.value) filters[voteLogFilterType.value] = voteLogFilter.value.trim();
  if (voteLogEvent.value) filters.event = voteLogEvent.value;
  try { renderJsonResult(voteLogResult, (await runInspection('vote-log-search', filters, voteLogResult)).result); }
  catch (error) { text(voteLogResult, error.message); }
});

voteTraceForm.addEventListener('submit', async event => {
  event.preventDefault();
  try { renderJsonResult(voteTraceResult, (await runInspection('vote-trace',
    {voteId: voteTraceId.value.trim(), days: voteLogDays.value, limit: '100'}, voteTraceResult)).result); }
  catch (error) { text(voteTraceResult, error.message); }
});

siteResolutionForm.addEventListener('submit', async event => {
  event.preventDefault();
  try { renderJsonResult(siteResolutionResult, (await runInspection('vote-site-resolution',
    {serviceSite: siteResolutionService.value.trim(), includeDisabled: String(siteResolutionDisabled.checked)}, siteResolutionResult)).result); }
  catch (error) { text(siteResolutionResult, error.message); }
});

rewardScope.addEventListener('change', () => { rewardSiteLabel.hidden = rewardScope.value !== 'site'; });
function rewardProposal() {
  const items = boundedLines(rewardItems.value).map(line => {
    const match = line.match(/^([A-Za-z0-9_]{1,80})\s+([0-9]{1,2})$/);
    if (!match || Number(match[2]) < 1 || Number(match[2]) > 64) {
      throw new Error(`Invalid item “${line}”. Use MATERIAL and an amount from 1 to 64.`);
    }
    return {material: match[1].toUpperCase(), amount: Number(match[2])};
  });
  const proposal = {scope: rewardScope.value, commands: boundedLines(rewardCommands.value),
    playerMessages: boundedLines(rewardMessages.value), broadcastMessages: boundedLines(rewardBroadcasts.value),
    items, permissions: boundedLines(rewardPermissions.value), money: Number(rewardMoney.value),
    chancePercent: Number(rewardChance.value), onlineOnly: rewardOnlineOnly.checked};
  if (rewardScope.value === 'site') proposal.site = rewardSite.value.trim();
  return proposal;
}

rewardSimulationForm.addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const proposal = rewardProposal();
    const envelope = await runInspection('reward-simulation', {proposal: JSON.stringify(proposal)}, rewardSimulationResult);
    renderJsonResult(rewardSimulationResult, envelope.result);
    copyRewardToSetup.disabled = proposal.commands.length === 0;
  } catch (error) { text(rewardSimulationResult, error.message); }
});
previewReward.addEventListener('click', async () => {
  dedicatedSetupApprovals.delete('reward-builder');
  try {
    const proposal = JSON.stringify(rewardProposal());
    if (new TextEncoder().encode(proposal).length > 64 * 1024) throw new Error('Reward proposal exceeds the 64 KiB limit.');
    const nodeIds = backendQuickTargets();
    const signature = JSON.stringify({nodeIds, proposal});
    const operation = await startConfigurationOperation('/api/v1/configuration/preview', {
      nodeIds,
      configuration: {domain: 'quick-setup', preset: 'reward-builder', options: {proposal}}
    }, rewardSimulationResult);
    if (signature !== JSON.stringify({nodeIds: backendQuickTargets(), proposal: JSON.stringify(rewardProposal())})) {
      text(rewardSimulationResult, 'The target scope or reward changed while previewing. Preview again.');
    } else if (operation.state === 'SUCCEEDED' && operation.approvalToken) {
      dedicatedSetupApprovals.set('reward-builder', {operationId: operation.operationId,
        approvalToken: operation.approvalToken, nodeIds});
    }
  } catch (error) { text(rewardSimulationResult, error.message); }
  updateExtendedButtons();
});
applyReward.addEventListener('click', async () => {
  const approval = dedicatedSetupApprovals.get('reward-builder');
  if (!approval || !window.confirm('Apply this exact reward preview to every selected Bukkit node? It replaces the selected Rewards subtree; sibling sites, scopes, and settings remain unchanged.')) return;
  dedicatedSetupApprovals.delete('reward-builder');
  try {
    const operation = await startConfigurationOperation('/api/v1/configuration/apply', {
      previewOperationId: approval.operationId, approvalToken: approval.approvalToken
    }, rewardSimulationResult);
    if (operation.state === 'SUCCEEDED') fileReadCache.clear();
  } catch (error) { text(rewardSimulationResult, error.message); }
  updateExtendedButtons();
});
[rewardScope, rewardSite, rewardChance, rewardMoney, rewardCommands, rewardMessages, rewardBroadcasts,
  rewardPermissions, rewardItems, rewardOnlineOnly].forEach(field => field.addEventListener('input', () => {
    dedicatedSetupApprovals.delete('reward-builder');
    copyRewardToSetup.disabled = boundedLines(rewardCommands.value).length === 0;
    updateExtendedButtons();
  }));
copyRewardToSetup.addEventListener('click', () => {
  const command = boundedLines(rewardCommands.value)[0];
  if (!command) return;
  pendingDetectedVoteSite = null;
  quickPreset.value = 'easy-reward';
  quickRewardScope.value = rewardScope.value === 'site' ? 'site' : 'every-site';
  quickName.value = rewardScope.value === 'site' ? rewardSite.value.trim() : '';
  quickCommand.value = command;
  quickMessage.value = boundedLines(rewardMessages.value)[0] || '';
  updateQuickFields();
  clearApprovals();
  document.querySelector('#quick-setup-card').scrollIntoView({behavior: 'smooth', block: 'start'});
});

settingsFilter.addEventListener('input', renderSettingsCatalog);

saveProfile.addEventListener('click', () => {
  const name = profileName.value.trim();
  if (!name || name.length > 60 || /[\p{Cc}]/u.test(name)) {
    text(profileStatus, 'Enter a profile name between 1 and 60 visible characters.');
    return;
  }
  try {
    const profiles = readProfiles();
    if (!Object.hasOwn(profiles, name) && Object.keys(profiles).length >= 20) throw new Error('Delete a profile before saving another; the limit is 20.');
    profiles[name] = currentProfileValues();
    writeProfiles(profiles);
    populateProfilePicker();
    profilePicker.value = name;
    profilePicker.dispatchEvent(new Event('change'));
    text(profileStatus, `Saved “${name}” on this browser. It contains the visible setup form values, including entered URLs and commands, but no raw YAML or Control/database credentials.`);
  } catch (error) { text(profileStatus, error.message || 'The browser could not save this profile.'); }
});

profilePicker.addEventListener('change', () => {
  loadProfile.disabled = !profilePicker.value;
  deleteProfile.disabled = !profilePicker.value;
});
loadProfile.addEventListener('click', () => {
  const profile = readProfiles()[profilePicker.value];
  if (!profile || profile.version !== 1) { text(profileStatus, 'That profile is unavailable or unsupported.'); return; }
  pendingDetectedVoteSite = null;
  const assign = (field, value, max = 500) => { field.value = String(value ?? '').slice(0, max); };
  if ([...quickPreset.options].some(option => option.value === profile.preset)) quickPreset.value = profile.preset;
  assign(quickName, profile.name, 64); assign(quickMethod, profile.method, 32);
  assign(quickSiteDisplayName, profile.siteDisplayName, 200); assign(quickService, profile.service, 200);
  assign(quickUrl, profile.url, 500); assign(quickDelay, profile.delay, 20);
  assign(quickSitePriority, profile.priority, 3); assign(quickSiteMaterial, profile.material, 100);
  quickSiteEnabled.checked = Boolean(profile.siteEnabled); quickSiteHidden.checked = Boolean(profile.siteHidden);
  assign(quickRewardScope, profile.rewardScope, 20); assign(quickCommand, profile.command, 500);
  assign(quickMessage, profile.playerMessage, 500); quickProcessRewards.checked = Boolean(profile.processRewards);
  quickAutoSites.checked = Boolean(profile.autoSites); quickExtraCheck.checked = Boolean(profile.extraCheck);
  quickCountFake.checked = Boolean(profile.countFake); quickHideSiteWarning.checked = Boolean(profile.hideWarning);
  quickDisableUpdates.checked = Boolean(profile.disableUpdates); assign(quickPartyVotes, profile.partyVotes, 6);
  assign(quickPartyCommand, profile.partyCommand, 500); assign(quickPartyBroadcast, profile.partyBroadcast, 500);
  quickPartyAll.checked = Boolean(profile.partyAll); quickPartyOnline.checked = Boolean(profile.partyOnline);
  quickAutoSitesOnly.checked = Boolean(profile.autoSitesOnly); quickVoteLoggingEnabled.checked = Boolean(profile.voteLogging);
  assign(quickVoteLoggingDays, profile.voteLoggingDays, 4); quickVoteLoggingMainMysql.checked = Boolean(profile.voteLoggingMainMysql);
  if (profile.rewardBuilder && typeof profile.rewardBuilder === 'object') {
    assign(rewardScope, profile.rewardBuilder.scope, 20); assign(rewardSite, profile.rewardBuilder.site, 64);
    assign(rewardChance, profile.rewardBuilder.chance, 8); assign(rewardMoney, profile.rewardBuilder.money, 20);
    assign(rewardCommands, profile.rewardBuilder.commands, 10020); assign(rewardMessages, profile.rewardBuilder.messages, 10020);
    assign(rewardBroadcasts, profile.rewardBuilder.broadcasts, 10020); assign(rewardPermissions, profile.rewardBuilder.permissions, 4020);
    assign(rewardItems, profile.rewardBuilder.items, 2020); rewardOnlineOnly.checked = Boolean(profile.rewardBuilder.onlineOnly);
    rewardSiteLabel.hidden = rewardScope.value !== 'site';
    copyRewardToSetup.disabled = boundedLines(rewardCommands.value).length === 0;
  }
  loadedQuickSetup = null;
  updateQuickFields();
  clearApprovals();
  text(profileStatus, `Loaded “${profilePicker.value}”. Load live values first if this preset edits existing configuration.`);
});
deleteProfile.addEventListener('click', () => {
  const name = profilePicker.value;
  if (!name || !window.confirm(`Delete browser-local setup profile “${name}”?`)) return;
  try {
    const profiles = readProfiles();
    delete profiles[name];
    writeProfiles(profiles);
    populateProfilePicker();
    text(profileStatus, `Deleted “${name}”.`);
  } catch (error) { text(profileStatus, error.message || 'The browser could not delete this profile.'); }
});

clearOperationHistory.addEventListener('click', loadOperationHistory);

[quickName, quickMethod, quickSiteDisplayName, quickService, quickUrl, quickDelay,
  quickSitePriority, quickSiteMaterial, quickSiteEnabled, quickSiteHidden, quickRewardScope,
  quickCommand, quickMessage, quickProcessRewards, quickAutoSites, quickExtraCheck, quickCountFake,
  quickHideSiteWarning, quickDisableUpdates, quickPartyVotes, quickPartyCommand, quickPartyBroadcast,
  quickPartyAll, quickPartyOnline, quickAutoSitesOnly, quickVoteLoggingEnabled, quickVoteLoggingDays,
  quickVoteLoggingMainMysql].forEach(field => field.addEventListener('input', clearApprovals));
quickName.addEventListener('input', () => {
  if (pendingDetectedVoteSite && pendingDetectedVoteSite.key !== quickName.value.trim()) pendingDetectedVoteSite = null;
  updateQuickFields();
});
configurationContent.addEventListener('input', () => {
  configurationContentPresent = true;
  clearApprovals();
  updateEditorPosition();
});
configurationContent.addEventListener('click', updateEditorPosition);
configurationContent.addEventListener('keyup', updateEditorPosition);
configurationContent.addEventListener('keydown', handleEditorKeydown);
configurationFile.addEventListener('input', () => {
  configurationContent.value = '';
  configurationContentPresent = false;
  lastFileReadOperation = null;
  updateEditorPosition();
  text(fileOperationStatus, 'Read the selected file before previewing changes.');
  clearApprovals();
  updateExtendedButtons();
});
quickPreset.addEventListener('input', () => {
  loadedQuickSetup = null;
  if (quickPreset.value !== 'vote-site') pendingDetectedVoteSite = null;
  updateQuickFields();
  clearApprovals();
  if (quickPresetNeedsRead()) {
    text(quickOperationStatus, quickPreset.value === 'vote-site'
      ? 'Enter the vote-site key, then load its current values before previewing.'
      : 'Load the current values from the primary server before previewing changes.');
  }
});
serverPicker.addEventListener('change', () => selectPrimaryServer(serverPicker.value));
tabButtons.forEach(button => button.addEventListener('click', () => setActiveTab(button.dataset.tab, true)));
configViewButtons.forEach(button => button.addEventListener('click', () => setConfigView(button.dataset.configView)));
document.querySelectorAll('[data-open-tab]').forEach(button => button.addEventListener('click', () => {
  setActiveTab(button.dataset.openTab, true);
}));
document.querySelectorAll('[data-open-config-view]').forEach(button => button.addEventListener('click', () => {
  setActiveTab('configurations', true);
  setConfigView(button.dataset.openConfigView);
  if (button.dataset.file && configurationFile.value !== button.dataset.file) {
    configurationFile.value = button.dataset.file;
    configurationFile.dispatchEvent(new Event('input'));
  }
}));
window.addEventListener('hashchange', () => setActiveTab(tabFromHash()));
refresh.addEventListener('click', loadNodes);
previousPage.addEventListener('click', () => {
  pageOffset = Math.max(0, pageOffset - PAGE_SIZE);
  loadNodes();
});
nextPage.addEventListener('click', () => {
  pageOffset += PAGE_SIZE;
  loadNodes();
});
async function initialize() {
  await loadHealth();
  setActiveTab(tabFromHash());
  setConfigView('easy');
  updateQuickFields();
  updatePluginSuggestions();
  renderSettingsCatalog();
  populateProfilePicker();
  rewardSiteLabel.hidden = rewardScope.value !== 'site';
  updateExtendedButtons();
  if (!await loadSetupState()) {
    await restoreSession();
  }
}
initialize();
